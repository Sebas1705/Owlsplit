package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** Single-line text field on the surface colour, label above, optional error below. */
@Composable
fun OwlsplitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    maxLength: Int = Int.MAX_VALUE,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = Owlsplit.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = Owlsplit.type.label, color = colors.textSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            singleLine = true,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it, style = Owlsplit.type.body, color = colors.textMuted) } },
            textStyle = Owlsplit.type.body.copy(color = colors.textPrimary),
            shape = OwlsplitShapes.control,
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.divider,
                cursorColor = colors.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, style = Owlsplit.type.caption, color = colors.error)
    }
}

/** Secondary action next to a primary pill: plain accent text. */
@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Owlsplit.colors.accentText,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = Sizes.touchTarget)) {
        Text(text, style = Owlsplit.type.bodyStrong, color = if (enabled) color else Owlsplit.colors.textMuted)
    }
}

/** Row of colour swatches (avatar or group colour). [names] gives each one a spoken name. */
@Composable
fun AccentPicker(
    selected: GroupAccent,
    onSelect: (GroupAccent) -> Unit,
    names: @Composable (GroupAccent) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = Owlsplit.colors
    Row(
        modifier.fillMaxWidth().selectableGroup(),

    ) {
        GroupAccent.entries.forEach { accent ->
            val isSelected = accent == selected
            val label = names(accent)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.touchTarget)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(accent) })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(accent.content(colors), CircleShape)
                        .then(if (isSelected) Modifier.border(2.dp, colors.textPrimary, CircleShape) else Modifier)
                        .padding(Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            TablerIcons.Check,
                            null,
                            tint = colors.onAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
