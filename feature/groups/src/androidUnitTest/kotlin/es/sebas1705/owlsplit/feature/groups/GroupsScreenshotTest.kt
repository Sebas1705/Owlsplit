package es.sebas1705.owlsplit.feature.groups

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
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
    private val navigation = GroupNavigation({}, {}, {}, {}, {}, {}, {}, {})

    private fun group(name: String, tab: GroupTab, dark: Boolean = false) {
        compose.setContent {
            OwlsplitTheme(darkTheme = dark) { GroupScreen(state.copy(tab = tab), {}, navigation, today = today) }
        }
        compose.onRoot().captureRoboImage("screenshots/group_$name.png")
    }

    @Test
    fun expenses() = group("expenses", GroupTab.EXPENSES)

    @Test
    fun expensesDark() = group("expenses_dark", GroupTab.EXPENSES, dark = true)

    @Test
    fun balances() = group("balances", GroupTab.BALANCES)

    @Test
    fun settle() = group("settle", GroupTab.SETTLE)

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
        compose.onRoot().captureRoboImage("screenshots/group_editor.png")
    }

    @Test
    fun members() {
        compose.setContent { OwlsplitTheme { MembersScreen(MembersState(true, members, "ana"), {}, {}) } }
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
        compose.onRoot().captureRoboImage("screenshots/group_record_payment.png")
    }
}
