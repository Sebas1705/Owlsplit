package es.sebas1705.owlsplit.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: OnboardingState, dark: Boolean = false) {
        compose.setContent { OwlsplitTheme(darkTheme = dark) { OnboardingScreen(state, {}) } }
        compose.onRoot().captureRoboImage("screenshots/onboarding_$name.png")
    }

    @Test
    fun welcome() = capture("welcome", OnboardingState(googleAvailable = true))

    @Test
    fun profile() = capture(
        "profile",
        OnboardingState(OnboardingStep.PROFILE, name = "Ana", accent = GroupAccent.GREEN)
    )

    @Test
    fun profileDark() =
        capture(
            "profile_dark",
            OnboardingState(OnboardingStep.PROFILE, name = "Ana", accent = GroupAccent.GREEN),
            dark = true
        )

    @Test
    fun start() = capture("start", OnboardingState(OnboardingStep.START))

    @Test
    fun waitingForNetwork() =
        capture("waiting", OnboardingState(OnboardingStep.START, busy = true, waitingForNetwork = true))
}
