package es.sebas1705.owlsplit.core.common.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.UnexpectedErrors
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class MVIBaseViewModel<S : MVIBaseState, I : MVIBaseIntent> : ViewModel() {

    // Lazy: initState() may read constructor properties of the subclass, not yet set while this
    // base constructor runs.
    private val _uiState by lazy { MutableStateFlow(initState()) }
    val uiState: StateFlow<S> by lazy { _uiState.asStateFlow() }

    protected val currentState: S get() = _uiState.value

    abstract fun initState(): S
    abstract fun intentHandler(intent: I): Job

    fun onEvent(intent: I): Job = intentHandler(intent)

    protected fun updateState(reducer: (S) -> S) {
        _uiState.value = reducer(_uiState.value)
    }

    /** Work of the screen: an error that escapes it is reported instead of crashing the app. */
    protected fun execute(block: suspend () -> Unit): Job =
        viewModelScope.launch(errorHandler) { block() }

    private val errorHandler = CoroutineExceptionHandler { _, error -> UnexpectedErrors.report(error) }
}
