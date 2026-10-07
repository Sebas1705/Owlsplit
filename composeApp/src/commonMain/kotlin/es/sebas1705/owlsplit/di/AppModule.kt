package es.sebas1705.owlsplit.di

import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.datastore.createDataStore
import es.sebas1705.owlsplit.core.network.buildHttpClient
import es.sebas1705.owlsplit.core.security.SecureStorage
import es.sebas1705.owlsplit.data.local.DatabaseDriverFactory
import es.sebas1705.owlsplit.data.local.createDatabase
import es.sebas1705.owlsplit.data.local.dao.UserLocalDao
import es.sebas1705.owlsplit.data.network.api.UserApi
import es.sebas1705.owlsplit.data.network.di.networkDataModule
import es.sebas1705.owlsplit.data.repository.IUserRepository
import es.sebas1705.owlsplit.data.repository.UserRepository
import es.sebas1705.owlsplit.domain.usecases.user.GetMeUseCase
import es.sebas1705.owlsplit.domain.usecases.user.UpdateProfileUseCase
import es.sebas1705.owlsplit.feature.auth.AuthViewModel
import es.sebas1705.owlsplit.feature.home.HomeViewModel
import es.sebas1705.owlsplit.feature.profile.ProfileViewModel
import es.sebas1705.owlsplit.feature.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // ── Core ──────────────────────────────────────────────────────────────────
    single { SecureStorage() }
    single { AppPreferences(createDataStore()) }
    single { buildHttpClient(tokenProvider = { get<SecureStorage>().getString("auth_token").ifBlank { null } }) }

    // ── Local DB ──────────────────────────────────────────────────────────────
    single { createDatabase(get<DatabaseDriverFactory>()) }
    single { UserLocalDao(get()) }

    // ── Network ───────────────────────────────────────────────────────────────
    single { UserApi(client = get(), baseUrl = get<SecureStorage>().getString("base_url", "https://api.example.com")) }

    // ── Repository ────────────────────────────────────────────────────────────
    single<IUserRepository> { UserRepository(api = get(), dao = get()) }

    // ── Use cases ─────────────────────────────────────────────────────────────
    factory { GetMeUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }

    // ── ViewModels ────────────────────────────────────────────────────────────
    viewModel { AuthViewModel(get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}
