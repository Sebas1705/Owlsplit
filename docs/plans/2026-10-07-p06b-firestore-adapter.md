# P6b · Firestore adapter — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Connect the P6a engine to real Firestore: `FirestoreRemoteStore` (GitLive) implements `RemoteStore`, the engine recognizes the echo of its own upload, the app starts sync with an anonymous user, and an instrumented test syncs two engines through the Firestore and Auth emulators.

**Architecture:** The adapter lives in `data:remote` and depends on `data:repository` (the `RemoteStore` port lives there; no cycle). Each type has a DTO with exactly the keys the P5 rules accept; models are obtained with `decodeRemoteDoc` from the outbox payload. The Firestore cache is **in memory**: the local source of truth is SQLDelight, so we don't want a second persistent copy, nor the SDK retrying writes on its own after a restart.

**Tech Stack:** GitLive Firebase 2.7.0 (Firestore, Auth), Koin 4.2, AndroidX WorkManager 2.12.0 (minCompileSdk 35), Firebase emulators.

**Platform:** KMP (adapter in common code; WorkManager trigger on Android; instrumented test on Android)

**Depends on:** P6a (engine and port), P5 (rules and emulators).

---

## Decisions

1. **Firestore format = one DTO per type.** `id` and `groupId` come from the path (`groups/{g}/expenses/{id}`) and are not stored. `updatedAt` is a server `Timestamp` (`Timestamp.ServerTimestamp` on write); `createdAt` (client, ms) and amounts are integers; dates are `YYYY-MM-DD` text; nulls are written explicitly (the rules read `note`, `avatar`… even when null).
2. **`set(merge = true)`.** The client doesn't know `lastJoinCode` (it is written by whoever joins); overwriting the document would erase it and the rules would reject it.
3. **Errors:** `PERMISSION_DENIED` → `Rejected` on upload and `NoAccess` on read; `NOT_FOUND` or a missing document → `Missing`; any other error or a timeout (20 s; without network `set` never completes) → `Transient`. No user → `Transient`.
4. **Read to resolve a rejection** with `Source.SERVER`: the cache might hold our not-yet-confirmed local write.
5. **Listeners:** only confirmed documents (those with `hasPendingWrites` are skipped), only each snapshot's changes (`documentChanges`; the first one brings everything). The history has no `updatedAt`: it is filtered by `createdAt` (client clock, covered by the cursor's 5-minute margin). Transient errors → retry with growing backoff; `PERMISSION_DENIED` → the listener ends without error (the *my groups* listener marks the lost access).
6. **Echo of our own upload.** The listener may deliver our write before `push` returns. Left unhandled it would be a false conflict (higher version with a local pending change). The engine keeps the in-flight entry and its version; if a document arrives with that id, that version and the same content as the uploaded payload, it is treated as an acknowledgement (`onPushed`) instead of being applied.
7. **App startup:** `signInAnonymously` if there is no user (P8 will do the real onboarding), `SyncEngine.start` in an application scope with the uid, and WorkManager (`OneTimeWorkRequest` with network, `KEEP`) calling `pushNow()` when the outbox has pending entries. iOS: the engine starts the same way; the background trigger comes with P11.
8. **Emulators in the instrumented test:** `10.0.2.2:8080` (Firestore) and `10.0.2.2:9099` (Auth), project `demo-owlsplit`. Two Firebase apps with different names = two users; each with its own in-memory SQLite database and engine.

## File map

| Action | Path |
|---|---|
| Create | `data/remote/src/commonMain/kotlin/es/sebas1705/owlsplit/data/remote/`: `Dtos.kt`, `FirestoreRemoteStore.kt` |
| Modify | `data/remote/build.gradle.kts` (depends on `data:repository`, `data:local` for the test) |
| Modify | `data/repository/.../sync/SyncEngine.kt` (in-flight entry, own echo) |
| Test | `data/repository/src/androidUnitTest/.../sync/SyncEngineTest.kt`, `FakeRemoteStore.kt` (echo before the acknowledgement) |
| Test | `data/remote/src/androidInstrumentedTest/.../FirestoreSyncEmulatorTest.kt` |
| Create | `scripts/run-sync-emulator-test.sh` |
| Modify | `composeApp`: `build.gradle.kts`, `di/AppModule.kt`, `KmpApplication.kt`, `MainViewController.kt`, `sync/SyncWorker.kt` (androidMain), `sync/SyncStarter.kt` |
| Modify | `gradle/libs.versions.toml` (WorkManager) |
| Modify | `docs/SYNC.md`, `docs/FIREBASE.md`, `docs/plans/README.md` |

---

### Task 1: Echo of our own upload

- [ ] **Step 1:** `FakeRemoteStore`: `echoBeforeAck` option that, when accepting a `push`, emits the document and lets the listeners run before returning `Ok`.
- [ ] **Step 2:** Test `own upload echoed before its acknowledgement is not a conflict` in `SyncEngineTest` (0 conflicts, empty outbox, document `SYNCED`) and check that it fails.
- [ ] **Step 3:** `SyncEngine`: `InFlight(entry, version)` in a `MutableStateFlow`; `OutboxProcessor` publishes it around `remote.push`; listeners check `isOwnEcho(doc)` before `applier.apply`. Test green.
- [ ] **Step 4:** `./gradlew :data:repository:testDebugUnitTest detekt` and commit.

### Task 2: DTOs and `FirestoreRemoteStore`

- [ ] **Step 1:** DTOs with the `firestore.rules` keys and model↔DTO conversions.
- [ ] **Step 2:** `FirestoreRemoteStore(firestore, auth)` following decisions 1–5.
- [ ] **Step 3:** `./gradlew :data:remote:compileDebugKotlinAndroid :data:remote:compileKotlinIosSimulatorArm64 detekt` and commit.

### Task 3: Emulator test

- [ ] **Step 1:** `FirestoreSyncEmulatorTest`: Ana creates a group and an expense → Luis… (can't read without joining; the test makes Luis join with `arrayUnion` as in the rules) → the expense reaches Luis; Luis edits it → it reaches Ana; concurrent edit → one conflict; no conflict from echoes.
- [ ] **Step 2:** `scripts/run-sync-emulator-test.sh` (starts the emulators with `emulators:exec` and runs only that class). Run it on the local Android emulator.
- [ ] **Step 3:** Commit.

### Task 4: App startup

- [ ] **Step 1:** Koin: `FirebaseFirestore` (in-memory cache), `FirebaseAuth`, `RemoteStore`, `RepositoryContext` (Auth uid), `SyncEngine`.
- [ ] **Step 2:** `SyncStarter`: anonymous sign-in and `engine.start` in the application scope; without `google-services.json` it does nothing.
- [ ] **Step 3:** `SyncWorker` + scheduling when `pendingCount > 0`.
- [ ] **Step 4:** Build, install on the emulator, check in logcat that it signs in and doesn't crash. Commit.

### Task 5: Documentation and PR

- [ ] **Step 1:** `docs/SYNC.md` (P6b implementation section), `docs/FIREBASE.md` (document format), plan index.
- [ ] **Step 2:** PR, green CI, merge.
