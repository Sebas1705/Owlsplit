# P6a · Motor de sincronización — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implementar el motor de [SYNC.md](../SYNC.md) sobre la base local de P4: subida del outbox con reintentos y backoff, versión encadenada, detección de conflictos (la versión perdedora se guarda), idempotencia tras un acuse perdido, acceso perdido, aplicación de documentos remotos con cursores y escucha de mis grupos. Todo probado contra un Firestore **falso** que imita las reglas de P5.

**Architecture:** El motor vive en `data:repository` (paquete `sync`), donde ya están las conversiones fila↔modelo. Define el puerto `RemoteStore` (lo que necesita del servidor); P6b lo implementa con GitLive en `data:remote`. Piezas: `SyncLocalStore` (operaciones locales de sincronización, cada una en su transacción), `OutboxProcessor` (una pasada de subida), `SyncEngine` (bucle de subida que reacciona al outbox y escucha remota de grupos y subcolecciones). La versión que se sube siempre es `base_version + 1` (no la del payload), lo que permite que una edición durante la subida simplemente avance la base.

**Tech Stack:** Kotlin Multiplatform, SQLDelight 2.4.1 (+ coroutines-extensions), kotlinx-coroutines 1.11 (`Mutex`, `collectLatest`), kotlinx-serialization, Turbine/coroutines-test para los tests.

**Platform:** KMP (código común; tests en la JVM de Android)

**Depende de:** P4 (base local y repositorios), P5 (las reglas que imita el falso).

---

## Decisiones

1. **Versión de subida = `base_version + 1`.** Si el documento se edita mientras se sube, al confirmar la subida la entrada del outbox no se borra: avanza su base a la versión recién escrita y la siguiente subida encadena.
2. **Rechazo (`PERMISSION_DENIED`) → leer el remoto:** sin acceso → *acceso perdido* (no se reintenta en 1 h); versión remota ≥ la que intentábamos y mismo contenido → era nuestra escritura (acuse perdido) → se da por buena; versión remota ≥ y otro contenido → **conflicto**: gana el servidor, la versión local va a `conflict` y al historial; versión menor → rechazo por validación (bug) → reintento en 1 h con `last_error`.
3. **Orden FIFO por grupo:** si una entrada de un grupo no se pudo subir, el resto de ese grupo espera a la siguiente pasada (un miembro no puede subir antes que su grupo).
4. **Aplicar remoto:** versión menor que la conocida → se ignora; igual y sin pendiente local → se refresca (llega `updatedAt` del servidor); igual con pendiente → se ignora (es el eco de nuestra subida); mayor con pendiente → conflicto; mayor sin pendiente → se aplica.
5. **Acceso perdido por la escucha:** si un grupo ya sincronizado deja de aparecer en "mis grupos", se marca como perdido (clave `accessLost:<id>` en la tabla `meta`; sin migración).
6. Disparadores de plataforma (WorkManager, primer plano) y adaptador real: P6b.

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Modificar | `data/local/.../db/Outbox.sq`, `GroupEntity.sq`, `MemberEntity.sq`, `ExpenseEntity.sq`, `TransferEntity.sq`, `ChangeLog.sq`, `Conflict.sq` (solo consultas nuevas) |
| Crear | `data/repository/src/commonMain/kotlin/es/sebas1705/owlsplit/data/repository/sync/`: `RemoteStore.kt`, `Payloads.kt`, `SyncLocalStore.kt`, `OutboxProcessor.kt`, `SyncEngine.kt` |
| Modificar | `data/repository/src/androidUnitTest/.../TestEnv.kt` (reloj ligado al planificador de test) |
| Test | `data/repository/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/repository/sync/`: `FakeRemoteStore.kt`, `OutboxProcessorTest.kt`, `SyncEngineTest.kt` |
| Modificar | `docs/SYNC.md`, `MODULE_MAP.md`, `docs/plans/README.md` |

---

### Task 1: Consultas de sincronización

- [ ] **Step 1:** Añadir al final de cada archivo:

`Outbox.sq`:
```sql
selectReady:
SELECT * FROM outbox WHERE next_attempt_at <= ? ORDER BY created_at, id;

selectById:
SELECT * FROM outbox WHERE id = ?;

deleteById:
DELETE FROM outbox WHERE id = ?;

scheduleRetry:
UPDATE outbox SET attempts = attempts + 1, next_attempt_at = ?, last_error = ? WHERE id = ?;

advanceBase:
UPDATE outbox SET base_version = ? WHERE id = ?;

nextAttemptAt:
SELECT coalesce(min(next_attempt_at), -1) FROM outbox;
```
`GroupEntity.sq`, `MemberEntity.sq`, `ExpenseEntity.sq`, `TransferEntity.sq` (cambiando el nombre de la tabla en cada uno):
```sql
markSynced:
UPDATE group_entity SET server_version = :version, sync_state = 'SYNCED' WHERE id = :id;

setServerVersion:
UPDATE group_entity SET server_version = :version WHERE id = :id;
```
`ChangeLog.sq`:
```sql
selectById:
SELECT * FROM change_log WHERE id = ?;

markSynced:
UPDATE change_log SET sync_state = 'SYNCED' WHERE id = ?;
```
`Conflict.sq`:
```sql
countAll:
SELECT count(*) FROM conflict;
```

- [ ] **Step 2:** `./gradlew :data:local:assembleDebug verifySqlDelightMigration` → verde (el esquema no cambia, `1.db` sigue válido).
- [ ] **Step 3:** `git commit -m "feat(local): queries for the sync engine"`.

### Task 2: El puerto remoto y la carga útil

- [ ] **Step 1: `sync/RemoteStore.kt`**

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.coroutines.flow.Flow

/** A document as the server has it: [version] and [updatedAt] (server time) live in its meta. */
sealed interface RemoteDoc {
    val groupId: String
    val id: String
    val version: Long
    val updatedAt: Long
    val type: OutboxType

    data class GroupDoc(val group: Group) : RemoteDoc {
        override val groupId: String get() = group.id
        override val id: String get() = group.id
        override val version: Long get() = group.meta.version
        override val updatedAt: Long get() = group.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.GROUP
    }

    data class MemberDoc(val member: Member) : RemoteDoc {
        override val groupId: String get() = member.groupId
        override val id: String get() = member.id
        override val version: Long get() = member.meta.version
        override val updatedAt: Long get() = member.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.MEMBER
    }

    data class ExpenseDoc(val expense: Expense) : RemoteDoc {
        override val groupId: String get() = expense.groupId
        override val id: String get() = expense.id
        override val version: Long get() = expense.meta.version
        override val updatedAt: Long get() = expense.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.EXPENSE
    }

    data class TransferDoc(val transfer: Transfer) : RemoteDoc {
        override val groupId: String get() = transfer.groupId
        override val id: String get() = transfer.id
        override val version: Long get() = transfer.meta.version
        override val updatedAt: Long get() = transfer.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.TRANSFER
    }

    /** History entries are create-only: version 1, ordered by creation time. */
    data class ChangeLogDoc(val entry: ChangeLogEntry) : RemoteDoc {
        override val groupId: String get() = entry.groupId
        override val id: String get() = entry.id
        override val version: Long get() = 1L
        override val updatedAt: Long get() = entry.createdAt
        override val type: OutboxType get() = OutboxType.CHANGE_LOG
    }
}

/** Sub-collections of a group that are pulled incrementally by `updatedAt`. */
enum class RemoteCollection(val type: OutboxType) {
    MEMBERS(OutboxType.MEMBER),
    EXPENSES(OutboxType.EXPENSE),
    TRANSFERS(OutboxType.TRANSFER),
    CHANGELOG(OutboxType.CHANGE_LOG),
}

sealed interface PushResult {
    data object Ok : PushResult
    /** Network or server hiccup: retry later. */
    data object Transient : PushResult
    /** The server refused the write (Firestore PERMISSION_DENIED): version or access problem. */
    data object Rejected : PushResult
}

sealed interface FetchResult {
    data class Found(val doc: RemoteDoc) : FetchResult
    data object Missing : FetchResult
    data object NoAccess : FetchResult
    data object Transient : FetchResult
}

/** What the sync engine needs from the server. P6b implements it on Firestore (GitLive). */
interface RemoteStore {
    /**
     * Writes the document encoded in [payload] (JSON of the model, as queued by the repositories)
     * as [version], with the server time as `updatedAt` and the caller as `updatedBy`.
     */
    suspend fun push(type: OutboxType, groupId: String, entityId: String, version: Long, payload: String): PushResult

    suspend fun fetch(type: OutboxType, groupId: String, entityId: String): FetchResult

    /** Every group whose `memberUids` contains [uid]; emits the full set on each change. */
    fun observeMyGroups(uid: String): Flow<List<RemoteDoc.GroupDoc>>

    /** Documents of [collection] with `updatedAt >= sinceMillis`; emits the full set on each change. */
    fun observe(groupId: String, collection: RemoteCollection, sinceMillis: Long): Flow<List<RemoteDoc>>
}
```

- [ ] **Step 2: `sync/Payloads.kt`**

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.json

/** Decodes an outbox payload into the document the server would store as [version] at [updatedAt]. */
fun decodeRemoteDoc(type: OutboxType, payload: String, version: Long, updatedAt: Long, updatedBy: String): RemoteDoc {
    fun SyncMeta.server() = copy(
        version = version, serverVersion = version, updatedAt = updatedAt, updatedBy = updatedBy,
        syncState = SyncState.SYNCED,
    )
    return when (type) {
        OutboxType.GROUP -> json.decodeFromString(Group.serializer(), payload).let { RemoteDoc.GroupDoc(it.copy(meta = it.meta.server())) }
        OutboxType.MEMBER -> json.decodeFromString(Member.serializer(), payload).let { RemoteDoc.MemberDoc(it.copy(meta = it.meta.server())) }
        OutboxType.EXPENSE -> json.decodeFromString(Expense.serializer(), payload).let { RemoteDoc.ExpenseDoc(it.copy(meta = it.meta.server())) }
        OutboxType.TRANSFER -> json.decodeFromString(Transfer.serializer(), payload).let { RemoteDoc.TransferDoc(it.copy(meta = it.meta.server())) }
        OutboxType.CHANGE_LOG -> RemoteDoc.ChangeLogDoc(json.decodeFromString(ChangeLogEntry.serializer(), payload))
    }
}

/** True when [payload] carries the same content as this remote document (sync metadata aside, `deleted` included). */
internal fun RemoteDoc.sameContentAs(payload: String): Boolean {
    val local = decodeRemoteDoc(type, payload, version, updatedAt, updatedBy = "")
    return neutral() == local.neutral()
}

private fun SyncMeta.neutral() = SyncMeta(0, 0, null, "", deleted, SyncState.SYNCED)

private fun RemoteDoc.neutral(): Any = when (this) {
    is RemoteDoc.GroupDoc -> group.copy(meta = group.meta.neutral())
    is RemoteDoc.MemberDoc -> member.copy(meta = member.meta.neutral())
    is RemoteDoc.ExpenseDoc -> expense.copy(meta = expense.meta.neutral())
    is RemoteDoc.TransferDoc -> transfer.copy(meta = transfer.meta.neutral())
    is RemoteDoc.ChangeLogDoc -> entry
}
```
`json` es `internal` en `Rows.kt`; mismo módulo, así que es accesible.

- [ ] **Step 3:** compilar (`./gradlew :data:repository:assembleDebug`) y `git commit -m "feat(sync): remote store port and payload decoding"`.

### Task 3: Operaciones locales de sincronización

- [ ] **Step 1: `sync/SyncLocalStore.kt`**

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Expense_payer
import es.sebas1705.owlsplit.data.local.db.Expense_split
import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.repository.Writer
import es.sebas1705.owlsplit.data.repository.toModel
import es.sebas1705.owlsplit.data.repository.toRow

/** Local side of synchronisation. Every public operation runs in its own transaction. */
internal class SyncLocalStore(private val db: AppDatabase, private val writer: Writer) {

    fun now(): Long = writer.now()

    fun ready(now: Long): List<Outbox> = db.outboxQueries.selectReady(now).executeAsList()

    /** -1 when the outbox is empty. */
    fun nextAttemptAt(): Long = db.outboxQueries.nextAttemptAt().executeAsOne()

    fun scheduleRetry(entry: Outbox, at: Long, error: String) = db.outboxQueries.scheduleRetry(at, error, entry.id)

    fun isAccessLost(groupId: String): Boolean = db.metaQueries.get(accessKey(groupId)).executeAsOneOrNull() == "1"

    fun markAccessLost(groupId: String) = db.metaQueries.put(accessKey(groupId), "1")

    /** Local groups already known to the server that list [uid] as member. */
    fun syncedGroupIdsOf(uid: String): Set<String> =
        db.groupEntityQueries.selectVisible().executeAsList()
            .map { it.toModel() }
            .filter { it.meta.serverVersion > 0 && uid in it.memberUids }
            .mapTo(HashSet()) { it.id }

    fun cursor(groupId: String, collection: RemoteCollection): Long =
        db.syncCursorQueries.get(groupId, collection.name).executeAsOneOrNull() ?: 0L

    fun advanceCursor(groupId: String, collection: RemoteCollection, updatedAt: Long) = db.transaction {
        if (updatedAt > cursor(groupId, collection)) db.syncCursorQueries.put(groupId, collection.name, updatedAt)
    }

    /** After [entry] was written remotely as [version]. */
    fun onPushed(entry: Outbox, version: Long) = db.transaction {
        val current = db.outboxQueries.selectById(entry.id).executeAsOneOrNull() ?: return@transaction
        val type = OutboxType.valueOf(entry.entity_type)
        if (current.payload == entry.payload) {
            db.outboxQueries.deleteById(entry.id)
            markSynced(type, entry.entity_id, version)
        } else {
            // Edited again while uploading: the next upload chains on what was just written.
            db.outboxQueries.advanceBase(version, entry.id)
            setServerVersion(type, entry.entity_id, version)
        }
    }

    /** The server kept someone else's version: store ours as a conflict and adopt theirs. */
    fun resolveConflict(entry: Outbox, remote: RemoteDoc) = db.transaction {
        recordConflict(entry, remote)
        write(remote)
    }

    /** Applies a remote document following docs/SYNC.md ("Bajada"). Returns true if local data changed. */
    fun apply(remote: RemoteDoc): Boolean = db.transactionWithResult {
        if (remote is RemoteDoc.ChangeLogDoc) {
            db.changeLogQueries.insert(remote.entry.toRow(SyncState.SYNCED))
            return@transactionWithResult true
        }
        val known = serverVersion(remote.type, remote.id)
        val pending = db.outboxQueries.selectByEntity(remote.type.name, remote.id).executeAsOneOrNull()
        when {
            known != null && remote.version < known -> false
            known != null && remote.version == known && pending != null -> false
            else -> {
                if (pending != null) recordConflict(pending, remote)
                write(remote)
                true
            }
        }
    }

    private fun recordConflict(entry: Outbox, remote: RemoteDoc) {
        db.conflictQueries.insert(entry.group_id, entry.entity_type, entry.entity_id, entry.payload, remote.version, now())
        db.outboxQueries.deleteById(entry.id)
        writer.log(remote.groupId, EntityType.valueOf(remote.type.name), remote.id, ChangeAction.CONFLICT, emptyList())
    }

    private fun write(remote: RemoteDoc) {
        when (remote) {
            is RemoteDoc.GroupDoc -> db.groupEntityQueries.upsert(remote.group.toRow())
            is RemoteDoc.MemberDoc -> db.memberEntityQueries.upsert(remote.member.toRow())
            is RemoteDoc.TransferDoc -> db.transferEntityQueries.upsert(remote.transfer.toRow())
            is RemoteDoc.ExpenseDoc -> {
                val q = db.expenseEntityQueries
                val e = remote.expense
                q.upsert(e.toRow())
                q.deletePayers(e.id)
                e.payers.forEach { (member, amount) -> q.insertPayer(Expense_payer(e.id, member, amount)) }
                q.deleteSplits(e.id)
                e.splitValues.forEach { (member, value) -> q.insertSplit(Expense_split(e.id, member, value)) }
            }
            is RemoteDoc.ChangeLogDoc -> db.changeLogQueries.insert(remote.entry.toRow(SyncState.SYNCED))
        }
    }

    private fun serverVersion(type: OutboxType, id: String): Long? = when (type) {
        OutboxType.GROUP -> db.groupEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.MEMBER -> db.memberEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.EXPENSE -> db.expenseEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.TRANSFER -> db.transferEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.CHANGE_LOG -> db.changeLogQueries.selectById(id).executeAsOneOrNull()?.let { 1L }
    }

    private fun markSynced(type: OutboxType, id: String, version: Long) = when (type) {
        OutboxType.GROUP -> db.groupEntityQueries.markSynced(version, id)
        OutboxType.MEMBER -> db.memberEntityQueries.markSynced(version, id)
        OutboxType.EXPENSE -> db.expenseEntityQueries.markSynced(version, id)
        OutboxType.TRANSFER -> db.transferEntityQueries.markSynced(version, id)
        OutboxType.CHANGE_LOG -> db.changeLogQueries.markSynced(id)
    }

    private fun setServerVersion(type: OutboxType, id: String, version: Long) {
        when (type) {
            OutboxType.GROUP -> db.groupEntityQueries.setServerVersion(version, id)
            OutboxType.MEMBER -> db.memberEntityQueries.setServerVersion(version, id)
            OutboxType.EXPENSE -> db.expenseEntityQueries.setServerVersion(version, id)
            OutboxType.TRANSFER -> db.transferEntityQueries.setServerVersion(version, id)
            OutboxType.CHANGE_LOG -> Unit
        }
    }

    private fun accessKey(groupId: String) = "accessLost:$groupId"
}
```
Para que compile: en `Rows.kt` y `Writer.kt` las funciones/clases ya son `internal` (mismo módulo). Las consultas `markSynced`/`setServerVersion` con parámetros con nombre generan la firma `(version: Long, id: String)`.

- [ ] **Step 2:** compilar y `git commit -m "feat(sync): local store for pushes, conflicts, remote apply and cursors"`.

### Task 4: Firestore falso y subida

- [ ] **Step 1: Reloj de test ligado al planificador**

En `TestEnv.kt`, el reloj pasa a `Instant.fromEpochMilliseconds(nowMillis + scope.testScheduler.currentTime)`, para que `delay` en los tests haga avanzar también la hora del motor (los tests de P4 no usan `delay`, así que no cambian).

- [ ] **Step 2: `sync/FakeRemoteStore.kt`** (en `androidUnitTest`)

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory server that mimics the P5 rules that matter to sync: chained versions, member-only
 * access (groups can always be created by their first member) and server time.
 */
class FakeRemoteStore(private val now: () -> Long) : RemoteStore {
    private val docs = MutableStateFlow<Map<String, RemoteDoc>>(emptyMap())
    var callerUid = "uid-ana"
    val scripted = ArrayDeque<PushResult>()
    val pushLog = mutableListOf<Pair<OutboxType, String>>()

    fun doc(type: OutboxType, id: String): RemoteDoc? = docs.value[key(type, id)]

    /** Another device wrote [doc] directly on the server. */
    fun serverWrite(doc: RemoteDoc) = docs.update { it + (key(doc.type, doc.id) to doc) }

    override suspend fun push(type: OutboxType, groupId: String, entityId: String, version: Long, payload: String): PushResult {
        pushLog += type to entityId
        scripted.removeFirstOrNull()?.let { return it }
        val current = doc(type, entityId)
        val creatingGroup = type == OutboxType.GROUP && current == null
        val allowed = when {
            type == OutboxType.CHANGE_LOG -> current == null && isMember(groupId)
            creatingGroup -> version == 1L
            else -> isMember(groupId) && version == (current?.version ?: 0L) + 1
        }
        if (!allowed) return PushResult.Rejected
        serverWrite(decodeRemoteDoc(type, payload, version, now(), callerUid))
        return PushResult.Ok
    }

    override suspend fun fetch(type: OutboxType, groupId: String, entityId: String): FetchResult {
        if (doc(OutboxType.GROUP, groupId) != null && !isMember(groupId)) return FetchResult.NoAccess
        return doc(type, entityId)?.let { FetchResult.Found(it) } ?: FetchResult.Missing
    }

    override fun observeMyGroups(uid: String): Flow<List<RemoteDoc.GroupDoc>> =
        docs.map { all -> all.values.filterIsInstance<RemoteDoc.GroupDoc>().filter { uid in it.group.memberUids } }

    override fun observe(groupId: String, collection: RemoteCollection, sinceMillis: Long): Flow<List<RemoteDoc>> =
        docs.map { all ->
            all.values.filter { it.groupId == groupId && it.type == collection.type && it.updatedAt >= sinceMillis }
        }

    private fun isMember(groupId: String): Boolean =
        (doc(OutboxType.GROUP, groupId) as? RemoteDoc.GroupDoc)?.group?.memberUids?.contains(callerUid) == true

    private fun key(type: OutboxType, id: String) = "$type/$id"
}
```

- [ ] **Step 3: Tests de subida** — `sync/OutboxProcessorTest.kt`

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.repository.GroupInput
import es.sebas1705.owlsplit.data.repository.TestEnv
import es.sebas1705.owlsplit.data.repository.Writer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboxProcessorTest {
    private val trip = GroupInput("Ibiza 2026", "plane", "BLUE", "EUR")

    private class Harness(scope: TestScope) {
        val env = TestEnv(scope)
        val remote = FakeRemoteStore { env.ctx.clock.now().toEpochMilliseconds() }
        val local = SyncLocalStore(env.db, Writer(env.db, env.ctx))
        val processor = OutboxProcessor(local, remote, Random(0))
        suspend fun push() = processor.processReady()
        suspend fun group(id: String) = env.groups.observeGroup(id).first()!!
    }

    @Test
    fun `a new group and its creator member are uploaded and marked synced`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.push()
        assertEquals(1L, h.remote.doc(OutboxType.GROUP, g)!!.version)
        assertEquals(SyncState.SYNCED, h.group(g).meta.syncState)
        assertEquals(1L, h.group(g).meta.serverVersion)
        assertTrue(h.env.outbox().isEmpty())
        assertEquals(listOf(OutboxType.GROUP, OutboxType.CHANGE_LOG, OutboxType.MEMBER, OutboxType.CHANGE_LOG), h.remote.pushLog.map { it.first })
    }

    @Test
    fun `offline edits upload once and later edits chain the version`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.env.groups.update(g, trip.copy(name = "Ibiza"))
        h.env.groups.update(g, trip.copy(name = "Ibiza!"))
        h.push()
        assertEquals(1, h.remote.pushLog.count { it.first == OutboxType.GROUP })
        assertEquals("Ibiza!", (h.remote.doc(OutboxType.GROUP, g) as RemoteDoc.GroupDoc).group.name)

        h.env.groups.update(g, trip.copy(name = "Ibiza 26"))
        h.push()
        assertEquals(2L, h.remote.doc(OutboxType.GROUP, g)!!.version)
    }

    @Test
    fun `a transient failure retries later and blocks the rest of the group`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.remote.scripted += PushResult.Transient
        h.push()
        assertEquals(1, h.remote.pushLog.size) // the member waited for its group
        val entry = h.env.outbox().first()
        assertEquals(1L, entry.attempts)
        assertTrue(entry.next_attempt_at > h.local.now())

        h.push() // too early: nothing is retried
        assertEquals(1, h.remote.pushLog.size)

        h.env.nowMillis = entry.next_attempt_at
        h.push()
        assertEquals(SyncState.SYNCED, h.group(g).meta.syncState)
    }

    @Test
    fun `an edit made during the upload advances the base instead of being lost`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val entry = h.env.outbox().first { it.entity_type == "GROUP" }
        // Uploaded as v1, but the user edits before the upload is confirmed locally.
        h.remote.push(OutboxType.GROUP, g, g, 1, entry.payload)
        h.env.groups.update(g, trip.copy(name = "Edited meanwhile"))
        h.local.onPushed(entry, 1L)
        val after = h.env.outbox().first { it.entity_type == "GROUP" }
        assertEquals(1L, after.base_version)
        h.push()
        assertEquals(2L, h.remote.doc(OutboxType.GROUP, g)!!.version)
        assertEquals("Edited meanwhile", (h.remote.doc(OutboxType.GROUP, g) as RemoteDoc.GroupDoc).group.name)
    }

    @Test
    fun `losing an acknowledgement does not create a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val entry = h.env.outbox().first { it.entity_type == "GROUP" }
        h.remote.push(OutboxType.GROUP, g, g, 1, entry.payload) // written, but the app died before recording it
        h.push()
        assertEquals(SyncState.SYNCED, h.group(g).meta.syncState)
        assertEquals(0L, h.env.db.conflictQueries.countAll().executeAsOne())
    }

    @Test
    fun `a concurrent edit by someone else wins and ours is kept as a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.push()
        val theirs = h.remote.doc(OutboxType.GROUP, g) as RemoteDoc.GroupDoc
        h.remote.serverWrite(theirs.copy(group = theirs.group.copy(name = "Theirs", meta = theirs.group.meta.copy(version = 2))))

        h.env.groups.update(g, trip.copy(name = "Mine"))
        h.push()

        assertEquals("Theirs", h.group(g).name)
        assertEquals(1L, h.env.db.conflictQueries.countAll().executeAsOne())
        assertTrue(h.env.db.conflictQueries.selectByGroup(g).executeAsList().single().local_payload.contains("Mine"))
        val history = h.env.changeLog.observe(g).first()
        assertTrue(history.any { it.action == ChangeAction.CONFLICT })
    }

    @Test
    fun `losing access stops uploads for that group`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.push()
        val doc = h.remote.doc(OutboxType.GROUP, g) as RemoteDoc.GroupDoc
        h.remote.serverWrite(doc.copy(group = doc.group.copy(memberUids = listOf("uid-luis"), meta = doc.group.meta.copy(version = 2))))

        h.env.groups.update(g, trip.copy(name = "Still mine?"))
        h.push()
        assertTrue(h.local.isAccessLost(g))
        val pushes = h.remote.pushLog.size
        h.push()
        assertEquals(pushes, h.remote.pushLog.size)
    }
}
```

- [ ] **Step 4: Ver que falla** (`OutboxProcessor` no existe): `./gradlew :data:repository:testDebugUnitTest --tests "*OutboxProcessorTest"`.

- [ ] **Step 5: `sync/OutboxProcessor.kt`**

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlin.math.min
import kotlin.random.Random

/** One upload pass over the outbox (docs/SYNC.md, "Subida"). */
internal class OutboxProcessor(
    private val local: SyncLocalStore,
    private val remote: RemoteStore,
    private val random: Random,
) {
    suspend fun processReady() {
        val blocked = HashSet<String>()
        for (entry in local.ready(local.now())) {
            if (entry.group_id in blocked) continue
            if (local.isAccessLost(entry.group_id)) {
                local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "access lost")
                continue
            }
            if (!process(entry)) blocked += entry.group_id
        }
    }

    /** Returns false when the entry stays pending (the rest of its group must wait). */
    private suspend fun process(entry: Outbox): Boolean {
        val type = OutboxType.valueOf(entry.entity_type)
        val version = if (type == OutboxType.CHANGE_LOG) 1L else entry.base_version + 1
        return when (remote.push(type, entry.group_id, entry.entity_id, version, entry.payload)) {
            PushResult.Ok -> {
                local.onPushed(entry, version)
                true
            }
            PushResult.Transient -> {
                retry(entry, "transient")
                false
            }
            PushResult.Rejected -> onRejected(entry, type, version)
        }
    }

    private suspend fun onRejected(entry: Outbox, type: OutboxType, version: Long): Boolean =
        when (val found = remote.fetch(type, entry.group_id, entry.entity_id)) {
            FetchResult.NoAccess -> {
                local.markAccessLost(entry.group_id)
                local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "access lost")
                false
            }
            FetchResult.Transient -> {
                retry(entry, "transient")
                false
            }
            FetchResult.Missing -> {
                local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "rejected")
                false
            }
            is FetchResult.Found -> when {
                found.doc.version >= version && found.doc.sameContentAs(entry.payload) -> {
                    local.onPushed(entry, found.doc.version)
                    true
                }
                found.doc.version >= version -> {
                    local.resolveConflict(entry, found.doc)
                    true
                }
                else -> {
                    local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "rejected")
                    false
                }
            }
        }

    private fun retry(entry: Outbox, error: String) {
        val exponential = min(BASE_DELAY_MILLIS shl min(entry.attempts.toInt(), MAX_SHIFT), MAX_DELAY_MILLIS)
        local.scheduleRetry(entry, local.now() + exponential + random.nextLong(JITTER_MILLIS), error)
    }

    private companion object {
        const val BASE_DELAY_MILLIS = 1_000L
        const val MAX_DELAY_MILLIS = 15 * 60 * 1_000L
        const val MAX_SHIFT = 20
        const val JITTER_MILLIS = 1_000L
        const val RECHECK_MILLIS = 60 * 60 * 1_000L
    }
}
```

- [ ] **Step 6:** Ver que pasa (7 tests) y `git commit -m "feat(sync): outbox processor with backoff, conflicts, idempotency and access loss"`.

### Task 5: Motor con escucha remota

- [ ] **Step 1: Tests** — `sync/SyncEngineTest.kt`

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.repository.ExpenseInput
import es.sebas1705.owlsplit.data.repository.GroupInput
import es.sebas1705.owlsplit.data.repository.TestEnv
import es.sebas1705.owlsplit.data.repository.observeMembersOnce
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncEngineTest {
    private val trip = GroupInput("Ibiza 2026", "plane", "BLUE", "EUR")

    private fun dinner(payer: String) = ExpenseInput(
        "Cena", ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", 4_860, 1_000_000_000,
        mapOf(payer to 4_860L), SplitType.EQUAL, mapOf(payer to 1L),
    )

    private class Harness(val scope: TestScope) {
        val env = TestEnv(scope)
        val remote = FakeRemoteStore { env.ctx.clock.now().toEpochMilliseconds() }
        val engine = SyncEngine(env.db, remote, env.ctx, env.dispatcher, Random(0))
        fun settle() {
            scope.advanceTimeBy(5_000)
            scope.runCurrent()
        }
    }

    @Test
    fun `local writes are uploaded automatically while the engine runs`() = runTest {
        val h = Harness(this)
        val job = h.engine.start(this, "uid-ana")
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        h.env.expenses.create(g, dinner(ana))
        h.settle()
        assertNotNull(h.remote.doc(OutboxType.GROUP, g))
        assertEquals(1, h.remote.pushLog.count { it.first == OutboxType.EXPENSE })
        assertEquals(0L, h.engine.pendingCount.first())
        job.cancel()
    }

    @Test
    fun `remote changes are pulled and older versions ignored`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        val job = h.engine.start(this, "uid-ana")
        h.settle()

        val synced = h.remote.doc(OutboxType.EXPENSE, e) as RemoteDoc.ExpenseDoc
        val newer = synced.expense.copy(title = "Cena con postre", meta = synced.expense.meta.copy(version = 3, updatedAt = h.env.nowMillis + 10_000))
        h.remote.serverWrite(RemoteDoc.ExpenseDoc(newer))
        h.settle()
        assertEquals("Cena con postre", h.env.expenses.get(e)!!.title)

        h.remote.serverWrite(RemoteDoc.ExpenseDoc(newer.copy(title = "Old", meta = newer.meta.copy(version = 2))))
        h.settle()
        assertEquals("Cena con postre", h.env.expenses.get(e)!!.title)
        assertEquals(SyncState.SYNCED, h.env.expenses.get(e)!!.meta.syncState)
        job.cancel()
    }

    @Test
    fun `a pulled change over a pending local edit becomes a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        h.engine.pushNow()

        h.remote.scripted += PushResult.Transient // keep our next edit pending
        h.env.expenses.update(e, dinner(ana).copy(title = "Mine"))
        h.engine.pushNow()
        val synced = h.remote.doc(OutboxType.EXPENSE, e) as RemoteDoc.ExpenseDoc
        h.remote.serverWrite(RemoteDoc.ExpenseDoc(synced.expense.copy(title = "Theirs", meta = synced.expense.meta.copy(version = 2, updatedAt = h.env.nowMillis + 1))))

        val job = h.engine.start(this, "uid-ana")
        h.settle()
        assertEquals("Theirs", h.env.expenses.get(e)!!.title)
        assertEquals(1L, h.env.db.conflictQueries.countAll().executeAsOne())
        assertTrue(h.env.outbox().none { it.entity_type == "EXPENSE" })
        job.cancel()
    }

    @Test
    fun `a group that disappears from my groups is marked as lost`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        val doc = h.remote.doc(OutboxType.GROUP, g) as RemoteDoc.GroupDoc
        h.remote.serverWrite(doc.copy(group = doc.group.copy(memberUids = listOf("uid-luis"), meta = doc.group.meta.copy(version = 2))))
        h.settle()
        assertTrue(h.engine.isAccessLost(g))
        job.cancel()
    }
}
```

- [ ] **Step 2: Ver que falla** (`SyncEngine` no existe).

- [ ] **Step 3: `sync/SyncEngine.kt`**

```kotlin
package es.sebas1705.owlsplit.data.repository.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.repository.RepositoryContext
import es.sebas1705.owlsplit.data.repository.Writer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Synchronises the local database with the server (docs/SYNC.md). [start] runs the upload loop
 * and the remote listeners until its scope is cancelled; [pushNow] is the hook for platform
 * triggers (WorkManager, app in foreground).
 */
class SyncEngine(
    db: AppDatabase,
    private val remote: RemoteStore,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
    random: Random = Random.Default,
) {
    private val local = SyncLocalStore(db, Writer(db, ctx))
    private val processor = OutboxProcessor(local, remote, random)
    private val pushLock = Mutex()

    /** Number of writes waiting to be uploaded. */
    val pendingCount: Flow<Long> = db.outboxQueries.countAll().asFlow().mapToOne(io)

    fun isAccessLost(groupId: String): Boolean = local.isAccessLost(groupId)

    suspend fun pushNow() = withContext(io) { pushLock.withLock { processor.processReady() } }

    fun start(scope: CoroutineScope, uid: String): Job = scope.launch(io) {
        launch { pushLoop() }
        launch { pullMyGroups(uid) }
    }

    private suspend fun pushLoop() {
        pendingCount.collectLatest { pending ->
            if (pending == 0L) return@collectLatest
            while (true) {
                pushNow()
                val next = local.nextAttemptAt()
                if (next < 0) break
                val wait = next - local.now()
                if (wait > 0) delay(wait)
            }
        }
    }

    private suspend fun pullMyGroups(uid: String) = coroutineScope {
        val listeners = HashMap<String, Job>()
        remote.observeMyGroups(uid).collect { docs ->
            // Before applying: once applied, a group that dropped me no longer lists me locally either.
            val known = local.syncedGroupIdsOf(uid)
            docs.forEach { local.apply(it) }
            val ids = docs.filterNot { it.group.meta.deleted }.mapTo(HashSet()) { it.id }
            (known - ids).forEach { local.markAccessLost(it) }
            (listeners.keys - ids).forEach { listeners.remove(it)?.cancel() }
            (ids - listeners.keys).forEach { id -> listeners[id] = launch { pullGroup(id) } }
        }
    }

    private suspend fun pullGroup(groupId: String) = coroutineScope {
        RemoteCollection.entries.forEach { collection ->
            launch {
                val since = (local.cursor(groupId, collection) - CURSOR_MARGIN_MILLIS).coerceAtLeast(0)
                remote.observe(groupId, collection, since).collect { docs ->
                    docs.forEach { local.apply(it) }
                    docs.maxOfOrNull { it.updatedAt }?.let { local.advanceCursor(groupId, collection, it) }
                }
            }
        }
    }

    private companion object {
        /** Re-read a little before the cursor: concurrent writes can carry close server times. */
        const val CURSOR_MARGIN_MILLIS = 5 * 60 * 1_000L
    }
}
```

- [ ] **Step 4:** Ver que pasan (4 tests) y commit `feat(sync): sync engine with upload loop and remote listeners`.

### Task 6: Cierre

- [ ] Todo verde: `./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug`.
- [ ] `docs/SYNC.md`: sección nueva "Implementación (P6a)" con las decisiones 1–5 de este plan; corregir "Subida" (versión = `base_version + 1`, base que avanza si se edita durante la subida, acuse perdido) y "Estado visible" (de momento `pendingCount` e `isAccessLost`).
- [ ] `MODULE_MAP.md`: `data:repository` añade "motor de sincronización (`sync`): `RemoteStore`, `SyncEngine`".
- [ ] `docs/plans/README.md`: fila P6 → "P6a hecho (motor); P6b pendiente (Firestore real, integración, disparadores)".
- [ ] Commit, push, PR `P6a: sync engine`.

## Verificación final de P6a

- 11 tests de sincronización en verde (7 de subida + 4 del motor) y los de P4 siguen en verde con el reloj ligado al planificador.
- Ninguna ruta de pérdida silenciosa: todo rechazo termina en sincronizado, conflicto guardado, acceso perdido o reintento con `last_error`.
