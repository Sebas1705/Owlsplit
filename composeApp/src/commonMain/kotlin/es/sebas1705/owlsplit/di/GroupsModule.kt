package es.sebas1705.owlsplit.di

import es.sebas1705.owlsplit.config.AppConfig
import es.sebas1705.owlsplit.core.network.buildHttpClient
import es.sebas1705.owlsplit.config.updatableVersionCode
import es.sebas1705.owlsplit.data.fx.FrankfurterRates
import es.sebas1705.owlsplit.data.fx.HostingReleaseFeed
import es.sebas1705.owlsplit.data.repository.update.ReleaseFeed
import es.sebas1705.owlsplit.domain.usecases.update.CheckForUpdate
import es.sebas1705.owlsplit.data.repository.fx.FxRates
import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.expenses.DeleteExpense
import es.sebas1705.owlsplit.domain.usecases.expenses.SaveExpense
import es.sebas1705.owlsplit.domain.usecases.expenses.GenerateRecurring
import es.sebas1705.owlsplit.domain.usecases.groups.DeleteTransfer
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import es.sebas1705.owlsplit.domain.usecases.importer.ImportGroup
import es.sebas1705.owlsplit.domain.usecases.groups.MemberActions
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import es.sebas1705.owlsplit.domain.usecases.fx.ConvertTotal
import es.sebas1705.owlsplit.domain.usecases.stats.ObserveMySpending
import es.sebas1705.owlsplit.domain.usecases.fx.GetFxRate
import es.sebas1705.owlsplit.domain.usecases.groups.RecordTransfer
import es.sebas1705.owlsplit.domain.usecases.history.HistoryDescriber
import es.sebas1705.owlsplit.domain.usecases.history.ObserveActivity
import es.sebas1705.owlsplit.domain.usecases.history.ObserveHistory
import es.sebas1705.owlsplit.domain.usecases.history.ResolveConflict
import es.sebas1705.owlsplit.domain.usecases.history.RestoreExpense
import es.sebas1705.owlsplit.domain.usecases.groups.SettleUp
import es.sebas1705.owlsplit.feature.expense.ExpenseEditorViewModel
import es.sebas1705.owlsplit.feature.groups.detail.GroupTab
import es.sebas1705.owlsplit.feature.groups.detail.GroupViewModel
import es.sebas1705.owlsplit.feature.groups.editor.GroupEditorViewModel
import es.sebas1705.owlsplit.feature.groups.members.MembersViewModel
import es.sebas1705.owlsplit.feature.groups.importer.ImportViewModel
import es.sebas1705.owlsplit.feature.groups.stats.MySpendingViewModel
import es.sebas1705.owlsplit.feature.groups.stats.StatsViewModel
import es.sebas1705.owlsplit.feature.groups.transfer.TransferViewModel
import es.sebas1705.owlsplit.feature.history.HistoryViewModel
import es.sebas1705.owlsplit.feature.home.HomeChores
import es.sebas1705.owlsplit.feature.home.HomeViewModel
import es.sebas1705.owlsplit.feature.home.UpdateOffer
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Groups, members, expenses and balances: use cases and the screens of Inicio and a group. */
val groupsModule = module {
    factory { ObserveGroupBalances(get(), get(), get(), get()) }
    factory { ObserveHome(get(), get(), get(), get(), get(), get(), get()) }
    factory { ObserveGroupDetail(get(), get(), get(), get(), get(), get(), get()) }
    factory { ObservePendingChanges(get()) }
    factory { GroupActions(get(), get(), get()) }
    factory { ImportGroup(get(), get(), get(), get(), get()) }
    factory { MemberActions(get()) }
    factory { SettleUp(get()) }
    factory { DeleteTransfer(get()) }
    factory { SaveExpense(get()) }
    factory { GenerateRecurring(get(), get(), get()) }
    factory { DeleteExpense(get()) }
    factory { RecordTransfer(get()) }
    single<FxRates> {
        FrankfurterRates(buildHttpClient(), AppConfig.FX_API_BASE_URL, get(), AppConfig.FX_CACHE_SECONDS)
    }
    factory { GetFxRate(get()) }
    single<ReleaseFeed> { HostingReleaseFeed(buildHttpClient(), AppConfig.LATEST_RELEASE_URL) }
    factory { CheckForUpdate(get(), updatableVersionCode) }
    factory { ConvertTotal(get()) }
    factory { ObserveMySpending(get(), get(), get(), get(), get(), get()) }
    factory { HistoryDescriber(get(), get(), get(), get(), get()) }
    factory { ObserveHistory(get(), get(), get(), get()) }
    factory { ObserveActivity(get(), get(), get(), get(), get()) }
    factory { RestoreExpense(get(), get()) }
    factory { ResolveConflict(get()) }

    factory { UpdateOffer(get(), get(), get()) }
    factory { HomeChores(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (groupId: String?) -> GroupEditorViewModel(groupId, get(), get(), get()) }
    viewModel { (groupId: String, tab: GroupTab) -> GroupViewModel(groupId, tab, get(), get(), get(), get(), get()) }
    viewModel { (groupId: String) -> MembersViewModel(groupId, get(), get()) }
    viewModel { (groupId: String) -> TransferViewModel(groupId, get(), get()) }
    viewModel { (groupId: String) -> StatsViewModel(groupId, get()) }
    viewModel { MySpendingViewModel(get()) }
    viewModel { ImportViewModel(get(), get(), get()) }
    viewModel { (groupId: String?) -> HistoryViewModel(groupId, get(), get(), get(), get()) }
    viewModel { (groupId: String, expenseId: String?, separator: Char) ->
        ExpenseEditorViewModel(groupId, expenseId, get(), get(), get(), get(), separator)
    }
}
