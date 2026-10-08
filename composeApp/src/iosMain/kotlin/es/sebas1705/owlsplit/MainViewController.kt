package es.sebas1705.owlsplit

import androidx.compose.ui.window.ComposeUIViewController
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.apps
import es.sebas1705.owlsplit.core.ui.components.QrScanner
import es.sebas1705.owlsplit.core.ui.components.TextSharer
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.data.models.GoogleTokenResult
import es.sebas1705.owlsplit.di.FirebaseStatus
import es.sebas1705.owlsplit.errors.CrashReporting
import es.sebas1705.owlsplit.di.accountModule
import es.sebas1705.owlsplit.di.appModule
import es.sebas1705.owlsplit.di.dataModule
import es.sebas1705.owlsplit.di.groupsModule
import es.sebas1705.owlsplit.di.inviteModule
import es.sebas1705.owlsplit.domain.usecases.account.GoogleCredentials
import es.sebas1705.owlsplit.navigation.IncomingInvites
import es.sebas1705.owlsplit.platform.IosQrScanner
import es.sebas1705.owlsplit.platform.IosTextSharer
import es.sebas1705.owlsplit.sync.SyncStarter
import es.sebas1705.owlsplit.sync.syncModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** Google sign-in on iOS needs the Google Sign-In SDK and a URL scheme; pending (docs/plans, P11c). */
private object UnavailableGoogleCredentials : GoogleCredentials {
    override val isAvailable = false

    override suspend fun requestIdToken() = GoogleTokenResult.Failed("Not available on iOS yet")
}

fun MainViewController() = ComposeUIViewController(
    configure = {
        val firebase = FirebaseStatus(configured = Firebase.apps().isNotEmpty())
        CrashReporting.start(firebase.configured)
        val koin = startKoin {
            modules(
                appModule,
                dataModule,
                syncModule,
                accountModule,
                groupsModule,
                inviteModule,
                module {
                    single { DatabaseDriverFactory() }
                    single { firebase }
                    single<GoogleCredentials> { UnavailableGoogleCredentials }
                    single<TextSharer> { IosTextSharer() }
                    single<QrScanner> { IosQrScanner() }
                },
            )
        }.koin
        // Background uploads on iOS (BGTaskScheduler) arrive with P11.
        if (firebase.configured) koin.get<SyncStarter>().start(koin.get())
    }
) { App() }

/**
 * Called by the Swift app with the URLs it is opened with (Universal Links of `owlsplit.web.app`,
 * once the Apple Team ID is in apple-app-site-association): `/j/<code>` opens Join.
 */
fun handleIncomingLink(url: String) {
    if ("/j/" in url) IncomingInvites.offer(url)
}
