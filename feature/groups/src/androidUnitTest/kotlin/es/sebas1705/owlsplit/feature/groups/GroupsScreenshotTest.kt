package es.sebas1705.owlsplit.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.domain.usecases.balances.balanceExplanationOf
import es.sebas1705.owlsplit.feature.groups.detail.BalanceExplanationContent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.testing.ui.assertAccessible
import es.sebas1705.owlsplit.domain.usecases.stats.MySpending
import es.sebas1705.owlsplit.domain.usecases.stats.SpendingGroup
import es.sebas1705.owlsplit.domain.usecases.stats.SpendingPeriod
import es.sebas1705.owlsplit.domain.usecases.stats.SpendingSource
import es.sebas1705.owlsplit.domain.usecases.stats.GroupStats
import es.sebas1705.owlsplit.feature.groups.stats.MySpendingScreen
import es.sebas1705.owlsplit.feature.groups.stats.StatsScreen
import es.sebas1705.owlsplit.feature.groups.stats.StatsState
import es.sebas1705.owlsplit.feature.groups.stats.MySpendingState
import es.sebas1705.owlsplit.core.ui.format.LocalMoneyFormat
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.usecases.balances.GroupBalances
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.feature.groups.detail.GroupNavigation
import es.sebas1705.owlsplit.feature.groups.detail.GroupScreen
import es.sebas1705.owlsplit.feature.groups.detail.GroupState
import es.sebas1705.owlsplit.feature.groups.detail.GroupTab
import es.sebas1705.owlsplit.feature.groups.editor.GroupEditorScreen
import es.sebas1705.owlsplit.feature.groups.editor.GroupEditorState
import es.sebas1705.owlsplit.feature.groups.members.MembersScreen
import es.sebas1705.owlsplit.feature.groups.members.MembersState
import es.sebas1705.owlsplit.feature.groups.transfer.TransferScreen
import es.sebas1705.owlsplit.feature.groups.transfer.TransferState
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp-xxhdpi")
class GroupsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val meta = SyncMeta(1, 1, 1, "uid-ana", false, SyncState.SYNCED)
    private val today = LocalDate(2026, 10, 8)
    private val group = Group(
        "g1", "Ibiza 2026", "plane", "BLUE", "EUR", null, null, "CODE",
        listOf(
            "uid-ana"
        ),
        emptyList(), "uid-ana", 0, meta,
    )
    private val members = listOf(
        Member("ana", "g1", "Ana", null, 1, "uid-ana", true, 0, meta),
        Member("luis", "g1", "Luis", null, 1, null, true, 1, meta),
        Member("marta", "g1", "Marta", null, 2, null, true, 2, meta),
        Member("pablo", "g1", "Pablo", null, 1, null, false, 3, meta),
    )

    private fun expense(id: String, title: String, category: ExpenseCategory, amount: Long, payer: String, date: LocalDate) =
        Expense(
            id, "g1", title, category, null, date, "EUR", amount, 1_000_000_000, mapOf(payer to amount),
            SplitType.EQUAL, mapOf("ana" to 1L, "luis" to 1L, "marta" to 1L), null, null, "uid-ana", 0, meta,
        )

    private val detail = GroupDetail(
        group = group,
        members = members,
        expenses = listOf(
            expense("e1", "Cena en Es Vedrà", ExpenseCategory.FOOD, 9_360, "ana", today),
            expense("e2", "Taxi aeropuerto", ExpenseCategory.TRANSPORT, 3_600, "luis", today),
            expense("e3", "Apartamento", ExpenseCategory.ACCOMMODATION, 42_000, "marta", LocalDate(2026, 10, 6)),
        ),
        transfers = listOf(
            Transfer("t1", "g1", "luis", "ana", 1_000, "EUR", 1_000_000_000, today, null, "uid", 0, meta)
        ),
        balances = GroupBalances(
            "EUR",
            mapOf("ana" to -7_000L, "luis" to -10_400L, "marta" to 17_400L, "pablo" to 0L),
            listOf(SuggestedPayment("luis", "marta", 10_400), SuggestedPayment("ana", "marta", 7_000)),
            emptyList(),
        ),
        myMemberId = "ana",
        myUid = "uid-ana",
    )
    private val state =
        GroupState(loaded = true, detail = detail, myShares = mapOf("e1" to 3_120L, "e2" to 1_200L, "e3" to 14_000L))
    private val navigation = GroupNavigation({}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun group(
        name: String,
        tab: GroupTab,
        dark: Boolean = false,
        format: MoneyFormat = MoneyFormat.Spanish,
        shown: GroupState = state,
    ) {
        compose.setContent {
            OwlsplitTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalMoneyFormat provides format) {
                    GroupScreen(shown.copy(tab = tab), {}, navigation, { true }, today = today)
                }
            }
        }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/group_$name.png")
    }

    /** Luis owes me (a bell to remind him) and also owes Marta (nothing to remind there). */
    private val owedToMe = state.copy(
        detail = detail.copy(
            balances = GroupBalances(
                "EUR",
                mapOf("ana" to 7_000L, "luis" to -10_400L, "marta" to 3_400L, "pablo" to 0L),
                listOf(SuggestedPayment("luis", "ana", 7_000), SuggestedPayment("luis", "marta", 3_400)),
                emptyList(),
            ),
        ),
    )

    @Test
    fun settleRemind() = group("settle_remind", GroupTab.SETTLE, shown = owedToMe)

    @Test
    fun settleRemindDark() = group("settle_remind_dark", GroupTab.SETTLE, dark = true, shown = owedToMe)

    @Test
    fun settleRemindLargeText() = largeText { group("settle_remind_large_text", GroupTab.SETTLE, shown = owedToMe) }

    @Test
    fun expenses() = group("expenses", GroupTab.EXPENSES)

    @Test
    fun expensesDark() = group("expenses_dark", GroupTab.EXPENSES, dark = true)

    @Test
    fun balances() = group("balances", GroupTab.BALANCES)

    // Text at 200 % (spec, accessibility): amounts shrink to fit, nothing is cut.
    @Test
    fun expensesLargeText() = largeText { group("expenses_large_text", GroupTab.EXPENSES) }

    @Test
    fun balancesLargeText() = largeText { group("balances_large_text", GroupTab.BALANCES) }

    @Test
    fun settleLargeText() = largeText { group("settle_large_text", GroupTab.SETTLE) }

    private fun largeText(block: () -> Unit) {
        RuntimeEnvironment.setFontScale(2f)
        block()
    }

    @Test
    fun settle() = group("settle", GroupTab.SETTLE)

    /** The real group's data with a dinner in dollars and a payment, explained by the domain itself. */
    private val explained = detail.copy(
        expenses = detail.expenses + Expense(
            "e4", "g1", "Cena en Nueva York", ExpenseCategory.FOOD, null, today, "USD", 6_000, 923_456_789,
            mapOf("luis" to 6_000L), SplitType.EQUAL, mapOf("ana" to 1L, "luis" to 1L, "marta" to 1L), null, null,
            "uid-ana", 0, meta,
        ),
    )

    private fun explanation(
        name: String,
        dark: Boolean = false,
        format: MoneyFormat = MoneyFormat.Spanish,
        shown: GroupDetail = explained,
    ) {
        val explanation = balanceExplanationOf("EUR", shown.members, shown.expenses, shown.transfers)
        compose.setContent {
            OwlsplitTheme(darkTheme = dark) {
                Box(Modifier.background(Owlsplit.colors.background)) {
                    BalanceExplanationContent(explanation, shown, Currency.of("EUR"), format, onClose = {})
                }
            }
        }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/group_explanation_$name.png")
    }

    @Test
    fun explanation() = explanation("light")

    @Test
    fun explanationDark() = explanation("dark", dark = true)

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun explanationEnglish() = explanation("en", format = MoneyFormat.English)

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun explanationEnglishDark() = explanation("en_dark", dark = true, format = MoneyFormat.English)

    /** Screen readers hear each card as one sentence; mine in the second person, the others in the third. */
    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun explanationCardsAreSpokenAsSentences() {
        explanation("en", format = MoneyFormat.English)
        compose.onNodeWithContentDescription(
            "You: you paid €93.60, minus your share, €201.67, minus payments you received, €10.00. Balance: −€118.07.",
        ).assertExists()
        compose.onNodeWithContentDescription(
            "Luis: paid €91.41, minus their share, €201.68, plus payments sent, €10.00. Balance: −€100.27.",
        ).assertExists()
    }

    @Test
    fun explanationLargeText() = largeText { explanation("large_text") }

    @Test
    fun explanationSettled() = explanation(
        "settled",
        shown = detail.copy(expenses = emptyList(), transfers = emptyList())
    )

    // English versions for the Google Play listing (scripts/play-screenshots.mjs).
    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun expensesEnglish() = group("expenses_en", GroupTab.EXPENSES, format = MoneyFormat.English)

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun settleEnglish() = group("settle_en", GroupTab.SETTLE, format = MoneyFormat.English)

    @Test
    fun editor() {
        compose.setContent {
            OwlsplitTheme {
                GroupEditorScreen(
                    GroupEditorState(
                        loaded = true,
                        name = "Ibiza 2026",
                        accent = GroupAccent.CYAN,
                        icon = "sun"
                    ),
                    {
                    },
                    {}
                )
            }
        }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/group_editor.png")
    }

    @Test
    fun members() {
        compose.setContent { OwlsplitTheme { MembersScreen(MembersState(true, members, "ana"), {}, {}) } }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/group_members.png")
    }

    @Test
    fun editorWithDates() {
        compose.setContent {
            OwlsplitTheme {
                GroupEditorScreen(
                    GroupEditorState(
                        loaded = true,
                        name = "Ibiza 2026",
                        startDate = LocalDate(2026, 7, 20),
                        endDate = LocalDate(2026, 7, 27),
                    ),
                    {},
                    {},
                )
            }
        }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/group_editor_dates.png")
    }

    @Test
    fun recordPayment() {
        compose.setContent {
            OwlsplitTheme {
                TransferScreen(
                    TransferState(true, "EUR", members.take(3), "ana", "luis", "ana", "23,5", 2_350, "Bizum"),
                    {},
                    {},
                )
            }
        }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/group_record_payment.png")
    }

    /** Ibiza plus a flat in another month: my part across both, by group, category and month. */
    private fun spending(period: SpendingPeriod = SpendingPeriod.ALL): MySpendingState {
        val flat = group.copy(id = "g2", name = "Piso Lavapiés", icon = "home", accent = "ORANGE")
        val rent = expense("r1", "Alquiler", ExpenseCategory.HOME, 120_000, "ana", LocalDate(2026, 9, 1))
            .copy(groupId = "g2")
        val source = SpendingSource(
            listOf(
                SpendingGroup(group, members, detail.expenses, "ana"),
                SpendingGroup(flat, members, listOf(rent), "ana"),
            ),
            "EUR",
            emptyMap(),
        )
        return MySpendingState(loaded = true, period = period, spending = MySpending.of(source, period, today))
    }

    private fun mySpending(name: String, shown: MySpendingState, dark: Boolean = false) {
        compose.setContent {
            OwlsplitTheme(darkTheme = dark) {
                MySpendingScreen(shown, {}, {})
            }
        }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/my_spending_$name.png")
    }

    @Test
    fun mySpending() = mySpending("all", spending())

    @Test
    fun mySpendingDark() = mySpending("all_dark", spending(), dark = true)

    @Test
    fun mySpendingLargeText() = largeText { mySpending("all_large_text", spending()) }

    @Test
    fun mySpendingEmptyMonth() = mySpending(
        "empty",
        spending(SpendingPeriod.MONTH).let {
            it.copy(spending = it.spending!!.copy(expenseCount = 0))
        }
    )

    private fun summary(name: String, dark: Boolean = false) {
        val stats = GroupStats.of("EUR", members, detail.expenses + septemberDinner)
        compose.setContent {
            OwlsplitTheme(darkTheme = dark) {
                StatsScreen(
                    StatsState(
                        loaded = true,
                        detail = detail.copy(expenses = detail.expenses + septemberDinner),
                        stats = stats
                    ),
                    {},
                    { _, _, _ -> true },
                )
            }
        }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/group_summary_$name.png")
    }

    private val septemberDinner =
        expense("e4", "Cena de bienvenida", ExpenseCategory.FOOD, 12_000, "luis", LocalDate(2026, 9, 28))

    @Test
    fun summary() = summary("light")

    @Test
    fun summaryDark() = summary("dark", dark = true)
}
