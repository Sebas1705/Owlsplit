package es.sebas1705.owlsplit.feature.profile

import es.sebas1705.owlsplit.core.testing.ledger.FakeInvites
import es.sebas1705.owlsplit.domain.usecases.account.CarryGroups
import es.sebas1705.owlsplit.domain.usecases.account.SwitchToGoogle
import es.sebas1705.owlsplit.domain.usecases.account.UnlinkGoogle
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import es.sebas1705.owlsplit.domain.usecases.invite.ShareInvite
import es.sebas1705.owlsplit.core.datastore.ThemeMode
import es.sebas1705.owlsplit.core.datastore.AppLanguage
import es.sebas1705.owlsplit.core.datastore.DisplaySettings
import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeGoogle
import es.sebas1705.owlsplit.core.testing.account.FakeProfileRemote
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.core.testing.account.NoGroups
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.PayMethods
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.DeleteAccount
import es.sebas1705.owlsplit.domain.usecases.account.DeviceReset
import es.sebas1705.owlsplit.domain.usecases.account.LinkGoogle
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ReauthenticateGoogle
import es.sebas1705.owlsplit.domain.usecases.account.SaveProfile
import es.sebas1705.owlsplit.domain.usecases.account.SignOut
import es.sebas1705.owlsplit.domain.usecases.pay.PayMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    private class Display : DisplaySettings {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }

        override val language = MutableStateFlow(AppLanguage.SYSTEM)
        override suspend fun setLanguage(language: AppLanguage) {
            this.language.value = language
        }
    }

    private val ana = UserProfile("Ana", "accent:GREEN", "EUR")
    private var resets = 0

    private inner class Env(session: Session? = FakeAccounts.ANONYMOUS, online: Boolean = true, switch: Boolean = false) {
        val accounts = FakeAccounts(session)
        val profiles = FakeProfiles(ana)
        val ledger = InMemoryLedger()
        val google = FakeGoogle()
        val display = Display()
        val remote = FakeProfileRemote()
        private val sync = FakeSync(online)
        private val reset = DeviceReset { resets++ }
        val vm = ProfileViewModel(
            ObserveAccount(accounts, profiles),
            display,
            SaveProfile(profiles, ledger.members),
            LinkGoogle(google, accounts, profiles, if (switch) switchToGoogle() else null),
            SignOut(sync, accounts, reset),
            DeleteAccount(NoGroups(), sync, accounts, remote, reset),
            ReauthenticateGoogle(google, accounts),
            UnlinkGoogle(accounts, profiles),
        )

        private fun switchToGoogle(): SwitchToGoogle {
            val invites = FakeInvites(ledger)
            val carry = CarryGroups(
                ledger.groups,
                ledger.members,
                accounts,
                ShareInvite(ledger.groups, ledger.members, invites, sync, "https://owlsplit.web.app"),
                PreviewInvite(invites),
                JoinGroup(invites, ledger.groups, accounts),
                ClaimMember(ledger.members, profiles, accounts),
            )
            return SwitchToGoogle(accounts, profiles, sync, carry)
        }
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
    fun payHandlesAreCheckedCleanedUpAndPublishedToMyMembers() = runTest {
        val env = Env()
        val group = env.ledger.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")

        env.vm.onEvent(ProfileIntent.PayChanged(PayMethod.BIZUM, "600 11"))
        assertEquals(setOf(PayMethod.BIZUM), env.vm.uiState.value.invalidPay)
        assertFalse(env.vm.uiState.value.canSave)

        env.vm.onEvent(ProfileIntent.PayChanged(PayMethod.BIZUM, "600 11 12 22"))
        env.vm.onEvent(ProfileIntent.PayChanged(PayMethod.PAYPAL, "paypal.me/AnaG"))
        assertTrue(env.vm.uiState.value.dirty)
        env.vm.onEvent(ProfileIntent.Save)

        val saved = PayMethods(bizum = "600111222", paypal = "AnaG")
        assertEquals(saved, env.profiles.profile.value?.payMethods)
        assertEquals(saved, env.vm.uiState.value.pay)
        assertFalse(env.vm.uiState.value.dirty)
        assertEquals(saved, env.ledger.members.observeMembers(group).first().single().payMethods)
    }

    @Test
    fun anIbanIsCheckedThenSavedCompactWithItsHolderAndReachesMyMembers() = runTest {
        val env = Env()
        val group = env.ledger.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")

        env.vm.onEvent(ProfileIntent.PayChanged(PayMethod.IBAN, "ES91 2100 0418 4502 0005 1333"))
        assertEquals(setOf(PayMethod.IBAN), env.vm.uiState.value.invalidPay)
        assertFalse(env.vm.uiState.value.canSave)

        env.vm.onEvent(ProfileIntent.PayChanged(PayMethod.IBAN, "es91 2100 0418 4502 0005 1332"))
        env.vm.onEvent(ProfileIntent.IbanHolderChanged("Ana García López"))
        // The Save button shows only for changes it sees.
        assertTrue(env.vm.uiState.value.dirty)
        env.vm.onEvent(ProfileIntent.Save)

        val saved = PayMethods(iban = "ES9121000418450200051332", ibanHolder = "Ana García López")
        assertEquals(saved, env.profiles.profile.value?.payMethods)
        assertEquals(saved, env.ledger.members.observeMembers(group).first().single().payMethods)
        assertFalse(env.vm.uiState.value.dirty)
    }

    @Test
    fun currencyThemeAndLanguageApplyAtOnce() = runTest {
        val env = Env()
        env.vm.onEvent(ProfileIntent.CurrencyChanged("USD"))
        env.vm.onEvent(ProfileIntent.ThemeChanged(ThemeMode.DARK))
        env.vm.onEvent(ProfileIntent.LanguageChanged(AppLanguage.ENGLISH))
        assertEquals("USD", env.profiles.profile.value?.currency)
        assertEquals(ThemeMode.DARK, env.vm.uiState.value.theme)
        assertEquals(AppLanguage.ENGLISH, env.vm.uiState.value.language)
        assertEquals(AppLanguage.ENGLISH, env.display.language.value)
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
    fun aGoogleAccountWithDataIsSignedInToWithOneGoogleSheetAndNoQuestion() = runTest {
        val env = Env(switch = true)
        env.accounts.linkResult = AccountResult.CredentialInUse
        env.vm.onEvent(ProfileIntent.Link)

        assertEquals(null, env.vm.uiState.value.dialog)
        assertEquals(1, env.google.requests)
        assertEquals("uid-google", env.accounts.currentSession()!!.uid)
        assertFalse(env.vm.uiState.value.isAnonymous)
        assertEquals(ProfileMessage.LINKED, env.vm.uiState.value.message)
    }

    @Test
    fun oneGoogleAtATimeUnlinkingAsksFirstAndLetsLinkAnother() = runTest {
        val env = Env(Session("uid-ana", isAnonymous = false, providers = FakeAccounts.GOOGLE))
        assertTrue(env.vm.uiState.value.linkedGoogle)
        env.vm.onEvent(ProfileIntent.AskUnlink)
        assertEquals(ProfileDialog.CONFIRM_UNLINK, env.vm.uiState.value.dialog)

        env.vm.onEvent(ProfileIntent.ConfirmUnlink)
        assertEquals(ProfileMessage.UNLINKED, env.vm.uiState.value.message)
        assertFalse(env.vm.uiState.value.linkedGoogle)
        // Anonymous again: the same uid, and linking is offered again.
        assertTrue(env.vm.uiState.value.isAnonymous)
        assertEquals("uid-ana", env.accounts.currentSession()!!.uid)
        env.vm.onEvent(ProfileIntent.Link)
        assertTrue(env.vm.uiState.value.linkedGoogle)
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
