package es.sebas1705.owlsplit

import androidx.compose.ui.window.ComposeUIViewController
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.di.appModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(
                appModule,
                module { single { DatabaseDriverFactory() } }
            )
        }
    }
) { App() }
