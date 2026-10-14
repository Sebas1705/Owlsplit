package es.sebas1705.owlsplit.core.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsLeftRight
import compose.icons.tablericons.BellRinging
import compose.icons.tablericons.Gift
import compose.icons.tablericons.Home
import compose.icons.tablericons.Plane
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Qrcode
import compose.icons.tablericons.UserPlus
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.AmountKeypad
import es.sebas1705.owlsplit.core.ui.components.AvatarToggle
import es.sebas1705.owlsplit.core.ui.components.BalanceHero
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.GroupRow
import es.sebas1705.owlsplit.core.ui.components.KeypadKey
import es.sebas1705.owlsplit.core.ui.components.MemberBalanceRow
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitLogo
import es.sebas1705.owlsplit.core.ui.components.RowAction
import es.sebas1705.owlsplit.core.ui.components.QuickActionButton
import es.sebas1705.owlsplit.core.ui.components.SegmentedControl
import es.sebas1705.owlsplit.core.ui.components.SettleSuggestionRow
import es.sebas1705.owlsplit.core.ui.components.SyncStatus
import es.sebas1705.owlsplit.core.ui.components.SyncStatusChip
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing

private const val SAMPLE_BALANCE = 4_230L
private const val SAMPLE_STEP = 1_000L
private const val LARGEST_SAMPLE_BALANCE = 7_000L
private const val DECIMAL_SHIFT = 10L

private val eur = Currency.of("EUR")
private val format = MoneyFormat.Spanish

/**
 * Every component with the mockup's sample data. Shown by the app until P9 builds real screens.
 * The texts are demo data, not product copy.
 */
@Composable
fun DesignCatalog(modifier: Modifier = Modifier) {
    var balance by remember { mutableLongStateOf(SAMPLE_BALANCE) }
    Column(
        modifier
            .fillMaxSize()
            .background(Owlsplit.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        HomeSection(balance, onBalanceChange = { balance = it })
        GroupSection()
        EditorSection(onDigit = { digit -> balance = balance * DECIMAL_SHIFT + digit })
        EmptyState(
            "Crea tu primer grupo",
            "Apunta gastos y Owlsplit calcula quién debe a quién.",
            actionLabel = "Crear grupo",
        )
    }
}

@Composable
private fun HomeSection(balance: Long, onBalanceChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OwlsplitLogo(size = 28.dp)
        Text("owlsplit", style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary)
    }
    BalanceHero(label = "Balance total · EUR", amountMinor = balance, currency = eur, format = format) {
        SyncStatusChip(SyncStatus.SYNCED, "Sincronizado")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        QuickActionButton(TablerIcons.Plus, "Gasto", onClick = { onBalanceChange(balance + SAMPLE_STEP) })
        QuickActionButton(TablerIcons.ArrowsLeftRight, "Saldar", onClick = { onBalanceChange(balance - SAMPLE_STEP) })
        QuickActionButton(TablerIcons.UserPlus, "Grupo", onClick = {})
        QuickActionButton(TablerIcons.Qrcode, "Unirse", onClick = {})
    }
    OwlsplitContainer(title = "Grupos") {
        GroupRow("Ibiza 2026", "5 personas · hace 2 h", TablerIcons.Plane, GroupAccent.BLUE, 2_350, eur, format, {})
        GroupRow("Piso Lavapiés", "3 personas · ayer", TablerIcons.Home, GroupAccent.ORANGE, -1_200, eur, format, {})
        GroupRow("Cumple Marta", "8 personas · 3 oct", TablerIcons.Gift, GroupAccent.VIOLET, 3_080, eur, format, {})
    }
}

@Composable
private fun GroupSection() {
    var tab by remember { mutableIntStateOf(1) }
    val members = listOf("Ana" to 5_820L, "Sebas" to 2_350L, "Marta" to -1_170L, "Luis" to -7_000L)
    SegmentedControl(listOf("Gastos", "Saldos", "Saldar"), tab, { tab = it })
    OwlsplitContainer {
        members.forEach { (name, amount) -> MemberBalanceRow(name, amount, LARGEST_SAMPLE_BALANCE, eur, format) }
    }
    OwlsplitContainer {
        SettleSuggestionRow(
            text = "Luis te debe 23,50 €",
            subtitle = "Parte de 3 pagos para cerrar",
            actionLabel = "Saldar",
            icon = TablerIcons.ArrowsLeftRight,
            onAction = {},
            secondary = RowAction(TablerIcons.BellRinging, "Recordar a Luis", {}),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SyncStatusChip(SyncStatus.PENDING, "1 cambio pendiente")
        SyncStatusChip(SyncStatus.OFFLINE, "Sin conexión")
        SyncStatusChip(SyncStatus.ERROR, "Error")
    }
}

@Composable
private fun EditorSection(onDigit: (Int) -> Unit) {
    // SnapshotStateMap does not keep insertion order: iterate a fixed list of names.
    val names = listOf("Ana", "Sebas", "Marta", "Luis", "Jaime")
    val included = remember { mutableStateMapOf<String, Boolean>().apply { names.forEach { put(it, it != "Jaime") } } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        names.forEach { name ->
            val on = included[name] == true
            AvatarToggle(name, on, if (on) "12,15" else "—", onToggle = { included[name] = it })
        }
    }
    OwlsplitContainer {
        AmountKeypad(decimalSeparator = ',', backspaceLabel = "Borrar", onKey = { key ->
            if (key is KeypadKey.Digit) onDigit(key.value)
        })
    }
}
