# Owlsplit: sincronización offline-first para gastos compartidos

Cómo construí una sincronización local-first y unas cuentas exactas con enteros para que cada dispositivo, e incluso un navegador sin la app, calcule los mismos saldos.

## Contexto

[Owlsplit](https://github.com/Sebas1705/Owlsplit) es una app Kotlin Multiplatform (Android e iOS) para repartir gastos en grupo. Varias personas editan el mismo grupo desde su móvil, muchas veces de viaje y sin cobertura. El backend es Firebase (Firestore y Auth) en el plan gratuito Spark, así que no hay un servidor propio que arbitre conflictos o calcule saldos: lo hace cada cliente.

## Problema

Dos requisitos tiran en direcciones opuestas:

- **Ediciones offline en varios dispositivos.** La app debe funcionar entera sin conexión, encolar escrituras y reconciliarlas después sin perder en silencio la edición de nadie.
- **El mismo dinero en todos los dispositivos.** Cada móvil calcula los saldos en local. Un céntimo de diferencia por redondeo hace que dos personas vean deudas distintas y que la suma del grupo deje de ser cero.

La caché offline de Firestore solo resuelve lo primero a medias: no es una base de datos consultable, y yo quería saldos y estadísticas como consultas SQL locales. La cuota gratuita (50.000 lecturas al día) era otra restricción.

## Enfoque

**Almacenamiento local-first.** El [ADR 0003](https://github.com/Sebas1705/Owlsplit/blob/main/docs/adr/0003-local-first-sqldelight-sync.md) convierte SQLDelight en la única fuente de verdad de la UI; Firestore es solo el medio de intercambio. La persistencia del SDK de Firestore está desactivada para que no haya dos cachés divergentes. Una escritura local es una sola transacción: la entidad pasa a `PENDING` con `version = serverVersion + 1` y se inserta una fila en el `outbox` (o se *compacta* con la pendiente): diez ediciones offline del mismo gasto se suben como una, con el `baseVersion` original.

**Concurrencia optimista, no "gana la última escritura".** Las subidas son FIFO estricto por grupo con backoff exponencial (`min(2^intentos s, 15 min)` más jitter). Las reglas de seguridad solo aceptan `version == anterior + 1`, así que gana la primera escritura que llega al servidor. Ante un rechazo, el motor lee el documento remoto: misma versión y mismo contenido es nuestra escritura con el acuse perdido; versión mayor con contenido distinto es un **conflicto**, que va a la tabla `conflict` y al historial con *Reaplicar* o *Descartar*. Descarté "gana la última por timestamp" porque cuesta lo mismo y pierde ediciones sin avisar. Firestore real destapó un caso sutil: el listener entrega nuestra escritura *antes* de que `set` devuelva, y sin recordar la subida en curso todo eran falsos conflictos. Detalles en [docs/SYNC.md](https://github.com/Sebas1705/Owlsplit/blob/main/docs/SYNC.md).

**Descarga guiada por el historial.** En lugar de un listener por colección, cada grupo tiene uno solo sobre su `changelog` (solo anexado), y cada entrada indica qué documento descargar. Como el outbox sube el documento antes que su entrada, el documento ya existe cuando llega. Según el modelo de costes de SYNC.md, un arranque sin cambios con 10 grupos (3 archivados) pasa de unas 50 lecturas a unas 17.

**El dinero, en enteros.** El [ADR 0005](https://github.com/Sebas1705/Owlsplit/blob/main/docs/adr/0005-money-as-minor-units.md) y [docs/DOMAIN.md](https://github.com/Sebas1705/Owlsplit/blob/main/docs/DOMAIN.md) fijan las reglas: importes como `Long` en unidades mínimas según los decimales ISO 4217 (nunca `Double`), tipos de cambio como `Long` con escala 10⁹ congelados en cada gasto, y repartos por el método del resto mayor con desempate determinista:

```kotlin
fun byWeights(total: Long, weights: List<Long>): List<Long> {
    val weightSum = weights.sum()
    val parts = LongArray(weights.size)
    val remainders = LongArray(weights.size)
    weights.forEachIndexed { i, w ->
        val qr = mulDivRem(total, w, weightSum)
        parts[i] = qr.quotient; remainders[i] = qr.remainder
    }
    val leftover = total - parts.sum()
    weights.indices
        .sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
        .take(leftover.toInt()).forEach { parts[it]++ }
    return parts.toList()
}
```

10,00 € entre tres siempre da 3,34 / 3,33 / 3,33, con el céntimo extra en el mismo miembro en todos los móviles. En otra moneda se convierte el *neto* de cada miembro y el desajuste de redondeo se reparte igual: los saldos siguen sumando cero. Un simplificador voraz determinista salda las deudas en como mucho `n − 1` pagos.

**Una segunda implementación, vigilada con fixtures.** La [web para invitados](https://github.com/Sebas1705/Owlsplit/blob/main/docs/designs/2026-10-16-web-guests.md) en `/g/{code}` permite unirse desde el navegador. Allí no corren los módulos KMP, así que porté el cálculo a JavaScript con BigInt ([money.js](https://github.com/Sebas1705/Owlsplit/blob/main/firebase/hosting/js/money.js)). Para que no diverjan, [LedgerFixturesTest](https://github.com/Sebas1705/Owlsplit/blob/main/domain/usecases/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/domain/usecases/balances/LedgerFixturesTest.kt) genera desde Kotlin 60 grupos aleatorios con semilla fija (varias monedas, los cuatro tipos de reparto, alguna entrada incoherente) y los saldos y pagos que calcula la app en [ledger.json](https://github.com/Sebas1705/Owlsplit/blob/main/firebase/rules-test/fixtures/ledger.json). Falla si el fichero está desactualizado, y una suite de Vitest comprueba que `money.js` reproduce cada caso al céntimo.

## Resultado

- El motor de sync tiene tests unitarios contra un almacén falso (compactación, orden, backoff, ambas ramas de conflicto, pérdida de acceso, muerte del proceso en plena subida) y un test con dos dispositivos contra los emuladores de Firestore y Auth con las reglas reales.
- El repositorio tiene 322 funciones `@Test` en Kotlin; los fixtures suman 60 casos entre lenguajes.
- [Kover](https://github.com/Sebas1705/Owlsplit/pull/81) da un 79,6 % de cobertura de líneas combinada (6.978 / 8.769), con un mínimo del 75 % exigido en CI. `core:money` está al 99 %, `domain:usecases` al 97 % y `data:repository` (el motor de sync) al 92 %.

## Lecciones

- **El determinismo es una propiedad de diseño, no un resultado de los tests.** Con enteros y desempates fijos, "todos los dispositivos coinciden" pasó a ser algo enunciable y comprobable.
- **Si la lógica se duplica entre lenguajes, el oráculo se genera desde un lado.** Los fixtures compartidos hicieron de un port arriesgado una comprobación mecánica.
- **Compromisos aceptados:** sincronización por documento (sin fusión de campos), descarga que asume que toda escritura registra su entrada, un simplificador que no garantiza el mínimo de pagos, y `data:local` y `data:remote` sin tests unitarios.
- **Siguiente paso:** llevar a CI el test de sync con emuladores (hoy requiere un emulador Android conectado), cubrir los adaptadores local y remoto, y mover la purga remota de borrados a Cloud Functions, como prevé la documentación.
