package es.sebas1705.owlsplit

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.config.EnvironmentBadge
import es.sebas1705.owlsplit.core.common.UnexpectedErrors
import es.sebas1705.owlsplit.core.datastore.AppLanguage
import es.sebas1705.owlsplit.core.datastore.DisplaySettings
import es.sebas1705.owlsplit.core.datastore.ThemeMode
import es.sebas1705.owlsplit.core.ui.components.ErrorScreen
import es.sebas1705.owlsplit.core.ui.format.LocalMoneyFormat
import es.sebas1705.owlsplit.core.ui.format.moneyFormatFor
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.di.FirebaseStatus
import es.sebas1705.owlsplit.locale.LocalAppLocale
import es.sebas1705.owlsplit.navigation.AppRoot
import es.sebas1705.owlsplit.resources.Res
import es.sebas1705.owlsplit.resources.firebase_missing
import es.sebas1705.owlsplit.resources.unexpected_error
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Root composable: theme and language from the user's preferences, then onboarding or the tabs. */
@Composable
fun App(modifier: Modifier = Modifier) {
    val display = koinInject<DisplaySettings>()
    val themeMode by display.themeMode.collectAsState(ThemeMode.SYSTEM)
    // Null until read: starting in the device language and switching would flash the other one.
    val language by display.language.collectAsState<AppLanguage, AppLanguage?>(null)
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SystemBarsAppearance(dark)
    OwlsplitTheme(darkTheme = dark) {
        val chosen = language
        if (chosen == null) {
            Spacer(modifier.background(Owlsplit.colors.background))
            return@OwlsplitTheme
        }
        CompositionLocalProvider(LocalAppLocale provides chosen.tag) {
            CompositionLocalProvider(LocalMoneyFormat provides moneyFormatFor(LocalAppLocale.current)) {
                // Background first, then insets: the colour reaches behind the system bars (edge to edge).
                EnvironmentBadge(modifier.background(Owlsplit.colors.background)) {
                    if (koinInject<FirebaseStatus>().configured) {
                        AppRoot()
                    } else {
                        ErrorScreen(stringResource(Res.string.firebase_missing))
                    }
                    UnexpectedErrorMessage()
                }
            }
        }
    }
}

/** The generic message for an unexpected error (spec 9): never the trace, which went to Crashlytics. */
@Composable
private fun UnexpectedErrorMessage() {
    val host = remember { SnackbarHostState() }
    val message = stringResource(Res.string.unexpected_error)
    LaunchedEffect(Unit) {
        UnexpectedErrors.events.collect { host.showSnackbar(message) }
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(host)
    }
}
