package com.numbered.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import com.numbered.app.domain.WeekTone

/** Week colours are explicit so the grid keeps its meaning across devices and themes. */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E56),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEEE2),
    onPrimaryContainer = Color(0xFF04342C),
    secondary = Color(0xFFB4492A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBE3D9),
    onSecondaryContainer = Color(0xFF712B13),
    tertiary = Color(0xFF5A51C0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7E5FD),
    onTertiaryContainer = Color(0xFF26215C),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1F1C18),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1F1C18),
    surfaceVariant = Color(0xFFEFEAE2),
    onSurfaceVariant = Color(0xFF5F5A53),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3EC),
    surfaceContainer = Color(0xFFF2EDE5),
    surfaceContainerHigh = Color(0xFFECE7DE),
    surfaceContainerHighest = Color(0xFFE6E0D6),
    outline = Color(0xFF8C857B),
    outlineVariant = Color(0xFFDDD6CB),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD3B0),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF0B5443),
    onPrimaryContainer = Color(0xFFC6F1E1),
    secondary = Color(0xFFF3A487),
    onSecondary = Color(0xFF5A1E0A),
    secondaryContainer = Color(0xFF6E2B14),
    onSecondaryContainer = Color(0xFFFCDDD0),
    tertiary = Color(0xFFB8B2F2),
    onTertiary = Color(0xFF26215C),
    tertiaryContainer = Color(0xFF3C3489),
    onTertiaryContainer = Color(0xFFE6E3FD),
    background = Color(0xFF151412),
    onBackground = Color(0xFFECE7DF),
    surface = Color(0xFF151412),
    onSurface = Color(0xFFECE7DF),
    surfaceVariant = Color(0xFF2A2723),
    onSurfaceVariant = Color(0xFFB8B0A5),
    surfaceContainerLowest = Color(0xFF100F0D),
    surfaceContainerLow = Color(0xFF1B1A17),
    surfaceContainer = Color(0xFF201E1B),
    surfaceContainerHigh = Color(0xFF2A2824),
    surfaceContainerHighest = Color(0xFF353230),
    outline = Color(0xFF8A8379),
    outlineVariant = Color(0xFF3D3934),
)

private val WarmPaperColors = LightColors.copy(
    primary = Color(0xFF51447A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DFF6),
    onPrimaryContainer = Color(0xFF302454),
    secondary = Color(0xFF835719),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E8C9),
    onSecondaryContainer = Color(0xFF57390C),
    tertiary = Color(0xFF446574),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9EAF0),
    onTertiaryContainer = Color(0xFF1F4555),
    background = Color(0xFFFFFAEF),
    onBackground = Color(0xFF28231F),
    surface = Color(0xFFFFFAEF),
    onSurface = Color(0xFF28231F),
    surfaceVariant = Color(0xFFF3EBD8),
    onSurfaceVariant = Color(0xFF625B50),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF5E6),
    surfaceContainer = Color(0xFFF7EEDC),
    surfaceContainerHigh = Color(0xFFF0E6D2),
    surfaceContainerHighest = Color(0xFFE9DDC7),
    outline = Color(0xFF81796D),
    outlineVariant = Color(0xFFDBD1BD),
)

private val DeepInkColors = DarkColors.copy(
    primary = Color(0xFF9BC9ED),
    onPrimary = Color(0xFF0C304C),
    primaryContainer = Color(0xFF244762),
    onPrimaryContainer = Color(0xFFD1E8FA),
    secondary = Color(0xFFFFB49D),
    onSecondary = Color(0xFF632719),
    secondaryContainer = Color(0xFF703A2B),
    onSecondaryContainer = Color(0xFFFFDDD2),
    tertiary = Color(0xFFC5B7F4),
    onTertiary = Color(0xFF332B62),
    tertiaryContainer = Color(0xFF4A407D),
    onTertiaryContainer = Color(0xFFE9E1FF),
    background = Color(0xFF111B27),
    onBackground = Color(0xFFF0F3F8),
    surface = Color(0xFF111B27),
    onSurface = Color(0xFFF0F3F8),
    surfaceVariant = Color(0xFF253242),
    onSurfaceVariant = Color(0xFFBECAD7),
    surfaceContainerLowest = Color(0xFF0B141F),
    surfaceContainerLow = Color(0xFF192533),
    surfaceContainer = Color(0xFF202D3C),
    surfaceContainerHigh = Color(0xFF2A3849),
    surfaceContainerHighest = Color(0xFF354557),
    outline = Color(0xFF93A4B5),
    outlineVariant = Color(0xFF455466),
)

private val SoftSageColors = LightColors.copy(
    primary = Color(0xFF275D50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E7DB),
    onPrimaryContainer = Color(0xFF163B32),
    secondary = Color(0xFF74558B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDF1),
    onSecondaryContainer = Color(0xFF4D3163),
    tertiary = Color(0xFF96602D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4E2CA),
    onTertiaryContainer = Color(0xFF623D18),
    background = Color(0xFFF4F8F2),
    onBackground = Color(0xFF1D2A22),
    surface = Color(0xFFF4F8F2),
    onSurface = Color(0xFF1D2A22),
    surfaceVariant = Color(0xFFE2EBDF),
    onSurfaceVariant = Color(0xFF526158),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF4EC),
    surfaceContainer = Color(0xFFE7F0E5),
    surfaceContainerHigh = Color(0xFFDFE9DD),
    surfaceContainerHighest = Color(0xFFD5E2D3),
    outline = Color(0xFF77867B),
    outlineVariant = Color(0xFFC7D7C8),
)

private val HighContrastColors = LightColors.copy(
    primary = Color(0xFF003FB3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF001F64),
    secondary = Color(0xFF8C3000),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0CF),
    onSecondaryContainer = Color(0xFF542000),
    tertiary = Color(0xFF5632A0),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF301066),
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF303030),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainer = Color(0xFFEBEBEB),
    surfaceContainerHigh = Color(0xFFDEDEDE),
    surfaceContainerHighest = Color(0xFFD2D2D2),
    outline = Color(0xFF3B3B3B),
    outlineVariant = Color(0xFF8D8D8D),
)

/** Colours for each kind of week on the life grid. */
@Immutable
data class WeekColors(
    val lived: Color,
    val someDone: Color,
    val allDone: Color,
    val current: Color,
    val pinned: Color,
    val ahead: Color,
) {
    fun of(tone: WeekTone): Color = when (tone) {
        WeekTone.Lived -> lived
        WeekTone.SomeDone -> someDone
        WeekTone.AllDone -> allDone
        WeekTone.Current -> current
        WeekTone.Pinned -> pinned
        WeekTone.Ahead -> ahead
    }
}

private val LightWeekColors = WeekColors(
    lived = Color(0xFFCBC4B8),
    someDone = Color(0xFF8ED6BC),
    allDone = Color(0xFF0F6E56),
    current = Color(0xFFD85A30),
    pinned = Color(0xFF7F77DD),
    ahead = Color(0xFFEAE5DC),
)

private val DarkWeekColors = WeekColors(
    lived = Color(0xFF4F4A43),
    someDone = Color(0xFF2C7D63),
    allDone = Color(0xFF6FD3B0),
    current = Color(0xFFF0997B),
    pinned = Color(0xFFAFA9EC),
    ahead = Color(0xFF2A2824),
)

private val WarmPaperWeekColors = LightWeekColors.copy(
    lived = Color(0xFFD2C7B2),
    someDone = Color(0xFFC4B4E1),
    allDone = Color(0xFF51447A),
    current = Color(0xFF9C641A),
    pinned = Color(0xFF457488),
    ahead = Color(0xFFF2E9D8),
)

private val DeepInkWeekColors = DarkWeekColors.copy(
    lived = Color(0xFF435267),
    someDone = Color(0xFF47789C),
    allDone = Color(0xFF9BC9ED),
    current = Color(0xFFFFB49D),
    pinned = Color(0xFFC5B7F4),
    ahead = Color(0xFF202D3C),
)

private val SoftSageWeekColors = LightWeekColors.copy(
    lived = Color(0xFFB9C9BB),
    someDone = Color(0xFF9AC7AC),
    allDone = Color(0xFF275D50),
    current = Color(0xFF9B6434),
    pinned = Color(0xFF8D6BA4),
    ahead = Color(0xFFE5EEE4),
)

private val HighContrastWeekColors = LightWeekColors.copy(
    lived = Color(0xFF777777),
    someDone = Color(0xFF83B8FF),
    allDone = Color(0xFF003FB3),
    current = Color(0xFFB23D00),
    pinned = Color(0xFF6843B5),
    ahead = Color(0xFFE6E6E6),
)

val LocalWeekColors = staticCompositionLocalOf { LightWeekColors }

/**
 * Raised cards: white on paper in light mode, one step above the background in dark mode,
 * where the lowest container would look sunken instead.
 */
val ColorScheme.card: Color
    get() = if (background.luminance() < 0.5f) surfaceContainerLow else surfaceContainerLowest

private val BaseTypography = Typography()

private val NumberedTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
)

val ThemeOption.isDark: Boolean get() = this == ThemeOption.DeepInk

private val ThemeOption.colors: ColorScheme
    get() = when (this) {
        ThemeOption.FocusLight -> LightColors
        ThemeOption.WarmPaper -> WarmPaperColors
        ThemeOption.DeepInk -> DeepInkColors
        ThemeOption.SoftSage -> SoftSageColors
        ThemeOption.HighContrast -> HighContrastColors
    }

private val ThemeOption.weekColors: WeekColors
    get() = when (this) {
        ThemeOption.FocusLight -> LightWeekColors
        ThemeOption.WarmPaper -> WarmPaperWeekColors
        ThemeOption.DeepInk -> DeepInkWeekColors
        ThemeOption.SoftSage -> SoftSageWeekColors
        ThemeOption.HighContrast -> HighContrastWeekColors
    }

@Composable
fun NumberedTheme(theme: ThemeOption, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWeekColors provides theme.weekColors) {
        MaterialTheme(
            colorScheme = theme.colors,
            typography = NumberedTypography,
            content = content,
        )
    }
}
