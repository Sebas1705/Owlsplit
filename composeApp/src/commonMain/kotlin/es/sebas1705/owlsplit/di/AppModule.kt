package es.sebas1705.owlsplit.di

import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.datastore.createDataStore
import es.sebas1705.owlsplit.core.security.SecureStorage
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.data.local.createDatabase
import org.koin.dsl.module

val appModule = module {
    single { SecureStorage() }
    single { AppPreferences(createDataStore()) }
    single { createDatabase(get<DatabaseDriverFactory>()) }
}
