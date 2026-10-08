package es.sebas1705.owlsplit.feature.expense

import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.ui.components.KeypadKey
import es.sebas1705.owlsplit.data.repository.fx.FxQuote
import es.sebas1705.owlsplit.data.repository.fx.FxRates
import es.sebas1705.owlsplit.domain.usecases.fx.GetFxRate
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.domain.ledger.SplitIssue
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.expenses.DeleteExpense
import es.sebas1705.owlsplit.domain.usecases.expenses.DraftProblem
import es.sebas1705.owlsplit.domain.usecases.expenses.SaveExpense
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseEditorTest {
    private val rule = CoroutineTestRule()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private val ledger = InMemoryLedger()
    private val detail = ObserveGroupDetail(
        ledger.groups,
        ledger.members,
        ledger.expenses,
        ledger.transfers,
        ObserveGroupBalances(ledger.groups, ledger.members, ledger.expenses, ledger.transfers),
        FakeAccounts(),
    )

    private fun AmountInput.type(vararg keys: KeypadKey) = keys.fold(this) { input, key -> input.apply(key) }
    private fun d(n: Int) = KeypadKey.Digit(n)

    @Test
    fun theKeypadBuildsMinorUnitsAndRespectsTheCurrencyDecimals() {
        val eur = AmountInput().type(d(0), d(1), d(2), KeypadKey.Decimal, d(5))
        assertEquals("12,5", eur.text(','))
        assertEquals(1_250, eur.minorUnits)
        assertEquals(1_257, eur.type(d(7), d(9)).minorUnits) // third decimal ignored
        assertEquals("12", eur.type(KeypadKey.Backspace, KeypadKey.Backspace).text(','))

        val yen = AmountInput(decimals = 0).type(d(5), KeypadKey.Decimal, d(0))
        assertEquals(50, yen.minorUnits)
        assertEquals("0,5", AmountInput.of(50, 2).text(','))
        assertEquals("120", AmountInput.of(12_000, 2).text(','))
        assertEquals(1_250, parseDecimal("12.5", 2))
        assertNull(parseDecimal("1,234", 2))
        assertEquals("33,33", formatDecimal(3_333, 2, ','))
    }

    private suspend fun group(): Triple<String, String, String> {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val luis = ledger.members.add(g, MemberInput("Luis"))
        val ana = ledger.memberList.value.first { it.userUid == "uid-ana" }.id
        return Triple(g, ana, luis)
    }

    private val rates = object : FxRates {
        override suspend fun latest(from: String, to: String) =
            if (from == "USD") FxQuote(FxRate.parse("0.9"), "2026-10-07") else null
    }

    private fun editor(g: String, expenseId: String? = null) = ExpenseEditorViewModel(
        g,
        expenseId,
        detail,
        SaveExpense(ledger.expenses),
        DeleteExpense(ledger.expenses),
        GetFxRate(rates),
    )

    @Test
    fun aNewEqualExpensePaidByMe() = runTest {
        val (g, ana, luis) = group()
        val vm = editor(g)
        assertTrue(vm.uiState.value.keypadOpen)
        assertEquals(ana, vm.uiState.value.draft.payerId)
        listOf(d(3), d(0)).forEach { vm.onEvent(ExpenseEditorIntent.Key(it)) }
        vm.onEvent(ExpenseEditorIntent.KeypadOpen(false))
        assertEquals(DraftProblem.MissingTitle, vm.uiState.value.check.problem)
        assertEquals(mapOf(ana to 1_500L, luis to 1_500L), vm.uiState.value.check.shares)

        vm.onEvent(ExpenseEditorIntent.TitleChanged("Cena"))
        vm.onEvent(ExpenseEditorIntent.CategoryChanged(ExpenseCategory.FOOD))
        vm.onEvent(ExpenseEditorIntent.Save)
        assertTrue(vm.uiState.value.done)
        val saved = ledger.expenseList.value.single()
        assertEquals(mapOf(ana to 3_000L), saved.payers)
        assertEquals(ExpenseCategory.FOOD, saved.category)
    }

    @Test
    fun exactAmountsFollowTheTotalUntilEditedAndSayWhatIsMissing() = runTest {
        val (g, ana, luis) = group()
        val vm = editor(g)
        vm.onEvent(ExpenseEditorIntent.TypeChanged(SplitType.EXACT))
        listOf(d(1), d(0)).forEach { vm.onEvent(ExpenseEditorIntent.Key(it)) }
        assertEquals(mapOf(ana to 500L, luis to 500L), vm.uiState.value.draft.values)
        assertEquals("5", vm.uiState.value.valueTexts[ana])

        vm.onEvent(ExpenseEditorIntent.ValueChanged(ana, "7,5"))
        assertEquals(
            DraftProblem.Split(SplitIssue.ExactSumMismatch(-250)),
            vm.uiState.value.check.problem,
        )
        vm.onEvent(ExpenseEditorIntent.Key(d(0))) // 100,00 now: the edited values stay
        assertEquals(750L, vm.uiState.value.draft.values[ana])
        vm.onEvent(ExpenseEditorIntent.ValueChanged(ana, "abc")) // not a number: ignored
        assertEquals("7,5", vm.uiState.value.valueTexts[ana])
    }

    @Test
    fun excludingEveryoneCannotBeSaved() = runTest {
        val (g, ana, luis) = group()
        val vm = editor(g)
        vm.onEvent(ExpenseEditorIntent.Key(d(9)))
        vm.onEvent(ExpenseEditorIntent.TitleChanged("Café"))
        vm.onEvent(ExpenseEditorIntent.Toggle(ana))
        assertEquals(mapOf(luis to 900L), vm.uiState.value.check.shares)
        vm.onEvent(ExpenseEditorIntent.Toggle(luis))
        assertEquals(DraftProblem.Split(SplitIssue.NoBeneficiary), vm.uiState.value.check.problem)
        vm.onEvent(ExpenseEditorIntent.Save)
        assertFalse(vm.uiState.value.done)
    }

    @Test
    fun editingLoadsTheExpenseAndDeletingCloses() = runTest {
        val (g, _, _) = group()
        val first = editor(g)
        listOf(d(4), d(2)).forEach { first.onEvent(ExpenseEditorIntent.Key(it)) }
        first.onEvent(ExpenseEditorIntent.TitleChanged("Súper"))
        first.onEvent(ExpenseEditorIntent.Save)
        val id = ledger.expenseList.value.single().id

        val vm = editor(g, id)
        assertTrue(vm.uiState.value.editing)
        assertFalse(vm.uiState.value.keypadOpen)
        assertEquals("42", vm.uiState.value.amount.text(','))
        vm.onEvent(ExpenseEditorIntent.AskDelete)
        vm.onEvent(ExpenseEditorIntent.ConfirmDelete)
        assertTrue(vm.uiState.value.done)
        assertTrue(ledger.expenseList.value.single().meta.deleted)
    }

    @Test
    fun aForeignCurrencyFreezesTheRateWhichCanBeOverwritten() = runTest {
        val (g, _, _) = group()
        val vm = editor(g)
        listOf(d(1), d(0)).forEach { vm.onEvent(ExpenseEditorIntent.Key(it)) }
        vm.onEvent(ExpenseEditorIntent.TitleChanged("Taxi"))
        vm.onEvent(ExpenseEditorIntent.CurrencyChanged("USD"))
        assertEquals("0,9", vm.uiState.value.rate.text)
        assertEquals("2026-10-07", vm.uiState.value.rate.day)
        assertEquals(900L, vm.uiState.value.baseEquivalent)

        vm.onEvent(ExpenseEditorIntent.RateChanged("0,95"))
        assertEquals(950L, vm.uiState.value.baseEquivalent)
        vm.onEvent(ExpenseEditorIntent.RateChanged("abc"))
        assertEquals(DraftProblem.MissingRate, vm.uiState.value.check.problem)

        vm.onEvent(ExpenseEditorIntent.CurrencyChanged("GBP")) // no rate: typed by hand
        assertNull(vm.uiState.value.rate.day)
        vm.onEvent(ExpenseEditorIntent.RateChanged("1,17"))
        vm.onEvent(ExpenseEditorIntent.Save)
        val saved = ledger.expenseList.value.single()
        assertEquals("GBP", saved.currency)
        assertEquals(1_170_000_000L, saved.fxRateScaled)
    }

    @Test
    fun severalPayersMustAddUpToTheTotal() = runTest {
        val (g, ana, luis) = group()
        val vm = editor(g)
        listOf(d(5), d(0)).forEach { vm.onEvent(ExpenseEditorIntent.Key(it)) }
        vm.onEvent(ExpenseEditorIntent.TitleChanged("Súper"))
        vm.onEvent(ExpenseEditorIntent.MultiplePayers(true))
        assertEquals(mapOf(ana to 5_000L), vm.uiState.value.draft.payers)
        vm.onEvent(ExpenseEditorIntent.PayerAmountChanged(ana, "30"))
        vm.onEvent(ExpenseEditorIntent.PayerAmountChanged(luis, "15"))
        assertEquals(DraftProblem.PayersMismatch(500), vm.uiState.value.check.problem)
        vm.onEvent(ExpenseEditorIntent.PayerAmountChanged(luis, "20"))
        vm.onEvent(ExpenseEditorIntent.DateChanged(LocalDate(2026, 7, 12)))
        vm.onEvent(ExpenseEditorIntent.NoteChanged("Para la semana"))
        vm.onEvent(ExpenseEditorIntent.Save)
        val saved = ledger.expenseList.value.single()
        assertEquals(mapOf(ana to 3_000L, luis to 2_000L), saved.payers)
        assertEquals(LocalDate(2026, 7, 12), saved.date)
        assertEquals("Para la semana", saved.note)
    }
}
