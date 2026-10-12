# Identidad de los miembros: salir, volver y cuentas — Estudio

**Fecha:** 2026-10-12 · **Estado:** aprobado (opción A, 2026-10-12); plan P14.

El objetivo es que los gastos, las deudas y el historial de una persona sigan unidos a ella aunque:

- salga del grupo y vuelva,
- cambie de móvil,
- o entre con otra cuenta.

Además, si inicia sesión con Google, su miembro debe quedar ligado a un identificador permanente y no solo a un nombre.

## 1. Cómo funciona hoy

| Situación | Qué pasa | ¿Se pierde algo? |
|---|---|---|
| Alguien se une con «Soy nuevo» | Se crea su miembro con `userUid` = su uid | — |
| Alguien se une eligiendo un nombre (fantasma) | El fantasma pasa a tener su `userUid` (`claim`) | — |
| Sale del grupo | Su miembro se queda con sus gastos, pero `userUid` pasa a `null` y se borran sus datos de cobro. Su uid sale de `memberUids` | Los gastos y saldos no. **El vínculo miembro ↔ persona sí** |
| Vuelve con **la misma cuenta** | Ve la lista de fantasmas y tiene que **elegirse a mano** | Nada, pero depende de que se reconozca por el nombre |
| Vuelve con **otra cuenta** (otro móvil sin Google, o tras reinstalar siendo anónimo) | Igual: se elige a mano | Nada, pero **cualquiera con la invitación puede elegir ese perfil** |
| Borra su cuenta | Sale de todos sus grupos (igual que salir) | Igual que salir |
| Inicia sesión con Google en otro móvil | El uid es el mismo: sigue en sus grupos y lo recupera todo | — |

Lo que ya funciona bien:

- **El historial no se pierde.** Gastos, repartos y pagos apuntan al *id del miembro*, no a la cuenta. Las entradas del historial guardan el uid del autor. P9c reconstruye a quién pertenece cada uid a partir de las entradas `JOINED` y de los cambios de `userUid`, así que los nombres se mantienen después de salir.
- **Con Google la identidad ya es permanente.** El uid de Firebase es el mismo en cualquier móvil donde se entre con esa cuenta de Google. Con una cuenta anónima, el uid se pierde al desinstalar.

## 2. Problemas

1. **El miembro olvida a quién perteneció.** Al salir, `userUid` pasa a `null` y no se guarda nada más. Al volver no se le reconoce, aunque sea la misma cuenta.
2. **Un perfil abandonado no se distingue de un fantasma creado a mano.** En «¿Quién eres?», «Carlos (salió del grupo)» y «Marta (añadida por Ana, sin cuenta)» se ven igual.
3. **Cualquiera puede quedarse con el perfil de otra persona.** Las reglas permiten reclamar cualquier fantasma, incluido uno que perteneció a otra cuenta, y así heredar su saldo.
4. **No se ve quién tiene su cuenta protegida.** Los demás miembros no saben si un perfil está ligado a Google (y por tanto se recupera siempre) o a una cuenta anónima que puede perderse.

## 3. Propuesta

### 3.1 El miembro recuerda sus cuentas: `pastUids`

Se añade al miembro una lista `pastUids: List<String>` con las cuentas que lo tuvieron. Al salir o al borrar la cuenta, tu uid se añade a `pastUids` del miembro a la vez que `userUid` pasa a `null`.

- **Volver con la misma cuenta (anónima o Google): reasociación automática.** Al unirse, si tu uid está en `pastUids` de algún miembro, la app lo reclama sin preguntar. Recuperas tu nombre, tus saldos y tu historial, y el historial lo muestra como «Carlos volvió al grupo».
- **Con Google esto es permanente.** Como el uid de Google no cambia, cualquier móvil en el que entres con esa cuenta te reconoce. Con una cuenta anónima, solo te reconoce mientras no reinstales la app.

### 3.2 Perfiles abandonados, visibles como tales

Un miembro con `userUid == null` y `pastUids` no vacío es un **perfil abandonado**:

- En Miembros, Saldos y «¿Quién eres?» se muestra como «Carlos · salió del grupo», separado de los miembros sin cuenta que se añadieron a mano.
- Conserva su saldo, sus gastos y su historial. Nada se borra.

### 3.3 Volver con otra cuenta: reclamar un perfil abandonado

Es el caso de quien perdió una cuenta anónima o entra desde una cuenta distinta. Hay dos opciones; **la decisión es del propietario**:

- **A. Libre, pero visible (recomendada para la beta).**
  - Cualquiera que se una puede elegir un perfil abandonado, como hoy.
  - La app pide una confirmación explícita: «Este perfil era de otra cuenta. Elígelo solo si eres Carlos».
  - Queda en el historial como «Carlos (otra cuenta) recuperó el perfil de Carlos», visible para todos.
- **B. Con aprobación.**
  - Elegir un perfil abandonado crea una solicitud.
  - Un miembro con cuenta la aprueba desde el grupo, y mientras tanto entras como «Soy nuevo».
  - Es más seguro, pero más lento, y necesita un documento de solicitudes con sus reglas. Sin avisos push (plan Blaze), el que aprueba solo se entera al abrir la app.

### 3.4 Identidad de Google visible

Se añade al miembro un campo `linked: Boolean`, que publica la propia persona igual que los datos de cobro. Vale `true` cuando su cuenta está vinculada a Google.

- En Miembros se muestra «cuenta con Google», así el grupo sabe qué perfiles se recuperan siempre.
- Al reclamar un perfil abandonado que estaba vinculado a Google con otra cuenta, el aviso lo destaca: «Este perfil era de una cuenta de Google».

### 3.5 Reglas de Firestore

- `pastUids`: lista de cadenas, como máximo 20, que solo puede crecer. Solo se le añade tu propio uid y solo cuando liberas tu miembro (`userUid` pasa de tu uid a `null`).
- Reclamar sigue siendo `null → mi uid`. En la opción B, reclamar un perfil abandonado con otra cuenta requiere una solicitud aprobada.
- `linked`: solo lo cambia la persona del miembro (igual que `payMethods`).

### 3.6 Datos y migración

- Esquema local 2 → 3: se añaden las columnas `past_uids` (JSON) y `linked`.
- Para los documentos ya existentes, `pastUids` falta y se lee como vacío. Los fantasmas que salieron antes de este cambio pueden recibir sus `pastUids` a partir del historial (las entradas `JOINED` y los cambios de `userUid` dicen qué uid tuvo cada miembro). Se hace en el cliente, la primera vez que un miembro del grupo abre la app.

## 4. Pruebas

Con tres emuladores:

1. Carlos (anónimo) sale y vuelve con la misma cuenta: se reasocia solo, sin elegir.
2. Carlos con Google sale, reinstala y entra con Google en otro emulador: se reasocia solo.
3. Carlos anónimo reinstala (cuenta nueva) y se une: ve «Carlos · salió del grupo». Con la opción A, confirma y lo recupera, y el historial lo dice. Con la opción B, queda pendiente hasta que Ana lo apruebe.
4. Otra persona intenta elegir el perfil de Carlos: ve el aviso. Con la opción B, no lo obtiene sin aprobación.
5. Los saldos, el historial y los nombres se mantienen en todos los casos.
6. Las reglas se prueban en el emulador: `pastUids` no se puede reescribir y no se puede añadir el uid de otra persona.
