package de.fembiond.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fembiond.app.data.ThemeMode
import de.fembiond.app.domain.Phase

private val Light = lightColorScheme(
    primary = Color(0xFFA0295B), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E0020),
    secondary = Color(0xFF7A5368), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8EA), onSecondaryContainer = Color(0xFF2F1123),
    tertiary = Color(0xFF8C4A1F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC8), onTertiaryContainer = Color(0xFF331200),
    background = Color(0xFFFFF8F8), onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8), onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF3DDE2), onSurfaceVariant = Color(0xFF514347),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCF0F2),
    surfaceContainer = Color(0xFFF6EAEC), surfaceContainerHigh = Color(0xFFF0E4E7),
    surfaceContainerHighest = Color(0xFFEADFE1),
    outline = Color(0xFF847377), outlineVariant = Color(0xFFD6C2C6),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C8), onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7E2949), onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE3BDD0), onSecondary = Color(0xFF432839),
    secondaryContainer = Color(0xFF5B3E50), onSecondaryContainer = Color(0xFFFFD8EA),
    tertiary = Color(0xFFFFB68E), onTertiary = Color(0xFF532200),
    tertiaryContainer = Color(0xFF6F3810), onTertiaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF1A1418), onBackground = Color(0xFFEFDFE3),
    surface = Color(0xFF1A1418), onSurface = Color(0xFFEFDFE3),
    surfaceVariant = Color(0xFF514347), onSurfaceVariant = Color(0xFFD5C2C7),
    surfaceContainerLowest = Color(0xFF140F12), surfaceContainerLow = Color(0xFF221B20),
    surfaceContainer = Color(0xFF271F24), surfaceContainerHigh = Color(0xFF31292E),
    surfaceContainerHighest = Color(0xFF3C3339),
    outline = Color(0xFF9E8C91), outlineVariant = Color(0xFF514347),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

/** Phasenfarben – immer zusammen mit Beschriftung/Symbol verwenden, nie allein. */
@Immutable
data class PhaseColors(
    val menstruation: Color,
    val follicular: Color,
    val ovulation: Color,
    val luteal: Color,
    val fertile: Color,
    val heatLow: Color,
    val heatHigh: Color,
) {
    fun of(p: Phase): Color = when (p) {
        Phase.MENSTRUATION -> menstruation
        Phase.FOLLICULAR -> follicular
        Phase.OVULATION -> ovulation
        Phase.LUTEAL -> luteal
    }
}

private val LightPhase = PhaseColors(
    menstruation = Color(0xFFC2185B), follicular = Color(0xFFD9783A), ovulation = Color(0xFF7E6BC0),
    luteal = Color(0xFF7B3F73), fertile = Color(0xFFE6DDF7), heatLow = Color(0xFFFCEFF3), heatHigh = Color(0xFF9C1E55),
)
private val DarkPhase = PhaseColors(
    menstruation = Color(0xFFF48FB1), follicular = Color(0xFFFFB68E), ovulation = Color(0xFFC5B6F2),
    luteal = Color(0xFFD7A6CF), fertile = Color(0xFF3B3352), heatLow = Color(0xFF2A2126), heatHigh = Color(0xFFFF8FB5),
)

val LocalPhaseColors = staticCompositionLocalOf { LightPhase }

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun FembiondTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = if (dark) Dark else Light
    CompositionLocalProvider(LocalPhaseColors provides if (dark) DarkPhase else LightPhase) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

val SmallCaps = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
