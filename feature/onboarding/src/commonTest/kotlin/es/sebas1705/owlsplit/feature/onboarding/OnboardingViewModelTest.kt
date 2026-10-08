package es.sebas1705.owlsplit.feature.onboarding

import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeGoogle
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GoogleTokenResult
import es.sebas1705.owlsplit.domain.usecases.account.CompleteOnboarding
import es.sebas1705.owlsplit.domain.usecases.account.SignInWithGoogle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    private val rule = CoroutineTestRule()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(
        accounts: FakeAccounts = FakeAccounts(),
        profiles: FakeProfiles = FakeProfiles(),
        google: FakeGoogle = FakeGoogle(),
    ) = OnboardingViewModel(CompleteOnboarding(accounts, profiles), SignInWithGoogle(google, accounts, profiles))

    @Test
    fun walkingThroughSavesTheProfileAndReportsTheChoice() = runTest {
        val profiles = FakeProfiles()
        val vm = viewModel(profiles = profiles)
        vm.onEvent(OnboardingIntent.Next)
        vm.onEvent(OnboardingIntent.NameChanged("  Ana  "))
        vm.onEvent(OnboardingIntent.AccentChanged(GroupAccent.GREEN))
        vm.onEvent(OnboardingIntent.Next)
        assertEquals(OnboardingStep.START, vm.uiState.value.step)

        vm.onEvent(OnboardingIntent.Finish(StartChoice.CREATE_GROUP, "Yo"))

        assertEquals(UserProfile("Ana", "accent:GREEN", "EUR"), profiles.profile.value)
        assertEquals(StartChoice.CREATE_GROUP, vm.uiState.value.finished)
    }

    @Test
    fun skippingUsesTheFallbackName() = runTest {
        val profiles = FakeProfiles()
        val vm = viewModel(profiles = profiles)
        vm.onEvent(OnboardingIntent.Finish(StartChoice.LATER, "Yo"))
        assertEquals("Yo", profiles.profile.value?.name)
    }

    @Test
    fun finishingWaitsForTheFirstSessionAndSaysSoWhenItTakes() = runTest {
        val accounts = FakeAccounts(initial = null)
        val profiles = FakeProfiles()
        val vm = viewModel(accounts = accounts, profiles = profiles)
        vm.onEvent(OnboardingIntent.Finish(StartChoice.LATER, "Yo"))
        rule.dispatcher.scheduler.advanceTimeBy(5_000)
        assertTrue(vm.uiState.value.waitingForNetwork)
        assertNull(profiles.profile.value)

        accounts.session.value = FakeAccounts.ANONYMOUS
        rule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StartChoice.LATER, vm.uiState.value.finished)
        assertEquals(false, vm.uiState.value.waitingForNetwork)
    }

    @Test
    fun anExistingGoogleAccountRestoresItsProfile() = runTest {
        val accounts = FakeAccounts()
        val vm = viewModel(accounts, FakeProfiles(remote = UserProfile("Ana G", "accent:PINK", "EUR")))
        vm.onEvent(OnboardingIntent.UseExistingAccount)
        assertEquals(listOf("signInWithGoogle google-token"), accounts.calls)
        assertEquals(OnboardingStep.PROFILE, vm.uiState.value.step)
        assertEquals("Ana G", vm.uiState.value.name)
        assertEquals(GroupAccent.PINK, vm.uiState.value.accent)
    }

    @Test
    fun cancellingGoogleIsSilentAndFailuresAreShown() = runTest {
        val google = FakeGoogle(next = GoogleTokenResult.Cancelled)
        val accounts = FakeAccounts().apply { signInResult = AccountResult.Offline }
        val vm = viewModel(accounts = accounts, google = google)
        vm.onEvent(OnboardingIntent.UseExistingAccount)
        assertNull(vm.uiState.value.message)

        google.next = GoogleTokenResult.Token("t")
        vm.onEvent(OnboardingIntent.UseExistingAccount)
        assertEquals(OnboardingMessage.OFFLINE, vm.uiState.value.message)
        assertEquals(OnboardingStep.WELCOME, vm.uiState.value.step)
    }
}
