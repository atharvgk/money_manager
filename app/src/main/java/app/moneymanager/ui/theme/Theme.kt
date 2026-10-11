package app.moneymanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.moneymanager.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0D6),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    secondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFF3E6374),
    background = Color(0xFFFCFCFB),
    surface = Color(0xFFFCFCFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F5),
    surfaceContainer = Color(0xFFF0F2EF),
    surfaceContainerHigh = Color(0xFFEAECE9),
    surfaceContainerHighest = Color(0xFFE4E6E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD8B3),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFB4F0D6),
    secondary = Color(0xFFB3CCBF),
    secondaryContainer = Color(0xFF354B40),
    tertiary = Color(0xFFA6CCE0),
    background = Color(0xFF121211),
    surface = Color(0xFF121211),
    surfaceContainerLowest = Color(0xFF0D0D0C),
    surfaceContainerLow = Color(0xFF1A1A19),
    surfaceContainer = Color(0xFF1E1F1D),
    surfaceContainerHigh = Color(0xFF282927),
    surfaceContainerHighest = Color(0xFF333432),
)

/** Colours with a fixed meaning that must not follow the dynamic wallpaper palette. */
@Immutable
data class MoneyColors(
    val dark: Boolean,
    val income: Color,
    /** Spending went up: shown with ▲ and a label, never colour alone. */
    val worse: Color,
    /** Spending went down. */
    val better: Color,
    val worseContainer: Color,
    val betterContainer: Color,
    val chartSeries: Color,
    val gridline: Color,
)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(false, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

private val LightMoney = MoneyColors(
    dark = false,
    income = Color(0xFF006300),
    worse = Color(0xFFD03B3B),
    better = Color(0xFF006300),
    worseContainer = Color(0x1FD03B3B),
    betterContainer = Color(0x1F0CA30C),
    chartSeries = Color(0xFF2A78D6),
    gridline = Color(0xFFE1E0D9),
)

private val DarkMoney = MoneyColors(
    dark = true,
    income = Color(0xFF0CA30C),
    worse = Color(0xFFE66767),
    better = Color(0xFF0CA30C),
    worseContainer = Color(0x33E66767),
    betterContainer = Color(0x330CA30C),
    chartSeries = Color(0xFF3987E5),
    gridline = Color(0xFF2C2C2A),
)

@Composable
fun MoneyManagerTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
