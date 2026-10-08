package es.sebas1705.owlsplit.feature.groups.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.categoryName
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.nameOf
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.clear_filters
import es.sebas1705.owlsplit.feature.groups.resources.filter_any_category
import es.sebas1705.owlsplit.feature.groups.resources.filter_anyone
import es.sebas1705.owlsplit.feature.groups.resources.search_label
import es.sebas1705.owlsplit.feature.groups.resources.search_placeholder
import org.jetbrains.compose.resources.stringResource

/** Search by title, and filter by person and category (decision 1 of P9c). */
@Composable
internal fun ExpenseFilters(state: GroupState, detail: GroupDetail, onEvent: (GroupIntent) -> Unit) {
    val filter = state.filter
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OwlsplitTextField(
            value = filter.query,
            onValueChange = { onEvent(GroupIntent.Search(it)) },
            label = stringResource(Res.string.search_label),
            placeholder = stringResource(Res.string.search_placeholder),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            val anyone = stringResource(Res.string.filter_anyone)
            Chip(
                label = filter.memberId?.let { detail.members.nameOf(it) } ?: anyone,
                selected = filter.memberId != null,
                options = listOf<Pair<String?, String>>(null to anyone) + detail.members.map { it.id to it.name },
                onSelect = { onEvent(GroupIntent.FilterMember(it)) },
            )
            val anyCategory = stringResource(Res.string.filter_any_category)
            Chip(
                label = filter.category?.let { categoryName(it.name) } ?: anyCategory,
                selected = filter.category != null,
                options = listOf<Pair<ExpenseCategory?, String>>(null to anyCategory) +
                    ExpenseCategory.entries.map { it to categoryName(it.name) },
                onSelect = { onEvent(GroupIntent.FilterCategory(it)) },
            )
        }
        if (filter.active) {
            TextActionButton(stringResource(Res.string.clear_filters), { onEvent(GroupIntent.ClearFilters) })
        }
    }
}

@Composable
private fun <T> Chip(label: String, selected: Boolean, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = Owlsplit.colors
    Box {
        Row(
            Modifier
                .heightIn(min = Sizes.touchTarget)
                .background(if (selected) colors.accentContainer else colors.surface, OwlsplitShapes.pill)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(label, style = Owlsplit.type.label, color = if (selected) colors.accentText else colors.textPrimary)
            Icon(TablerIcons.ChevronDown, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = Owlsplit.type.body) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}
