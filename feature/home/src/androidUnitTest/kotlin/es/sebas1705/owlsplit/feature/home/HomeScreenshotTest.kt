package es.sebas1705.owlsplit.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.domain.usecases.groups.HomeGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val navigation = HomeNavigation({}, {}, {}, {}, {}, {})

    private fun group(id: String, name: String, icon: String, accent: String, currency: String = "EUR") = Group(
        id, name, icon, accent, currency, null, null, "CODE", listOf("uid"), emptyList(), "uid", 0,
        SyncMeta(1, 1, 1, "uid", false, SyncState.SYNCED),
    )

    private val withGroups = HomeState(
        loaded = true,
        groups = listOf(
            HomeGroup(group("g1", "Ibiza 2026", "plane", "BLUE"), 5, 2_350, 3),
            HomeGroup(group("g2", "Piso Lavapiés", "home", "ORANGE"), 3, -1_200, 2),
            HomeGroup(group("g3", "Cumple Marta", "gift", "VIOLET", "USD"), 8, 3_080, 1),
        ),
        total = 1_150,
        otherCurrencies = mapOf("USD" to 3_080L),
        pending = 2,
        suggestLinking = true,
    )

    private fun capture(name: String, state: HomeState, dark: Boolean = false) {
        compose.setContent { OwlsplitTheme(darkTheme = dark) { HomeScreen(state, {}, navigation) } }
        compose.onRoot().captureRoboImage("screenshots/home_$name.png")
    }

    @Test
    fun empty() = capture("empty", HomeState(loaded = true))

    @Test
    fun groups() = capture("groups", withGroups)

    @Test
    fun groupsDark() = capture("groups_dark", withGroups.copy(suggestLinking = false, pending = 0), dark = true)
}
