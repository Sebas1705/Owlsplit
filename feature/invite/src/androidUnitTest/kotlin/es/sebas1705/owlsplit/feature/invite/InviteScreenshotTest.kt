package es.sebas1705.owlsplit.feature.invite

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.testing.ui.assertAccessible
import es.sebas1705.owlsplit.core.ui.components.TextSharer
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.data.models.InvitePreview
import es.sebas1705.owlsplit.domain.usecases.invite.InviteLink
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "es-w411dp-h1200dp-xxhdpi")
class InviteScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val sharer = TextSharer { true }
    private val ready = InviteState(
        status = InviteStatus.READY,
        groupName = "Ibiza 2026",
        link = InviteLink("K7MP2QXR", "https://owlsplit-dev.web.app/j/K7MP2QXR"),
    )
    private val preview = JoinState(
        code = "K7MP 2QXR",
        step = JoinStep.PREVIEW,
        preview = InvitePreview("K7MP2QXR", "g1", "Ibiza 2026", "plane", 4),
    )
    private val picking = preview.copy(
        step = JoinStep.PICK,
        ghosts = listOf(Ghost("m2", "Luis"), Ghost("m3", "Marta Ruiz")),
        myName = "Luis G",
    )

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { OwlsplitTheme(darkTheme = dark) { content() } }
        compose.assertAccessible()
        compose.onRoot().captureRoboImage("screenshots/invite_$name.png")
    }

    @Test
    fun inviteReady() = capture("ready") { InviteScreen(ready, {}, {}, sharer) }

    @Test
    fun inviteReadyDark() = capture("ready_dark", dark = true) { InviteScreen(ready, {}, {}, sharer) }

    @Test
    fun inviteOffline() = capture("offline") {
        InviteScreen(InviteState(status = InviteStatus.OFFLINE), {}, {}, sharer)
    }

    @Test
    fun joinEnterWithScanner() = capture("join_enter") { JoinScreen(JoinState(), {}, {}, onScan = {}) }

    @Test
    fun joinNotFound() = capture("join_not_found") {
        JoinScreen(JoinState(code = "ZZZZ ZZZZ", error = JoinError.NOT_FOUND), {}, {})
    }

    @Test
    fun joinPreview() = capture("join_preview") { JoinScreen(preview, {}, {}) }

    @Test
    fun joinPick() = capture("join_pick") { JoinScreen(picking, {}, {}) }

    @Test
    fun joinPickDark() = capture("join_pick_dark", dark = true) { JoinScreen(picking, {}, {}) }

    @Test
    @Config(qualifiers = "en-w411dp-h1200dp-xxhdpi")
    fun joinPickEnglish() = capture("join_pick_en") { JoinScreen(picking, {}, {}) }
}
