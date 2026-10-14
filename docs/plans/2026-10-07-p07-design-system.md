# P7 · Design system — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implement in `core:ui` the fintech design system from [DESIGN_SYSTEM.md](../DESIGN_SYSTEM.md): tokens (light/dark colour, group accents, Inter typography with tabular figures, shapes, spacing), `OwlsplitTheme`, amount formatting, the logo and the catalogue components, plus a catalogue screen visible in the app and automatic light and dark screenshots.

**Architecture:** `core:ui` now depends on `core:money` (to format amounts) and on Tabler Icons. Components **contain no hard-coded text**: they receive already-localised strings (i18n arrives in P11). `OwlsplitTheme` exposes its own tokens through `CompositionLocal` (`Owlsplit.colors`, `Owlsplit.type`) and also feeds `MaterialTheme` so Material 3 components inherit the brand. The app shows the catalogue until P9 sets up the real navigation.

**Tech Stack:** Compose Multiplatform 1.11.1 (material3 1.9.0, multiplatform resources for fonts), Tabler Icons KMP (`br.com.devsrsouza.compose.icons:tabler-icons` 1.1.1), Inter 4.1 (OFL), Roborazzi 1.76.0 + Robolectric 4.17 for screenshots.

**Platform:** KMP (common code; screenshots on the Android JVM)

**Depends on:** P1. Independent of P2–P6.

---

## Context for the implementer

- Branch `p7/design-system` from `main`. Same environment as P1.
- Base path: `core/ui/src/commonMain/kotlin/es/sebas1705/owlsplit/core/ui/`.
- Colours and measurements: those in [DESIGN_SYSTEM.md](../DESIGN_SYSTEM.md); if something does not match, that document wins.
- **Font download**: Inter comes from the official release `https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip` (33.7 MB). Ask the user for permission before downloading it; only 4 static TTFs (~300 KB each) and the licence are copied.
- Detekt: `ignorePropertyDeclaration` is already enabled for `MagicNumber`; colour literals go in properties. `@Composable` functions are exempt from `FunctionNaming`.

## File map

| Action | Path |
|---|---|
| Modify | `gradle/libs.versions.toml`, `build.gradle.kts`, `core/ui/build.gradle.kts`, `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/App.kt`, `composeApp/src/commonMain/kotlin/es/sebas1705/owlsplit/config/EnvironmentBadge.kt`, `.github/workflows/ci.yml`, `docs/DESIGN_SYSTEM.md`, `MODULE_MAP.md`, `docs/plans/README.md` |
| Delete | `core/ui/.../theme/Color.kt`, `Shape.kt`, `Theme.kt`, `Type.kt` (template theme) |
| Create | `core/ui/src/commonMain/composeResources/font/inter_{regular,medium,semibold,bold}.ttf`, `docs/licenses/Inter-OFL.txt` |
| Create | `theme/Tokens.kt`, `theme/Typography.kt`, `theme/Dimens.kt`, `theme/OwlsplitTheme.kt` |
| Create | `format/MoneyFormat.kt`; test `core/ui/src/commonTest/kotlin/.../format/MoneyFormatTest.kt` |
| Create | `components/`: `OwlsplitLogo.kt`, `Avatar.kt`, `Containers.kt`, `Buttons.kt`, `SegmentedControl.kt`, `AmountText.kt`, `BalanceHero.kt`, `SyncStatusChip.kt`, `Rows.kt`, `AmountKeypad.kt`, `AvatarToggle.kt`, `EmptyState.kt` |
| Create | `catalog/DesignCatalog.kt` |
| Test | `core/ui/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/core/ui/DesignCatalogScreenshotTest.kt`, `core/ui/screenshots/*.png` (generated) |

---

### Task 1: Fonts and icons

- [ ] **Step 1: 👤 Permission and Inter download**

Ask the user for permission (file `Inter-4.1.zip`, 33.7 MB, official rsms/inter release on GitHub). Once they agree:
```bash
T="$(mktemp -d)"
curl -sL -o "$T/inter.zip" https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip
unzip -l "$T/inter.zip" | grep -E "extras/ttf/Inter-(Regular|Medium|SemiBold|Bold)\.ttf|LICENSE"
F=core/ui/src/commonMain/composeResources/font
mkdir -p "$F" docs/licenses
unzip -p "$T/inter.zip" extras/ttf/Inter-Regular.ttf  > "$F/inter_regular.ttf"
unzip -p "$T/inter.zip" extras/ttf/Inter-Medium.ttf   > "$F/inter_medium.ttf"
unzip -p "$T/inter.zip" extras/ttf/Inter-SemiBold.ttf > "$F/inter_semibold.ttf"
unzip -p "$T/inter.zip" extras/ttf/Inter-Bold.ttf     > "$F/inter_bold.ttf"
unzip -p "$T/inter.zip" LICENSE.txt > docs/licenses/Inter-OFL.txt
ls -la "$F"; rm -rf "$T"
```
Expected: four TTFs of 300–420 KB. If the paths inside the zip differ, use the ones `unzip -l` shows.

- [ ] **Step 2: Version catalog and screenshot plugin**

`gradle/libs.versions.toml`:
- `[versions]`: `compose-icons = "1.1.1"`, `roborazzi = "1.76.0"`, `robolectric = "4.17"`, `androidx-compose-test = "1.11.3"`, `junit4 = "4.13.2"`.
- `[libraries]`:
```toml
compose-icons-tabler       = { module = "br.com.devsrsouza.compose.icons:tabler-icons", version.ref = "compose-icons" }
roborazzi                  = { module = "io.github.takahirom.roborazzi:roborazzi",         version.ref = "roborazzi" }
roborazzi-compose          = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
robolectric                = { module = "org.robolectric:robolectric",                     version.ref = "robolectric" }
androidx-compose-ui-test-junit4   = { module = "androidx.compose.ui:ui-test-junit4",   version.ref = "androidx-compose-test" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest", version.ref = "androidx-compose-test" }
junit4                     = { module = "junit:junit", version.ref = "junit4" }
```
- `[plugins]`: `roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }`.

In the root `build.gradle.kts`, inside `plugins {}`: `alias(libs.plugins.roborazzi) apply false`.

- [ ] **Step 3: `core/ui/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kmp.core)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.compose.icons.tabler)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            api(project(":core:money"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidUnitTest.dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.androidx.compose.ui.test.junit4)
        }
    }
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    "debugImplementation"(libs.androidx.compose.ui.test.manifest)
}

compose.resources {
    packageOfResClass = "es.sebas1705.owlsplit.core.ui.resources"
    publicResClass = false
}
```

- [ ] **Step 4: Build and commit**

```bash
./gradlew :core:ui:assembleDebug --console=plain | tail -2
git add gradle/libs.versions.toml build.gradle.kts core/ui/build.gradle.kts core/ui/src/commonMain/composeResources docs/licenses
git commit -m "build(ui): Inter fonts, Tabler icons and Roborazzi"
```

### Task 2: Amount formatting

**Files:** Create `format/MoneyFormat.kt`; Test `format/MoneyFormatTest.kt` (in `commonTest`).

- [ ] **Step 1: Test**

`core/ui/src/commonTest/kotlin/es/sebas1705/owlsplit/core/ui/format/MoneyFormatTest.kt`:
```kotlin
package es.sebas1705.owlsplit.core.ui.format

import es.sebas1705.owlsplit.core.money.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    private val eur = Currency.of("EUR")
    private val jpy = Currency.of("JPY")
    private val kwd = Currency.of("KWD")

    @Test
    fun `spanish puts the symbol after and groups with dots`() {
        assertEquals("1.284,60 €", MoneyFormat.Spanish.format(128_460, eur))
        assertEquals("0,05 €", MoneyFormat.Spanish.format(5, eur))
        assertEquals("−12,00 €", MoneyFormat.Spanish.format(-1_200, eur))
        assertEquals("+23,50 €", MoneyFormat.Spanish.format(2_350, eur, showPlus = true))
    }

    @Test
    fun `english puts the symbol before and groups with commas`() {
        assertEquals("€1,284.60", MoneyFormat.English.format(128_460, eur))
        assertEquals("−€12.00", MoneyFormat.English.format(-1_200, eur))
    }

    @Test
    fun `currencies without or with three decimals`() {
        assertEquals("1.635 ¥", MoneyFormat.Spanish.format(1_635, jpy))
        assertEquals("3,300 KWD", MoneyFormat.Spanish.format(3_300, kwd))
    }

    @Test
    fun `parts split integer and fraction for big amounts`() {
        val parts = MoneyFormat.Spanish.parts(4_230, eur, showPlus = true)
        assertEquals(MoneyFormat.Parts(sign = "+", integer = "42", fraction = ",30", symbol = " €", symbolBefore = false), parts)
    }

    @Test
    fun `zero has no sign`() {
        assertEquals("0,00 €", MoneyFormat.Spanish.format(0, eur, showPlus = true))
    }
}
```

- [ ] **Step 2: Watch it fail**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*MoneyFormatTest"`
Expected: FAIL (`MoneyFormat` does not exist).

- [ ] **Step 3: Implement**

`format/MoneyFormat.kt`:
```kotlin
package es.sebas1705.owlsplit.core.ui.format

import es.sebas1705.owlsplit.core.money.Currency

/**
 * Locale-style money formatting without platform APIs. The minus sign is U+2212, as in the
 * design system; amounts are always grouped by thousands.
 */
data class MoneyFormat(
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    val symbolBefore: Boolean,
) {
    /** Pieces of a formatted amount, so big displays can shrink the fraction. */
    data class Parts(
        val sign: String,
        val integer: String,
        val fraction: String,
        val symbol: String,
        val symbolBefore: Boolean,
    ) {
        override fun toString(): String =
            if (symbolBefore) "$sign$symbol$integer$fraction" else "$sign$integer$fraction$symbol"
    }

    fun parts(amountMinor: Long, currency: Currency, showPlus: Boolean = false): Parts {
        val sign = when {
            amountMinor < 0 -> MINUS
            amountMinor > 0 && showPlus -> "+"
            else -> ""
        }
        var unit = 1L
        repeat(currency.decimals) { unit *= TEN }
        val abs = if (amountMinor < 0) -amountMinor else amountMinor
        val integer = group((abs / unit).toString())
        val fraction = if (currency.decimals == 0) {
            ""
        } else {
            decimalSeparator + (abs % unit).toString().padStart(currency.decimals, '0')
        }
        val symbol = symbolOf(currency.code)
        return Parts(sign, integer, fraction, if (symbolBefore) symbol else " $symbol", symbolBefore)
    }

    fun format(amountMinor: Long, currency: Currency, showPlus: Boolean = false): String =
        parts(amountMinor, currency, showPlus).toString()

    private fun group(digits: String): String =
        digits.reversed().chunked(GROUP).joinToString(groupingSeparator.toString()).reversed()

    companion object {
        private const val MINUS = "−"
        private const val TEN = 10L
        private const val GROUP = 3

        val Spanish = MoneyFormat(decimalSeparator = ',', groupingSeparator = '.', symbolBefore = false)
        val English = MoneyFormat(decimalSeparator = '.', groupingSeparator = ',', symbolBefore = true)

        private val symbols = mapOf("EUR" to "€", "USD" to "$", "GBP" to "£", "JPY" to "¥")

        fun symbolOf(code: String): String = symbols[code] ?: code
    }
}
```

- [ ] **Step 4: Watch it pass and commit**

Run: `./gradlew :core:ui:testDebugUnitTest --tests "*MoneyFormatTest"` → PASS (5 tests).
```bash
git add core/ui/src
git commit -m "feat(ui): locale-style money formatting"
```

### Task 3: Tokens and theme

**Files:** Delete `theme/Color.kt`, `theme/Shape.kt`, `theme/Theme.kt`, `theme/Type.kt`; Create `theme/Tokens.kt`, `theme/Typography.kt`, `theme/Dimens.kt`, `theme/OwlsplitTheme.kt`; Modify `App.kt`.

- [ ] **Step 1: Delete the template theme**

```bash
git rm -q core/ui/src/commonMain/kotlin/es/sebas1705/owlsplit/core/ui/theme/{Color,Shape,Theme,Type}.kt
grep -rn "AppTheme\|AppTypography\|AppShapes\|core.ui.theme.Primary" --include=*.kt . | grep -v /build/
```
Expected: only `App.kt` uses `AppTheme` (changed in Step 6).

- [ ] **Step 2: `theme/Tokens.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/** Colour tokens from docs/DESIGN_SYSTEM.md. Components read these, never literals. */
@Immutable
data class OwlsplitColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentContainer: Color,
    val positive: Color,
    val positiveFill: Color,
    val negativeFill: Color,
    val warning: Color,
    val error: Color,
    val brandBeak: Color,
    val isDark: Boolean,
)

val LightOwlsplitColors = OwlsplitColors(
    background = Color(0xFFF2F3F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4E6EE),
    divider = Color(0xFFECEEF3),
    textPrimary = Color(0xFF0B0C10),
    textSecondary = Color(0xFF5B5F6B),
    textMuted = Color(0xFF8A8E99),
    accent = Color(0xFF3B4BF5),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF3B4BF5),
    accentContainer = Color(0xFFE8EBFF),
    positive = Color(0xFF087A54),
    positiveFill = Color(0xFF0E9F6E),
    negativeFill = Color(0xFF0B0C10),
    warning = Color(0xFFB26A00),
    error = Color(0xFFC4372B),
    brandBeak = Color(0xFFE8B04B),
    isDark = false,
)

val DarkOwlsplitColors = OwlsplitColors(
    background = Color(0xFF0B0C10),
    surface = Color(0xFF16181F),
    surfaceVariant = Color(0xFF1C1E26),
    divider = Color(0xFF22252E),
    textPrimary = Color(0xFFF5F6FA),
    textSecondary = Color(0xFF8A8E99),
    textMuted = Color(0xFF5B5F6B),
    accent = Color(0xFF3B4BF5),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF8C96FF),
    accentContainer = Color(0xFF232750),
    positive = Color(0xFF34C38F),
    positiveFill = Color(0xFF34C38F),
    negativeFill = Color(0xFFF5F6FA),
    warning = Color(0xFFF2B84B),
    error = Color(0xFFFF7A6E),
    brandBeak = Color(0xFFE8B04B),
    isDark = true,
)

/** The eight group accents. Only the group icon is tinted, never a whole screen. */
enum class GroupAccent(private val lightContainer: Color, private val content: Color) {
    BLUE(Color(0xFFE8EBFF), Color(0xFF3B4BF5)),
    ORANGE(Color(0xFFFFF1E5), Color(0xFFD9661A)),
    VIOLET(Color(0xFFF3E8FF), Color(0xFF8B3FD9)),
    GREEN(Color(0xFFE3F5EE), Color(0xFF087A54)),
    PINK(Color(0xFFFDE8F0), Color(0xFFC2306A)),
    AMBER(Color(0xFFFFF4D6), Color(0xFF9A6400)),
    CYAN(Color(0xFFE0F4FA), Color(0xFF0A7893)),
    GRAPHITE(Color(0xFFECEEF3), Color(0xFF3A3E4A)),
    ;

    fun container(colors: OwlsplitColors): Color =
        if (colors.isDark) content.copy(alpha = DARK_CONTAINER_ALPHA).compositeOver(colors.surface) else lightContainer

    fun content(colors: OwlsplitColors): Color =
        if (colors.isDark) lerp(content, Color.White, DARK_CONTENT_LIGHTEN) else content

    private companion object {
        const val DARK_CONTAINER_ALPHA = 0.18f
        const val DARK_CONTENT_LIGHTEN = 0.35f
    }
}
```

- [ ] **Step 3: `theme/Typography.kt`**

```kotlin
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
private const val TABULAR = "tnum"

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
    val amount = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR)
    return OwlsplitTypography(
        amountDisplay = amount.copy(fontSize = 44.sp, lineHeight = 52.sp),
        amountHeadline = amount.copy(fontSize = 30.sp, lineHeight = 36.sp),
        title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
        bodyStrong = TextStyle(
            fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
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
```

- [ ] **Step 4: `theme/Dimens.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object OwlsplitShapes {
    val container = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(percent = 50)
}

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Sizes {
    val touchTarget = 48.dp
    val quickAction = 48.dp
    val rowMinHeight = 56.dp
    val iconTile = 36.dp
    val primaryButtonHeight = 52.dp
    val balanceBar = 4.dp
}
```

- [ ] **Step 5: `theme/OwlsplitTheme.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

private val LocalOwlsplitColors = staticCompositionLocalOf { LightOwlsplitColors }
private val LocalOwlsplitTypography = staticCompositionLocalOf<OwlsplitTypography> {
    error("Owlsplit typography requested outside OwlsplitTheme")
}

/** Access to the brand tokens: `Owlsplit.colors.accent`, `Owlsplit.type.amountDisplay`. */
object Owlsplit {
    val colors: OwlsplitColors
        @Composable @ReadOnlyComposable get() = LocalOwlsplitColors.current

    val type: OwlsplitTypography
        @Composable @ReadOnlyComposable get() = LocalOwlsplitTypography.current
}

/**
 * Brand theme. [fontFamily] defaults to the bundled Inter; tests may pass [FontFamily.Default]
 * where resource fonts cannot be loaded.
 */
@Composable
fun OwlsplitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkOwlsplitColors else LightOwlsplitColors
    val family = fontFamily ?: interFontFamily()
    CompositionLocalProvider(
        LocalOwlsplitColors provides colors,
        LocalOwlsplitTypography provides owlsplitTypography(family),
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = materialTypography(family),
            shapes = Shapes(small = OwlsplitShapes.control, medium = OwlsplitShapes.container, large = OwlsplitShapes.container),
            content = content,
        )
    }
}

private fun OwlsplitColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = accentText,
        secondary = accent,
        onSecondary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        outline = textMuted,
        outlineVariant = divider,
        error = error,
    )
}
```

- [ ] **Step 6: Use the new theme in `App.kt`**

Replace `import es.sebas1705.owlsplit.core.ui.theme.AppTheme` with `import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme` and `AppTheme(modifier) {` with `OwlsplitTheme {` (the `modifier` moves to the `Surface`: `Surface(modifier.fillMaxSize())`).

- [ ] **Step 7: Build and commit**

```bash
./gradlew :composeApp:assembleDebug detekt --console=plain | grep -E "BUILD|\.kt:"
git add -A
git commit -m "feat(ui): Owlsplit theme tokens, Inter typography and shapes"
```

### Task 4: Basic components

**Files:** Create `components/OwlsplitLogo.kt`, `Avatar.kt`, `Containers.kt`, `Buttons.kt`, `SegmentedControl.kt`.

- [ ] **Step 1: Logo**

`components/OwlsplitLogo.kt`:
```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit

/** Geometric owl: accent rounded square, two white rings for eyes and an amber beak. */
@Composable
fun OwlsplitLogo(size: Dp = 48.dp, contentDescription: String? = null, modifier: Modifier = Modifier) {
    val accent = Owlsplit.colors.accent
    val beak = Owlsplit.colors.brandBeak
    Canvas(
        modifier
            .size(size)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val unit = this.size.width / LOGO_GRID
        drawRoundRect(accent, cornerRadius = CornerRadius(CORNER * unit))
        val ring = Stroke(width = RING_STROKE * unit)
        drawCircle(Color.White, radius = EYE_RADIUS * unit, center = Offset(LEFT_EYE_X * unit, EYE_Y * unit), style = ring)
        drawCircle(Color.White, radius = EYE_RADIUS * unit, center = Offset(RIGHT_EYE_X * unit, EYE_Y * unit), style = ring)
        val beakPath = Path().apply {
            moveTo(BEAK_LEFT * unit, BEAK_TOP * unit)
            lineTo(BEAK_RIGHT * unit, BEAK_TOP * unit)
            lineTo(LOGO_GRID / 2 * unit, BEAK_TIP * unit)
            close()
        }
        drawPath(beakPath, beak)
    }
}

// Proportions on a 24-unit grid, as in the mockup.
private const val LOGO_GRID = 24f
private const val CORNER = 7f
private const val RING_STROKE = 1.8f
private const val EYE_RADIUS = 3.2f
private const val LEFT_EYE_X = 8.5f
private const val RIGHT_EYE_X = 15.5f
private const val EYE_Y = 11f
private const val BEAK_LEFT = 10.8f
private const val BEAK_RIGHT = 13.2f
private const val BEAK_TOP = 15.6f
private const val BEAK_TIP = 18f
```

- [ ] **Step 2: Avatar**

`components/Avatar.kt`:
```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit

/** Initials on a neutral tone. Decorative: the name is always next to it, so it is hidden from screen readers. */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color = Owlsplit.colors.surfaceVariant,
    content: Color = Owlsplit.colors.textPrimary,
) {
    Box(
        modifier.size(size).background(background, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), style = Owlsplit.type.label, color = content)
    }
}

internal fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
```

- [ ] **Step 3: Container and buttons**

`components/Containers.kt`:
```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** White (graphite in dark) rounded container that holds rows. No shadow: depth comes from contrast. */
@Composable
fun OwlsplitContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().background(Owlsplit.colors.surface, OwlsplitShapes.container).padding(vertical = Spacing.xs),
    ) {
        if (title != null) {
            Text(
                title,
                style = Owlsplit.type.bodyStrong,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).semantics { heading() },
            )
        }
        content()
    }
}
```
`components/Buttons.kt`:
```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** The one primary action of a screen: wide accent pill. */
@Composable
fun PrimaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = OwlsplitShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Owlsplit.colors.accent,
            contentColor = Owlsplit.colors.onAccent,
        ),
        modifier = modifier.fillMaxWidth().height(Sizes.primaryButtonHeight),
    ) {
        Text(text, style = Owlsplit.type.bodyStrong)
    }
}

/** Round shortcut under the balance (Expense, Settle, Group, Join). */
@Composable
fun QuickActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.size(Sizes.quickAction).background(Owlsplit.colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Owlsplit.colors.textPrimary, modifier = Modifier.size(22.dp))
        }
        Text(label, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
    }
}
```

- [ ] **Step 4: Segmented control**

`components/SegmentedControl.kt`:
```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes

/** Pill with the selected option raised onto the surface colour (Gastos · Saldos · Saldar). */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Owlsplit.colors.surfaceVariant, OwlsplitShapes.control)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.touchTarget - 8.dp)
                    .background(if (selected) Owlsplit.colors.surface else Color.Transparent, OwlsplitShapes.control)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = Owlsplit.type.label,
                    color = if (selected) Owlsplit.colors.textPrimary else Owlsplit.colors.textSecondary,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Build and commit**

```bash
./gradlew :core:ui:assembleDebug detekt --console=plain | grep -E "BUILD|\.kt:"
git add core/ui/src
git commit -m "feat(ui): logo, avatar, container, buttons and segmented control"
```

### Task 5: Amounts, main balance and sync status

**Files:** Create `components/AmountText.kt`, `BalanceHero.kt`, `SyncStatusChip.kt`.

- [ ] **Step 1: `components/AmountText.kt`** (amount with decimals at 60 % and a rolling number)

```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import kotlin.math.roundToLong

private const val FRACTION_SCALE = 0.6f
private const val ROLL_MILLIS = 300

/**
 * An amount where the integer part leads and the fraction shrinks to 60 %. When [animate] is
 * true a change rolls from the previous value; the final frame is always the exact amount.
 * [spokenDescription] replaces the digits for screen readers ("Te deben veintitrés euros con cincuenta").
 */
@Composable
fun AmountText(
    amountMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    showPlus: Boolean = false,
    animate: Boolean = true,
    spokenDescription: String? = null,
) {
    val shown = if (animate) rememberRollingAmount(amountMinor) else amountMinor
    val parts = format.parts(shown, currency, showPlus)
    val small = SpanStyle(fontSize = style.fontSize * FRACTION_SCALE)
    val text = buildAnnotatedString {
        append(parts.sign)
        if (parts.symbolBefore) append(parts.symbol)
        append(parts.integer)
        withStyle(small) {
            append(parts.fraction)
            if (!parts.symbolBefore) append(parts.symbol)
        }
    }
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier.semantics { if (spokenDescription != null) contentDescription = spokenDescription },
    )
}

@Composable
private fun rememberRollingAmount(target: Long): Long {
    var shown by remember { mutableLongStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        val from = shown
        if (from == target) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ROLL_MILLIS, easing = FastOutSlowInEasing)) {
            shown = from + ((target - from) * value.toDouble()).roundToLong()
        }
        shown = target
    }
    return shown
}
```

- [ ] **Step 2: `components/SyncStatusChip.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Check
import compose.icons.tablericons.CloudOff
import compose.icons.tablericons.CloudUpload
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

enum class SyncStatus { SYNCED, PENDING, OFFLINE, ERROR }

/** Small pill with the sync state; [text] comes localised from the caller ("1 cambio pendiente"). */
@Composable
fun SyncStatusChip(status: SyncStatus, text: String, modifier: Modifier = Modifier) {
    val colors = Owlsplit.colors
    val (icon, tint) = when (status) {
        SyncStatus.SYNCED -> TablerIcons.Check to colors.accentText
        SyncStatus.PENDING -> TablerIcons.CloudUpload to colors.textSecondary
        SyncStatus.OFFLINE -> TablerIcons.CloudOff to colors.textSecondary
        SyncStatus.ERROR -> TablerIcons.AlertTriangle to colors.warning
    }
    Row(
        modifier.background(colors.surface, OwlsplitShapes.pill).padding(horizontal = Spacing.s + 2.dp, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = Owlsplit.type.caption, color = colors.textSecondary)
    }
}
```

- [ ] **Step 3: `components/BalanceHero.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** Big centred balance: green when you are owed, neutral when you owe (never red). */
@Composable
fun BalanceHero(
    label: String,
    amountMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    modifier: Modifier = Modifier,
    spokenDescription: String? = null,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
        AmountText(
            amountMinor = amountMinor,
            currency = currency,
            format = format,
            style = Owlsplit.type.amountDisplay,
            color = if (amountMinor > 0) Owlsplit.colors.positive else Owlsplit.colors.textPrimary,
            showPlus = true,
            spokenDescription = spokenDescription,
        )
        footer()
    }
}
```

- [ ] **Step 4: Build and commit**

```bash
./gradlew :core:ui:assembleDebug detekt --console=plain | grep -E "BUILD|\.kt:"
git add core/ui/src
git commit -m "feat(ui): rolling amounts, balance hero and sync status chip"
```

### Task 6: Rows, keypad, avatar toggles and empty state

**Files:** Create `components/Rows.kt`, `AmountKeypad.kt`, `AvatarToggle.kt`, `EmptyState.kt`.

- [ ] **Step 1: `components/Rows.kt`**

```kotlin
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Text(name, style = Owlsplit.type.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
) {
    val colors = Owlsplit.colors
    val fraction = if (largestAbsBalanceMinor == 0L) 0f else abs(balanceMinor).toFloat() / largestAbsBalanceMinor
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .semantics(mergeDescendants = true) { if (spokenDescription != null) contentDescription = spokenDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Avatar(name, size = 30.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(name, style = Owlsplit.type.bodyStrong, color = colors.textPrimary, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(Sizes.balanceBar).background(colors.divider, RoundedCornerShape(2.dp))) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (balanceMinor >= 0) colors.positiveFill else colors.negativeFill, RoundedCornerShape(2.dp)),
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

/** "Luis te debe 23,50 €" with a pill action to settle it. */
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
        modifier.fillMaxWidth().heightIn(min = Sizes.rowMinHeight).padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(Modifier.size(Sizes.iconTile).background(colors.accentContainer, CircleShape), contentAlignment = Alignment.Center) {
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
```

- [ ] **Step 2: `components/AmountKeypad.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Backspace
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes

sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey
    data object Decimal : KeypadKey
    data object Backspace : KeypadKey
}

/** Numeric keypad for the expense editor; [decimalSeparator] follows the locale. */
@Composable
fun AmountKeypad(
    decimalSeparator: Char,
    backspaceLabel: String,
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf(KeypadKey.Digit(1), KeypadKey.Digit(2), KeypadKey.Digit(3)),
        listOf(KeypadKey.Digit(4), KeypadKey.Digit(5), KeypadKey.Digit(6)),
        listOf(KeypadKey.Digit(7), KeypadKey.Digit(8), KeypadKey.Digit(9)),
        listOf(KeypadKey.Decimal, KeypadKey.Digit(0), KeypadKey.Backspace),
    )
    Column(modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClick = { onKey(key) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            is KeypadKey.Digit -> KeyText(key.value.toString())
                            KeypadKey.Decimal -> KeyText(decimalSeparator.toString())
                            KeypadKey.Backspace -> Icon(TablerIcons.Backspace, contentDescription = backspaceLabel, tint = Owlsplit.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String) {
    Text(text, style = Owlsplit.type.title.copy(fontFeatureSettings = "tnum"), color = Owlsplit.colors.textPrimary)
}
```

- [ ] **Step 3: `components/AvatarToggle.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** Member in the split editor: tap to include (accent) or exclude (neutral); shows their share. */
@Composable
fun AvatarToggle(
    name: String,
    included: Boolean,
    shareText: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Owlsplit.colors
    Column(
        modifier
            .widthIn(min = Sizes.touchTarget)
            .toggleable(value = included, role = Role.Checkbox, onValueChange = onToggle)
            .semantics(mergeDescendants = true) { contentDescription = "$name, $shareText" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Avatar(
            name,
            size = 36.dp,
            background = if (included) colors.accent else colors.surfaceVariant,
            content = if (included) colors.onAccent else colors.textMuted,
        )
        Text(shareText, style = Owlsplit.type.caption, color = if (included) colors.textSecondary else colors.textMuted)
    }
}
```

- [ ] **Step 4: `components/EmptyState.kt`**

```kotlin
package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** An invitation, not an apology: logo, headline, one line and a verb. */
@Composable
fun EmptyState(title: String, body: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        OwlsplitLogo(size = 56.dp)
        Text(title, style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary, textAlign = TextAlign.Center)
        PrimaryPillButton(actionLabel, onAction)
    }
}
```

- [ ] **Step 5: Build and commit**

```bash
./gradlew :core:ui:assembleDebug detekt --console=plain | grep -E "BUILD|\.kt:"
git add core/ui/src
git commit -m "feat(ui): group, member and settle rows, keypad, avatar toggle and empty state"
```

### Task 7: Catalogue visible in the app

**Files:** Create `catalog/DesignCatalog.kt`; Modify `App.kt`, `config/EnvironmentBadge.kt`.

- [ ] **Step 1: `catalog/DesignCatalog.kt`** (sample data from the mockup; demo strings, not product copy)

```kotlin
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
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsLeftRight
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
import es.sebas1705.owlsplit.core.ui.components.QuickActionButton
import es.sebas1705.owlsplit.core.ui.components.SegmentedControl
import es.sebas1705.owlsplit.core.ui.components.SettleSuggestionRow
import es.sebas1705.owlsplit.core.ui.components.SyncStatus
import es.sebas1705.owlsplit.core.ui.components.SyncStatusChip
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** Every component with the mockup's sample data. Shown by the app until P9 builds real screens. */
@Composable
fun DesignCatalog(modifier: Modifier = Modifier) {
    val eur = remember { Currency.of("EUR") }
    val format = MoneyFormat.Spanish
    var balance by remember { mutableLongStateOf(4_230) }
    var tab by remember { mutableIntStateOf(1) }
    val included = remember { mutableStateMapOf("Ana" to true, "Sebas" to true, "Marta" to true, "Luis" to true, "Jaime" to false) }
    val members = listOf("Ana" to 5_820L, "Sebas" to 2_350L, "Marta" to -1_170L, "Luis" to -7_000L)

    Column(
        modifier
            .fillMaxSize()
            .background(Owlsplit.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OwlsplitLogo(size = 28.dp)
            Text("owlsplit", style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary)
        }
        BalanceHero(label = "Balance total · EUR", amountMinor = balance, currency = eur, format = format) {
            SyncStatusChip(SyncStatus.SYNCED, "Sincronizado")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            QuickActionButton(TablerIcons.Plus, "Gasto", onClick = { balance += 1_000 })
            QuickActionButton(TablerIcons.ArrowsLeftRight, "Saldar", onClick = { balance -= 1_000 })
            QuickActionButton(TablerIcons.UserPlus, "Grupo", onClick = {})
            QuickActionButton(TablerIcons.Qrcode, "Unirse", onClick = {})
        }
        OwlsplitContainer(title = "Grupos") {
            GroupRow("Ibiza 2026", "5 personas · hace 2 h", TablerIcons.Plane, GroupAccent.BLUE, 2_350, eur, format, {})
            GroupRow("Piso Lavapiés", "3 personas · ayer", TablerIcons.Home, GroupAccent.ORANGE, -1_200, eur, format, {})
            GroupRow("Cumple Marta", "8 personas · 3 oct", TablerIcons.Gift, GroupAccent.VIOLET, 3_080, eur, format, {})
        }
        SegmentedControl(listOf("Gastos", "Saldos", "Saldar"), tab, { tab = it })
        OwlsplitContainer {
            members.forEach { (name, amount) -> MemberBalanceRow(name, amount, 7_000, eur, format) }
        }
        OwlsplitContainer {
            SettleSuggestionRow("Luis te debe 23,50 €", "Parte de 3 pagos para cerrar", "Saldar", TablerIcons.ArrowsLeftRight, {})
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            included.forEach { (name, on) ->
                AvatarToggle(name, on, if (on) "12,15" else "—", onToggle = { included[name] = it })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SyncStatusChip(SyncStatus.PENDING, "1 cambio pendiente")
            SyncStatusChip(SyncStatus.OFFLINE, "Sin conexión")
            SyncStatusChip(SyncStatus.ERROR, "Error")
        }
        OwlsplitContainer {
            AmountKeypad(decimalSeparator = ',', backspaceLabel = "Borrar", onKey = { key ->
                if (key is KeypadKey.Digit) balance = balance * 10 + key.value
            })
        }
        EmptyState("Crea tu primer grupo", "Apunta gastos y Owlsplit calcula quién debe a quién.", "Crear grupo", {})
    }
}
```
Add `import androidx.compose.ui.unit.dp`.

- [ ] **Step 2: Show the catalogue and respect the system insets**

`App.kt`:
```kotlin
package es.sebas1705.owlsplit

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.config.EnvironmentBadge
import es.sebas1705.owlsplit.core.ui.catalog.DesignCatalog
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme

/** Root composable. Shows the design catalog until P9 builds the real navigation. */
@Composable
fun App(modifier: Modifier = Modifier) {
    OwlsplitTheme {
        EnvironmentBadge(modifier) {
            DesignCatalog(Modifier.safeDrawingPadding())
        }
    }
}
```
In `config/EnvironmentBadge.kt`, add `Modifier.safeDrawingPadding()` to the badge's `Surface`, before its existing `padding` (the badge overlapped the status bar in P1).

- [ ] **Step 3: Try it on the emulator**

```bash
./gradlew :composeApp:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n es.sebas1705.owlsplit.dev/es.sebas1705.owlsplit.MainActivity
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > /tmp/p7-light.png
"$ANDROID_HOME/platform-tools/adb" shell cmd uimode night yes
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > /tmp/p7-dark.png
"$ANDROID_HOME/platform-tools/adb" shell cmd uimode night no
```
Expected: catalogue in Inter, green balance "+42,30 €", group cards, bars, keypad; in dark mode, black background and graphite surfaces. Compare by eye with `docs/mockups/owlsplit-fintech-v3.html`. Tap "Gasto" (Expense) and watch the balance roll.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(ui): design catalog shown by the app"
```

### Task 8: Automatic screenshots

**Files:** Test `core/ui/src/androidUnitTest/kotlin/es/sebas1705/owlsplit/core/ui/DesignCatalogScreenshotTest.kt`; Create `core/ui/screenshots/*.png`.

- [ ] **Step 1: Test**

```kotlin
package es.sebas1705.owlsplit.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import es.sebas1705.owlsplit.core.ui.catalog.DesignCatalog
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2200dp-xxhdpi")
class DesignCatalogScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalogLight() {
        compose.setContent { OwlsplitTheme(darkTheme = false) { DesignCatalog() } }
        compose.onRoot().captureRoboImage("screenshots/catalog_light.png")
    }

    @Test
    fun catalogDark() {
        compose.setContent { OwlsplitTheme(darkTheme = true) { DesignCatalog() } }
        compose.onRoot().captureRoboImage("screenshots/catalog_dark.png")
    }
}
```
`androidx.test.ext:junit` has been in the version catalog since P2 (`libs.androidx.test.ext.junit`); add it to the `androidUnitTest.dependencies` of `core/ui`.

- [ ] **Step 2: Record and review**

```bash
./gradlew :core:ui:recordRoborazziDebug --console=plain | tail -3
ls core/ui/screenshots
```
Expected: `catalog_light.png` and `catalog_dark.png`. Open them and check they look as on the emulator. If they fail with a resource font loading error in Robolectric, pass `fontFamily = FontFamily.Default` to `OwlsplitTheme` in the test and note it in the commit.

- [ ] **Step 3: Local verification**

Run: `./gradlew :core:ui:verifyRoborazziDebug` → PASS (no changes).

- [ ] **Step 4: CI publishes the screenshots as an artifact**

In `.github/workflows/ci.yml`, job `android`, add after the Gradle step:
```yaml
      - run: ./gradlew :core:ui:recordRoborazziDebug
      - uses: actions/upload-artifact@v6
        with:
          name: ui-screenshots
          path: core/ui/screenshots/
```
(Screenshots are not compared in CI: text rendering differs between Windows and Linux. Comparison happens locally with `verifyRoborazziDebug`.)

- [ ] **Step 5: Commit**

```bash
git add core/ui .github/workflows/ci.yml
git commit -m "test(ui): Roborazzi screenshots of the design catalog in light and dark"
```

### Task 9: Documentation and wrap-up

- [ ] `docs/DESIGN_SYSTEM.md`: under "Components", add `OwlsplitLogo`, `AmountText` (decimals at 60 % and a rolling number) and `KeypadKey`; note that **reduce motion** is wired up in P11 (Compose Multiplatform does not expose it in common code; `AmountText(animate = false)` already supports it).
- [ ] `MODULE_MAP.md`: `core:ui` → "`OwlsplitTheme`, tokens, `MoneyFormat`, components and `DesignCatalog`".
- [ ] `docs/plans/README.md`: P7 → `Done`.
- [ ] Verification: `./gradlew detekt testDebugUnitTest verifySqlDelightMigration :composeApp:assembleDebug` green.
- [ ] Commit, push, PR:

```bash
git add -A
git commit -m "docs: mark P7 as done"
git push -u origin p7/design-system
gh pr create --title "P7: design system" --body "Implements docs/plans/2026-10-07-p07-design-system.md"
```

## P7 final verification

- The app shows the catalogue in light and dark with Inter and the mockup's colours.
- `MoneyFormatTest` (5) green; screenshots recorded and verified locally.
- No colour literals outside `theme/Tokens.kt` and `OwlsplitLogo` (`grep -rn "Color(0x" core/ui/src/commonMain | grep -v theme/Tokens.kt` empty).

## Deviations during execution (2026-10-07)

- **New token `segmentSelected`** (`#FFFFFF` / `#2C303B`): in dark mode, `surface` is darker than `surfaceVariant`, so the active option of the segmented control looked sunken. Also added to DESIGN_SYSTEM.md.
- **Edge-to-edge background**: the background is applied in `App` before `safeDrawingPadding()`, so it extends behind the system bars (in light mode the status bar showed grey).
- **Detekt**: `MagicNumber.ignoreEnums` (`GroupAccent` colours) and `LongParameterList.ignoreAnnotated: [Composable]` + `ignoreDataClasses`; `SyncStatus` and `KeypadKey` in their own files (`MatchingDeclarationName`); `DesignCatalog` split into sections (`LongMethod`).
- **Catalogue**: `mutableStateMapOf` does not keep insertion order; the avatars are iterated from a fixed list. Caught by the Roborazzi screenshots.
- The Inter fonts load fine in Robolectric: `FontFamily.Default` was not needed in the tests.
