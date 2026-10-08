package es.sebas1705.owlsplit.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.ui.catalog.DesignCatalog
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with `recordRoborazziDebug`, compare locally with `verifyRoborazziDebug`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "es-w411dp-h2200dp-xxhdpi")
class DesignCatalogScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalogLight() {
        compose.setContent { OwlsplitTheme(darkTheme = false) { DesignCatalog() } }
        compose.onRoot().captureRoboImage("screenshots/catalog_light.png")
    }

    @Test
    fun catalogDark() {
        compose.setContent { OwlsplitTheme(darkTheme = true) { DesignCatalog() } }
        compose.onRoot().captureRoboImage("screenshots/catalog_dark.png")
    }
}
