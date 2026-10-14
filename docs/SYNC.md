# Synchronization

Owlsplit is **local-first**: SQLDelight is the UI's source of truth and Firestore is the exchange medium between devices. Decision in [ADR 0003](adr/0003-local-first-sqldelight-sync.md).

## Principles

1. The UI **never** waits for the network: it reads SQLDelight Flows.
2. The unit of synchronization is the **document** (an expense, a member…). There is no field merging: a document wins or loses as a whole.
3. **Optimistic concurrency** with `version`: the first write to reach the server wins; the other is kept as a conflict and is never silently lost.
4. Everything is **idempotent**: repeating an upload or a download does not change the result.
5. The Firestore SDK's own offline persistence is **disabled** (in-memory cache) to avoid two caches diverging; the outbox is the only queue.

## Local tables (SQLDelight)

| Table | Purpose |
|---|---|
| `group_entity`, `member_entity`, `expense_entity`, `transfer_entity`, `change_log` | Entities (mirror of Firestore + local columns). Booleans as `INTEGER` 0/1 |
| `expense_payer`, `expense_split` | Payers and split values for each expense (fully rewritten on every edit) |
| `outbox` | Queue of pending writes: `id`, `groupId`, `entityType`, `entityId`, `baseVersion`, `payloadJson`, `attempts`, `nextAttemptAt`, `lastError`, `createdAt` |
| `sync_cursor` | Per group and collection: last applied remote `updatedAt` |
| `conflict` | Rejected local edits, to reapply or discard |
| `fx_rate_cache` | Exchange rates with fetch date (arrives in P9, first migration) |

Local columns on each entity: `syncState` (`SYNCED`, `PENDING`, `CONFLICT`), `serverVersion`.

## Local write

In **a single transaction**:
1. The entity is written with `version = serverVersion + 1` and `syncState = PENDING`.
2. The `outbox` entry is inserted (or **compacted**, if there was already a pending one for the same document) with `baseVersion = serverVersion` and the full document.
3. The local history entry is added.

Compaction: if there are several offline edits of the same document, only the latest state is uploaded, with the original `baseVersion`. So ten offline edits = one write.

## Upload (outbox → Firestore)

- Triggers: connectivity change, new outbox entry, app to foreground, periodic WorkManager (Android, with network constraint) and a foreground timer (iOS).
- Order: FIFO by `createdAt` within each group; groups are independent of each other.
- Each entry is written with a `set` of the full document, `version = baseVersion + 1` and `updatedAt = serverTimestamp()`. The rules require `request.resource.data.version == resource.data.version + 1` (or `== 1` on create).
- **Success** → removed from the outbox; `serverVersion = version`, `syncState = SYNCED` (if there is no other pending entry on top).
- **Transient error** (network, `unavailable`, `deadline-exceeded`) → `attempts++`, `nextAttemptAt = now + min(2^attempts s, 15 min)` with jitter.
- **`permission-denied`** → the remote document is read to tell apart:
  - Remote version ≥ `baseVersion + 1` → **conflict**: the remote one is applied locally, the local one goes to `conflict` and to the history ("Tu edición de *Cena* chocó con la de Ana" — "Your edit to *Dinner* clashed with Ana's") and the user is notified with the option to **Reapply** (creates a new edit on top of the remote version) or **Discard**.
  - No access to the group → **access lost**: the group becomes read-only; that group's outbox entries are discarded after the user confirms.
  - Other reason (validation) → `lastError` and a notice; it is a bug: it is logged to Crashlytics.

## Download (Firestore → local)

- For each group that is **open or recently active** (last 30 days, max. 20) there are listeners on `members`, `expenses`, `transfers` and the group document, with `where("updatedAt", ">=", cursor - 5 min)`, ordered by `updatedAt`. The margin covers server clocks of concurrent writes; idempotency makes repeats harmless.
- The remaining groups are synced with a one-off query when the app or the group is opened.
- For each received document:
  - If `remote.version > local.serverVersion` and there is no local pending entry → it is overwritten locally.
  - If there is a local pending entry → the remote `serverVersion` is stored; the pending upload will fail on version and enter the conflict flow. To avoid waiting, it is resolved right away with the same flow.
  - If `remote.version <= local.serverVersion` → ignored.
- The cursor advances to the largest applied `updatedAt` (after the local transaction commits).
- New group (join): full paginated download before showing it as synced.

## Deletions

- Soft: `deleted = true` is just another edit (with a version). They are hidden in the UI and excluded from balances.
- They can be restored from the history for 30 days; after that they are purged locally (in Firestore they stay flagged; remote purging will come with Cloud Functions in the future).

## History (`changelog`)

- Created on the client with each action and uploaded through the outbox as a create-only document (the rules forbid editing or deleting it).
- `diff`: human-readable list of `{field, before, after}`, generated in the domain.

## Sync status in the UI

| Status | Condition |
|---|---|
| Synced | empty outbox and active listeners |
| N pending changes | outbox with N entries and network available (uploading) |
| Offline | no network; N pending if any |
| Error | some entry with a non-transient error or an unresolved conflict |

## Tested cases (`data:sync`)

Offline and online create, edit and delete; compaction; ordering; retries with backoff; conflict (both branches: reapply and discard); access lost; process death mid-upload (idempotency); download with margin and duplicates; joining a large group with pagination; skewed device clock (no effect, because `updatedAt` comes from the server).

## Implementation (P6a)

Engine in `data:repository`, package `sync`: `RemoteStore` (port to the server; P6b implements it with Firestore), `SyncLocalStore` (outbox, cursors, access notices), `RemoteApplier` (apply remote and conflicts), `OutboxProcessor` (one upload pass) and `SyncEngine` (upload loop + listening). Clarifications to the above:

- **Upload version = `base_version + 1`**, not the payload's. If the document is edited while uploading, the entry is not removed on confirmation: its base advances to the written version and the next upload chains on.
- **Server rejection → the remote is read**: no access → access lost (retry in 1 h); same or higher version with **the same content** → it was our write whose acknowledgement was lost, accepted as done; higher with different content → conflict; lower → validation rejection (bug), retry in 1 h with `last_error`.
- **Strict per-group FIFO**: an entry waiting for its retry holds back the following ones in its group, even if they are ready. The loop wakes up at the first retry among the **group heads** (waking at the global minimum spun idly).
- **Apply remote**: lower version → ignored; equal with a pending local edit → ignored (echo of our upload); equal with nothing pending → refreshed (server `updatedAt`); higher with pending → conflict; whatever is applied always ends `SYNCED` with `serverVersion = version`.
- **Access lost**: stored as `accessLost:<group>` in the `meta` table. Detected on upload (the server denies the read) and on listen (an already synced group stops appearing in "my groups").
- Status exposed for now: `SyncEngine.pendingCount` and `isAccessLost(group)`; conflicts are in the `conflict` table and in the history (`CONFLICT`). The UI arrives in P9.

## Implementation (P6b): Firestore

`FirestoreRemoteStore` (`data:remote`, GitLive) implements `RemoteStore`; the app starts it in `composeApp/.../sync/SyncModule.kt`.

- **Format**: one DTO per type with exactly the keys in the rules (see [FIREBASE](FIREBASE.md#document-format)). Written with `set(merge = true)` so as not to erase `lastJoinCode`, which the client does not know.
- **In-memory Firestore cache**: the local copy is SQLDelight. Without network, `set` does not complete (the SDK queues it): after 20 s it is treated as transient and the outbox retries it; if the queued write lands first, the retry is rejected, the remote is read and recognized as our own.
- **Errors**: `PERMISSION_DENIED` → rejection (on upload) or no access (on read); everything else → transient. To resolve a rejection, the read goes to the **server** (the cache might hold our unconfirmed write).
- **Listeners**: only confirmed documents (no `hasPendingWrites`) and only each snapshot's changes. *My groups* is delivered complete and only when it neither comes from cache nor has pending writes, because the engine infers access loss from what is missing. Transient errors → retry with increasing backoff; `PERMISSION_DENIED` ends the listener without breaking the engine.
- **History with server time**: `changelog` also carries `updatedAt` (the rules require `request.time`) and is downloaded by it. Filtering by `createdAt` (client clock) lost entries created offline longer ago than the cursor margin.
- **Echo of our own upload**: with real Firestore the listener delivers our write **before** `set` returns. The engine remembers the in-flight upload (`InFlightUpload`) and, if that document arrives with that version and the same content, treats it as the acknowledgement. Without this every upload ended up as a conflict (the emulator test always fails without it).
- **Conflict seen twice**: if the listener already recorded the conflict while the upload was in flight, the later rejection does not record it again.
- **Startup**: without `google-services.json` nothing starts. With it, anonymous session (retries with backoff if there is no network; the real onboarding is P8) and `SyncEngine.start` with the uid of each session state. On Android, WorkManager (`SyncWorker`, with network, unique work `KEEP`) uploads pending changes even if the app is closed. On iOS the background trigger arrives in P11.
- **Resolved in P8**: onboarding does not finish without a session, so nothing is created without `createdBy`. Each user's engine lives in its own scope: switching sessions cancels the previous one.
- **Tests**: `FirestoreSyncEmulatorTest` (two devices with the Firestore and Auth emulators and the real rules: join with code, changes in both directions, concurrent edit → a single conflict). Run with `scripts/run-sync-emulator-test.sh` with an Android emulator connected; not in CI.

## Conflicts in the UI (P9c)

The history's conflict entry (and the "Cambios por revisar" ("Changes to review") section of Activity) offers **Reaplicar mi cambio** ("Reapply my change") and **Descartar** ("Discard"). Reapply reads the losing version stored in `conflict` and writes it through the normal repositories (`ConflictRepositoryImpl`): it is just another edit, with a new version on top of the winner, which is uploaded, recorded in the history and may clash again. Discard only deletes the `conflict` row. Deleted expenses can be restored from the history for 30 days.
