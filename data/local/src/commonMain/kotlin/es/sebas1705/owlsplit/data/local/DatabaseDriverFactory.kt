package es.sebas1705.owlsplit.data.local

import app.cash.sqldelight.db.SqlDriver
import es.sebas1705.owlsplit.data.local.db.AppDatabase

expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): AppDatabase =
    AppDatabase(factory.create())
