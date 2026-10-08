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
  displayName, avatar ("accent:<COLOR>"), preferredCurrency, linkedProviders[], updatedAt

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

## Formato de los documentos

Escritos por `FirestoreRemoteStore` (`data:remote/.../Dtos.kt`):

- El id del documento es el del modelo; `groupId` sale de la ruta y no se guarda.
- `version` (entero), `updatedAt` (`Timestamp` de servidor), `updatedBy` (uid) y `deleted` en grupos, miembros, gastos y transferencias. El historial lleva `updatedAt` de servidor además de `createdAt`.
- `createdAt` (ms del cliente), importes y tipos de cambio: enteros. Fechas: texto `AAAA-MM-DD`. Enumerados: su nombre.
- Los opcionales se escriben como `null` explícito (las reglas leen todas las claves).
- `payers` y `splitValues`: mapas `idMiembro → entero`. `diff`: lista de `{field, before, after}`.

## Reglas

Implementadas en `firebase/firestore.rules` y probadas con el emulador en `firebase/rules-test` (33 tests; job `Firestore rules (emulator)` en CI). En local:

```bash
cd firebase && npx firebase-tools emulators:exec --only firestore --project demo-owlsplit "npm --prefix rules-test test"
```

- **Autenticación obligatoria** en todo (incluidos los usuarios anónimos).
- **Toda escritura** lleva `version` = anterior + 1 (o 1 al crear), `updatedAt == request.time` (`serverTimestamp()`) y `updatedBy` = quien escribe. Las claves de cada documento están cerradas (`hasOnly`).
- **Nada se borra** desde el cliente (borrado lógico con `deleted`), salvo `invites/{code}` al regenerar el código.
- `users/{uid}`: solo su dueño lee y escribe.
- `groups/{id}`:
  - **Leer**: miembros (`uid in memberUids`); la lista de mis grupos se consulta con `array-contains`.
  - **Crear**: `memberUids == [uid]`, `createdBy == uid`, `archivedBy == []`.
  - **Editar (miembro)**: no cambia `memberUids`, `createdBy`, `createdAt` ni `lastJoinCode`; en `archivedBy` solo añade o quita su propio uid.
  - **Unirse (no miembro)**: como no puede leer el grupo, escribe con transformaciones: `memberUids: arrayUnion(uid)`, `version: increment(1)`, `updatedAt: serverTimestamp()`, `lastJoinCode` = código. Las reglas exigen que el código sea el vigente y que solo se añada su uid (máx. 50 miembros).
  - **Salir**: solo quita su propio uid (`arrayRemove`).
- `members`, `expenses`, `transfers`: leer y escribir solo miembros del grupo (`get()` del grupo). Se validan tipos, rangos y tamaños: textos con longitud máxima, divisa ISO, fecha `AAAA-MM-DD`, importe entero en (0, 10¹²], tipo de cambio entero, categoría y tipo de reparto de la lista, mapas de pagadores y reparto con 1–50 entradas, peso 1–100.
  - **Las sumas no se validan** (pagadores = importe, partes exactas = importe): las reglas no tienen bucles. Las garantiza el cliente (dominio de P3) y `ObserveGroupBalances` (P4) deja fuera y señala cualquier gasto inconsistente.
  - **Reclamar miembro**: `userUid` solo pasa de nulo al uid propio o del uid propio a nulo; nunca se toma ni se libera el de otro. Que un usuario no reclame dos miembros del mismo grupo lo comprueba el cliente.
  - En gastos y transferencias no cambian `createdBy` ni `createdAt`; en transferencias tampoco origen, destino ni importe (se borran y se crean de nuevo).
- `changelog`: crear si eres miembro, `authorUid == uid` y `updatedAt == request.time` (se baja por hora de servidor); **nunca** editar ni borrar.
- `invites/{code}` (id = código): `get` para cualquier usuario autenticado, `list` prohibido. Crear solo miembros y solo con el código vigente del grupo; contiene `groupId`, `groupName`, `groupIcon` y `memberCount` para previsualizar antes de unirse.
- `smoke/{uid}`: se mantiene para repetir la prueba de humo de P2 al actualizar GitLive.

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

### Estado (P8)

- Implementado en `FirebaseAccountService` y los casos de uso de `domain:usecases/account`. La app inicia sesión anónima sola (`SyncStarter`) y el onboarding no termina sin sesión.
- **Google en Android**: Credential Manager (`AndroidGoogleCredentials`) con el *Web client id* `default_web_client_id`, que el plugin de Google Services genera desde `google-services.json` **solo cuando Google está activado** en Authentication. Sin él, la app oculta "Ya tengo cuenta" y muestra "Vincular estará disponible pronto".
  - Huella SHA‑1 de la clave de depuración ya registrada en la app Android de `owlsplit-dev` (2026-10-07). La de la clave de publicación se añadirá en P11.
  - Para activarlo: consola de Firebase → Authentication → Sign-in method → Google → Activar (correo de soporte); después volver a descargar `google-services.json` a Doppler y ejecutar `scripts/doppler-sync-local.sh`.
- **"Ya tengo cuenta"** (instalación nueva): `signInWithCredential` y se recupera el perfil de `users/{uid}`.
- **La credencial ya pertenece a otra cuenta**: por ahora se informa y no se cambia nada; el traspaso de grupos descrito arriba necesita unirse por código (P10).
- **Apple e iOS**: aplazados a P11 (falta el proyecto Xcode); obligatorio para publicar en App Store si se ofrece Google.

### Estado (P10a)

- `FirestoreInviteService`: al abrir Invitar se vacía el outbox y se escribe `invites/{code}`; al regenerar se borra el anterior. Unirse escribe en `groups/{id}` solo `memberUids` (`arrayUnion`), `lastJoinCode`, `version` (`increment`), `updatedAt` y `updatedBy`, que la regla `join()` comprueba contra el código vigente.
- Probado contra `owlsplit-dev` (2026-10-08): invitar, borrar los datos de la app (usuario anónimo nuevo), unirse con el código y reclamar un miembro sin cuenta.
- Los enlaces `https://owlsplit-dev.web.app/j/<código>` aún no abren la app ni tienen página: llegan con P10b (Hosting, App Links / Universal Links). Mientras tanto, pegar el enlace en Unirse funciona.

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
