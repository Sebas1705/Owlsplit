# Owlo — Núcleo (MVP): diseño

- **Fecha:** 2026-10-07
- **Estado:** aprobado (pendiente de revisión final del documento escrito)
- **Subproyecto:** 1 de 5 (ver [ROADMAP](../ROADMAP.md))
- **Inspiración:** Settle Up (Play Store) — mismo problema, mejor diseño y más funciones

## 1. Qué es Owlo

App móvil para apuntar gastos compartidos en grupo (viajes, pisos, cenas, regalos) y saber en todo momento quién debe a quién y cómo saldar con el menor número de pagos. Funciona sin conexión y se sincroniza entre todos los miembros del grupo.

### Objetivos del MVP

1. Apuntar un gasto normal en **menos de 10 segundos** (importe → concepto → guardar, con los valores por defecto del grupo).
2. Ver el saldo propio y del grupo **sin leer números** (jerarquía visual + barras).
3. Funcionar **100 % sin conexión** y converger al reconectar, sin perder datos.
4. Unirse a un grupo **sin registrarse** (cuenta anónima, enlace o QR).
5. Que todos los dispositivos calculen **exactamente** los mismos saldos (aritmética entera determinista).

### No objetivos del MVP

Liquidación con Bizum/PayPal/Revolut, recordatorios push, gastos recurrentes, presupuestos, estadísticas, exportación, escaneo de tickets, adjuntos, versión web y escritorio. Están en el [ROADMAP](../ROADMAP.md); el modelo de datos ya reserva sus campos.

## 2. Plataformas y stack

| Decisión | Elección | ADR |
|---|---|---|
| Plataformas | Android + iOS con **Kotlin Multiplatform** y **Compose Multiplatform** | [0001](../adr/0001-kotlin-multiplatform.md) |
| Base del proyecto | Plantilla Templetry `kmp/modular-features` (versiones actualizadas al generar) | [0001](../adr/0001-kotlin-multiplatform.md) |
| Backend | **Firebase** (Auth, Firestore) con el SDK KMP de GitLive | [0002](../adr/0002-firebase-gitlive.md) |
| Persistencia y sincronización | **SQLDelight** como fuente de verdad + motor de sincronización propio (outbox) | [0003](../adr/0003-local-first-sqldelight-sync.md) |
| Cuentas | Firebase Auth **anónimo** + vinculación Google / Apple | [0004](../adr/0004-anonymous-auth-linking.md) |
| Dinero | Enteros `Long` en unidades mínimas, reparto determinista de restos | [0005](../adr/0005-money-as-minor-units.md) |
| Dirección visual | **Fintech limpio** (estilo Revolut/Monzo) | [0006](../adr/0006-fintech-visual-direction.md) |
| Arquitectura de UI | MVI de la plantilla, Koin, Navigation tipada | plantilla |
| Tipos de cambio | Frankfurter (BCE), sin clave, con caché; tipo manual como alternativa | [DOMAIN](../DOMAIN.md#divisas) |

- Package / bundle id: `es.sebas1705.owlo`
- Restricción: **iOS no compila en Windows**. El desarrollo diario se hace en Android; iOS se compila en CI (runner macOS) o en un Mac.
- Perfiles `development` / `staging` / `production` (de la plantilla), cada uno con su proyecto Firebase y su configuración en Doppler (`owlo`).

## 3. Alcance funcional del MVP

### 3.1 Cuenta y onboarding
- Al primer arranque se crea una **cuenta anónima** automáticamente; se pide solo nombre y avatar (foto opcional o avatar de iniciales).
- En Perfil: **vincular** Google (Android e iOS) y Apple (iOS; obligatorio en App Store si se ofrece Google). La vinculación conserva el uid y todos los datos.
- Aviso no intrusivo para vincular tras crear el primer grupo o el quinto gasto (“si pierdes el móvil, pierdes tus grupos”).
- **Borrar cuenta** desde Perfil (requisito de Play Store y App Store), ver [FIREBASE](../FIREBASE.md#borrado-de-cuenta).
- Onboarding de 3 pasos saltables: bienvenida → nombre/avatar → crear grupo o unirse.

### 3.2 Grupos
- Crear con nombre, icono (catálogo de iconos), color de acento del icono (8 tonos), **divisa base** y fechas opcionales (viaje).
- Editar, **archivar** (sale de Inicio, sigue accesible) y **salir** del grupo. No hay borrado duro de grupos en el MVP: el último miembro que sale lo deja archivado e inaccesible.
- Límites: 50 miembros, 10 000 gastos por grupo (suficiente; protege rendimiento y coste).

### 3.3 Miembros
- Un miembro es una persona **dentro de un grupo**. Puede ser **fantasma** (sin usuario, solo nombre/avatar) o estar **vinculado** a un usuario.
- **Peso por defecto** (entero ≥ 1; p. ej. pareja = 2) que usa el reparto “por pesos”.
- **Desactivar** en lugar de borrar si tiene movimientos (deja de aparecer en repartos nuevos; su historial y saldo siguen). Un miembro sin movimientos se puede borrar.
- Al unirse por invitación, el usuario **reclama** un miembro fantasma o crea uno nuevo.

### 3.4 Gastos
- Campos: concepto, importe, divisa, fecha, categoría (lista fija del MVP), pagador(es), reparto, nota opcional.
- **Pagadores**: uno (por defecto, el usuario actual) o varios con importes que deben sumar el total.
- **Tipos de reparto**: partes iguales (por defecto, entre todos los activos), por pesos, importes exactos, porcentajes. Regla exacta de cálculo en [DOMAIN](../DOMAIN.md#reparto).
- Editar y borrar (borrado lógico, recuperable desde el historial durante 30 días).
- Lista agrupada por día, con filtros por persona y categoría y búsqueda por concepto.

### 3.5 Transferencias (pagos entre miembros)
- “Ana pagó 23,50 € a Luis”. Se crean a mano o desde una sugerencia de “Cómo saldar” (un toque).
- Afectan a los saldos igual que un gasto (pagador = quien paga, beneficiario único = quien recibe).

### 3.6 Saldos y “Cómo saldar”
- **Saldo neto** por miembro en la divisa base del grupo: lo pagado menos lo que le corresponde, más/menos transferencias. La suma de todos los saldos es siempre **exactamente 0**.
- **Cómo saldar**: lista de pagos sugeridos con el algoritmo voraz determinista (como Settle Up). Ver [DOMAIN](../DOMAIN.md#simplificación-de-deudas).
- Inicio muestra el **balance total** del usuario sumando sus grupos convertidos a su divisa preferida (aproximado, marcado como tal si hay varias divisas).

### 3.7 Multidivisa
- Cada grupo tiene divisa base; cada gasto puede estar en cualquier divisa ISO 4217.
- Al crear un gasto en otra divisa se **congela** el tipo de cambio del momento (editable a mano). Los saldos nunca cambian porque cambie el mercado.

### 3.8 Invitaciones
- Cada grupo tiene un **código de invitación** regenerable. Se comparte como **enlace** y **QR**.
- El enlace es una URL de Firebase Hosting (`https://<proyecto>.web.app/j/<código>`) configurada como **App Link** (Android) y **Universal Link** (iOS); sin la app instalada muestra una página con enlaces a las tiendas. Firebase Dynamic Links no se usa (servicio cerrado en 2025). Dominio propio más adelante.
- Unirse también escribiendo el código o escaneando el QR desde la app.
- Regenerar el código invalida los enlaces anteriores.

### 3.9 Historial
- Registro por grupo de altas, ediciones (con diff legible: “12,40 € → 14,40 €”), borrados, restauraciones, uniones y conflictos de sincronización.

### 3.10 Ajustes
- Perfil (nombre, avatar, vincular, cerrar sesión solo si está vinculada, borrar cuenta), divisa preferida, tema (sistema / claro / oscuro), idioma (es / en).

## 4. Modelo de dominio

Detalle completo, invariantes y algoritmos en [DOMAIN.md](../DOMAIN.md). Resumen:

```
User(uid, displayName, avatar, preferredCurrency, linkedProviders, createdAt)
Group(id, name, icon, accent, baseCurrency, startDate?, endDate?, inviteCode,
      memberUids[], createdBy, archivedBy[], createdAt, updatedAt, version, deleted)
Member(id, groupId, name, avatar, defaultWeight, userUid?, active, sortKey,
       updatedAt, version, deleted)
Expense(id, groupId, title, category, note?, date, currency, amountMinor,
        fxRateToBase, payers{memberId→amountMinor}, split{type, shares{memberId→value}},
        receiptUrl?, recurrenceId?, createdBy, updatedAt, version, deleted)
Transfer(id, groupId, fromMemberId, toMemberId, amountMinor, currency, fxRateToBase,
         date, note?, createdBy, updatedAt, version, deleted)
ChangeLogEntry(id, groupId, entityType, entityId, action, authorUid, diff, createdAt)
```

`receiptUrl` y `recurrenceId` existen desde el MVP y quedan vacíos hasta los subproyectos 3 y 5.

## 5. Arquitectura

Estructura de la plantilla `kmp/modular-features` (Clean Architecture, dependencias hacia abajo):

```
composeApp / iosApp                       entrada, DI, navegación raíz
feature:onboarding  feature:home  feature:group  feature:expense-editor
feature:settle      feature:invite feature:history feature:settings
domain:usecases                           CreateExpense, CalculateBalances, SimplifyDebts,
                                          ConvertCurrency, JoinGroup, ClaimMember, LinkAccount…
data:models  data:local (SQLDelight)  data:remote (Firebase GitLive)
data:sync    data:fx (Ktor → Frankfurter)  data:repository
core:common  core:money  core:ui (design system Owlo)  core:security
```

- `core:money` y `domain:usecases` son **Kotlin común puro**, sin dependencias de plataforma: la lógica crítica se escribe y prueba una vez.
- La UI solo lee de SQLDelight (Flows). Nunca espera a la red.
- Cada feature sigue MVI de la plantilla (`State`, `Intent`, `ViewModel`).

## 6. Sincronización

Detalle en [SYNC.md](../SYNC.md). Resumen:

1. **Escritura local atómica**: entidad + entrada en `outbox` en la misma transacción SQLDelight. La UI se actualiza al instante.
2. **Subida**: un worker vacía el outbox en orden cuando hay red (WorkManager en Android; al volver a primer plano y con temporizador en iOS), con backoff exponencial.
3. **Concurrencia optimista**: cada escritura lleva `version = versión base + 1`; las reglas de Firestore rechazan versiones que no encadenan. **Gana quien llega primero al servidor**; la edición rechazada no se pierde: se guarda en el historial como conflicto y se avisa al usuario con opción de reaplicarla.
4. **Bajada**: por cada grupo, listeners de Firestore sobre cada subcolección con `updatedAt >= cursor - margen`; se mezclan en local si la versión remota es mayor.
5. **Borrados lógicos** (`deleted = true`) para que se propaguen; purga local tras 30 días.
6. **Estado visible**: sincronizado / N cambios pendientes / sin conexión / error, en Inicio y en cada grupo.
7. Expulsión o pérdida de acceso: el grupo queda en local en **solo lectura** con aviso “ya no tienes acceso”.

## 7. Firebase

Detalle en [FIREBASE.md](../FIREBASE.md). Colecciones:

```
users/{uid}
groups/{groupId}
groups/{groupId}/members/{memberId}
groups/{groupId}/expenses/{expenseId}
groups/{groupId}/transfers/{transferId}
groups/{groupId}/changelog/{entryId}
invites/{code}                     → { groupId }
```

Reglas clave: solo los uids de `memberUids` leen o escriben un grupo; unirse solo añade el uid propio y exige el código vigente; validación de gastos (pagadores = total, reparto coherente, divisa válida, `version` encadenada, `updatedAt == request.time`). Se prueban con el emulador en CI. **Sin Cloud Functions en el MVP** (plan Spark gratuito).

## 8. Diseño visual y UX

Dirección **fintech limpia**: las cifras son las protagonistas, fondo gris muy claro con contenedores blancos redondeados (negro y grafito en oscuro), un único acento azul eléctrico, botones de acción circulares y píldoras. Tokens, componentes y reglas en [DESIGN_SYSTEM.md](../DESIGN_SYSTEM.md). Mockup aprobado: [mockups/owlo-fintech-v3.html](../mockups/owlo-fintech-v3.html).

### Navegación

Barra inferior con **Inicio · Actividad · Perfil**.

| Pantalla | Contenido |
|---|---|
| Inicio | Balance total grande (te deben / debes), estado de sync, acciones rápidas circulares (Gasto, Saldar, Grupo, Unirse), lista de grupos con tu saldo en cada uno |
| Grupo | Cabecera con icono, nombre y tu saldo grande; control segmentado **Gastos · Saldos · Saldar** |
| Gastos | Lista por día, filtros (persona, categoría), búsqueda, botón añadir |
| Saldos | Fila por miembro con barra fina proporcional y saldo (verde = le deben; negro = debe) |
| Saldar | Pagos sugeridos con botón “Saldar” (crea la transferencia) y transferencias registradas |
| Editor de gasto | Pantalla completa: importe enorme primero con teclado propio, chip de divisa, concepto con categoría, “Pagó” y “Entre” como filas, avatares tocables para incluir/excluir y parte por persona en vivo |
| Editor de reparto | Tipo (iguales, pesos, importes, %), valores por miembro con validación en vivo (“faltan 3,20 €”) |
| Invitar | QR grande, enlace, compartir, regenerar código |
| Unirse | Escanear QR o escribir código → vista previa del grupo → elegir miembro fantasma o crear uno |
| Actividad | Historial de todos los grupos, filtrable por grupo |
| Perfil | Cuenta, vincular, preferencias, borrar cuenta |

Estados vacíos con el logotipo de Owlo y una acción clara (“Crea tu primer grupo”).

### Accesibilidad
- Contraste mínimo AA en ambos temas; el color nunca es el único indicador (signo + texto “te deben/debes”).
- Texto dinámico hasta 200 % sin cortar importes (los importes grandes escalan con límite y se ajustan).
- Descripciones para lectores de pantalla en avatares, barras e importes (“Ana, le deben 58 euros con 20”).
- Respeto a “reducir movimiento”. Áreas táctiles ≥ 48 dp.

### Localización
Español e inglés desde el MVP (recursos de Compose Multiplatform). Formatos de número, fecha y moneda según la configuración regional.

## 9. Gestión de errores

| Situación | Comportamiento |
|---|---|
| Sin red | Todo funciona; indicador “sin conexión”; outbox acumula |
| Fallo transitorio al subir | Reintento con backoff exponencial (máx. 15 min entre intentos) |
| Conflicto de versión | Se conserva la versión del servidor; la local va al historial como conflicto; aviso con “Reaplicar mi cambio” |
| Permiso denegado (expulsado) | Grupo a solo lectura con aviso; outbox de ese grupo descartado tras confirmar |
| Validación (reparto no cuadra) | Se impide guardar con mensaje inline; nunca llega a la red |
| Tipo de cambio no disponible | Pedir tipo manual; nunca bloquear el guardado si el usuario lo introduce |
| Invitación inválida o caducada | Mensaje claro y opción de pedir un enlace nuevo |
| Error inesperado | Crashlytics (no fatal) + mensaje genérico; nunca se muestran trazas |

## 10. Pruebas

- **`core:money` y `domain`** (commonTest): tablas de casos límite (céntimos indivisibles, pesos, varios pagadores, divisas sin decimales como JPY, porcentajes que no suman 100) y **tests de propiedades**: suma de saldos = 0; la simplificación conserva los saldos netos; resultados idénticos ante permutaciones de entrada; número de pagos sugeridos ≤ n − 1.
- **`data:sync`**: Firestore falso en memoria; outbox, reintentos, orden, conflictos, borrados, pérdida de acceso, reinicio a mitad de subida (idempotencia).
- **`data:local`**: migraciones de SQLDelight verificadas.
- **Reglas de Firestore**: emulador con casos permitidos y denegados por cada regla.
- **UI**: tests de Compose de los flujos clave (crear grupo, añadir gasto, saldar, unirse) y capturas de pantalla en claro y oscuro.
- Verificación manual en emulador Android en cada PR con cambios de UI; iOS en CI.

## 11. CI/CD

- **PR**: build Android, `allTests` comunes, detekt, tests de reglas con emulador, compilación del framework iOS en runner macOS.
- **main**: despliegue de reglas e índices de Firestore (proyecto de producción vía cuenta de servicio con permisos mínimos).
- **Tag `vX.Y.Z`**: APK/AAB firmado → GitHub Release + Firebase App Distribution (grupo `testers`). Secretos en Doppler `owlo` (dev/stg/prd), token de solo lectura en GitHub.
- Política de dependencias como en YouKnow (Dependabot agrupado minor+patch).

## 12. Criterios de aceptación del MVP

1. Dos dispositivos (Android + iOS) con cuentas anónimas comparten un grupo por enlace y ven los mismos gastos y saldos al céntimo.
2. Con un dispositivo en modo avión se crean, editan y borran gastos; al reconectar ambos convergen y nada se pierde.
3. Una edición concurrente del mismo gasto produce un aviso de conflicto y la versión perdedora es recuperable desde el historial.
4. Un gasto en USD en un grupo en EUR usa el tipo congelado y los saldos no cambian al día siguiente.
5. Vincular Google conserva todos los grupos; borrar la cuenta deja sus miembros como fantasmas y no quedan datos personales.
6. Un gasto a partes iguales se añade en < 10 s desde Inicio (medido en prueba manual).
7. Todos los tests verdes en CI, incluidos los de reglas y la compilación iOS.

## 13. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Plantilla KMP con versiones antiguas (Kotlin 2.1, CMP 1.7) | Actualizar a las versiones estables actuales al generar, antes de añadir código |
| Madurez del SDK GitLive en iOS | Prototipo de humo (auth anónima + escritura + listener) en el primer paso del plan; alternativa: REST de Firestore con Ktor |
| Sin Mac para iOS | CI en macOS; validación manual de iOS en hitos |
| Complejidad del motor de sync | Diseño acotado (por documento, sin merge de campos), tests exhaustivos con Firestore falso |
| Coste de Firestore con muchos listeners | Listeners solo para grupos abiertos o recientes; consultas incrementales por `updatedAt` |
| Nombre “Owlo” | Verificar disponibilidad en Play Store, App Store, dominio y marca antes de publicar |
