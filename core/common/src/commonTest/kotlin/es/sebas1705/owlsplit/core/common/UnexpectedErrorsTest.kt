package es.sebas1705.owlsplit.core.common

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UnexpectedErrorsTest {
    private data class State(val count: Int = 0) : MVIBaseState
    private sealed interface Intent : MVIBaseIntent {
        data object Fail : Intent
        data object Count : Intent
    }

    private class Screen : MVIBaseViewModel<State, Intent>() {
        override fun initState() = State()
        override fun intentHandler(intent: Intent): Job = execute {
            when (intent) {
                Intent.Fail -> error("boom")
                Intent.Count -> updateState { it.copy(count = it.count + 1) }
            }
        }
    }

    private val reported = mutableListOf<Throwable>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        UnexpectedErrors.reporter = ErrorReporter { reported += it }
    }

    @AfterTest
    fun tearDown() {
        UnexpectedErrors.reporter = ErrorReporter { }
        Dispatchers.resetMain()
    }

    @Test
    fun anErrorInAScreenIsReportedAndTheScreenKeepsWorking() {
        val screen = Screen()
        screen.onEvent(Intent.Fail)
        screen.onEvent(Intent.Count)
        assertEquals(listOf("boom"), reported.map { it.message })
        assertEquals(1, screen.uiState.value.count)
    }
}
