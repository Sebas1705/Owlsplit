# Firebase

Decisión en [ADR 0002](adr/0002-firebase-gitlive.md). SDK KMP: `dev.gitlive:firebase-*`.

## Proyectos

| Perfil | Proyecto Firebase | Doppler | Uso |
|---|---|---|---|
| development | `owlsplit-dev` | `owlsplit/dev` | Desarrollo local y emuladores |
| staging | `owlsplit-stg` | `owlsplit/stg` | Pruebas internas (App Distribution) |
| production | `owlsplit-prd` | `owlsplit/prd` | Tiendas |

Los ids definitivos se fijan al crearlos (si los nombres están cogidos, se añade sufijo) y se anotan aquí. Una app Android y una iOS por proyecto.

| Proyecto | Estado | Firestore | App Android | App iOS |
|---|---|---|---|---|
| `owlsplit-dev` | Creado 2026-10-07 (P2); Auth anónimo activo | `(default)`, `eur3` | `1:217818479934:android:046eb4b90d7880ae2f406f` (`es.sebas1705.owlsplit.dev`) | `1:217818479934:ios:7d3e0b78a8f438342f406f` (`es.sebas1705.owlsplit.dev`) |
| `owlsplit-stg` | Pendiente (P11) | — | — | — |
| `owlsplit-prd` | Pendiente (P11) | — | — | — |

Notas de creación: un proyecto nuevo trae desactivada la API de Firestore (se activa antes de crear la base de datos), y Authentication solo se inicializa desde la consola (*Comenzar*); por API exige Identity Platform con facturación. Plan **Spark** (gratuito) durante el MVP: no se usan Cloud Functions ni Storage (Storage exige Blaze para buckets nuevos; llegará con el escaneo de tickets).

Servicios del MVP: **Authentication** (anónimo, Google, Apple), **Firestore**, **Hosting** (página de invitación + `assetlinks.json` + `apple-app-site-association`), **Crashlytics** y **App Distribution**.

## Colecciones

```
users/{uid}
  displayName, avatar, preferredCurrency, linkedProviders[], createdAt

groups/{groupId}
  name, icon, accent, baseCurrency, startDate?, endDate?,
  inviteCode, memberUids[], createdBy, archivedBy[],
  lastJoinCode?, updatedAt, updatedBy, version, deleted

groups/{groupId}/members/{memberId}
groups/{groupId}/expenses/{expenseId}
groups/{groupId}/transfers/{transferId}
groups/{groupId}/changelog/{entryId}

invites/{code}
  groupId, groupName, groupIcon, memberCount, createdBy, createdAt
```

Índices compuestos: `updatedAt` ascendente en cada subcolección (consultas incrementales de [SYNC](SYNC.md#bajada-firestore--local)).

## Reglas (resumen)

Se escriben en `firebase/firestore.rules` y se prueban en `firebase/rules-test` con el emulador.

- **Autenticación obligatoria** en todo (incluidos los usuarios anónimos).
- `users/{uid}`: solo su dueño lee y escribe.
- `groups/{id}`:
  - **Leer**: `request.auth.uid in resource.data.memberUids`.
  - **Crear**: `memberUids == [request.auth.uid]`, `createdBy == request.auth.uid`, `version == 1`.
  - **Editar (miembro)**: es miembro, `version` encadenada, `updatedAt == request.time`, no puede quitar a otros de `memberUids` (MVP: sin expulsiones).
  - **Unirse (no miembro)**: el único cambio es añadir `request.auth.uid` a `memberUids` y fijar `lastJoinCode`, que debe ser igual a `resource.data.inviteCode`.
  - **Salir**: el único cambio es quitar el uid propio de `memberUids`.
- Subcolecciones `members`, `expenses`, `transfers`: leer y escribir solo miembros del grupo (`get()` del grupo padre); validación de forma y tipos, `version` encadenada, `updatedAt == request.time`, `updatedBy == request.auth.uid`. En gastos: suma de pagadores = `amountMinor`; en `EXACT`, suma de partes = `amountMinor`.
  - **Reclamar miembro**: solo se puede poner `userUid` de nulo a `request.auth.uid`, y solo si el uid ya está en `memberUids` y no tiene otro miembro en el grupo (el cliente lo comprueba; las reglas garantizan al menos lo primero).
- `changelog`: crear si eres miembro y `authorUid == request.auth.uid`; **nunca** editar ni borrar.
- `invites/{code}`: leer cualquier usuario autenticado (solo contiene `groupId`); crear y borrar solo miembros del grupo.
- **Previsualizar un grupo antes de unirse**: el no miembro solo puede leer `invites/{code}`; la vista previa muestra el nombre, que se copia en el documento de invitación (`groupName`, `memberCount`) al generarlo.

## Autenticación

- Primer arranque: `signInAnonymously`.
- Vincular: `currentUser.linkWithCredential` (Google con Credential Manager en Android y Google Sign-In en iOS; Apple con AuthenticationServices en iOS). Es el caso normal y no mueve ningún dato.
- **La credencial ya pertenece a otra cuenta** (p. ej. reinstaló la app): se ofrece entrar en esa cuenta trayendo los grupos de la anónima, en este orden:
  1. Todavía como anónimo: se guardan en local los grupos, sus códigos de invitación y los miembros reclamados, y se liberan esos miembros (`userUid = null`).
  2. `signInWithCredential` con la cuenta existente.
  3. Para cada grupo guardado: unirse con el código y reclamar el mismo miembro.
  4. Si el paso 2 falla o se cancela, se vuelven a reclamar los miembros como anónimo (no se pierde nada).

  El uid anónimo se queda en `memberUids` de esos grupos sin uso (no se muestra; se limpiará cuando haya Cloud Functions).
- Cerrar sesión solo disponible con cuenta vinculada (en anónima significaría perder el acceso).

## Borrado de cuenta

Sin Cloud Functions, lo hace el cliente en este orden (reintentable):
1. Para cada grupo: poner `userUid = null` en su miembro (pasa a fantasma, se conserva el historial contable del grupo) y quitar su uid de `memberUids`.
2. Borrar `users/{uid}`.
3. `currentUser.delete()` (puede pedir reautenticación reciente).
4. Borrar la base de datos local.

Los nombres que el usuario escribió en gastos de grupos ajenos se quedan (son datos del grupo). Se documenta en la política de privacidad.

## Secretos y configuración

- `google-services.json` y `GoogleService-Info.plist` en Doppler por perfil; un script los sincroniza en local (como en YouKnow).
- Cuenta de servicio de CI para App Distribution con permisos mínimos; otra para desplegar reglas.
