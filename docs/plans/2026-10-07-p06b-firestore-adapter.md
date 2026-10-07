# P6b · Adaptador Firestore — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Conectar el motor de P6a con Firestore real: `FirestoreRemoteStore` (GitLive) implementa `RemoteStore`, el motor reconoce el eco de su propia subida, la app arranca la sincronización con un usuario anónimo y un test instrumentado sincroniza dos motores a través de los emuladores de Firestore y Auth.

**Architecture:** El adaptador vive en `data:remote` y depende de `data:repository` (el puerto `RemoteStore` está allí; no hay ciclo). Cada tipo tiene un DTO con exactamente las claves que aceptan las reglas de P5; los modelos se obtienen con `decodeRemoteDoc` del payload del outbox. La caché de Firestore es **en memoria**: la fuente de verdad local es SQLDelight, así que no queremos una segunda copia persistente ni que el SDK reintente escrituras por su cuenta tras reiniciar.

**Tech Stack:** GitLive Firebase 2.7.0 (Firestore, Auth), Koin 4.2, AndroidX WorkManager 2.12.0 (minCompileSdk 35), emuladores de Firebase.

**Platform:** KMP (adaptador en código común; disparador WorkManager en Android; test instrumentado en Android)

**Depende de:** P6a (motor y puerto), P5 (reglas y emuladores).

---

## Decisiones

1. **Formato en Firestore = DTO por tipo.** `id` y `groupId` salen de la ruta (`groups/{g}/expenses/{id}`), no se guardan. `updatedAt` es `Timestamp` de servidor (`Timestamp.ServerTimestamp` al escribir); `createdAt` (cliente, ms) y los importes son enteros; las fechas, texto `AAAA-MM-DD`; los nulos se escriben explícitos (las reglas leen `note`, `avatar`… aunque sean nulos).
2. **`set(merge = true)`.** El cliente no conoce `lastJoinCode` (lo escribe quien se une); sobrescribir el documento lo borraría y las reglas lo rechazarían.
3. **Errores:** `PERMISSION_DENIED` → `Rejected` al subir y `NoAccess` al leer; `NOT_FOUND` o documento inexistente → `Missing`; cualquier otro error o un tiempo de espera (20 s; sin red `set` no termina) → `Transient`. Sin usuario → `Transient`.
4. **Leer para resolver un rechazo** con `Source.SERVER`: la caché podría tener nuestra escritura local aún no confirmada.
5. **Escuchas:** solo documentos confirmados (se saltan los que tienen `hasPendingWrites`), solo los cambios de cada instantánea (`documentChanges`; la primera trae todo). El historial no tiene `updatedAt`: se filtra por `createdAt` (reloj del cliente, cubierto por el margen de 5 min del cursor). Errores transitorios → reintento con espera creciente; `PERMISSION_DENIED` → la escucha termina sin error (la escucha de *mis grupos* marca el acceso perdido).
6. **Eco de la propia subida.** La escucha puede entregar nuestra escritura antes de que `push` vuelva. Sin tratarlo sería un conflicto falso (versión mayor con pendiente local). El motor guarda la entrada en vuelo y su versión; si llega un documento con ese id, esa versión y el mismo contenido que el payload subido, se trata como acuse (`onPushed`) en lugar de aplicarlo.
7. **Arranque en la app:** `signInAnonymously` si no hay usuario (P8 hará el onboarding real), `SyncEngine.start` en un ámbito de aplicación con el uid, y WorkManager (`OneTimeWorkRequest` con red, `KEEP`) que llama a `pushNow()` cuando el outbox tiene pendientes. iOS: el motor arranca igual; el disparador en segundo plano llega con P11.
8. **Emuladores en el test instrumentado:** `10.0.2.2:8080` (Firestore) y `10.0.2.2:9099` (Auth), proyecto `demo-owlsplit`. Dos apps de Firebase con nombre distinto = dos usuarios; cada uno con su base SQLite en memoria y su motor.

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Crear | `data/remote/src/commonMain/kotlin/es/sebas1705/owlsplit/data/remote/`: `Dtos.kt`, `FirestoreRemoteStore.kt` |
| Modificar | `data/remote/build.gradle.kts` (depende de `data:repository`, `data:local` para el test) |
| Modificar | `data/repository/.../sync/SyncEngine.kt` (entrada en vuelo, eco propio) |
| Test | `data/repository/src/androidUnitTest/.../sync/SyncEngineTest.kt`, `FakeRemoteStore.kt` (eco antes del acuse) |
| Test | `data/remote/src/androidInstrumentedTest/.../FirestoreSyncEmulatorTest.kt` |
| Crear | `scripts/run-sync-emulator-test.sh` |
| Modificar | `composeApp`: `build.gradle.kts`, `di/AppModule.kt`, `KmpApplication.kt`, `MainViewController.kt`, `sync/SyncWorker.kt` (androidMain), `sync/SyncStarter.kt` |
| Modificar | `gradle/libs.versions.toml` (WorkManager) |
| Modificar | `docs/SYNC.md`, `docs/FIREBASE.md`, `docs/plans/README.md` |

---

### Task 1: Eco de la propia subida

- [ ] **Step 1:** `FakeRemoteStore`: opción `echoBeforeAck` que, al aceptar un `push`, emite el documento y deja correr a los escuchas antes de devolver `Ok`.
- [ ] **Step 2:** Test `own upload echoed before its acknowledgement is not a conflict` en `SyncEngineTest` (0 conflictos, outbox vacío, documento `SYNCED`) y comprobar que falla.
- [ ] **Step 3:** `SyncEngine`: `InFlight(entry, version)` en un `MutableStateFlow`; `OutboxProcessor` lo publica alrededor de `remote.push`; los escuchas consultan `isOwnEcho(doc)` antes de `applier.apply`. Test en verde.
- [ ] **Step 4:** `./gradlew :data:repository:testDebugUnitTest detekt` y commit.

### Task 2: DTOs y `FirestoreRemoteStore`

- [ ] **Step 1:** DTOs con las claves de `firestore.rules` y conversiones modelo↔DTO.
- [ ] **Step 2:** `FirestoreRemoteStore(firestore, auth)` según las decisiones 1–5.
- [ ] **Step 3:** `./gradlew :data:remote:compileDebugKotlinAndroid :data:remote:compileKotlinIosSimulatorArm64 detekt` y commit.

### Task 3: Test con emuladores

- [ ] **Step 1:** `FirestoreSyncEmulatorTest`: Ana crea grupo y gasto → Luis… (no puede leer sin unirse; el test hace que Luis se una con `arrayUnion` como en las reglas) → el gasto llega a Luis; Luis lo edita → llega a Ana; edición concurrente → un conflicto; ningún conflicto por eco.
- [ ] **Step 2:** `scripts/run-sync-emulator-test.sh` (levanta emuladores con `emulators:exec` y lanza solo esa clase). Ejecutarlo en el emulador Android local.
- [ ] **Step 3:** Commit.

### Task 4: Arranque en la app

- [ ] **Step 1:** Koin: `FirebaseFirestore` (caché en memoria), `FirebaseAuth`, `RemoteStore`, `RepositoryContext` (uid de Auth), `SyncEngine`.
- [ ] **Step 2:** `SyncStarter`: inicio de sesión anónimo y `engine.start` en el ámbito de la aplicación; sin `google-services.json` no hace nada.
- [ ] **Step 3:** `SyncWorker` + programación cuando `pendingCount > 0`.
- [ ] **Step 4:** Build, instalar en el emulador, comprobar en logcat que inicia sesión y no falla. Commit.

### Task 5: Documentación y PR

- [ ] **Step 1:** `docs/SYNC.md` (sección de implementación P6b), `docs/FIREBASE.md` (formato de documentos), índice de planes.
- [ ] **Step 2:** PR, CI verde, merge.
