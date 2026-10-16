package es.sebas1705.owlsplit

import android.app.Application
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.apps
import es.sebas1705.owlsplit.account.AndroidGoogleCredentials
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.appcheck.AppCheckSetup
import es.sebas1705.owlsplit.core.security.appContext
import es.sebas1705.owlsplit.core.ui.components.QrScanner
import es.sebas1705.owlsplit.core.ui.components.FilePicker
import es.sebas1705.owlsplit.core.ui.components.FileSharer
import es.sebas1705.owlsplit.core.ui.components.BankApps
import es.sebas1705.owlsplit.core.ui.components.TextSharer
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.di.FirebaseStatus
import es.sebas1705.owlsplit.errors.CrashReporting
import es.sebas1705.owlsplit.di.accountModule
import es.sebas1705.owlsplit.di.appModule
import es.sebas1705.owlsplit.di.dataModule
import es.sebas1705.owlsplit.di.groupsModule
import es.sebas1705.owlsplit.di.inviteModule
import es.sebas1705.owlsplit.domain.usecases.account.GoogleCredentials
import es.sebas1705.owlsplit.domain.usecases.update.NoStoreUpdates
import es.sebas1705.owlsplit.domain.usecases.update.StoreUpdates
import es.sebas1705.owlsplit.config.installedFromPlay
import es.sebas1705.owlsplit.update.PlayStoreUpdates
import es.sebas1705.owlsplit.share.AndroidQrScanner
import es.sebas1705.owlsplit.share.AndroidFilePicker
import es.sebas1705.owlsplit.share.AndroidFileSharer
import es.sebas1705.owlsplit.share.AndroidBankApps
import es.sebas1705.owlsplit.share.AndroidTextSharer
import es.sebas1705.owlsplit.sync.SyncStarter
import es.sebas1705.owlsplit.sync.SyncWorker
import es.sebas1705.owlsplit.sync.syncModule
import es.sebas1705.owlsplit.widget.WidgetUpdates
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class KmpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        val activities = ResumedActivity().also(::registerActivityLifecycleCallbacks)
        val firebase = FirebaseStatus(configured = Firebase.apps(this).isNotEmpty())
        // Before Firestore or Auth make their first request, so that every request carries a token.
        if (firebase.configured) AppCheckSetup.install()
        CrashReporting.start(firebase.configured)

        val koin = startKoin {
            androidContext(this@KmpApplication)
            modules(
                appModule,
                dataModule,
                syncModule,
                accountModule,
                groupsModule,
                inviteModule,
                module {
                    single { DatabaseDriverFactory(this@KmpApplication) }
                    single { firebase }
                    single<GoogleCredentials> { AndroidGoogleCredentials(this@KmpApplication, activities) }
                    single<TextSharer> { AndroidTextSharer(activities) }
                    single<BankApps> { AndroidBankApps(this@KmpApplication, activities) }
                    single<FileSharer> { AndroidFileSharer(activities) }
                    single<FilePicker> { AndroidFilePicker(activities) }
                    single<QrScanner> { AndroidQrScanner(this@KmpApplication, activities) }
                    single<StoreUpdates> {
                        if (installedFromPlay()) PlayStoreUpdates(this@KmpApplication, activities) else NoStoreUpdates
                    }
                },
            )
        }.koin

        if (firebase.configured) {
            val scope = koin.get<CoroutineScope>()
            koin.get<SyncStarter>().start(scope, this)
            SyncWorker.scheduleOnPending(this, koin.get(), scope)
            WidgetUpdates.start(this, koin.get(), scope)
        }
        WidgetUpdates.publishShortcut(this)
    }
}
