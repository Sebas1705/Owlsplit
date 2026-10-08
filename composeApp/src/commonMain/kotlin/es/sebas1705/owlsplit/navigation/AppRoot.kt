package es.sebas1705.owlsplit.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Home
import compose.icons.tablericons.User
import es.sebas1705.owlsplit.config.AppConfig
import es.sebas1705.owlsplit.core.ui.catalog.DesignCatalog
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.feature.expense.ExpenseEditorRoute
import es.sebas1705.owlsplit.feature.groups.detail.GroupNavigation
import es.sebas1705.owlsplit.feature.groups.detail.GroupRoute
import es.sebas1705.owlsplit.feature.groups.detail.GroupTab
import es.sebas1705.owlsplit.feature.groups.editor.GroupEditorRoute
import es.sebas1705.owlsplit.feature.groups.members.MembersRoute
import es.sebas1705.owlsplit.feature.home.HomeNavigation
import es.sebas1705.owlsplit.feature.home.HomeRoute
import es.sebas1705.owlsplit.feature.onboarding.OnboardingRoute
import es.sebas1705.owlsplit.feature.onboarding.StartChoice
import es.sebas1705.owlsplit.feature.profile.ProfileRoute
import es.sebas1705.owlsplit.resources.Res
import es.sebas1705.owlsplit.resources.activity_empty_body
import es.sebas1705.owlsplit.resources.activity_empty_title
import es.sebas1705.owlsplit.resources.back
import es.sebas1705.owlsplit.resources.tab_activity
import es.sebas1705.owlsplit.resources.tab_home
import es.sebas1705.owlsplit.resources.tab_profile
import kotlinx.coroutines.flow.map
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

@Serializable
private data class GroupDest(val id: String, val tab: String = GroupTab.EXPENSES.name)

/** [id] null creates a group. */
@Serializable
private data class GroupEditorDest(val id: String? = null)

@Serializable
private data class MembersDest(val groupId: String)

/** [expenseId] null creates an expense. */
@Serializable
private data class ExpenseDest(val groupId: String, val expenseId: String? = null)

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

/** Onboarding until there is a profile; then the tabs (Inicio · Actividad · Perfil) and their screens. */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val observeAccount = koinInject<ObserveAccount>()
    val root by remember {
        observeAccount().map { if (it.profile == null) RootState.Onboarding else RootState.Ready }
    }.collectAsState(RootState.Loading)
    // What the user chose on the last onboarding step, consumed once the tabs show.
    var startChoice by rememberSaveable { mutableStateOf<StartChoice?>(null) }
    // Onboarding saves the profile before it reports the choice: stay on it until it says it is done.
    var onboarding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(root) { if (root == RootState.Onboarding) onboarding = true }
    when {
        root == RootState.Loading -> Spacer(modifier.fillMaxSize().background(Owlsplit.colors.background))
        root == RootState.Onboarding || onboarding -> OnboardingRoute(
            onFinished = {
                startChoice = it
                onboarding = false
            },
            modifier = modifier.safeDrawingPadding(),
        )
        else -> Tabs(modifier, startChoice) { startChoice = null }
    }
}

@Composable
private fun Tabs(modifier: Modifier, startChoice: StartChoice?, onStartHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.route::class) == true }
    LaunchedEffect(startChoice) {
        // Joining arrives with invitations (P10); for now only "create a group" opens a screen.
        if (startChoice == StartChoice.CREATE_GROUP) nav.navigate(GroupEditorDest())
        if (startChoice != null) onStartHandled()
    }
    Scaffold(
        modifier = modifier,
        containerColor = Owlsplit.colors.background,
        bottomBar = { if (current != null) BottomBar(nav, current) },
    ) { padding ->
        NavHost(nav, startDestination = HomeTab, modifier = Modifier.padding(padding)) {
            composable<HomeTab> { HomeRoute(homeNavigation(nav)) }
            composable<ActivityTab> {
                EmptyState(
                    stringResource(Res.string.activity_empty_title),
                    stringResource(Res.string.activity_empty_body)
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
            groupScreens(nav)
        }
    }
}

/** A group, its editor, its members and the expense editor. */
private fun NavGraphBuilder.groupScreens(nav: NavController) {
    composable<GroupDest> { backStack ->
        val route = backStack.toRoute<GroupDest>()
        GroupRoute(route.id, GroupTab.valueOf(route.tab), groupNavigation(nav, route.id))
    }
    composable<GroupEditorDest> { backStack ->
        val route = backStack.toRoute<GroupEditorDest>()
        GroupEditorRoute(
            route.id,
            onBack = { nav.popBackStack() },
            onSaved = { id ->
                if (route.id == null) {
                    // A new group opens right away, replacing the editor in the back stack.
                    nav.navigate(GroupDest(id)) { popUpTo<GroupEditorDest> { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
            },
        )
    }
    composable<MembersDest> { backStack ->
        MembersRoute(backStack.toRoute<MembersDest>().groupId, onBack = { nav.popBackStack() })
    }
    composable<ExpenseDest> { backStack ->
        val route = backStack.toRoute<ExpenseDest>()
        ExpenseEditorRoute(route.groupId, route.expenseId, onClose = { nav.popBackStack() })
    }
}

private fun homeNavigation(nav: NavController) = HomeNavigation(
    openGroup = { nav.navigate(GroupDest(it)) },
    createGroup = { nav.navigate(GroupEditorDest()) },
    addExpense = { nav.navigate(ExpenseDest(it)) },
    settle = { nav.navigate(GroupDest(it, GroupTab.SETTLE.name)) },
    link = { nav.navigateToTab(ProfileTab) },
)

private fun groupNavigation(nav: NavController, groupId: String) = GroupNavigation(
    back = { nav.popBackStack() },
    members = { nav.navigate(MembersDest(groupId)) },
    edit = { nav.navigate(GroupEditorDest(groupId)) },
    addExpense = { nav.navigate(ExpenseDest(groupId)) },
    openExpense = { nav.navigate(ExpenseDest(groupId, it)) },
)

private fun NavController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(nav: NavController, current: Tab) {
    NavigationBar(containerColor = Owlsplit.colors.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { nav.navigateToTab(tab.route) },
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
