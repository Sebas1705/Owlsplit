package es.sebas1705.owlsplit

import android.app.Application
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.apps
import es.sebas1705.owlsplit.account.AndroidGoogleCredentials
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.core.security.appContext
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.di.FirebaseStatus
import es.sebas1705.owlsplit.di.accountModule
import es.sebas1705.owlsplit.di.appModule
import es.sebas1705.owlsplit.di.dataModule
import es.sebas1705.owlsplit.di.groupsModule
import es.sebas1705.owlsplit.domain.usecases.account.GoogleCredentials
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
        val activities = ResumedActivity().also(::registerActivityLifecycleCallbacks)
        val firebase = FirebaseStatus(configured = Firebase.apps(this).isNotEmpty())

        val koin = startKoin {
            androidContext(this@KmpApplication)
            modules(
                appModule,
                dataModule,
                syncModule,
                accountModule,
                groupsModule,
                module {
                    single { DatabaseDriverFactory(this@KmpApplication) }
                    single { firebase }
                    single<GoogleCredentials> { AndroidGoogleCredentials(this@KmpApplication, activities) }
                },
            )
        }.koin

        if (firebase.configured) {
            val scope = koin.get<CoroutineScope>()
            koin.get<SyncStarter>().start(scope, this)
            SyncWorker.scheduleOnPending(this, koin.get(), scope)
        }
    }
}
