package es.sebas1705.owlsplit.feature.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.data.models.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi")
class ProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val ana = UserProfile("Ana García", "accent:GREEN", "EUR")
    private val anonymous = ProfileState(
        loaded = true,
        saved = ana,
        name = ana.name,
        accent = GroupAccent.GREEN,
        canLink = true,
    )

    private fun capture(name: String, state: ProfileState, dark: Boolean = false) {
        compose.setContent { OwlsplitTheme(darkTheme = dark) { ProfileScreen(state, {}, onOpenCatalog = {}) } }
        compose.onRoot().captureRoboImage("screenshots/profile_$name.png")
    }

    @Test
    fun anonymous() = capture("anonymous", anonymous)

    @Test
    fun anonymousDark() = capture("anonymous_dark", anonymous, dark = true)

    @Test
    fun linkedAndEditing() = capture(
        "linked_editing",
        anonymous.copy(isAnonymous = false, linkedGoogle = true, name = "Ana G", message = ProfileMessage.LINKED),
    )
}
