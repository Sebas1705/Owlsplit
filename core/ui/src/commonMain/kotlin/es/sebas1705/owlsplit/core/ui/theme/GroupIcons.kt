package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.BallFootball
import compose.icons.tablericons.Bed
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.Briefcase
import compose.icons.tablericons.Bus
import compose.icons.tablericons.Car
import compose.icons.tablericons.Dots
import compose.icons.tablericons.Gift
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Home
import compose.icons.tablericons.MedicalCross
import compose.icons.tablericons.Music
import compose.icons.tablericons.Plane
import compose.icons.tablericons.Shirt
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Sun
import compose.icons.tablericons.Tent
import compose.icons.tablericons.Ticket
import compose.icons.tablericons.ToolsKitchen2
import compose.icons.tablericons.Users
import es.sebas1705.owlsplit.core.ui.resources.Res
import es.sebas1705.owlsplit.core.ui.resources.category_accommodation
import es.sebas1705.owlsplit.core.ui.resources.category_food
import es.sebas1705.owlsplit.core.ui.resources.category_gifts
import es.sebas1705.owlsplit.core.ui.resources.category_groceries
import es.sebas1705.owlsplit.core.ui.resources.category_health
import es.sebas1705.owlsplit.core.ui.resources.category_home
import es.sebas1705.owlsplit.core.ui.resources.category_leisure
import es.sebas1705.owlsplit.core.ui.resources.category_other
import es.sebas1705.owlsplit.core.ui.resources.category_shopping
import es.sebas1705.owlsplit.core.ui.resources.category_transport
import es.sebas1705.owlsplit.core.ui.resources.category_utilities
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Icons a group can have. The key is what `Group.icon` stores; unknown keys fall back to the first. */
object GroupIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "plane" to TablerIcons.Plane,
        "home" to TablerIcons.Home,
        "users" to TablerIcons.Users,
        "gift" to TablerIcons.Gift,
        "sun" to TablerIcons.Sun,
        "tent" to TablerIcons.Tent,
        "car" to TablerIcons.Car,
        "ticket" to TablerIcons.Ticket,
        "music" to TablerIcons.Music,
        "ball" to TablerIcons.BallFootball,
        "briefcase" to TablerIcons.Briefcase,
        "heart" to TablerIcons.Heart,
    )

    fun of(key: String): ImageVector = all.firstOrNull { it.first == key }?.second ?: all.first().second
}

private class Category(val icon: ImageVector, val label: StringResource)

/** Expense categories, keyed by `ExpenseCategory.name` (core:ui does not see the data models). */
private val categories: Map<String, Category> = mapOf(
    "FOOD" to Category(TablerIcons.ToolsKitchen2, Res.string.category_food),
    "GROCERIES" to Category(TablerIcons.ShoppingCart, Res.string.category_groceries),
    "TRANSPORT" to Category(TablerIcons.Bus, Res.string.category_transport),
    "ACCOMMODATION" to Category(TablerIcons.Bed, Res.string.category_accommodation),
    "LEISURE" to Category(TablerIcons.Ticket, Res.string.category_leisure),
    "SHOPPING" to Category(TablerIcons.Shirt, Res.string.category_shopping),
    "HOME" to Category(TablerIcons.Home, Res.string.category_home),
    "UTILITIES" to Category(TablerIcons.Bolt, Res.string.category_utilities),
    "HEALTH" to Category(TablerIcons.MedicalCross, Res.string.category_health),
    "GIFTS" to Category(TablerIcons.Gift, Res.string.category_gifts),
    "OTHER" to Category(TablerIcons.Dots, Res.string.category_other),
)

private fun category(name: String) = categories[name] ?: categories.getValue("OTHER")

fun categoryIcon(name: String): ImageVector = category(name).icon

@Composable
fun categoryName(name: String): String = stringResource(category(name).label)
