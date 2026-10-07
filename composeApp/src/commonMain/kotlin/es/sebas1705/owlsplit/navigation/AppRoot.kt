package es.sebas1705.owlsplit.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Home
import compose.icons.tablericons.User
import es.sebas1705.owlsplit.config.AppConfig
import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.ui.catalog.DesignCatalog
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ShouldSuggestLinking
import es.sebas1705.owlsplit.feature.onboarding.OnboardingRoute
import es.sebas1705.owlsplit.feature.profile.ProfileRoute
import es.sebas1705.owlsplit.resources.Res
import es.sebas1705.owlsplit.resources.activity_empty_body
import es.sebas1705.owlsplit.resources.activity_empty_title
import es.sebas1705.owlsplit.resources.back
import es.sebas1705.owlsplit.resources.home_empty_body
import es.sebas1705.owlsplit.resources.home_empty_title
import es.sebas1705.owlsplit.resources.link_nudge_action
import es.sebas1705.owlsplit.resources.link_nudge_body
import es.sebas1705.owlsplit.resources.link_nudge_dismiss
import es.sebas1705.owlsplit.resources.link_nudge_title
import es.sebas1705.owlsplit.resources.tab_activity
import es.sebas1705.owlsplit.resources.tab_home
import es.sebas1705.owlsplit.resources.tab_profile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Serializable
private data object HomeTab

@Serializable
private data object ActivityTab

@Serializable
private data object ProfileTab

@Serializable
private data object CatalogRoute

private enum class Tab(val route: Any, val icon: ImageVector, val label: StringResource) {
    HOME(HomeTab, TablerIcons.Home, Res.string.tab_home),
    ACTIVITY(ActivityTab, TablerIcons.Activity, Res.string.tab_activity),
    PROFILE(ProfileTab, TablerIcons.User, Res.string.tab_profile),
}

private sealed interface RootState {
    data object Loading : RootState
    data object Onboarding : RootState
    data object Ready : RootState
}

/** Onboarding until there is a profile; then the tabs (Inicio · Actividad · Perfil). */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val observeAccount = koinInject<ObserveAccount>()
    val root by remember {
        observeAccount().map { if (it.profile == null) RootState.Onboarding else RootState.Ready }
    }.collectAsState(RootState.Loading)
    when (root) {
        RootState.Loading -> Spacer(modifier.fillMaxSize().background(Owlsplit.colors.background))
        // P9/P10 open "create group" or "join" from the choice.
        RootState.Onboarding -> OnboardingRoute(onFinished = {}, modifier = modifier.safeDrawingPadding())
        RootState.Ready -> Tabs(modifier)
    }
}

@Composable
private fun Tabs(modifier: Modifier) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.route::class) == true }
    Scaffold(
        modifier = modifier,
        containerColor = Owlsplit.colors.background,
        bottomBar = {
            if (current != null) {
                NavigationBar(containerColor = Owlsplit.colors.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), style = Owlsplit.type.caption) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Owlsplit.colors.accentText,
                                selectedTextColor = Owlsplit.colors.accentText,
                                indicatorColor = Owlsplit.colors.accentContainer,
                                unselectedIconColor = Owlsplit.colors.textMuted,
                                unselectedTextColor = Owlsplit.colors.textMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeTab, modifier = Modifier.padding(padding)) {
            composable<HomeTab> { HomePlaceholder(onLink = { nav.navigate(ProfileTab) }) }
            composable<ActivityTab> {
                EmptyState(
                    stringResource(Res.string.activity_empty_title),
                    stringResource(Res.string.activity_empty_body),
                    Modifier,
                )
            }
            composable<ProfileTab> {
                val catalog = if (AppConfig.ENVIRONMENT == "development") ({ nav.navigate(CatalogRoute) }) else null
                ProfileRoute(onOpenCatalog = catalog)
            }
            composable<CatalogRoute> {
                Column {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(TablerIcons.ArrowLeft, stringResource(Res.string.back), tint = Owlsplit.colors.textPrimary)
                    }
                    DesignCatalog()
                }
            }
        }
    }
}

/** Inicio until P9: the link suggestion (decision 8 of the P8 plan) and an empty state. */
@Composable
private fun HomePlaceholder(onLink: () -> Unit) {
    val observeAccount = koinInject<ObserveAccount>()
    val groups = koinInject<GroupRepository>()
    val prefs = koinInject<AppPreferences>()
    val scope = rememberCoroutineScope()
    val account by remember { observeAccount() }.collectAsState(null)
    val groupCount by remember { groups.observeGroups().map { it.size } }.collectAsState(0)
    val dismissed by prefs.linkNudgeDismissed.collectAsState(true)
    Column(
        Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (ShouldSuggestLinking(account?.session, groupCount, dismissed)) {
            OwlsplitContainer(title = stringResource(Res.string.link_nudge_title)) {
                Column(
                    Modifier.padding(horizontal = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    Text(
                        stringResource(Res.string.link_nudge_body),
                        style = Owlsplit.type.body,
                        color = Owlsplit.colors.textSecondary
                    )
                    Row {
                        TextActionButton(
                            stringResource(Res.string.link_nudge_dismiss),
                            { scope.launch { prefs.dismissLinkNudge() } }
                        )
                        Spacer(Modifier.weight(1f))
                        PrimaryPillButton(stringResource(Res.string.link_nudge_action), onLink, Modifier.weight(1f))
                    }
                }
            }
        }
        EmptyState(stringResource(Res.string.home_empty_title), stringResource(Res.string.home_empty_body))
    }
}
