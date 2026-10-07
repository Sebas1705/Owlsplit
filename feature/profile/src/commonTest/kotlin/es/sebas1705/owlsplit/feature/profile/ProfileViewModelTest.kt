package es.sebas1705.owlsplit.feature.profile

import es.sebas1705.owlsplit.core.datastore.ThemeMode
import es.sebas1705.owlsplit.core.datastore.ThemeSettings
import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeGoogle
import es.sebas1705.owlsplit.core.testing.account.FakeProfileRemote
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.core.testing.account.NoGroups
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.DeleteAccount
import es.sebas1705.owlsplit.domain.usecases.account.DeviceReset
import es.sebas1705.owlsplit.domain.usecases.account.LinkGoogle
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ReauthenticateGoogle
import es.sebas1705.owlsplit.domain.usecases.account.SaveProfile
import es.sebas1705.owlsplit.domain.usecases.account.SignOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileViewModelTest {
    private val rule = CoroutineTestRule()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private class Themes : ThemeSettings {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }
    }

    private val ana = UserProfile("Ana", "accent:GREEN", "EUR")
    private var resets = 0

    private inner class Env(session: Session? = FakeAccounts.ANONYMOUS, online: Boolean = true) {
        val accounts = FakeAccounts(session)
        val profiles = FakeProfiles(ana)
        val google = FakeGoogle()
        val themes = Themes()
        val remote = FakeProfileRemote()
        private val sync = FakeSync(online)
        private val reset = DeviceReset { resets++ }
        val vm = ProfileViewModel(
            ObserveAccount(accounts, profiles),
            themes,
            SaveProfile(profiles),
            LinkGoogle(google, accounts, profiles),
            SignOut(sync, accounts, reset),
            DeleteAccount(NoGroups(), sync, accounts, remote, reset),
            ReauthenticateGoogle(google, accounts),
        )
    }

    @Test
    fun showsTheSavedProfileAndSavesEditsOnlyWhenAsked() = runTest {
        val env = Env()
        assertEquals("Ana", env.vm.uiState.value.name)
        assertEquals(GroupAccent.GREEN, env.vm.uiState.value.accent)
        assertFalse(env.vm.uiState.value.dirty)

        env.vm.onEvent(ProfileIntent.NameChanged("Ana María"))
        assertTrue(env.vm.uiState.value.dirty)
        assertEquals("Ana", env.profiles.profile.value?.name)

        env.vm.onEvent(ProfileIntent.Save)
        assertEquals("Ana María", env.profiles.profile.value?.name)
        assertFalse(env.vm.uiState.value.dirty)
        assertEquals(ProfileMessage.SAVED, env.vm.uiState.value.message)
    }

    @Test
    fun currencyAndThemeApplyAtOnce() = runTest {
        val env = Env()
        env.vm.onEvent(ProfileIntent.CurrencyChanged("USD"))
        env.vm.onEvent(ProfileIntent.ThemeChanged(ThemeMode.DARK))
        assertEquals("USD", env.profiles.profile.value?.currency)
        assertEquals(ThemeMode.DARK, env.vm.uiState.value.theme)
    }

    @Test
    fun linkingTurnsTheAccountIntoAGoogleOne() = runTest {
        val env = Env()
        assertTrue(env.vm.uiState.value.isAnonymous)
        env.vm.onEvent(ProfileIntent.Link)
        assertFalse(env.vm.uiState.value.isAnonymous)
        assertTrue(env.vm.uiState.value.linkedGoogle)
        assertEquals(ProfileMessage.LINKED, env.vm.uiState.value.message)
    }

    @Test
    fun aGoogleAccountWithDataIsReportedNotMerged() = runTest {
        val env = Env()
        env.accounts.linkResult = AccountResult.CredentialInUse
        env.vm.onEvent(ProfileIntent.Link)
        assertEquals(ProfileMessage.CREDENTIAL_IN_USE, env.vm.uiState.value.message)
        assertTrue(env.vm.uiState.value.isAnonymous)
    }

    @Test
    fun deletingAsksFirstAndResetsTheDevice() = runTest {
        val env = Env()
        env.vm.onEvent(ProfileIntent.AskDelete)
        assertEquals(ProfileDialog.CONFIRM_DELETE, env.vm.uiState.value.dialog)
        env.vm.onEvent(ProfileIntent.ConfirmDelete)
        assertEquals(listOf("uid-ana"), env.remote.deleted)
        assertEquals(1, resets)
    }

    @Test
    fun deletingALinkedAccountMayAskToSignInAgain() = runTest {
        val env = Env(Session("uid-ana", false, listOf("google.com")))
        env.accounts.deleteResult = AccountResult.RecentLoginRequired
        env.vm.onEvent(ProfileIntent.ConfirmDelete)
        assertEquals(ProfileDialog.REAUTHENTICATE, env.vm.uiState.value.dialog)
        assertEquals(0, resets)

        env.vm.onEvent(ProfileIntent.Reauthenticate)
        assertTrue("reauthenticateGoogle google-token" in env.accounts.calls)
        assertEquals(1, resets)
    }

    @Test
    fun offlineDeletionChangesNothing() = runTest {
        val env = Env(online = false)
        env.vm.onEvent(ProfileIntent.ConfirmDelete)
        assertEquals(ProfileMessage.OFFLINE, env.vm.uiState.value.message)
        assertEquals(0, resets)
    }
}
