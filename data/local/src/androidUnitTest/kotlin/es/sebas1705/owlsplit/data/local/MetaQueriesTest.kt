package es.sebas1705.owlsplit.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetaQueriesTest {
    private fun newDb(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }

    @Test
    fun `missing key returns null`() {
        assertNull(newDb().metaQueries.get("schema").executeAsOneOrNull())
    }

    @Test
    fun `put overwrites the previous value`() {
        val db = newDb()
        db.metaQueries.put("schema", "1")
        db.metaQueries.put("schema", "2")
        assertEquals("2", db.metaQueries.get("schema").executeAsOne())
    }
}
