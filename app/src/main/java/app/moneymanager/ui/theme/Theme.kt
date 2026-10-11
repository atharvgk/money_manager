package app.moneymanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.moneymanager.data.ThemeMode

// One fixed palette (not the wallpaper colours): soft grey-green background, white cards, deep green accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EFE3),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF4C6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBE3),
    onSecondaryContainer = Color(0xFF14352A),
    tertiary = Color(0xFF3E6374),
    background = Color(0xFFF2F4F3),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF2F4F3),
    onSurface = Color(0xFF171D1A),
    onSurfaceVariant = Color(0xFF5C6661),
    surfaceVariant = Color(0xFFE2E7E4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE6EBE8),
    outline = Color(0xFF89938E),
    outlineVariant = Color(0xFFDDE3E0),
    inverseSurface = Color(0xFF2C322F),
    inverseOnSurface = Color(0xFFEFF2F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD3AC),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF124C3A),
    onPrimaryContainer = Color(0xFFBDF2DC),
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF26372F),
    onSecondaryContainer = Color(0xFFCFE8DC),
    tertiary = Color(0xFFA6CCE0),
    background = Color(0xFF0E1110),
    onBackground = Color(0xFFE2E7E4),
    surface = Color(0xFF0E1110),
    onSurface = Color(0xFFE2E7E4),
    onSurfaceVariant = Color(0xFFA2ACA7),
    surfaceVariant = Color(0xFF29302C),
    surfaceContainerLowest = Color(0xFF0A0C0B),
    surfaceContainerLow = Color(0xFF171B19),
    surfaceContainer = Color(0xFF171B19),
    surfaceContainerHigh = Color(0xFF1E2320),
    surfaceContainerHighest = Color(0xFF29302C),
    outline = Color(0xFF7C8781),
    outlineVariant = Color(0xFF2B312E),
    inverseSurface = Color(0xFFE2E7E4),
    inverseOnSurface = Color(0xFF2C322F),
)

/** Colours with a fixed meaning. Status is always paired with an arrow or a word, never colour alone. */
@Immutable
data class MoneyColors(
    val dark: Boolean,
    /** Card background (white on the grey page in light mode). */
    val card: Color,
    val income: Color,
    /** Spending went up / over budget. */
    val worse: Color,
    /** Spending went down / within budget. */
    val better: Color,
    val worseContainer: Color,
    val betterContainer: Color,
    /** Expected but not seen (a bill that hasn't shown up). */
    val warning: Color,
    val chartSeries: Color,
    /** Bars that aren't selected, and last month's line. */
    val chartMuted: Color,
    val gridline: Color,
)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(
        false, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
    )
}

private val LightMoney = MoneyColors(
    dark = false,
    card = Color(0xFFFFFFFF),
    income = Color(0xFF006D2E),
    worse = Color(0xFFC62F2F),
    better = Color(0xFF006D2E),
    worseContainer = Color(0x1AC62F2F),
    betterContainer = Color(0x1A0CA30C),
    warning = Color(0xFFB25E00),
    chartSeries = Color(0xFF0E6B4F),
    chartMuted = Color(0xFFCFE2D9),
    gridline = Color(0xFFE6EAE8),
)

private val DarkMoney = MoneyColors(
    dark = true,
    card = Color(0xFF171B19),
    income = Color(0xFF4FCB7E),
    worse = Color(0xFFEF7474),
    better = Color(0xFF4FCB7E),
    worseContainer = Color(0x29EF7474),
    betterContainer = Color(0x294FCB7E),
    warning = Color(0xFFE6A23C),
    chartSeries = Color(0xFF6FD3AC),
    chartMuted = Color(0xFF2E3F37),
    gridline = Color(0xFF262C29),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun MoneyManagerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
