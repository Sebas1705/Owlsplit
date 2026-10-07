package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import es.sebas1705.owlsplit.core.ui.resources.Res
import es.sebas1705.owlsplit.core.ui.resources.inter_bold
import es.sebas1705.owlsplit.core.ui.resources.inter_medium
import es.sebas1705.owlsplit.core.ui.resources.inter_regular
import es.sebas1705.owlsplit.core.ui.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/** Tabular figures keep columns of amounts aligned. */
internal const val TABULAR = "tnum"

@Immutable
data class OwlsplitTypography(
    val amountDisplay: TextStyle,
    val amountHeadline: TextStyle,
    val title: TextStyle,
    val bodyStrong: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

@Composable
fun interFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

fun owlsplitTypography(family: FontFamily): OwlsplitTypography {
    val amount = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = TABULAR,
    )
    return OwlsplitTypography(
        amountDisplay = amount.copy(fontSize = 44.sp, lineHeight = 52.sp),
        amountHeadline = amount.copy(fontSize = 30.sp, lineHeight = 36.sp),
        title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
        bodyStrong = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontFeatureSettings = TABULAR,
        ),
        body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        label = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp),
    )
}

/** Material typography with Inter everywhere, so stock M3 components match the brand. */
fun materialTypography(family: FontFamily): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}
