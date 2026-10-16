package es.sebas1705.owlsplit.feature.home

import es.sebas1705.owlsplit.domain.usecases.account.SyncMyLinked
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.domain.usecases.expenses.GenerateRecurring
import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.datastore.LinkNudgeSettings
import es.sebas1705.owlsplit.core.datastore.UpdateSettings
import es.sebas1705.owlsplit.data.models.LatestRelease
import es.sebas1705.owlsplit.domain.usecases.update.CheckForUpdate
import es.sebas1705.owlsplit.domain.usecases.update.NoStoreUpdates
import es.sebas1705.owlsplit.domain.usecases.update.StoreUpdate
import es.sebas1705.owlsplit.domain.usecases.update.StoreUpdates
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.account.ShouldSuggestLinking
import es.sebas1705.owlsplit.domain.usecases.fx.ConvertTotal
import es.sebas1705.owlsplit.domain.usecases.groups.HomeData
import es.sebas1705.owlsplit.domain.usecases.groups.HomeGroup
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import es.sebas1705.owlsplit.domain.usecases.groups.RejoinGroup
import es.sebas1705.owlsplit.domain.usecases.groups.RejoinResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/** Shortcuts that need a group; with several groups the user picks one first. */
enum class QuickAction { EXPENSE, SETTLE }

data class HomeState(
    val loaded: Boolean = false,
    val groups: List<HomeGroup> = emptyList(),
    /** Groups I archived: listed apart, collapsed. */
    val archived: List<Group> = emptyList(),
    /** Groups I left that are still on this device: listed apart, to go back in. */
    val left: List<Group> = emptyList(),
    /** Going back into one of [left]: asking, then how it went. */
    val rejoin: RejoinState? = null,
    /** In again: Home opens it once. */
    val rejoined: String? = null,
    val currency: String = "EUR",
    val total: Long = 0,
    /** Balances in currencies without a rate, kept apart. */
    val otherCurrencies: Map<String, Long> = emptyMap(),
    /** The total includes groups converted at today's rate. */
    val approximate: Boolean = false,
    val pending: Long = 0,
    val suggestLinking: Boolean = false,
    /** Asking which group a quick action is for. */
    val picking: QuickAction? = null,
    /** A newer release to install, unless the user put it off. */
    val update: LatestRelease? = null,
    /**
     * An update from the store (Google Play installs): offered while [StoreUpdate.Available] and not put
     * off, then [StoreUpdate.Downloaded] until the restart. Hidden while it downloads.
     */
    val storeUpdate: StoreUpdate? = null,
) : MVIBaseState

/** The group I asked to go back into, while it runs ([busy]) and if it could not ([result]). */
data class RejoinState(val group: Group, val busy: Boolean = false, val result: RejoinResult? = null)

sealed interface HomeIntent : MVIBaseIntent {
    data class AskRejoin(val group: Group) : HomeIntent
    data object ConfirmRejoin : HomeIntent
    data object DismissRejoin : HomeIntent
    data object RejoinedOpened : HomeIntent
    data class Pick(val action: QuickAction) : HomeIntent
    data object DismissPicker : HomeIntent
    data object DismissNudge : HomeIntent
    data object DismissUpdate : HomeIntent
    data object StartStoreUpdate : HomeIntent
    data object CompleteStoreUpdate : HomeIntent
}

/**
 * Adds the repeating expenses that are due in every group, and shows my groups whether my account is
 * linked to Google.
 */
class HomeChores(val generateRecurring: GenerateRecurring, val syncMyLinked: SyncMyLinked)

/**
 * Finding a newer release and remembering which one the user put off. [check] covers APK installs (the
 * download page); [store] covers store installs (Google Play) and is [NoStoreUpdates] everywhere else.
 */
class UpdateOffer(
    val check: CheckForUpdate,
    val settings: UpdateSettings,
    val store: StoreUpdates = NoStoreUpdates,
)

@OptIn(ExperimentalCoroutinesApi::class) // mapLatest
class HomeViewModel(
    observeHome: ObserveHome,
    observeAccount: ObserveAccount,
    observePending: ObservePendingChanges,
    private val convertTotal: ConvertTotal,
    private val nudge: LinkNudgeSettings,
    /** Work for every group when Inicio opens (null in tests that do not need it). */
    chores: HomeChores? = null,
    /** Looks for a newer release once per launch (null in tests that do not need it). */
    private val updates: UpdateOffer? = null,
    /** Going back into a group I left (null in tests that do not need it). */
    private val rejoinGroup: RejoinGroup? = null,
) : MVIBaseViewModel<HomeState, HomeIntent>() {

    override fun initState() = HomeState()

    init {
        chores?.let {
            execute { it.generateRecurring.all() }
            execute { it.syncMyLinked() }
        }
        updates?.let { offer ->
            viewModelScope.launch {
                val latest = offer.check() ?: return@launch
                offer.settings.dismissedUpdate.collect { dismissed ->
                    updateState { it.copy(update = latest.takeIf { latest.versionCode != dismissed }) }
                }
            }
            viewModelScope.launch {
                combine(offer.store.update, offer.settings.dismissedUpdate, ::shownStoreUpdate)
                    .collect { shown -> updateState { it.copy(storeUpdate = shown) } }
            }
        }
        viewModelScope.launch {
            combine(
                observeHome(),
                observeAccount(),
                observePending(),
                nudge.linkNudgeDismissed,
            ) { home, account, pending, dismissed ->
                HomeState(
                    loaded = true,
                    groups = home.groups,
                    archived = home.archived,
                    left = home.left,
                    currency = home.preferredCurrency,
                    total = home.total,
                    otherCurrencies = home.otherCurrencies,
                    pending = pending,
                    suggestLinking = ShouldSuggestLinking(account.session, home.groups.size, dismissed),
                )
            }.mapLatest { fresh ->
                // Other currencies at the latest rate (cached; may hit the network once).
                val converted = convertTotal(HomeData(fresh.groups, fresh.currency, fresh.total, fresh.otherCurrencies))
                fresh.copy(
                    total = converted.total,
                    otherCurrencies = converted.unconverted,
                    approximate = converted.approximate
                )
            }.collect { fresh ->
                updateState {
                    fresh.copy(
                        picking = it.picking,
                        update = it.update,
                        storeUpdate = it.storeUpdate,
                        rejoin = it.rejoin,
                        rejoined = it.rejoined,
                    )
                }
            }
        }
    }

    override fun intentHandler(intent: HomeIntent): Job = execute {
        when (intent) {
            is HomeIntent.Pick -> updateState { it.copy(picking = intent.action) }
            HomeIntent.DismissPicker -> updateState { it.copy(picking = null) }
            HomeIntent.DismissNudge -> nudge.dismissLinkNudge()
            HomeIntent.DismissUpdate -> {
                val code = currentState.update?.versionCode ?: currentState.storeUpdate?.versionCode
                code?.let { updates?.settings?.dismissUpdate(it) }
            }
            HomeIntent.StartStoreUpdate -> updates?.store?.start()
            HomeIntent.CompleteStoreUpdate -> updates?.store?.complete()
            is HomeIntent.AskRejoin -> updateState { it.copy(rejoin = RejoinState(intent.group)) }
            HomeIntent.ConfirmRejoin -> rejoin()
            HomeIntent.DismissRejoin -> updateState { it.copy(rejoin = null) }
            HomeIntent.RejoinedOpened -> updateState { it.copy(rejoined = null) }
        }
    }

    private suspend fun rejoin() {
        val asked = currentState.rejoin ?: return
        val run = rejoinGroup ?: return
        updateState { it.copy(rejoin = asked.copy(busy = true, result = null)) }
        when (val result = run(asked.group.id)) {
            RejoinResult.JOINED -> updateState { it.copy(rejoin = null, rejoined = asked.group.id) }
            else -> updateState { it.copy(rejoin = asked.copy(busy = false, result = result)) }
        }
    }
}

/** The store update Home shows: an available one unless put off, a downloaded one always (it needs a restart). */
internal fun shownStoreUpdate(update: StoreUpdate?, dismissed: Long?): StoreUpdate? = when (update) {
    is StoreUpdate.Available -> update.takeIf { it.versionCode != dismissed }
    is StoreUpdate.Downloaded -> update
    is StoreUpdate.Downloading, null -> null
}
