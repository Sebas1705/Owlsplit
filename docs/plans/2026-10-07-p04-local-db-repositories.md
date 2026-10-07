# P4 · Base de datos local y repositorios — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Dejar SQLDelight como fuente de verdad: esquema completo (entidades, outbox, cursores, conflictos, historial), modelos de datos, repositorios que escriben en local + outbox en una sola transacción y exponen Flows, y el caso de uso que convierte todo eso en saldos con el `ledger` de P3.

**Architecture:** Tres módulos de datos según la plantilla: `data:models` (modelos serializables, sin dependencias de plataforma), `data:local` (esquema `.sq` y `AppDatabase`), `data:repository` (interfaces + implementaciones sobre `AppDatabase`). Cada escritura, en una transacción: entidad con `version = serverVersion + 1` y `syncState = PENDING`, entrada compactada en `outbox` y entrada de historial (también encolada). En `domain`, `ObserveGroupBalances` combina los Flows y llama al `ledger`. La validación de negocio de un gasto (reparto que cuadra) **no** está en el repositorio: llega con los casos de uso de P9; aquí el repositorio confía en su entrada.

**Tech Stack:** SQLDelight 2.4.1 (dialecto SQLite por defecto, compatible con Android 7 / minSdk 24: sin `UPSERT`), kotlinx-serialization 1.11.0, kotlinx-datetime 0.8.0, kotlinx-coroutines 1.11.0, Turbine 1.2.1, `kotlin.time.Clock` y `kotlin.uuid.Uuid`.

**Platform:** KMP (código común; tests en la JVM de Android)

**Depende de:** P1 y P3 en `main`.

---

## Contexto para quien ejecuta

- Repo `~/Documents/Repos/Owlsplit`, rama `p4/local-db` desde `main`. Mismo entorno que P1.
- Modelo y reglas: [docs/DOMAIN.md](../DOMAIN.md) y [docs/SYNC.md](../SYNC.md). Este plan implementa las tablas de SYNC.md salvo `fx_rate_cache`, que llega en P9 (primera migración real).
- **Sin `INSERT … ON CONFLICT DO UPDATE`**: Android 7 trae SQLite 3.9. La compactación del outbox se hace con `SELECT` + `UPDATE`/`INSERT` dentro de la transacción.
- **Sin claves foráneas ni `ON DELETE CASCADE`**: `INSERT OR REPLACE` borra la fila antigua y, con cascadas, se llevaría las hijas. Las tablas hijas (`expense_payer`, `expense_split`) se reescriben a mano.
- Nombres de tabla con sufijo `_entity` (`group` es palabra reservada). SQLDelight genera las clases `Group_entity`, etc., con propiedades en `snake_case`.
- Instantes como `INTEGER` (epoch ms), fechas de calendario como `TEXT` ISO (`2026-07-12`), booleanos como `INTEGER AS Boolean`, listas y mapas pequeños de grupo como `TEXT` JSON.
- Tests: `./gradlew :data:local:testDebugUnitTest`, `:data:repository:testDebugUnitTest`, `:domain:usecases:testDebugUnitTest`.

## Mapa de archivos

| Acción | Ruta |
|---|---|
| Modificar | `settings.gradle.kts`, `gradle/libs.versions.toml`, `domain/usecases/build.gradle.kts`, `data/local/build.gradle.kts`, `.github/workflows/ci.yml`, `MODULE_MAP.md`, `docs/plans/README.md` |
| Crear | `data/models/build.gradle.kts`, `data/models/src/commonMain/kotlin/es/sebas1705/owlsplit/data/models/Models.kt` |
| Crear | `data/local/src/commonMain/sqldelight/es/sebas1705/owlsplit/data/local/db/`: `GroupEntity.sq`, `MemberEntity.sq`, `ExpenseEntity.sq`, `TransferEntity.sq`, `ChangeLog.sq`, `Outbox.sq`, `SyncCursor.sq`, `Conflict.sq` |
| Crear | `data/local/src/commonMain/sqldelight/databases/1.db` (generado) |
| Test | `data/local/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/local/SchemaTest.kt` |
| Crear | `data/repository/build.gradle.kts`, `data/repository/src/commonMain/kotlin/es/sebas1705/owlsplit/data/repository/`: `Repositories.kt`, `Infrastructure.kt`, `Writer.kt`, `Rows.kt`, `Diffs.kt`, `GroupRepositoryImpl.kt`, `MemberRepositoryImpl.kt`, `ExpenseRepositoryImpl.kt`, `TransferRepositoryImpl.kt`, `ChangeLogRepositoryImpl.kt` |
| Test | `data/repository/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/repository/`: `TestEnv.kt`, `GroupRepositoryTest.kt`, `MemberRepositoryTest.kt`, `ExpenseRepositoryTest.kt`, `TransferRepositoryTest.kt` |
| Crear | `domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/usecases/balances/ObserveGroupBalances.kt` |
| Test | `domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/usecases/balances/ObserveGroupBalancesTest.kt`, `FakeRepositories.kt` |

---

### Task 1: Módulo `data:models`

**Files:**
- Create: `data/models/build.gradle.kts`, `Models.kt`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Registrar el módulo y crear la rama**

```bash
git switch main && git pull && git switch -c p4/local-db
```
En `settings.gradle.kts` sustituir `include(":data:local", ":data:remote")` por:
```kotlin
include(":data:models", ":data:local", ":data:remote", ":data:repository")
```
(`data:repository` se crea en la Tarea 4; hasta entonces Gradle avisa de que el directorio no existe y lo ignora.)

- [ ] **Step 2: `data/models/build.gradle.kts`**

```kotlin
plugins { alias(libs.plugins.kmp.data) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }
    }
}
```

- [ ] **Step 3: Modelos**

`data/models/src/commonMain/kotlin/es/sebas1705/owlsplit/data/models/Models.kt`:
```kotlin
package es.sebas1705.owlsplit.data.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Local synchronisation state of a document. See docs/SYNC.md. */
enum class SyncState { SYNCED, PENDING, CONFLICT }

/**
 * Sync metadata shared by every editable document.
 * [version] is the local version (serverVersion + 1 while a write is pending);
 * [updatedAt] is the server time of the last synced write, null until synced.
 */
@Serializable
data class SyncMeta(
    val version: Long,
    val serverVersion: Long,
    val updatedAt: Long?,
    val updatedBy: String,
    val deleted: Boolean,
    val syncState: SyncState,
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val icon: String,
    val accent: String,
    val baseCurrency: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val inviteCode: String,
    val memberUids: List<String>,
    val archivedBy: List<String>,
    val createdBy: String,
    val createdAt: Long,
    val meta: SyncMeta,
)

@Serializable
data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    val avatar: String?,
    val defaultWeight: Long,
    val userUid: String?,
    val active: Boolean,
    val sortKey: Long,
    val meta: SyncMeta,
)

enum class ExpenseCategory {
    FOOD, GROCERIES, TRANSPORT, ACCOMMODATION, LEISURE, SHOPPING, HOME, UTILITIES, HEALTH, GIFTS, OTHER,
}

enum class SplitType { EQUAL, WEIGHTS, EXACT, PERCENT }

@Serializable
data class Expense(
    val id: String,
    val groupId: String,
    val title: String,
    val category: ExpenseCategory,
    val note: String?,
    val date: LocalDate,
    val currency: String,
    val amountMinor: Long,
    val fxRateScaled: Long,
    val payers: Map<String, Long>,
    val splitType: SplitType,
    val splitValues: Map<String, Long>,
    val receiptUrl: String?,
    val recurrenceId: String?,
    val createdBy: String,
    val createdAt: Long,
    val meta: SyncMeta,
)

@Serializable
data class Transfer(
    val id: String,
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateScaled: Long,
    val date: LocalDate,
    val note: String?,
    val createdBy: String,
    val createdAt: Long,
    val meta: SyncMeta,
)

enum class EntityType { GROUP, MEMBER, EXPENSE, TRANSFER }

enum class ChangeAction { CREATED, UPDATED, DELETED, RESTORED, JOINED, CONFLICT }

@Serializable
data class FieldChange(val field: String, val before: String?, val after: String?)

@Serializable
data class ChangeLogEntry(
    val id: String,
    val groupId: String,
    val entityType: EntityType,
    val entityId: String,
    val action: ChangeAction,
    val authorUid: String,
    val diff: List<FieldChange>,
    val createdAt: Long,
)

/** What an outbox entry uploads. CHANGE_LOG entries are create-only. */
enum class OutboxType { GROUP, MEMBER, EXPENSE, TRANSFER, CHANGE_LOG }
```

- [ ] **Step 4: Compilar**

Run: `./gradlew :data:models:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts data/models
git commit -m "feat(data): add data:models with sync-aware domain documents"
```

### Task 2: Esquema SQLDelight

**Files:**
- Create: los ocho `.sq` de la tabla de archivos
- Modify: `data/local/build.gradle.kts`

- [ ] **Step 1: `GroupEntity.sq`**

```sql
CREATE TABLE group_entity (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    accent TEXT NOT NULL,
    base_currency TEXT NOT NULL,
    start_date TEXT,
    end_date TEXT,
    invite_code TEXT NOT NULL,
    member_uids TEXT NOT NULL,
    archived_by TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    version INTEGER NOT NULL,
    server_version INTEGER NOT NULL,
    updated_at INTEGER,
    updated_by TEXT NOT NULL,
    deleted INTEGER AS Boolean NOT NULL,
    sync_state TEXT NOT NULL
);

upsert:
INSERT OR REPLACE INTO group_entity VALUES ?;

selectVisible:
SELECT * FROM group_entity WHERE deleted = 0 ORDER BY name COLLATE NOCASE, id;

selectById:
SELECT * FROM group_entity WHERE id = ?;
```

- [ ] **Step 2: `MemberEntity.sq`**

```sql
CREATE TABLE member_entity (
    id TEXT NOT NULL PRIMARY KEY,
    group_id TEXT NOT NULL,
    name TEXT NOT NULL,
    avatar TEXT,
    default_weight INTEGER NOT NULL,
    user_uid TEXT,
    active INTEGER AS Boolean NOT NULL,
    sort_key INTEGER NOT NULL,
    version INTEGER NOT NULL,
    server_version INTEGER NOT NULL,
    updated_at INTEGER,
    updated_by TEXT NOT NULL,
    deleted INTEGER AS Boolean NOT NULL,
    sync_state TEXT NOT NULL
);

CREATE INDEX member_by_group ON member_entity(group_id);

upsert:
INSERT OR REPLACE INTO member_entity VALUES ?;

selectVisibleByGroup:
SELECT * FROM member_entity WHERE group_id = ? AND deleted = 0 ORDER BY sort_key, id;

selectById:
SELECT * FROM member_entity WHERE id = ?;

nextSortKey:
SELECT coalesce(max(sort_key), -1) + 1 FROM member_entity WHERE group_id = ?;

countMovements:
SELECT
    (SELECT count(*) FROM expense_payer p JOIN expense_entity e ON e.id = p.expense_id
        WHERE p.member_id = :memberId AND e.deleted = 0)
  + (SELECT count(*) FROM expense_split s JOIN expense_entity e ON e.id = s.expense_id
        WHERE s.member_id = :memberId AND s.split_value > 0 AND e.deleted = 0)
  + (SELECT count(*) FROM transfer_entity t
        WHERE (t.from_member_id = :memberId OR t.to_member_id = :memberId) AND t.deleted = 0);
```

- [ ] **Step 3: `ExpenseEntity.sq`**

```sql
CREATE TABLE expense_entity (
    id TEXT NOT NULL PRIMARY KEY,
    group_id TEXT NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    note TEXT,
    date TEXT NOT NULL,
    currency TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    fx_rate_scaled INTEGER NOT NULL,
    split_type TEXT NOT NULL,
    receipt_url TEXT,
    recurrence_id TEXT,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    version INTEGER NOT NULL,
    server_version INTEGER NOT NULL,
    updated_at INTEGER,
    updated_by TEXT NOT NULL,
    deleted INTEGER AS Boolean NOT NULL,
    sync_state TEXT NOT NULL
);

CREATE INDEX expense_by_group ON expense_entity(group_id, deleted, date);

CREATE TABLE expense_payer (
    expense_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    PRIMARY KEY (expense_id, member_id)
);

CREATE TABLE expense_split (
    expense_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    split_value INTEGER NOT NULL,
    PRIMARY KEY (expense_id, member_id)
);

upsert:
INSERT OR REPLACE INTO expense_entity VALUES ?;

deletePayers:
DELETE FROM expense_payer WHERE expense_id = ?;

insertPayer:
INSERT INTO expense_payer VALUES ?;

deleteSplits:
DELETE FROM expense_split WHERE expense_id = ?;

insertSplit:
INSERT INTO expense_split VALUES ?;

selectVisibleByGroup:
SELECT * FROM expense_entity WHERE group_id = ? AND deleted = 0 ORDER BY date DESC, created_at DESC, id;

selectById:
SELECT * FROM expense_entity WHERE id = ?;

payersByGroup:
SELECT p.* FROM expense_payer p JOIN expense_entity e ON e.id = p.expense_id WHERE e.group_id = ?;

splitsByGroup:
SELECT s.* FROM expense_split s JOIN expense_entity e ON e.id = s.expense_id WHERE e.group_id = ?;

payersByExpense:
SELECT * FROM expense_payer WHERE expense_id = ?;

splitsByExpense:
SELECT * FROM expense_split WHERE expense_id = ?;
```

- [ ] **Step 4: `TransferEntity.sq`**

```sql
CREATE TABLE transfer_entity (
    id TEXT NOT NULL PRIMARY KEY,
    group_id TEXT NOT NULL,
    from_member_id TEXT NOT NULL,
    to_member_id TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    currency TEXT NOT NULL,
    fx_rate_scaled INTEGER NOT NULL,
    date TEXT NOT NULL,
    note TEXT,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    version INTEGER NOT NULL,
    server_version INTEGER NOT NULL,
    updated_at INTEGER,
    updated_by TEXT NOT NULL,
    deleted INTEGER AS Boolean NOT NULL,
    sync_state TEXT NOT NULL
);

CREATE INDEX transfer_by_group ON transfer_entity(group_id, deleted, date);

upsert:
INSERT OR REPLACE INTO transfer_entity VALUES ?;

selectVisibleByGroup:
SELECT * FROM transfer_entity WHERE group_id = ? AND deleted = 0 ORDER BY date DESC, created_at DESC, id;

selectById:
SELECT * FROM transfer_entity WHERE id = ?;
```

- [ ] **Step 5: `ChangeLog.sq`**

```sql
CREATE TABLE change_log (
    id TEXT NOT NULL PRIMARY KEY,
    group_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    action TEXT NOT NULL,
    author_uid TEXT NOT NULL,
    diff TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    sync_state TEXT NOT NULL
);

CREATE INDEX change_log_by_group ON change_log(group_id, created_at);

insert:
INSERT OR REPLACE INTO change_log VALUES ?;

selectByGroup:
SELECT * FROM change_log WHERE group_id = ? ORDER BY created_at DESC, id;
```

- [ ] **Step 6: `Outbox.sq`, `SyncCursor.sq`, `Conflict.sq`**

`Outbox.sq`:
```sql
CREATE TABLE outbox (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    group_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    base_version INTEGER NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at INTEGER NOT NULL,
    UNIQUE (entity_type, entity_id)
);

insert:
INSERT INTO outbox(group_id, entity_type, entity_id, base_version, payload, created_at)
VALUES (?, ?, ?, ?, ?, ?);

selectByEntity:
SELECT * FROM outbox WHERE entity_type = ? AND entity_id = ?;

replacePayload:
UPDATE outbox SET payload = ?, attempts = 0, next_attempt_at = 0, last_error = NULL WHERE id = ?;

selectAll:
SELECT * FROM outbox ORDER BY created_at, id;

countAll:
SELECT count(*) FROM outbox;
```
`SyncCursor.sq`:
```sql
CREATE TABLE sync_cursor (
    group_id TEXT NOT NULL,
    collection TEXT NOT NULL,
    last_updated_at INTEGER NOT NULL,
    PRIMARY KEY (group_id, collection)
);

get:
SELECT last_updated_at FROM sync_cursor WHERE group_id = ? AND collection = ?;

put:
INSERT OR REPLACE INTO sync_cursor VALUES (?, ?, ?);
```
`Conflict.sq`:
```sql
CREATE TABLE conflict (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    group_id TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    local_payload TEXT NOT NULL,
    remote_version INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

insert:
INSERT INTO conflict(group_id, entity_type, entity_id, local_payload, remote_version, created_at)
VALUES (?, ?, ?, ?, ?, ?);

selectByGroup:
SELECT * FROM conflict WHERE group_id = ? ORDER BY created_at, id;

deleteById:
DELETE FROM conflict WHERE id = ?;
```

- [ ] **Step 7: Esquema versionado en `data/local/build.gradle.kts`**

Sustituir el bloque `sqldelight { … }` por:
```kotlin
sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("es.sebas1705.owlsplit.data.local.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            // Snapshots of every released schema; verifySqlDelightMigration checks
            // that migrations (n.sqm) turn snapshot n into n + 1.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
```

- [ ] **Step 8: Compilar y generar el esquema 1**

```bash
./gradlew :data:local:generateSqlDelightInterface --console=plain | tail -3
./gradlew :data:local:tasks --all --console=plain | grep -i "schema\|verify"
```
Ejecutar la tarea `generate…AppDatabaseSchema` que aparezca (en KMP suele ser `generateCommonMainAppDatabaseSchema`) y comprobar:
```bash
ls data/local/src/commonMain/sqldelight/databases/
```
Expected: `1.db`.

- [ ] **Step 9: Commit**

```bash
git add data/local
git commit -m "feat(local): full SQLDelight schema for documents, outbox, cursors and conflicts"
```

### Task 3: Tests del esquema

**Files:**
- Test: `data/local/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/local/SchemaTest.kt`

- [ ] **Step 1: Test**

```kotlin
package es.sebas1705.owlsplit.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Expense_entity
import es.sebas1705.owlsplit.data.local.db.Expense_payer
import es.sebas1705.owlsplit.data.local.db.Expense_split
import es.sebas1705.owlsplit.data.local.db.Member_entity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SchemaTest {
    private val db = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).let {
        AppDatabase.Schema.create(it)
        AppDatabase(it)
    }

    private fun member(id: String, group: String = "g1", sortKey: Long = 0) = Member_entity(
        id = id, group_id = group, name = id, avatar = null, default_weight = 1, user_uid = null,
        active = true, sort_key = sortKey, version = 1, server_version = 0, updated_at = null,
        updated_by = "u1", deleted = false, sync_state = "PENDING",
    )

    private fun expense(id: String, deleted: Boolean = false) = Expense_entity(
        id = id, group_id = "g1", title = "Cena", category = "FOOD", note = null, date = "2026-07-12",
        currency = "EUR", amount_minor = 1000, fx_rate_scaled = 1_000_000_000, split_type = "EQUAL",
        receipt_url = null, recurrence_id = null, created_by = "u1", created_at = 1, version = 1,
        server_version = 0, updated_at = null, updated_by = "u1", deleted = deleted, sync_state = "PENDING",
    )

    @Test
    fun `next sort key starts at zero and follows the maximum`() {
        assertEquals(0L, db.memberEntityQueries.nextSortKey("g1").executeAsOne())
        db.memberEntityQueries.upsert(member("a", sortKey = 4))
        assertEquals(5L, db.memberEntityQueries.nextSortKey("g1").executeAsOne())
    }

    @Test
    fun `movements count payers, positive splits and transfers of non-deleted entries`() {
        db.memberEntityQueries.upsert(member("ana"))
        db.expenseEntityQueries.upsert(expense("e1"))
        db.expenseEntityQueries.insertPayer(Expense_payer("e1", "ana", 1000))
        db.expenseEntityQueries.insertSplit(Expense_split("e1", "ana", 1))
        db.expenseEntityQueries.upsert(expense("e2", deleted = true))
        db.expenseEntityQueries.insertPayer(Expense_payer("e2", "ana", 1000))
        assertEquals(2L, db.memberEntityQueries.countMovements("ana").executeAsOne())
        assertEquals(0L, db.memberEntityQueries.countMovements("luis").executeAsOne())
    }

    @Test
    fun `outbox keeps one entry per entity`() {
        db.outboxQueries.insert("g1", "EXPENSE", "e1", 0, "{v1}", 10)
        val entry = db.outboxQueries.selectByEntity("EXPENSE", "e1").executeAsOne()
        db.outboxQueries.replacePayload("{v2}", entry.id)
        val after = db.outboxQueries.selectByEntity("EXPENSE", "e1").executeAsOne()
        assertEquals("{v2}", after.payload)
        assertEquals(0L, after.base_version)
        assertEquals(1L, db.outboxQueries.countAll().executeAsOne())
    }

    @Test
    fun `sync cursor is absent until written`() {
        assertNull(db.syncCursorQueries.get("g1", "expenses").executeAsOneOrNull())
        db.syncCursorQueries.put("g1", "expenses", 42)
        assertEquals(42L, db.syncCursorQueries.get("g1", "expenses").executeAsOne())
    }
}
```

- [ ] **Step 2: Ejecutar**

Run: `./gradlew :data:local:testDebugUnitTest`
Expected: PASS (`MetaQueriesTest` 2 + `SchemaTest` 4). Si el compilador dice que no conoce el tipo `Boolean` en `AS Boolean`, añadir `import kotlin.Boolean;` como primera línea de cada `.sq` que lo use.

- [ ] **Step 3: Commit**

```bash
git add data/local/src/androidUnitTest
git commit -m "test(local): schema queries for sort keys, movements, outbox and cursors"
```

### Task 4: Módulo `data:repository`: interfaces e infraestructura

**Files:**
- Modify: `gradle/libs.versions.toml`
- Create: `data/repository/build.gradle.kts`, `Repositories.kt`, `Infrastructure.kt`, `Rows.kt`, `Diffs.kt`, `Writer.kt`

- [ ] **Step 1: Turbine en el catálogo**

`[versions]`: `turbine = "1.2.1"`. `[libraries]`: `turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }`.

- [ ] **Step 2: `data/repository/build.gradle.kts`**

```kotlin
plugins { alias(libs.plugins.kmp.data) }

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":data:models"))
            implementation(project(":data:local"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
```
`data:local` necesita exponer `AppDatabase` a quien la use: en `data/local/build.gradle.kts`, cambiar `implementation(libs.sqldelight.runtime)` por `api(libs.sqldelight.runtime)`.

- [ ] **Step 3: Interfaces públicas**

`Repositories.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class GroupInput(
    val name: String,
    val icon: String,
    val accent: String,
    val baseCurrency: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

data class MemberInput(val name: String, val avatar: String? = null, val defaultWeight: Long = 1)

data class ExpenseInput(
    val title: String,
    val category: ExpenseCategory,
    val note: String?,
    val date: LocalDate,
    val currency: String,
    val amountMinor: Long,
    val fxRateScaled: Long,
    val payers: Map<String, Long>,
    val splitType: SplitType,
    val splitValues: Map<String, Long>,
)

data class TransferInput(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateScaled: Long,
    val date: LocalDate,
    val note: String? = null,
)

interface GroupRepository {
    fun observeGroups(): Flow<List<Group>>
    fun observeGroup(id: String): Flow<Group?>

    /** Creates the group and the creator's member (linked to the current user). Returns the group id. */
    suspend fun create(input: GroupInput, creatorName: String): String
    suspend fun update(id: String, input: GroupInput)
    suspend fun setArchived(id: String, archived: Boolean)
}

interface MemberRepository {
    fun observeMembers(groupId: String): Flow<List<Member>>
    suspend fun add(groupId: String, input: MemberInput): String
    suspend fun update(id: String, input: MemberInput)
    suspend fun setActive(id: String, active: Boolean)

    /** Deletes a member without movements; returns false (and changes nothing) otherwise. */
    suspend fun deleteIfUnused(id: String): Boolean
}

interface ExpenseRepository {
    fun observeExpenses(groupId: String): Flow<List<Expense>>
    suspend fun get(id: String): Expense?
    suspend fun create(groupId: String, input: ExpenseInput): String
    suspend fun update(id: String, input: ExpenseInput)
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

interface TransferRepository {
    fun observeTransfers(groupId: String): Flow<List<Transfer>>
    suspend fun create(groupId: String, input: TransferInput): String
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

interface ChangeLogRepository {
    fun observe(groupId: String): Flow<List<ChangeLogEntry>>
}
```

- [ ] **Step 4: Infraestructura inyectable**

`Infrastructure.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Uid of the signed-in user; P8 backs it with Firebase Auth. */
fun interface UidProvider {
    fun currentUid(): String
}

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { Uuid.random().toString() }
    }
}

/** Short, unambiguous invite codes (no 0/O/1/I). */
fun interface InviteCodeGenerator {
    fun newCode(): String

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val LENGTH = 8
        val Random = InviteCodeGenerator {
            (1..LENGTH).map { ALPHABET[kotlin.random.Random.nextInt(ALPHABET.length)] }.joinToString("")
        }
    }
}

/** Everything a repository needs besides the database. */
data class RepositoryContext(
    val uid: UidProvider,
    val clock: Clock = Clock.System,
    val ids: IdGenerator = IdGenerator.Random,
    val inviteCodes: InviteCodeGenerator = InviteCodeGenerator.Random,
)
```

- [ ] **Step 5: Conversión filas ↔ modelos**

`Rows.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.local.db.Change_log
import es.sebas1705.owlsplit.data.local.db.Expense_entity
import es.sebas1705.owlsplit.data.local.db.Group_entity
import es.sebas1705.owlsplit.data.local.db.Member_entity
import es.sebas1705.owlsplit.data.local.db.Transfer_entity
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val json = Json { ignoreUnknownKeys = true }
private val stringList = ListSerializer(String.serializer())
private val fieldChanges = ListSerializer(FieldChange.serializer())

private fun meta(version: Long, serverVersion: Long, updatedAt: Long?, updatedBy: String, deleted: Boolean, state: String) =
    SyncMeta(version, serverVersion, updatedAt, updatedBy, deleted, SyncState.valueOf(state))

internal fun Group_entity.toModel() = Group(
    id = id, name = name, icon = icon, accent = accent, baseCurrency = base_currency,
    startDate = start_date?.let(LocalDate::parse), endDate = end_date?.let(LocalDate::parse),
    inviteCode = invite_code, memberUids = json.decodeFromString(stringList, member_uids),
    archivedBy = json.decodeFromString(stringList, archived_by), createdBy = created_by, createdAt = created_at,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Group.toRow() = Group_entity(
    id = id, name = name, icon = icon, accent = accent, base_currency = baseCurrency,
    start_date = startDate?.toString(), end_date = endDate?.toString(), invite_code = inviteCode,
    member_uids = json.encodeToString(stringList, memberUids), archived_by = json.encodeToString(stringList, archivedBy),
    created_by = createdBy, created_at = createdAt, version = meta.version, server_version = meta.serverVersion,
    updated_at = meta.updatedAt, updated_by = meta.updatedBy, deleted = meta.deleted, sync_state = meta.syncState.name,
)

internal fun Member_entity.toModel() = Member(
    id = id, groupId = group_id, name = name, avatar = avatar, defaultWeight = default_weight, userUid = user_uid,
    active = active, sortKey = sort_key,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Member.toRow() = Member_entity(
    id = id, group_id = groupId, name = name, avatar = avatar, default_weight = defaultWeight, user_uid = userUid,
    active = active, sort_key = sortKey, version = meta.version, server_version = meta.serverVersion,
    updated_at = meta.updatedAt, updated_by = meta.updatedBy, deleted = meta.deleted, sync_state = meta.syncState.name,
)

internal fun Expense_entity.toModel(payers: Map<String, Long>, splitValues: Map<String, Long>) = Expense(
    id = id, groupId = group_id, title = title, category = ExpenseCategory.valueOf(category), note = note,
    date = LocalDate.parse(date), currency = currency, amountMinor = amount_minor, fxRateScaled = fx_rate_scaled,
    payers = payers, splitType = SplitType.valueOf(split_type), splitValues = splitValues,
    receiptUrl = receipt_url, recurrenceId = recurrence_id, createdBy = created_by, createdAt = created_at,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Expense.toRow() = Expense_entity(
    id = id, group_id = groupId, title = title, category = category.name, note = note, date = date.toString(),
    currency = currency, amount_minor = amountMinor, fx_rate_scaled = fxRateScaled, split_type = splitType.name,
    receipt_url = receiptUrl, recurrence_id = recurrenceId, created_by = createdBy, created_at = createdAt,
    version = meta.version, server_version = meta.serverVersion, updated_at = meta.updatedAt,
    updated_by = meta.updatedBy, deleted = meta.deleted, sync_state = meta.syncState.name,
)

internal fun Transfer_entity.toModel() = Transfer(
    id = id, groupId = group_id, fromMemberId = from_member_id, toMemberId = to_member_id,
    amountMinor = amount_minor, currency = currency, fxRateScaled = fx_rate_scaled, date = LocalDate.parse(date),
    note = note, createdBy = created_by, createdAt = created_at,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Transfer.toRow() = Transfer_entity(
    id = id, group_id = groupId, from_member_id = fromMemberId, to_member_id = toMemberId,
    amount_minor = amountMinor, currency = currency, fx_rate_scaled = fxRateScaled, date = date.toString(),
    note = note, created_by = createdBy, created_at = createdAt, version = meta.version,
    server_version = meta.serverVersion, updated_at = meta.updatedAt, updated_by = meta.updatedBy,
    deleted = meta.deleted, sync_state = meta.syncState.name,
)

internal fun Change_log.toModel() = ChangeLogEntry(
    id = id, groupId = group_id, entityType = EntityType.valueOf(entity_type), entityId = entity_id,
    action = ChangeAction.valueOf(action), authorUid = author_uid,
    diff = json.decodeFromString(fieldChanges, diff), createdAt = created_at,
)

internal fun ChangeLogEntry.toRow(state: SyncState) = Change_log(
    id = id, group_id = groupId, entity_type = entityType.name, entity_id = entityId, action = action.name,
    author_uid = authorUid, diff = json.encodeToString(fieldChanges, diff), created_at = createdAt,
    sync_state = state.name,
)
```

- [ ] **Step 6: Diferencias para el historial**

`Diffs.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Transfer

/** Raw before/after values; the UI formats them (amounts with their currency, dates, names). */
internal object Diffs {
    private fun MutableList<FieldChange>.field(name: String, before: Any?, after: Any?) {
        val b = before?.let(::render)
        val a = after?.let(::render)
        if (b != a) add(FieldChange(name, b, a))
    }

    private fun render(value: Any): String = when (value) {
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.joinToString(",") { "${it.key}=${it.value}" }
        else -> value.toString()
    }

    fun expense(old: Expense?, new: Expense): List<FieldChange> = buildList {
        field("title", old?.title, new.title)
        field("amountMinor", old?.amountMinor, new.amountMinor)
        field("currency", old?.currency, new.currency)
        field("fxRateScaled", old?.fxRateScaled, new.fxRateScaled)
        field("date", old?.date, new.date)
        field("category", old?.category, new.category)
        field("note", old?.note, new.note)
        field("payers", old?.payers, new.payers)
        field("splitType", old?.splitType, new.splitType)
        field("splitValues", old?.splitValues, new.splitValues)
    }

    fun transfer(old: Transfer?, new: Transfer): List<FieldChange> = buildList {
        field("fromMemberId", old?.fromMemberId, new.fromMemberId)
        field("toMemberId", old?.toMemberId, new.toMemberId)
        field("amountMinor", old?.amountMinor, new.amountMinor)
        field("currency", old?.currency, new.currency)
        field("date", old?.date, new.date)
        field("note", old?.note, new.note)
    }

    fun group(old: Group?, new: Group): List<FieldChange> = buildList {
        field("name", old?.name, new.name)
        field("icon", old?.icon, new.icon)
        field("accent", old?.accent, new.accent)
        field("baseCurrency", old?.baseCurrency, new.baseCurrency)
        field("startDate", old?.startDate, new.startDate)
        field("endDate", old?.endDate, new.endDate)
    }

    fun member(old: Member?, new: Member): List<FieldChange> = buildList {
        field("name", old?.name, new.name)
        field("defaultWeight", old?.defaultWeight, new.defaultWeight)
        field("active", old?.active, new.active)
    }
}
```

- [ ] **Step 7: Escritura con outbox e historial**

`Writer.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState

/**
 * Shared write path (docs/SYNC.md, "Escritura local"). Must be called inside `db.transaction {}`.
 */
internal class Writer(private val db: AppDatabase, private val ctx: RepositoryContext) {

    fun now(): Long = ctx.clock.now().toEpochMilliseconds()

    fun uid(): String = ctx.uid.currentUid()

    fun newId(): String = ctx.ids.newId()

    fun newInviteCode(): String = ctx.inviteCodes.newCode()

    /** Metadata of a brand-new document. */
    fun createdMeta() = SyncMeta(
        version = 1, serverVersion = 0, updatedAt = null, updatedBy = uid(), deleted = false,
        syncState = SyncState.PENDING,
    )

    /** Metadata after a local edit of a document whose last synced version is [old].serverVersion. */
    fun editedMeta(old: SyncMeta, deleted: Boolean = old.deleted) = old.copy(
        version = old.serverVersion + 1, updatedBy = uid(), deleted = deleted, syncState = SyncState.PENDING,
    )

    /** Queues [payload] for upload, compacting with a pending entry of the same document. */
    fun enqueue(groupId: String, type: OutboxType, entityId: String, baseVersion: Long, payload: String) {
        val pending = db.outboxQueries.selectByEntity(type.name, entityId).executeAsOneOrNull()
        if (pending == null) {
            db.outboxQueries.insert(groupId, type.name, entityId, baseVersion, payload, now())
        } else {
            db.outboxQueries.replacePayload(payload, pending.id)
        }
    }

    fun log(groupId: String, type: EntityType, entityId: String, action: ChangeAction, diff: List<FieldChange>) {
        val entry = ChangeLogEntry(newId(), groupId, type, entityId, action, uid(), diff, now())
        db.changeLogQueries.insert(entry.toRow(SyncState.PENDING))
        enqueue(groupId, OutboxType.CHANGE_LOG, entry.id, 0, json.encodeToString(ChangeLogEntry.serializer(), entry))
    }
}
```

- [ ] **Step 8: Compilar**

Run: `./gradlew :data:repository:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml data/local/build.gradle.kts data/repository
git commit -m "feat(repository): repository contracts, row mapping, diffs and outbox writer"
```

### Task 5: Repositorio de grupos

**Files:**
- Create: `GroupRepositoryImpl.kt`, `MemberRepositoryImpl.kt` (el de grupos crea el primer miembro), `ChangeLogRepositoryImpl.kt`
- Test: `TestEnv.kt`, `GroupRepositoryTest.kt`

- [ ] **Step 1: Entorno de test**

`data/repository/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/data/repository/TestEnv.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.Member
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** In-memory database with a fixed clock, predictable ids and a settable user. */
class TestEnv(val scope: TestScope = TestScope()) {
    var uid = "uid-ana"
    var nowMillis = 1_000L
    private var nextId = 0
    private var nextCode = 0

    val db: AppDatabase = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).let {
        AppDatabase.Schema.create(it)
        AppDatabase(it)
    }

    val ctx = RepositoryContext(
        uid = { uid },
        clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
        },
        ids = { "id${nextId++}" },
        inviteCodes = { "CODE${nextCode++}" },
    )

    val dispatcher = StandardTestDispatcher(scope.testScheduler)
    val groups = GroupRepositoryImpl(db, ctx, dispatcher)
    val members = MemberRepositoryImpl(db, ctx, dispatcher)
    val changeLog = ChangeLogRepositoryImpl(db, dispatcher)
    // Task 6 adds: val expenses = ExpenseRepositoryImpl(db, ctx, dispatcher)
    // Task 7 adds: val transfers = TransferRepositoryImpl(db, ctx, dispatcher)

    fun outbox() = db.outboxQueries.selectAll().executeAsList()
}

suspend fun MemberRepository.observeMembersOnce(groupId: String): List<Member> = observeMembers(groupId).first()
```
Las dos líneas comentadas se sustituyen por código real en las Tareas 6 y 7, cuando existan esas clases.

- [ ] **Step 2: Test de grupos**

`GroupRepositoryTest.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.turbine.test
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupRepositoryTest {
    private val input = GroupInput(name = "Ibiza 2026", icon = "plane", accent = "blue", baseCurrency = "EUR")

    @Test
    fun `creating a group also creates the creator member and queues both`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, creatorName = "Ana")

        val group = env.groups.observeGroup(groupId).first()!!
        assertEquals("Ibiza 2026", group.name)
        assertEquals(listOf("uid-ana"), group.memberUids)
        assertEquals("CODE0", group.inviteCode)
        assertEquals(1L, group.meta.version)
        assertEquals(SyncState.PENDING, group.meta.syncState)

        env.members.observeMembers(groupId).test {
            val members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("Ana", members.single().name)
            assertEquals("uid-ana", members.single().userUid)
            assertEquals(0L, members.single().sortKey)
        }

        val types = env.outbox().map { OutboxType.valueOf(it.entity_type) }
        assertEquals(listOf(OutboxType.GROUP, OutboxType.CHANGE_LOG, OutboxType.MEMBER, OutboxType.CHANGE_LOG), types)
    }

    @Test
    fun `edits before sync are compacted into one outbox entry with the original base version`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, "Ana")
        env.groups.update(groupId, input.copy(name = "Ibiza"))
        env.groups.update(groupId, input.copy(name = "Ibiza!"))

        val groupEntries = env.outbox().filter { it.entity_type == OutboxType.GROUP.name }
        assertEquals(1, groupEntries.size)
        assertEquals(0L, groupEntries.single().base_version)
        assertTrue(groupEntries.single().payload.contains("Ibiza!"))

        // The outbox is compacted, the history is not: both edits are recorded.
        env.changeLog.observe(groupId).test {
            val groupEntries = awaitItem().filter { it.entityType == EntityType.GROUP }
            assertEquals(1, groupEntries.count { it.action == ChangeAction.CREATED })
            assertEquals(2, groupEntries.count { it.action == ChangeAction.UPDATED })
        }
    }

    @Test
    fun `archiving is per user and hides nothing from the list`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, "Ana")
        env.groups.setArchived(groupId, true)
        env.groups.observeGroups().test {
            assertEquals(listOf("uid-ana"), awaitItem().single().archivedBy)
        }
        env.groups.setArchived(groupId, false)
        env.groups.observeGroups().test {
            assertTrue(awaitItem().single().archivedBy.isEmpty())
        }
    }
}
```

- [ ] **Step 3: Ver que falla**

Run: `./gradlew :data:repository:testDebugUnitTest --tests "*GroupRepositoryTest"`
Expected: FAIL de compilación (`GroupRepositoryImpl` no existe).

- [ ] **Step 4: Implementar grupos**

`GroupRepositoryImpl.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GroupRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : GroupRepository {
    private val writer = Writer(db, ctx)

    override fun observeGroups(): Flow<List<Group>> =
        db.groupEntityQueries.selectVisible().asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }

    override fun observeGroup(id: String): Flow<Group?> =
        db.groupEntityQueries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.toModel() }

    override suspend fun create(input: GroupInput, creatorName: String): String = withContext(io) {
        db.transactionWithResult {
            val group = Group(
                id = writer.newId(), name = input.name, icon = input.icon, accent = input.accent,
                baseCurrency = input.baseCurrency, startDate = input.startDate, endDate = input.endDate,
                inviteCode = writer.newInviteCode(), memberUids = listOf(writer.uid()), archivedBy = emptyList(),
                createdBy = writer.uid(), createdAt = writer.now(), meta = writer.createdMeta(),
            )
            save(group, old = null, action = ChangeAction.CREATED)
            val member = Member(
                id = writer.newId(), groupId = group.id, name = creatorName, avatar = null, defaultWeight = 1,
                userUid = writer.uid(), active = true, sortKey = 0, meta = writer.createdMeta(),
            )
            MemberRepositoryImpl.save(db, writer, member, old = null, action = ChangeAction.CREATED)
            group.id
        }
    }

    override suspend fun update(id: String, input: GroupInput) = withContext(io) {
        db.transaction {
            val old = requireGroup(id)
            val new = old.copy(
                name = input.name, icon = input.icon, accent = input.accent, baseCurrency = input.baseCurrency,
                startDate = input.startDate, endDate = input.endDate, meta = writer.editedMeta(old.meta),
            )
            save(new, old, ChangeAction.UPDATED)
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) = withContext(io) {
        db.transaction {
            val old = requireGroup(id)
            val me = writer.uid()
            val archivedBy = if (archived) (old.archivedBy + me).distinct() else old.archivedBy - me
            if (archivedBy != old.archivedBy) {
                // Archiving is a personal preference: no history entry.
                val new = old.copy(archivedBy = archivedBy, meta = writer.editedMeta(old.meta))
                db.groupEntityQueries.upsert(new.toRow())
                writer.enqueue(new.id, OutboxType.GROUP, new.id, old.meta.serverVersion, json.encodeToString(Group.serializer(), new))
            }
        }
    }

    private fun requireGroup(id: String): Group =
        requireNotNull(db.groupEntityQueries.selectById(id).executeAsOneOrNull()) { "Unknown group $id" }.toModel()

    private fun save(group: Group, old: Group?, action: ChangeAction) {
        db.groupEntityQueries.upsert(group.toRow())
        writer.enqueue(group.id, OutboxType.GROUP, group.id, old?.meta?.serverVersion ?: 0, json.encodeToString(Group.serializer(), group))
        writer.log(group.id, EntityType.GROUP, group.id, action, Diffs.group(old, group))
    }
}
```

- [ ] **Step 5: Implementar miembros**

`MemberRepositoryImpl.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MemberRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : MemberRepository {
    private val writer = Writer(db, ctx)

    override fun observeMembers(groupId: String): Flow<List<Member>> =
        db.memberEntityQueries.selectVisibleByGroup(groupId).asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(groupId: String, input: MemberInput): String = withContext(io) {
        db.transactionWithResult {
            val member = Member(
                id = writer.newId(), groupId = groupId, name = input.name, avatar = input.avatar,
                defaultWeight = input.defaultWeight, userUid = null, active = true,
                sortKey = db.memberEntityQueries.nextSortKey(groupId).executeAsOne(), meta = writer.createdMeta(),
            )
            save(db, writer, member, old = null, action = ChangeAction.CREATED)
            member.id
        }
    }

    override suspend fun update(id: String, input: MemberInput) = edit(id) { old ->
        old.copy(name = input.name, avatar = input.avatar, defaultWeight = input.defaultWeight)
    }

    override suspend fun setActive(id: String, active: Boolean) = edit(id) { it.copy(active = active) }

    override suspend fun deleteIfUnused(id: String): Boolean = withContext(io) {
        db.transactionWithResult {
            if (db.memberEntityQueries.countMovements(id).executeAsOne() > 0) return@transactionWithResult false
            val old = requireMember(id)
            save(db, writer, old.copy(meta = writer.editedMeta(old.meta, deleted = true)), old, ChangeAction.DELETED)
            true
        }
    }

    private suspend fun edit(id: String, change: (Member) -> Member) = withContext(io) {
        db.transaction {
            val old = requireMember(id)
            val new = change(old).copy(meta = writer.editedMeta(old.meta))
            save(db, writer, new, old, ChangeAction.UPDATED)
        }
    }

    private fun requireMember(id: String): Member =
        requireNotNull(db.memberEntityQueries.selectById(id).executeAsOneOrNull()) { "Unknown member $id" }.toModel()

    internal companion object {
        fun save(db: AppDatabase, writer: Writer, member: Member, old: Member?, action: ChangeAction) {
            db.memberEntityQueries.upsert(member.toRow())
            writer.enqueue(
                member.groupId, OutboxType.MEMBER, member.id, old?.meta?.serverVersion ?: 0,
                json.encodeToString(Member.serializer(), member),
            )
            writer.log(member.groupId, EntityType.MEMBER, member.id, action, Diffs.member(old, member))
        }
    }
}
```

- [ ] **Step 6: Implementar el historial (lo usa el segundo test)**

`ChangeLogRepositoryImpl.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChangeLogRepositoryImpl(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
) : ChangeLogRepository {
    override fun observe(groupId: String): Flow<List<ChangeLogEntry>> =
        db.changeLogQueries.selectByGroup(groupId).asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }
}
```

- [ ] **Step 7: Ver que pasa**

Run: `./gradlew :data:repository:testDebugUnitTest --tests "*GroupRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add data/repository
git commit -m "feat(repository): groups and members with outbox and history"
```

### Task 6: Repositorio de gastos

**Files:**
- Create: `ExpenseRepositoryImpl.kt`
- Test: `ExpenseRepositoryTest.kt`

- [ ] **Step 1: Test**

```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.turbine.test
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.SplitType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseRepositoryTest {
    private val group = GroupInput("Piso", "home", "green", "EUR")

    private fun dinner(payer: String, members: List<String>, amount: Long = 4_860) = ExpenseInput(
        title = "Cena", category = ExpenseCategory.FOOD, note = null, date = LocalDate(2026, 7, 12),
        currency = "EUR", amountMinor = amount, fxRateScaled = 1_000_000_000,
        payers = mapOf(payer to amount), splitType = SplitType.EQUAL, splitValues = members.associateWith { 1L },
    )

    @Test
    fun `an expense round-trips with payers and split values`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))

        val id = env.expenses.create(g, dinner(ana, listOf(ana, luis)))

        env.expenses.observeExpenses(g).test {
            val e = awaitItem().single()
            assertEquals(id, e.id)
            assertEquals(mapOf(ana to 4_860L), e.payers)
            assertEquals(mapOf(ana to 1L, luis to 1L), e.splitValues)
            assertEquals(LocalDate(2026, 7, 12), e.date)
        }
    }

    @Test
    fun `editing replaces payers and records the diff`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))
        val id = env.expenses.create(g, dinner(ana, listOf(ana, luis)))

        env.expenses.update(id, dinner(luis, listOf(ana, luis), amount = 5_000))

        val e = env.expenses.get(id)!!
        assertEquals(mapOf(luis to 5_000L), e.payers)
        env.changeLog.observe(g).test {
            val update = awaitItem().first { it.action == ChangeAction.UPDATED && it.entityId == id }
            val fields = update.diff.map { it.field }.toSet()
            assertEquals(setOf("amountMinor", "payers"), fields)
            assertEquals("4860", update.diff.first { it.field == "amountMinor" }.before)
        }
    }

    @Test
    fun `deleted expenses disappear from the list and can be restored`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val id = env.expenses.create(g, dinner(ana, listOf(ana)))

        env.expenses.delete(id)
        env.expenses.observeExpenses(g).test { assertTrue(awaitItem().isEmpty()) }
        assertTrue(env.expenses.get(id)!!.meta.deleted)

        env.expenses.restore(id)
        env.expenses.observeExpenses(g).test { assertEquals(id, awaitItem().single().id) }
    }

    @Test
    fun `unknown expense is null`() = runTest {
        assertNull(TestEnv(this).expenses.get("nope"))
    }
}
```
En `TestEnv.kt`, sustituir la línea comentada `// Task 6 adds: val expenses = …` por:
```kotlin
    val expenses = ExpenseRepositoryImpl(db, ctx, dispatcher)
```

- [ ] **Step 2: Ver que falla**

Run: `./gradlew :data:repository:testDebugUnitTest --tests "*ExpenseRepositoryTest"`
Expected: FAIL (`ExpenseRepositoryImpl` no existe).

- [ ] **Step 3: Implementar**

`ExpenseRepositoryImpl.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Expense_entity
import es.sebas1705.owlsplit.data.local.db.Expense_payer
import es.sebas1705.owlsplit.data.local.db.Expense_split
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExpenseRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : ExpenseRepository {
    private val writer = Writer(db, ctx)
    private val q get() = db.expenseEntityQueries

    // Every write rewrites the expense row, so observing expense_entity is enough to
    // re-read the child tables when payers or splits change.
    override fun observeExpenses(groupId: String): Flow<List<Expense>> =
        q.selectVisibleByGroup(groupId).asFlow().mapToList(io).map { rows ->
            val payers = q.payersByGroup(groupId).executeAsList().groupBy({ it.expense_id }, { it.member_id to it.amount_minor })
            val splits = q.splitsByGroup(groupId).executeAsList().groupBy({ it.expense_id }, { it.member_id to it.split_value })
            rows.map { it.toModel(payers[it.id].orEmpty().toMap(), splits[it.id].orEmpty().toMap()) }
        }.flowOn(io)

    override suspend fun get(id: String): Expense? = withContext(io) { load(id) }

    override suspend fun create(groupId: String, input: ExpenseInput): String = withContext(io) {
        db.transactionWithResult {
            val expense = Expense(
                id = writer.newId(), groupId = groupId, title = input.title, category = input.category,
                note = input.note, date = input.date, currency = input.currency, amountMinor = input.amountMinor,
                fxRateScaled = input.fxRateScaled, payers = input.payers, splitType = input.splitType,
                splitValues = input.splitValues, receiptUrl = null, recurrenceId = null, createdBy = writer.uid(),
                createdAt = writer.now(), meta = writer.createdMeta(),
            )
            save(expense, old = null, action = ChangeAction.CREATED)
            expense.id
        }
    }

    override suspend fun update(id: String, input: ExpenseInput) = edit(id, ChangeAction.UPDATED) { old ->
        old.copy(
            title = input.title, category = input.category, note = input.note, date = input.date,
            currency = input.currency, amountMinor = input.amountMinor, fxRateScaled = input.fxRateScaled,
            payers = input.payers, splitType = input.splitType, splitValues = input.splitValues,
            meta = writer.editedMeta(old.meta),
        )
    }

    override suspend fun delete(id: String) = edit(id, ChangeAction.DELETED) { old ->
        old.copy(meta = writer.editedMeta(old.meta, deleted = true))
    }

    override suspend fun restore(id: String) = edit(id, ChangeAction.RESTORED) { old ->
        old.copy(meta = writer.editedMeta(old.meta, deleted = false))
    }

    private suspend fun edit(id: String, action: ChangeAction, change: (Expense) -> Expense) = withContext(io) {
        db.transaction {
            val old = requireNotNull(load(id)) { "Unknown expense $id" }
            save(change(old), old, action)
        }
    }

    private fun load(id: String): Expense? = q.selectById(id).executeAsOneOrNull()?.let(::withChildren)

    private fun withChildren(row: Expense_entity): Expense = row.toModel(
        payers = q.payersByExpense(row.id).executeAsList().associate { it.member_id to it.amount_minor },
        splitValues = q.splitsByExpense(row.id).executeAsList().associate { it.member_id to it.split_value },
    )

    private fun save(expense: Expense, old: Expense?, action: ChangeAction) {
        q.upsert(expense.toRow())
        q.deletePayers(expense.id)
        expense.payers.forEach { (member, amount) -> q.insertPayer(Expense_payer(expense.id, member, amount)) }
        q.deleteSplits(expense.id)
        expense.splitValues.forEach { (member, value) -> q.insertSplit(Expense_split(expense.id, member, value)) }
        writer.enqueue(
            expense.groupId, OutboxType.EXPENSE, expense.id, old?.meta?.serverVersion ?: 0,
            json.encodeToString(Expense.serializer(), expense),
        )
        val diff = if (action == ChangeAction.UPDATED) Diffs.expense(old, expense) else emptyList()
        writer.log(expense.groupId, EntityType.EXPENSE, expense.id, action, diff)
    }
}
```

- [ ] **Step 4: Ver que pasa**

Run: `./gradlew :data:repository:testDebugUnitTest --tests "*ExpenseRepositoryTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add data/repository
git commit -m "feat(repository): expenses with payers, splits, soft delete and diffs"
```

### Task 7: Transferencias y ciclo de vida de los miembros

**Files:**
- Create: `TransferRepositoryImpl.kt`
- Modify: `TestEnv.kt`
- Test: `TransferRepositoryTest.kt`, `MemberRepositoryTest.kt`

- [ ] **Step 1: Implementar**

En `TestEnv.kt`, sustituir la línea comentada `// Task 7 adds: val transfers = …` por `    val transfers = TransferRepositoryImpl(db, ctx, dispatcher)`.


`TransferRepositoryImpl.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TransferRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : TransferRepository {
    private val writer = Writer(db, ctx)

    override fun observeTransfers(groupId: String): Flow<List<Transfer>> =
        db.transferEntityQueries.selectVisibleByGroup(groupId).asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }

    override suspend fun create(groupId: String, input: TransferInput): String = withContext(io) {
        db.transactionWithResult {
            val transfer = Transfer(
                id = writer.newId(), groupId = groupId, fromMemberId = input.fromMemberId,
                toMemberId = input.toMemberId, amountMinor = input.amountMinor, currency = input.currency,
                fxRateScaled = input.fxRateScaled, date = input.date, note = input.note, createdBy = writer.uid(),
                createdAt = writer.now(), meta = writer.createdMeta(),
            )
            save(transfer, old = null, action = ChangeAction.CREATED)
            transfer.id
        }
    }

    override suspend fun delete(id: String) = setDeleted(id, deleted = true, ChangeAction.DELETED)

    override suspend fun restore(id: String) = setDeleted(id, deleted = false, ChangeAction.RESTORED)

    private suspend fun setDeleted(id: String, deleted: Boolean, action: ChangeAction) = withContext(io) {
        db.transaction {
            val old = requireNotNull(db.transferEntityQueries.selectById(id).executeAsOneOrNull()) { "Unknown transfer $id" }.toModel()
            save(old.copy(meta = writer.editedMeta(old.meta, deleted = deleted)), old, action)
        }
    }

    private fun save(transfer: Transfer, old: Transfer?, action: ChangeAction) {
        db.transferEntityQueries.upsert(transfer.toRow())
        writer.enqueue(
            transfer.groupId, OutboxType.TRANSFER, transfer.id, old?.meta?.serverVersion ?: 0,
            json.encodeToString(Transfer.serializer(), transfer),
        )
        writer.log(transfer.groupId, EntityType.TRANSFER, transfer.id, action, emptyList())
    }
}
```
- [ ] **Step 2: Tests**

`TransferRepositoryTest.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferRepositoryTest {
    @Test
    fun `transfers can be created, deleted and restored`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Piso", "home", "green", "EUR"), "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))
        val id = env.transfers.create(g, TransferInput(luis, ana, 1_215, "EUR", 1_000_000_000, LocalDate(2026, 7, 13)))

        env.transfers.observeTransfers(g).test { assertEquals(1_215L, awaitItem().single().amountMinor) }
        env.transfers.delete(id)
        env.transfers.observeTransfers(g).test { assertTrue(awaitItem().isEmpty()) }
        env.transfers.restore(id)
        env.transfers.observeTransfers(g).test { assertEquals(id, awaitItem().single().id) }
    }
}
```
`MemberRepositoryTest.kt`:
```kotlin
package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.SplitType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberRepositoryTest {
    @Test
    fun `members get increasing sort keys and keep their order`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Viaje", "plane", "blue", "EUR"), "Ana")
        env.members.add(g, MemberInput("Luis"))
        env.members.add(g, MemberInput("Marta", defaultWeight = 2))
        val members = env.members.observeMembersOnce(g)
        assertEquals(listOf("Ana", "Luis", "Marta"), members.map { it.name })
        assertEquals(listOf(0L, 1L, 2L), members.map { it.sortKey })
        assertEquals(2L, members.last().defaultWeight)
    }

    @Test
    fun `a member with movements cannot be deleted, only deactivated`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Viaje", "plane", "blue", "EUR"), "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))
        val zoe = env.members.add(g, MemberInput("Zoe"))
        env.expenses.create(
            g,
            ExpenseInput(
                "Taxi", ExpenseCategory.TRANSPORT, null, LocalDate(2026, 7, 12), "EUR", 2_000, 1_000_000_000,
                mapOf(ana to 2_000L), SplitType.EQUAL, mapOf(ana to 1L, luis to 1L),
            ),
        )

        assertFalse(env.members.deleteIfUnused(luis))
        env.members.setActive(luis, false)
        assertTrue(env.members.deleteIfUnused(zoe))

        val members = env.members.observeMembersOnce(g)
        assertEquals(listOf("Ana", "Luis"), members.map { it.name })
        assertFalse(members.first { it.id == luis }.active)
    }
}
```

- [ ] **Step 3: Todo el módulo**

Run: `./gradlew :data:repository:testDebugUnitTest`
Expected: PASS (Group 3 + Expense 4 + Transfer 1 + Member 2 = 10 tests).

- [ ] **Step 4: Commit**

```bash
git add data/repository
git commit -m "feat(repository): transfers, change log and member lifecycle"
```

### Task 8: Saldos de un grupo en el dominio

**Files:**
- Modify: `domain/usecases/build.gradle.kts`
- Create: `ObserveGroupBalances.kt`
- Test: `FakeRepositories.kt`, `ObserveGroupBalancesTest.kt`

- [ ] **Step 1: Dependencias**

En `domain/usecases/build.gradle.kts`, `commonMain.dependencies`, añadir:
```kotlin
            implementation(project(":data:models"))
            implementation(project(":data:repository"))
```
y en `commonTest.dependencies`:
```kotlin
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
```

- [ ] **Step 2: Repositorios falsos para el test**

`domain/usecases/src/commonTest/kotlin/es/sebas1705/owlsplit/domain/usecases/balances/FakeRepositories.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.ExpenseInput
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupInput
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberInput
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferInput
import es.sebas1705.owlsplit.data.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Read-only fakes: tests push state into the flows directly. */
class FakeGroups(val group: MutableStateFlow<Group?>) : GroupRepository {
    override fun observeGroups(): Flow<List<Group>> = group.map { listOfNotNull(it) }
    override fun observeGroup(id: String): Flow<Group?> = group
    override suspend fun create(input: GroupInput, creatorName: String): String = error("unused")
    override suspend fun update(id: String, input: GroupInput) = error("unused")
    override suspend fun setArchived(id: String, archived: Boolean) = error("unused")
}

class FakeMembers(val members: MutableStateFlow<List<Member>>) : MemberRepository {
    override fun observeMembers(groupId: String): Flow<List<Member>> = members
    override suspend fun add(groupId: String, input: MemberInput): String = error("unused")
    override suspend fun update(id: String, input: MemberInput) = error("unused")
    override suspend fun setActive(id: String, active: Boolean) = error("unused")
    override suspend fun deleteIfUnused(id: String): Boolean = error("unused")
}

class FakeExpenses(val expenses: MutableStateFlow<List<Expense>>) : ExpenseRepository {
    override fun observeExpenses(groupId: String): Flow<List<Expense>> = expenses
    override suspend fun get(id: String): Expense? = expenses.value.firstOrNull { it.id == id }
    override suspend fun create(groupId: String, input: ExpenseInput): String = error("unused")
    override suspend fun update(id: String, input: ExpenseInput) = error("unused")
    override suspend fun delete(id: String) = error("unused")
    override suspend fun restore(id: String) = error("unused")
}

class FakeTransfers(val transfers: MutableStateFlow<List<Transfer>>) : TransferRepository {
    override fun observeTransfers(groupId: String): Flow<List<Transfer>> = transfers
    override suspend fun create(groupId: String, input: TransferInput): String = error("unused")
    override suspend fun delete(id: String) = error("unused")
    override suspend fun restore(id: String) = error("unused")
}
```

- [ ] **Step 3: Test**

`ObserveGroupBalancesTest.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.usecases.balances

import app.cash.turbine.test
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveGroupBalancesTest {
    private val meta = SyncMeta(1, 0, null, "u", false, SyncState.PENDING)
    private val group = Group("g", "Piso", "home", "green", "EUR", null, null, "C", listOf("u"), emptyList(), "u", 0, meta)
    private fun member(id: String, sort: Long) = Member(id, "g", id, null, 1, null, true, sort, meta)
    private fun expense(id: String, amount: Long, payer: String, among: List<String>, values: Map<String, Long>? = null) = Expense(
        id, "g", id, ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", amount, 1_000_000_000,
        mapOf(payer to amount), SplitType.EQUAL, values ?: among.associateWith { 1L }, null, null, "u", 0, meta,
    )

    @Test
    fun `balances and suggested payments update when expenses change`() = runTest {
        val expenses = MutableStateFlow(listOf(expense("e1", 3_000, "ana", listOf("ana", "luis", "marta"))))
        val useCase = ObserveGroupBalances(
            FakeGroups(MutableStateFlow(group)),
            FakeMembers(MutableStateFlow(listOf(member("ana", 0), member("luis", 1), member("marta", 2)))),
            FakeExpenses(expenses),
            FakeTransfers(MutableStateFlow(emptyList())),
        )

        useCase("g").test {
            val first = awaitItem()!!
            assertEquals(mapOf("ana" to 2_000L, "luis" to -1_000L, "marta" to -1_000L), first.balances)
            assertEquals(
                listOf(SuggestedPayment("luis", "ana", 1_000), SuggestedPayment("marta", "ana", 1_000)),
                first.suggestedPayments,
            )

            expenses.value = expenses.value + expense("e2", 1_000, "luis", listOf("luis", "marta"))
            assertEquals(mapOf("ana" to 2_000L, "luis" to -500L, "marta" to -1_500L), awaitItem()!!.balances)
        }
    }

    @Test
    fun `invalid entries are skipped and reported instead of breaking the group`() = runTest {
        val broken = expense("bad", 1_000, "ana", emptyList(), values = mapOf("ghost" to 1L))
        val useCase = ObserveGroupBalances(
            FakeGroups(MutableStateFlow(group)),
            FakeMembers(MutableStateFlow(listOf(member("ana", 0)))),
            FakeExpenses(MutableStateFlow(listOf(broken))),
            FakeTransfers(MutableStateFlow(emptyList())),
        )
        useCase("g").test {
            val result = awaitItem()!!
            assertEquals(mapOf("ana" to 0L), result.balances)
            assertEquals(listOf("bad"), result.invalidEntryIds)
        }
    }

    @Test
    fun `unknown group gives null`() = runTest {
        val useCase = ObserveGroupBalances(
            FakeGroups(MutableStateFlow(null)), FakeMembers(MutableStateFlow(emptyList())),
            FakeExpenses(MutableStateFlow(emptyList())), FakeTransfers(MutableStateFlow(emptyList())),
        )
        useCase("g").test { assertNull(awaitItem()) }
    }
}
```

- [ ] **Step 4: Ver que falla**

Run: `./gradlew :domain:usecases:testDebugUnitTest --tests "*ObserveGroupBalancesTest"`
Expected: FAIL (`ObserveGroupBalances` no existe).

- [ ] **Step 5: Implementar**

`domain/usecases/src/commonMain/kotlin/es/sebas1705/owlsplit/domain/usecases/balances/ObserveGroupBalances.kt`:
```kotlin
package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferRepository
import es.sebas1705.owlsplit.domain.ledger.BalanceCalculator
import es.sebas1705.owlsplit.domain.ledger.DebtSimplifier
import es.sebas1705.owlsplit.domain.ledger.ExpenseEntry
import es.sebas1705.owlsplit.domain.ledger.InvalidEntryException
import es.sebas1705.owlsplit.domain.ledger.LedgerMember
import es.sebas1705.owlsplit.domain.ledger.NetCalculator
import es.sebas1705.owlsplit.domain.ledger.SplitSpec
import es.sebas1705.owlsplit.domain.ledger.SplitType
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.ledger.TransferEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class GroupBalances(
    val baseCurrency: String,
    /** Member id → balance in base-currency minor units; positive = is owed. */
    val balances: Map<String, Long>,
    val suggestedPayments: List<SuggestedPayment>,
    /** Entries left out because they are inconsistent (e.g. synced from a buggy client). */
    val invalidEntryIds: List<String>,
)

/** Live balances of one group, recomputed from local data on every change. */
class ObserveGroupBalances(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val transfers: TransferRepository,
) {
    operator fun invoke(groupId: String): Flow<GroupBalances?> = combine(
        groups.observeGroup(groupId),
        members.observeMembers(groupId),
        expenses.observeExpenses(groupId),
        transfers.observeTransfers(groupId),
    ) { group, memberList, expenseList, transferList ->
        if (group == null) return@combine null
        val base = Currency.of(group.baseCurrency)
        val ledgerMembers = memberList.map { LedgerMember(it.id, it.sortKey) }
        val invalid = mutableListOf<String>()
        val validExpenses = expenseList.map { it.toEntry() }.filter { entry ->
            isValid(invalid) { NetCalculator.expenseNet(entry, ledgerMembers, base) }
        }
        val validTransfers = transferList.map { it.toEntry() }.filter { entry ->
            isValid(invalid) { NetCalculator.transferNet(entry, ledgerMembers, base) }
        }
        val balances = BalanceCalculator.balances(ledgerMembers, validExpenses, validTransfers, base)
        GroupBalances(group.baseCurrency, balances, DebtSimplifier.simplify(balances, ledgerMembers), invalid)
    }

    private inline fun isValid(invalid: MutableList<String>, check: () -> Unit): Boolean =
        try {
            check()
            true
        } catch (e: InvalidEntryException) {
            invalid += e.entryId
            false
        }

    private fun Expense.toEntry() = ExpenseEntry(
        id = id,
        amountMinor = amountMinor,
        currency = Currency.of(currency),
        fxRateToBase = FxRate(fxRateScaled),
        payers = payers,
        split = SplitSpec(SplitType.valueOf(splitType.name), splitValues),
    )

    private fun Transfer.toEntry() = TransferEntry(
        id = id,
        fromMemberId = fromMemberId,
        toMemberId = toMemberId,
        amountMinor = amountMinor,
        currency = Currency.of(currency),
        fxRateToBase = FxRate(fxRateScaled),
    )
}
```

- [ ] **Step 6: Ver que pasa**

Run: `./gradlew :domain:usecases:testDebugUnitTest`
Expected: PASS (26 de P3 + 3 nuevos = 29).

- [ ] **Step 7: Commit**

```bash
git add domain/usecases
git commit -m "feat(domain): observe group balances from local data"
```

### Task 9: CI, verificación y documentación

- [ ] **Step 1: Verificar migraciones en CI**

En `.github/workflows/ci.yml`, job `android`, cambiar el `run` por:
```yaml
      - run: ./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug --continue
```

- [ ] **Step 2: Todo en local**

```bash
./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`. Detekt: corregir el código; si marca `LongParameterList` en los modelos de datos (`data class` con muchos campos), excluir `**/data/models/**` de esa regla en `config/detekt/detekt.yml`.

- [ ] **Step 3: Documentación**

- `MODULE_MAP.md`: filas nuevas `data:models` (modelos de documento con `SyncMeta`, serializables) y `data:repository` (repositorios con Flows, escritura con outbox e historial); `data:local` pasa a "Esquema completo (v1), `verifyMigrations`"; `domain:usecases` añade `ObserveGroupBalances`. Actualizar la línea de módulos previstos (quedan `data:sync`, `feature:*`).
- `docs/SYNC.md`, sección "Tablas locales": añadir que `expense_payer` y `expense_split` guardan pagadores y reparto, y que `fx_rate_cache` llega en P9.
- `docs/plans/README.md`: P4 → `Hecho`.

- [ ] **Step 4: Commit, push y PR**

```bash
git add -A
git commit -m "ci: verify SQLDelight migrations; docs: mark P4 as done"
git push -u origin p4/local-db
gh pr create --title "P4: local database and repositories" --body "Implements docs/plans/2026-10-07-p04-local-db-repositories.md"
```

## Verificación final de P4

- `./gradlew testDebugUnitTest` verde: `data:local` 6, `data:repository` 10, `domain:usecases` 29, más los de P1–P3.
- `verifySqlDelightMigration` en verde y `1.db` versionado.
- Ningún `INSERT … ON CONFLICT` en los `.sq` (`grep -rn "ON CONFLICT" data/local/src` vacío).
