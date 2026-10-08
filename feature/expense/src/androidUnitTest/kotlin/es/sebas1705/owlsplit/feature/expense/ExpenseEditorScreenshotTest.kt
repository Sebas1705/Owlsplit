package es.sebas1705.owlsplit.feature.expense

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDraft
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDrafts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1000dp-xxhdpi")
class ExpenseEditorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val meta = SyncMeta(1, 1, 1, "uid", false, SyncState.SYNCED)
    private val members = listOf("Ana", "Luis", "Marta", "Pablo").mapIndexed { i, name ->
        Member(name.lowercase(), "g1", name, null, 1, if (i == 0) "uid" else null, true, i.toLong(), meta)
    }

    private fun state(draft: ExpenseDraft, keypad: Boolean = false, texts: Map<String, String> = emptyMap()) = ExpenseEditorState(
        loaded = true,
        currency = "EUR",
        myMemberId = "ana",
        members = members,
        amount = AmountInput.of(draft.amountMinor, 2),
        keypadOpen = keypad,
        draft = draft,
        check = ExpenseDrafts.check(draft, members),
        valueTexts = texts,
    )

    private fun capture(name: String, state: ExpenseEditorState, dark: Boolean = false) {
        compose.setContent { OwlsplitTheme(darkTheme = dark) { ExpenseEditorScreen(state, {}, {}) } }
        compose.onRoot().captureRoboImage("screenshots/expense_$name.png")
    }

    private val equal = ExpenseDraft(
        amountMinor = 4_860,
        title = "Cena en Es Vedrà",
        category = ExpenseCategory.FOOD,
        payerId = "ana",
        values = mapOf("ana" to 1L, "luis" to 1L, "marta" to 1L),
    )

    @Test
    fun keypad() = capture("keypad", state(ExpenseDraft(amountMinor = 1_250, payerId = "ana"), keypad = true))

    @Test
    fun equalSplit() = capture("equal", state(equal))

    @Test
    fun equalSplitDark() = capture("equal_dark", state(equal), dark = true)

    @Test
    fun exactSplitMissing() = capture(
        "exact_missing",
        state(
            equal.copy(splitType = SplitType.EXACT, values = mapOf("ana" to 2_000L, "luis" to 1_540L, "marta" to 0L)),
            texts = mapOf("ana" to "20", "luis" to "15,4", "marta" to "0"),
        ),
    )
}
