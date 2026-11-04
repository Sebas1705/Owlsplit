package es.sebas1705.owlsplit.feature.profile

import es.sebas1705.owlsplit.core.common.ErrorReporter
import es.sebas1705.owlsplit.core.common.RecentLogs
import es.sebas1705.owlsplit.core.common.UnexpectedErrors
import es.sebas1705.owlsplit.core.common.UserReport
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.support.ReportProblem
import es.sebas1705.owlsplit.domain.usecases.support.SyncNow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupportViewModelTest {
    private val rule = es.sebas1705.owlsplit.core.testing.CoroutineTestRule()
    private val sync = FakeSync()
    private val reported = mutableListOf<Throwable>()
    private val previous = UnexpectedErrors.reporter

    @BeforeTest
    fun setUp() {
        rule.setup()
        UnexpectedErrors.reporter = ErrorReporter { reported += it }
    }

    @AfterTest
    fun tearDown() {
        UnexpectedErrors.reporter = previous
        rule.tearDown()
    }

    private fun vm() = SupportViewModel(ObservePendingChanges(sync), SyncNow(sync), ReportProblem(sync))

    @Test
    fun syncNowShowsHowItEndedAndTheChangesWaiting() = runTest {
        sync.pendingCount.value = 12
        val vm = vm()
        assertEquals(12, vm.uiState.value.pending)

        sync.online = false
        vm.onEvent(SupportIntent.SyncNow)
        assertEquals(SyncOutcome.OFFLINE, vm.uiState.value.outcome)
        assertFalse(vm.uiState.value.syncing)

        sync.online = true
        vm.onEvent(SupportIntent.SyncNow)
        assertEquals(SyncOutcome.SYNCED, vm.uiState.value.outcome)
        sync.pendingCount.value = 0
        assertNull(vm.uiState.value.outcome)
    }

    @Test
    fun aReportCarriesTheWordsThePendingChangesAndTheRecentLogs() = runTest {
        RecentLogs.add("Error Sync: write rejected")
        sync.pendingCount.value = 12
        val vm = vm()
        vm.onEvent(SupportIntent.OpenReport)
        vm.onEvent(SupportIntent.DescriptionChanged("Merging left 12 pending"))
        vm.onEvent(SupportIntent.SendReport)

        val report = reported.single()
        assertTrue(report is UserReport)
        val text = report.message.orEmpty()
        assertTrue("Merging left 12 pending" in text)
        assertTrue("Pending changes: 12" in text)
        assertTrue("write rejected" in text)
        assertTrue(vm.uiState.value.reported)
        assertFalse(vm.uiState.value.reporting)
    }

    @Test
    fun cancellingAReportSendsNothing() = runTest {
        val vm = vm()
        vm.onEvent(SupportIntent.OpenReport)
        vm.onEvent(SupportIntent.DescriptionChanged("typed"))
        vm.onEvent(SupportIntent.DismissReport)
        assertTrue(reported.isEmpty())
        assertEquals("", vm.uiState.value.description)
    }
}
