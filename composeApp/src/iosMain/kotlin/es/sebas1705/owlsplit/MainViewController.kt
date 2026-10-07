package es.sebas1705.owlsplit

import androidx.compose.ui.window.ComposeUIViewController
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.di.appModule
import es.sebas1705.owlsplit.sync.SyncStarter
import es.sebas1705.owlsplit.sync.syncModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun MainViewController() = ComposeUIViewController(
    configure = {
        val koin = startKoin {
            modules(
                appModule,
                syncModule,
                module { single { DatabaseDriverFactory() } }
            )
        }.koin
        // Background uploads on iOS (BGTaskScheduler) arrive with P11.
        koin.get<SyncStarter>().start(koin.get())
    }
) { App() }
