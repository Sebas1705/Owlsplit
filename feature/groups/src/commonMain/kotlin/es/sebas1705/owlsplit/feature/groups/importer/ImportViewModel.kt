package es.sebas1705.owlsplit.feature.groups.importer

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.ui.components.PickedFile
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.GroupIcons
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.fx.GetFxRate
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import es.sebas1705.owlsplit.domain.usecases.importer.ImportGroup
import es.sebas1705.owlsplit.domain.usecases.importer.ParsedImport
import es.sebas1705.owlsplit.domain.usecases.importer.SplitwiseCsv
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

enum class ImportError { NOT_SPLITWISE, NOTHING_TO_IMPORT }

data class ImportState(
    val parsed: ParsedImport? = null,
    val error: ImportError? = null,
    val name: String = "",
    /** Which column is me; null: none of them (I join with my profile name). */
    val meIndex: Int? = null,
    val rates: Map<String, FxRate> = emptyMap(),
    /** Currencies whose rows will be left out: no rate (offline). */
    val missingRates: Set<String> = emptySet(),
    val busy: Boolean = false,
    val importedGroupId: String? = null,
) : MVIBaseState {
    val canImport: Boolean get() = parsed != null && name.isNotBlank() && !busy
}

sealed interface ImportIntent : MVIBaseIntent {
    /** Null when the picker was closed. */
    data class Picked(val file: PickedFile?) : ImportIntent
    data class NameChanged(val name: String) : ImportIntent
    data class MeChanged(val index: Int?) : ImportIntent
    data object Import : ImportIntent
    data object Reset : ImportIntent
}

/** Bringing a group from Splitwise: pick its spreadsheet, check what will come in, import. */
class ImportViewModel(
    private val observeAccount: ObserveAccount,
    private val getFxRate: GetFxRate,
    private val importGroup: ImportGroup,
) : MVIBaseViewModel<ImportState, ImportIntent>() {

    override fun initState() = ImportState()

    override fun intentHandler(intent: ImportIntent): Job = execute {
        when (intent) {
            is ImportIntent.Picked -> intent.file?.let { read(it) }
            is ImportIntent.NameChanged -> updateState { it.copy(name = intent.name.take(GroupActions.MAX_NAME)) }
            is ImportIntent.MeChanged -> updateState { it.copy(meIndex = intent.index) }
            ImportIntent.Import -> import()
            ImportIntent.Reset -> updateState { ImportState() }
        }
    }

    private suspend fun read(file: PickedFile) {
        val parsed = SplitwiseCsv.parse(file.text)
        when {
            parsed == null -> updateState { ImportState(error = ImportError.NOT_SPLITWISE) }
            parsed.entries.isEmpty() -> updateState { ImportState(error = ImportError.NOTHING_TO_IMPORT) }
            else -> {
                updateState { it.copy(busy = true) }
                val myName = observeAccount().first().profile?.name?.trim()?.lowercase()
                val base = parsed.mainCurrency
                val others = parsed.entries.map { it.currency }.toSet() - base
                val rates = others.associateWith { getFxRate(it, base)?.rate }
                updateState {
                    ImportState(
                        parsed = parsed,
                        name = groupName(file.name),
                        meIndex = parsed.members.indexOfFirst { m -> m.trim().lowercase() == myName }.takeIf { i -> i >= 0 },
                        rates = rates.filterValues { r -> r != null }.mapValues { r -> r.value!! },
                        missingRates = rates.filterValues { r -> r == null }.keys,
                    )
                }
            }
        }
    }

    private suspend fun import() {
        val state = currentState
        val parsed = state.parsed ?: return
        if (!state.canImport) return
        updateState { it.copy(busy = true) }
        val result = importGroup(
            parsed,
            state.name,
            GroupIcons.all.first().first,
            GroupAccent.BLUE.name,
            state.meIndex,
            state.rates,
        )
        updateState { it.copy(busy = false, importedGroupId = result.groupId) }
    }

    /** "viaje-a-ibiza_2026-10-09_export.csv" → "Viaje a ibiza". */
    private fun groupName(fileName: String): String {
        val base = fileName.substringBeforeLast('.').substringBefore("_").replace('-', ' ').replace('_', ' ').trim()
        return base.replaceFirstChar { it.uppercase() }.take(GroupActions.MAX_NAME)
    }
}
