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
        active = 1, sort_key = sortKey, version = 1, server_version = 0, updated_at = null,
        updated_by = "u1", deleted = 0, sync_state = "PENDING",
    )

    private fun expense(id: String, deleted: Boolean = false) = Expense_entity(
        id = id, group_id = "g1", title = "Cena", category = "FOOD", note = null, date = "2026-07-12",
        currency = "EUR", amount_minor = 1000, fx_rate_scaled = 1_000_000_000, split_type = "EQUAL",
        receipt_url = null, recurrence_id = null, created_by = "u1", created_at = 1, version = 1,
        server_version = 0, updated_at = null, updated_by = "u1", deleted = if (deleted) 1 else 0,
        sync_state = "PENDING",
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
