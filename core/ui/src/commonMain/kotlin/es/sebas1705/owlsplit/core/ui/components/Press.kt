package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/**
 * Press feedback that follows what the person sees. The touch area can be larger than the visible
 * shape (a circle and its label): give the area `interactionSource = press` and `indication = null`,
 * and the shape [pressFeedback], so the ripple fills the shape instead of a rectangle around both.
 */
@Composable
fun rememberPress(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Clips to [shape] and draws the press ripple of [press] inside it. */
fun Modifier.pressFeedback(press: MutableInteractionSource, shape: Shape): Modifier =
    clip(shape).indication(press, ripple())
