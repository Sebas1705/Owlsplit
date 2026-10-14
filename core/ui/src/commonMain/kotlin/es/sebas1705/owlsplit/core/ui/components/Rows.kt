package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import kotlin.math.abs

private val BarShape = RoundedCornerShape(2.dp)

/** A group in the home list: tinted icon, name, metadata and your balance in that group. */
@Composable
fun GroupRow(
    name: String,
    subtitle: String,
    icon: ImageVector,
    accent: GroupAccent,
    balanceMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Owlsplit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(Sizes.iconTile).background(accent.container(colors), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.content(colors), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = Owlsplit.type.bodyStrong,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = Owlsplit.type.caption, color = colors.textSecondary, maxLines = 1)
        }
        Text(
            format.format(balanceMinor, currency, showPlus = true),
            style = Owlsplit.type.bodyStrong,
            color = if (balanceMinor > 0) colors.positive else colors.textPrimary,
        )
    }
}

/** Member with a thin bar proportional to the largest balance of the group. */
@Composable
fun MemberBalanceRow(
    name: String,
    balanceMinor: Long,
    largestAbsBalanceMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    modifier: Modifier = Modifier,
    spokenDescription: String? = null,
    /** The member's colour (memberAccent); null: neutral. */
    accent: GroupAccent? = null,
) {
    val colors = Owlsplit.colors
    val fraction = if (largestAbsBalanceMinor == 0L) 0f else abs(balanceMinor).toFloat() / largestAbsBalanceMinor
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .semantics(
                mergeDescendants = true
            ) { if (spokenDescription != null) contentDescription = spokenDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (accent != null) ProfileAvatar(name, accent, size = 30.dp) else Avatar(name, size = 30.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(name, style = Owlsplit.type.bodyStrong, color = colors.textPrimary, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(Sizes.balanceBar).background(colors.divider, BarShape)) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (balanceMinor >= 0) colors.positiveFill else colors.negativeFill, BarShape),
                )
            }
        }
        Text(
            format.format(balanceMinor, currency, showPlus = true),
            style = Owlsplit.type.bodyStrong,
            color = if (balanceMinor > 0) colors.positive else colors.textPrimary,
        )
    }
}

/** "Luis owes you €23.50" with a pill action to settle it. */
@Composable
fun SettleSuggestionRow(
    text: String,
    subtitle: String,
    actionLabel: String,
    icon: ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Owlsplit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(Sizes.iconTile).background(colors.accentContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = Owlsplit.type.bodyStrong, color = colors.textPrimary)
            Text(subtitle, style = Owlsplit.type.caption, color = colors.textSecondary)
        }
        TextButton(
            onClick = onAction,
            shape = OwlsplitShapes.pill,
            colors = ButtonDefaults.textButtonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        ) {
            Text(actionLabel, style = Owlsplit.type.label)
        }
    }
}

/** An expense or transfer in a group list: category icon, title, who paid, amount and your part. */
@Composable
fun ExpenseRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    amountMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Owlsplit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(Sizes.iconTile).background(colors.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        val amount = format.format(amountMinor, currency)
        // With large text (≥ 150 %) the amounts go under the title instead of squeezing it.
        if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Owlsplit.type.bodyStrong, color = colors.textPrimary)
                Text(subtitle, style = Owlsplit.type.caption, color = colors.textSecondary)
                Text(amount, style = Owlsplit.type.bodyStrong, color = colors.textPrimary)
                if (detail != null) Text(detail, style = Owlsplit.type.caption, color = colors.textSecondary)
            }
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = Owlsplit.type.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, style = Owlsplit.type.caption, color = colors.textSecondary, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, style = Owlsplit.type.bodyStrong, color = colors.textPrimary)
                if (detail != null) {
                    Text(
                        detail,
                        style = Owlsplit.type.caption,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private const val STACK_FONT_SCALE = 1.5f
