package com.numbered.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

/**
 * Numbered keeps a fixed palette instead of dynamic colour, because the life grid's colours carry
 * meaning and must read the same on every phone.
 */
private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

@Composable
fun NumberedTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWeekColors provides if (darkTheme) DarkWeekColors else LightWeekColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = NumberedTypography,
            content = content,
        )
    }
}
