package es.sebas1705.owlsplit.feature.groups.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.back
import org.jetbrains.compose.resources.stringResource

/** Back arrow, title and optional actions on the right. */
@Composable
internal fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(TablerIcons.ArrowLeft, stringResource(Res.string.back), tint = Owlsplit.colors.textPrimary)
        }
        Text(
            title,
            style = Owlsplit.type.bodyStrong,
            color = Owlsplit.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
