package es.sebas1705.owlsplit.feature.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.domain.usecases.history.HistoryItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp-xxhdpi")
class HistoryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 10, 8)
    private val noon = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000

    private fun item(
        action: ChangeAction,
        subject: String,
        byMe: Boolean = false,
        author: String? = "Luis",
        diff: List<FieldChange> = emptyList(),
        at: Long = noon,
    ) = HistoryItem(
        ChangeLogEntry(subject + action, "g1", EntityType.EXPENSE, "e1", action, "uid", diff, at),
        "Ibiza 2026",
        author,
        byMe,
        subject,
        "EUR",
        canRestore = false,
        conflictId = null,
    )

    private val items = listOf(
        item(ChangeAction.CONFLICT, "Cena").copy(conflictId = 1),
        item(
            ChangeAction.UPDATED,
            "Cena larga",
            diff = listOf(
                FieldChange("amountMinor", "1240", "1440"),
                FieldChange("title", "Cena", "Cena larga"),
                FieldChange("splitValues", "a=1", "a=2")
            ),
        ),
        item(ChangeAction.DELETED, "Taxi", byMe = true).copy(canRestore = true),
        item(ChangeAction.CREATED, "Cena", author = "Marta", at = noon - 86_400_000),
        item(ChangeAction.JOINED, "Marta", author = "Marta", at = noon - 86_400_000)
            .let { it.copy(entry = it.entry.copy(entityType = EntityType.MEMBER)) },
    )

    @Test
    fun groupHistory() {
        compose.setContent {
            OwlsplitTheme { HistoryScreen(HistoryState(loaded = true, items = items), {}, onBack = {}, today = today) }
        }
        compose.onRoot().captureRoboImage("screenshots/history_group.png")
    }

    @Test
    fun activity() {
        compose.setContent {
            OwlsplitTheme(darkTheme = true) {
                HistoryScreen(
                    HistoryState(loaded = true, acrossGroups = true, pending = items.take(1), items = items),
                    {},
                    onBack = null,
                    today = today,
                )
            }
        }
        compose.onRoot().captureRoboImage("screenshots/history_activity_dark.png")
    }
}
