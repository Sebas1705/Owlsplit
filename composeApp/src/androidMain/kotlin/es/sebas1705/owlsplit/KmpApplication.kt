package es.sebas1705.owlsplit

import android.app.Application
import es.sebas1705.owlsplit.core.security.appContext
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.di.appModule
import es.sebas1705.owlsplit.sync.SyncStarter
import es.sebas1705.owlsplit.sync.SyncWorker
import es.sebas1705.owlsplit.sync.syncModule
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class KmpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this

        val koin = startKoin {
            androidContext(this@KmpApplication)
            modules(
                appModule,
                syncModule,
                module { single { DatabaseDriverFactory(this@KmpApplication) } }
            )
        }.koin

        val scope = koin.get<CoroutineScope>()
        if (koin.get<SyncStarter>().start(scope, this) != null) {
            SyncWorker.scheduleOnPending(this, koin.get(), scope)
        }
    }
}
