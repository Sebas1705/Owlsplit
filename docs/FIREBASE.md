# Firebase

Decisión en [ADR 0002](adr/0002-firebase-gitlive.md). SDK KMP: `dev.gitlive:firebase-*`.

## Proyecto

Un solo proyecto Firebase, **`owlsplit`** (decisión del 2026-10-08: hasta que haya usuarios reales, separar desarrollo, staging y producción no compensa el triple de configuración). Doppler: `owlsplit/prd`. Hosting: `owlsplit.web.app`.

| Proyecto | Estado | Firestore | Apps |
|---|---|---|---|
| `owlsplit` | Creado 2026-10-08 (P11b); Authentication activo (Anónimo, Google) | `(default)`, `eur3` | Android `1:596963610720:android:44ad7ca230b5953346f305` (`es.sebas1705.owlsplit`, builds de release: testers y usuarios) · Android `1:596963610720:android:61aa6038a2186e6a46f305` (`es.sebas1705.owlsplit.debug`, builds de depuración en local) · iOS `1:596963610720:ios:c3a61a92a48f3cd446f305` (`es.sebas1705.owlsplit`) |

Las builds de depuración son otra app del mismo proyecto para instalarse junto a la de los testers (la firma es distinta) y no mandar sus fallos a Crashlytics; comparten base de datos, así que en local se prueba con grupos de prueba identificables. Los proyectos `owlsplit-dev` (P2–P11a) y `owlsplit-stg` (P11b) se borraron.

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
  - Huellas registradas: la de depuración en la app `.debug` y la de publicación en la de release (2026-10-08).
  - Para activarlo: consola de Firebase → Authentication → Sign-in method → Google → Activar (correo de soporte); después `scripts/setup-firebase-env.sh` y `scripts/doppler-sync-local.sh`.
- **"Ya tengo cuenta"** (instalación nueva): `signInWithCredential` y se recupera el perfil de `users/{uid}`.
- **La credencial ya pertenece a otra cuenta**: por ahora se informa y no se cambia nada; el traspaso de grupos descrito arriba necesita unirse por código (P10).
- **Apple e iOS**: aplazados a P11 (falta el proyecto Xcode); obligatorio para publicar en App Store si se ofrece Google.

### Estado (P10a)

- `FirestoreInviteService`: al abrir Invitar se vacía el outbox y se escribe `invites/{code}`; al regenerar se borra el anterior. Unirse escribe en `groups/{id}` solo `memberUids` (`arrayUnion`), `lastJoinCode`, `version` (`increment`), `updatedAt` y `updatedBy`, que la regla `join()` comprueba contra el código vigente.
- Probado contra el antiguo `owlsplit-dev` (2026-10-08): invitar, borrar los datos de la app (usuario anónimo nuevo), unirse con el código y reclamar un miembro sin cuenta.

### Hosting y App Links (P10b)

- `firebase/hosting` se sirve en `https://owlsplit.web.app`: `/j/<código>` → `join.html` (código, abrir la app, pasos si no está instalada), `/` página de inicio y `/.well-known/assetlinks.json`. `firebase.json` define su propio `ignore` porque el de por defecto excluye los ficheros que empiezan por punto (`.well-known`).
- `assetlinks.json` declara `es.sebas1705.owlsplit` con la SHA‑256 de la clave de publicación (`99:0B:…:18:C3`) y `es.sebas1705.owlsplit.debug` con la de depuración de SEBAS-PC (`B3:DD:…:CE:F5`). Otra clave de depuración (otro PC) necesita su huella aquí y en la app de depuración. Comprobar en el dispositivo:

  ```sh
  adb shell pm verify-app-links --re-verify es.sebas1705.owlsplit.debug
  adb shell pm get-app-links es.sebas1705.owlsplit.debug
  ```

- Pendiente (P11c): `apple-app-site-association` para Universal Links (necesita el Team ID de Apple).

## Borrado de cuenta

Sin Cloud Functions, lo hace el cliente en este orden (reintentable):
1. Para cada grupo: poner `userUid = null` en su miembro (pasa a fantasma, se conserva el historial contable del grupo) y quitar su uid de `memberUids`.
2. Borrar `users/{uid}`.
3. `currentUser.delete()` (puede pedir reautenticación reciente).
4. Borrar la base de datos local.

Los nombres que el usuario escribió en gastos de grupos ajenos se quedan (son datos del grupo). Se documenta en la política de privacidad.

## Secretos y configuración

Doppler `owlsplit/prd` guarda todo: `GOOGLE_SERVICES_JSON` (las dos apps Android en un fichero), `GOOGLE_SERVICE_INFO_PLIST`, `FIREBASE_PROJECT_ID`, `FIREBASE_ANDROID_APP_ID` (la de release, para App Distribution), `FIREBASE_IOS_APP_ID`, `FIREBASE_API_KEY`, la firma (`SIGNING_*`) y la cuenta de servicio de CI (`FIREBASE_SERVICE_ACCOUNT_JSON`). GitHub solo tiene `DOPPLER_TOKEN`, de solo lectura.

- `scripts/doppler-sync-local.sh`: escribe `google-services.json` y `GoogleService-Info.plist` en `composeApp/` e `iosApp/` (git-ignored).
- `scripts/setup-firebase-env.sh`: descarga la configuración de las apps y la guarda en Doppler. Volver a ejecutarlo tras activar Google o registrar huellas: cambia `google-services.json`.
- `scripts/setup-release-keystore.sh`: crea la clave de publicación (una sola vez; ya hecho), la guarda en Doppler y registra sus huellas. La copia del `.jks` queda en `Documentsowlsplit-release.jks`.
- `node scripts/setup-ci-access.mjs [correos de testers]`: cuenta de servicio `owlsplit-ci@owlsplit` con permisos mínimos (App Distribution, reglas, Hosting, índices), su clave en Doppler, `DOPPLER_TOKEN` en GitHub y el grupo `testers`. Volver a ejecutarlo rota la clave y añade testers.

Distribución a testers: [RELEASE.md](RELEASE.md).

## Despliegues

- Cada push a `main` que toque `firebase/` despliega reglas, índices y Hosting (`.github/workflows/deploy-firebase.yml`, cuenta `owlsplit-ci`). Las reglas ya pasaron sus tests en el PR. A mano: `cd firebase && npx firebase-tools deploy --only firestore,hosting`.
- **Crashlytics**: activo en las builds de release (manifiesto, `firebase_crashlytics_collection_enabled`), apagado en las de depuración. Los errores inesperados de las pantallas se registran como no fatales y la app muestra un mensaje genérico.
