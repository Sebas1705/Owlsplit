package es.sebas1705.owlsplit.domain.usecases.account

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeGoogle
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.PayMethods
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkGoogleTest {
    private val local = UserProfile("Ana", "accent:BLUE", "EUR")
    private val cloud = UserProfile("Ana García", "accent:GREEN", "USD", PayMethods(bizum = "600111222"))
    private val ledger = InMemoryLedger()

    @Test
    fun theCloudProfileComesDownAndReplacesThePhonesOne() = runTest {
        val g = ledger.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")
        val profiles = FakeProfiles(local, remote = cloud)
        val outcome = LinkGoogle(FakeGoogle(), FakeAccounts(), profiles, ledger.members)()

        assertEquals(GoogleOutcome.Done(cloud), outcome)
        assertEquals(cloud, profiles.profile.value)
        val mine = ledger.members.observeMembers(g).first().single()
        assertEquals(PayMethods(bizum = "600111222"), mine.payMethods)
        assertTrue(mine.linked)
    }

    @Test
    fun withNothingInTheCloudThePhonesProfileStays() = runTest {
        val profiles = FakeProfiles(local, remote = null)
        val outcome = LinkGoogle(FakeGoogle(), FakeAccounts(), profiles, ledger.members)()

        assertEquals(GoogleOutcome.Done(), outcome)
        assertEquals(local, profiles.profile.value)
    }

    @Test
    fun anAccountThatAlreadyHasGoogleIsNotLinkedAgain() = runTest {
        val accounts = FakeAccounts(Session("uid-ana", isAnonymous = false, providers = FakeAccounts.GOOGLE))
        val profiles = FakeProfiles(local, remote = cloud)
        val outcome = LinkGoogle(FakeGoogle(), accounts, profiles, ledger.members)()

        assertEquals(GoogleOutcome.Done(), outcome)
        assertTrue(accounts.calls.none { it.startsWith("linkGoogle") })
        assertEquals(local, profiles.profile.value)
    }

    @Test
    fun aFailedLinkTouchesNothing() = runTest {
        val accounts = FakeAccounts().apply { linkResult = AccountResult.CredentialInUse }
        val profiles = FakeProfiles(local, remote = cloud)
        val outcome = LinkGoogle(FakeGoogle(), accounts, profiles, ledger.members)()

        assertEquals(GoogleOutcome.Failed(AccountResult.CredentialInUse), outcome)
        assertEquals(local, profiles.profile.value)
    }
}
