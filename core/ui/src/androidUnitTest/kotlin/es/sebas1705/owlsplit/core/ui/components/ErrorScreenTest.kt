package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The retry button reads in the app's language (it was hard-coded in Spanish). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ErrorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun retryReads(label: String) {
        var retries = 0
        compose.setContent { ErrorScreen("Boom", onRetry = { retries++ }) }
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }

    @Test
    @Config(qualifiers = "en")
    fun retryInEnglish() = retryReads("Retry")

    @Test
    @Config(qualifiers = "es")
    fun retryInSpanish() = retryReads("Reintentar")

    @Test
    fun noRetryButtonWithoutAnAction() {
        compose.setContent { ErrorScreen("Boom") }
        compose.onNodeWithText("Boom").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
}
