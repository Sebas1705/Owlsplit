package es.sebas1705.owlsplit.di

import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.expenses.DeleteExpense
import es.sebas1705.owlsplit.domain.usecases.expenses.SaveExpense
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import es.sebas1705.owlsplit.domain.usecases.groups.MemberActions
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import es.sebas1705.owlsplit.domain.usecases.groups.SettleUp
import es.sebas1705.owlsplit.feature.expense.ExpenseEditorViewModel
import es.sebas1705.owlsplit.feature.groups.detail.GroupTab
import es.sebas1705.owlsplit.feature.groups.detail.GroupViewModel
import es.sebas1705.owlsplit.feature.groups.editor.GroupEditorViewModel
import es.sebas1705.owlsplit.feature.groups.members.MembersViewModel
import es.sebas1705.owlsplit.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Groups, members, expenses and balances: use cases and the screens of Inicio and a group. */
val groupsModule = module {
    factory { ObserveGroupBalances(get(), get(), get(), get()) }
    factory { ObserveHome(get(), get(), get(), get(), get(), get(), get()) }
    factory { ObserveGroupDetail(get(), get(), get(), get(), get(), get()) }
    factory { ObservePendingChanges(get()) }
    factory { GroupActions(get(), get()) }
    factory { MemberActions(get()) }
    factory { SettleUp(get()) }
    factory { SaveExpense(get()) }
    factory { DeleteExpense(get()) }

    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { (groupId: String?) -> GroupEditorViewModel(groupId, get(), get(), get()) }
    viewModel { (groupId: String, tab: GroupTab) -> GroupViewModel(groupId, tab, get(), get(), get()) }
    viewModel { (groupId: String) -> MembersViewModel(groupId, get(), get()) }
    viewModel { (groupId: String, expenseId: String?) ->
        ExpenseEditorViewModel(groupId, expenseId, get(), get(), get())
    }
}
