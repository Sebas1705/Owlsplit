package es.sebas1705.owlsplit.di

import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.datastore.KeyValueCache
import es.sebas1705.owlsplit.core.datastore.LinkNudgeSettings
import es.sebas1705.owlsplit.core.datastore.UpdateSettings
import es.sebas1705.owlsplit.core.datastore.DisplaySettings
import es.sebas1705.owlsplit.core.datastore.createDataStore
import es.sebas1705.owlsplit.core.security.SecureStorage
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.data.local.createDatabase
import es.sebas1705.owlsplit.data.remote.FirebaseAccountService
import es.sebas1705.owlsplit.data.remote.FirestoreProfileRemote
import es.sebas1705.owlsplit.data.repository.ChangeLogRepository
import es.sebas1705.owlsplit.data.repository.ChangeLogRepositoryImpl
import es.sebas1705.owlsplit.data.repository.ConflictRepository
import es.sebas1705.owlsplit.data.repository.ConflictRepositoryImpl
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.ExpenseRepositoryImpl
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.GroupRepositoryImpl
import es.sebas1705.owlsplit.data.repository.LocalData
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.MemberRepositoryImpl
import es.sebas1705.owlsplit.data.repository.TransferRepository
import es.sebas1705.owlsplit.data.repository.TransferRepositoryImpl
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.account.ProfileRemote
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.data.repository.account.ProfileRepositoryImpl
import es.sebas1705.owlsplit.data.repository.sync.SyncControl
import es.sebas1705.owlsplit.data.repository.sync.SyncEngine
import es.sebas1705.owlsplit.domain.usecases.account.CompleteOnboarding
import es.sebas1705.owlsplit.domain.usecases.account.DeleteAccount
import es.sebas1705.owlsplit.domain.usecases.account.DeviceReset
import es.sebas1705.owlsplit.domain.usecases.account.LinkGoogle
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ReauthenticateGoogle
import es.sebas1705.owlsplit.domain.usecases.account.SaveProfile
import es.sebas1705.owlsplit.domain.usecases.account.SyncMyLinked
import es.sebas1705.owlsplit.domain.usecases.account.SignInWithGoogle
import es.sebas1705.owlsplit.domain.usecases.account.SignOut
import es.sebas1705.owlsplit.domain.usecases.account.CarryGroups
import es.sebas1705.owlsplit.domain.usecases.account.SwitchToGoogle
import es.sebas1705.owlsplit.domain.usecases.account.UnlinkGoogle
import es.sebas1705.owlsplit.feature.onboarding.OnboardingViewModel
import es.sebas1705.owlsplit.feature.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.binds
import org.koin.dsl.module

val appModule = module {
    single { SecureStorage() }
    single {
        AppPreferences(createDataStore())
    } binds arrayOf(DisplaySettings::class, LinkNudgeSettings::class, UpdateSettings::class, KeyValueCache::class)
    single { createDatabase(get<DatabaseDriverFactory>()) }
}

/** Local data: repositories over SQLDelight. */
val dataModule = module {
    single<GroupRepository> { GroupRepositoryImpl(get(), get(), Dispatchers.IO) }
    single<MemberRepository> { MemberRepositoryImpl(get(), get(), Dispatchers.IO) }
    single<ExpenseRepository> { ExpenseRepositoryImpl(get(), get(), Dispatchers.IO) }
    single<TransferRepository> { TransferRepositoryImpl(get(), get(), Dispatchers.IO) }
    single<ChangeLogRepository> { ChangeLogRepositoryImpl(get(), Dispatchers.IO) }
    single { LocalData(get(), Dispatchers.IO) }
    single<ConflictRepository> { ConflictRepositoryImpl(get(), get(), get(), get(), get(), Dispatchers.IO) }
}

/** Account, profile and their use cases. Needs [syncModule] (Firebase) and a platform `GoogleCredentials`. */
val accountModule = module {
    single<AccountService> { FirebaseAccountService(get()) }
    single<ProfileRemote> { FirestoreProfileRemote(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get(), get(), get(), get()) }
    single<SyncControl> { get<SyncEngine>() }
    single {
        DeviceReset {
            get<LocalData>().wipe()
            get<AppPreferences>().clear()
        }
    }

    factory { ObserveAccount(get(), get()) }
    factory { SaveProfile(get(), get()) }
    factory { CompleteOnboarding(get(), get()) }
    factory { SignInWithGoogle(get(), get(), get()) }
    factory { LinkGoogle(get(), get(), get(), get()) }
    factory { UnlinkGoogle(get(), get()) }
    factory { CarryGroups(get(), get(), get(), get(), get(), get(), get()) }
    factory { SwitchToGoogle(get(), get(), get(), get()) }
    factory { SyncMyLinked(get(), get()) }
    factory { ReauthenticateGoogle(get(), get()) }
    factory { SignOut(get(), get(), get()) }
    factory { DeleteAccount(get(), get(), get(), get(), get()) }

    viewModel { OnboardingViewModel(get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}

/** Whether this build has a Firebase configuration (google-services.json / GoogleService-Info.plist). */
data class FirebaseStatus(val configured: Boolean)
