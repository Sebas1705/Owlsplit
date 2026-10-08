package es.sebas1705.owlsplit.core.testing.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import kotlin.test.fail

/**
 * Smallest visible size of a control. Compose widens the touch area of every clickable to
 * 48 dp (`ViewConfiguration.minimumTouchTargetSize`), which meets the spec's "áreas táctiles
 * ≥ 48 dp"; below 40 dp (Material's icon buttons and chips) the control is too small to see.
 */
private const val MIN_VISIBLE_DP = 40f

/**
 * Fails listing every clickable element a screen reader cannot name or that is too small to
 * see and hit. Google's Accessibility Test Framework does not run under Robolectric, so the
 * screenshot tests call this instead on each screen they render.
 */
fun ComposeTestRule.assertAccessible() {
    val problems = onAllNodes(hasClickAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        // Zero size: scrolled out of view, nothing to judge.
        .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        .flatMap { node -> problemsOf(node) }
    if (problems.isNotEmpty()) fail("Accessibility problems:\n" + problems.joinToString("\n") { " - $it" })
}

private fun problemsOf(node: SemanticsNode): List<String> {
    val config = node.config
    val label = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.EditableText)?.text,
        config.getOrNull(SemanticsProperties.StateDescription),
    ).joinToString(" ").trim()
    val density = node.layoutInfo.density.density
    // The laid-out size: boundsInRoot is clipped when the node is partly scrolled away.
    val width = node.size.width / density
    val height = node.size.height / density
    val role = config.getOrNull(SemanticsProperties.Role)
    val name = label.ifEmpty { "${role ?: "element"} at ${node.boundsInRoot.topLeft}" }
    return buildList {
        if (label.isEmpty()) add("$name: clickable without a label")
        if (width < MIN_VISIBLE_DP || height < MIN_VISIBLE_DP) {
            add("$name: ${width.toInt()}x${height.toInt()} dp, under $MIN_VISIBLE_DP dp")
        }
    }
}
