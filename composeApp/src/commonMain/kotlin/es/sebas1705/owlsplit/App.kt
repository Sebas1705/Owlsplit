package es.sebas1705.owlsplit

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.config.EnvironmentBadge
import es.sebas1705.owlsplit.core.datastore.ThemeMode
import es.sebas1705.owlsplit.core.datastore.ThemeSettings
import es.sebas1705.owlsplit.core.ui.components.ErrorScreen
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import es.sebas1705.owlsplit.di.FirebaseStatus
import es.sebas1705.owlsplit.navigation.AppRoot
import es.sebas1705.owlsplit.resources.Res
import es.sebas1705.owlsplit.resources.firebase_missing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Root composable: theme from the user's preference, then onboarding or the tabs. */
@Composable
fun App(modifier: Modifier = Modifier) {
    val themeMode by koinInject<ThemeSettings>().themeMode.collectAsState(ThemeMode.SYSTEM)
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SystemBarsAppearance(dark)
    OwlsplitTheme(darkTheme = dark) {
        // Background first, then insets: the colour reaches behind the system bars (edge to edge).
        EnvironmentBadge(modifier.background(Owlsplit.colors.background)) {
            if (koinInject<FirebaseStatus>().configured) {
                AppRoot()
            } else {
                ErrorScreen(stringResource(Res.string.firebase_missing))
            }
        }
    }
}
