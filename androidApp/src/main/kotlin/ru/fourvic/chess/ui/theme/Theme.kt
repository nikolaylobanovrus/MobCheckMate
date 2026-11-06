package ru.fourvic.chess.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fourvic.chess.R
import ru.fourvic.chess.settings.BoardTheme

// --- Colours ------------------------------------------------------------------------------------------------------
// Dark first: a deep ink background, gold for victory and the main actions, emerald for wins and "online".

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5C04E),
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = Color(0xFF3B2D0B),
    onPrimaryContainer = Color(0xFFFFE2A1),
    inversePrimary = Color(0xFF8A5C00),
    secondary = Color(0xFF3DD68C),
    onSecondary = Color(0xFF00391E),
    secondaryContainer = Color(0xFF0F3A28),
    onSecondaryContainer = Color(0xFFA6F5CB),
    tertiary = Color(0xFF9AA8FF),
    onTertiary = Color(0xFF0E1A5E),
    tertiaryContainer = Color(0xFF26306A),
    onTertiaryContainer = Color(0xFFDDE1FF),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF4D1518),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFEEF1F5),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFEEF1F5),
    surfaceVariant = Color(0xFF1B222C),
    onSurfaceVariant = Color(0xFF9AA4B2),
    surfaceTint = Color(0xFFF5C04E),
    inverseSurface = Color(0xFFEEF1F5),
    inverseOnSurface = Color(0xFF151B23),
    outline = Color(0xFF3B4553),
    outlineVariant = Color(0xFF262E39),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A323D),
    surfaceDim = Color(0xFF0B0F14),
    surfaceContainerLowest = Color(0xFF07090D),
    surfaceContainerLow = Color(0xFF10151C),
    surfaceContainer = Color(0xFF151B23),
    surfaceContainerHigh = Color(0xFF1C232D),
    surfaceContainerHighest = Color(0xFF242C37),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A6300),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA3),
    onPrimaryContainer = Color(0xFF2B1D00),
    inversePrimary = Color(0xFFF5C04E),
    secondary = Color(0xFF0E8A56),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC2F3D8),
    onSecondaryContainer = Color(0xFF002113),
    tertiary = Color(0xFF4757C9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE1FF),
    onTertiaryContainer = Color(0xFF00105C),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF181B20),
    surface = Color(0xFFF6F4EF),
    onSurface = Color(0xFF181B20),
    surfaceVariant = Color(0xFFE7E2D8),
    onSurfaceVariant = Color(0xFF5B616D),
    surfaceTint = Color(0xFF9A6300),
    inverseSurface = Color(0xFF242830),
    inverseOnSurface = Color(0xFFF0EEE9),
    outline = Color(0xFF8D93A0),
    outlineVariant = Color(0xFFD9D3C7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE2DED5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF9F5),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0EDE6),
    surfaceContainerHighest = Color(0xFFE8E4DB),
)

/** Two-colour gradient for cards and avatars; drawn top-left to bottom-right. */
@Immutable
data class Gradient(val start: Color, val end: Color) {
    fun brush(): Brush = Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset.Infinite)
}

/** Colours outside the Material scheme: game results and one gradient per part of the app. */
@Immutable
data class BrandColors(
    val dark: Boolean,
    val win: Color,
    val loss: Color,
    val draw: Color,
    val gold: Color,
    val silver: Color,
    val bronze: Color,
    val online: Gradient = Gradient(Color(0xFF22C58B), Color(0xFF0C7B63)),
    val computer: Gradient = Gradient(Color(0xFF8E7BFF), Color(0xFF5440D6)),
    val friend: Gradient = Gradient(Color(0xFF40B8F5), Color(0xFF2667E8)),
    val puzzles: Gradient = Gradient(Color(0xFFFFB443), Color(0xFFF06A2A)),
    val tournaments: Gradient = Gradient(Color(0xFFF7C64F), Color(0xFFD9822B)),
    val danger: Gradient = Gradient(Color(0xFFFF6B6B), Color(0xFFD7263D)),
)

private val DarkBrand = BrandColors(
    dark = true,
    win = Color(0xFF3DD68C),
    loss = Color(0xFFFF6B6B),
    draw = Color(0xFF9AA4B2),
    gold = Color(0xFFF5C04E),
    silver = Color(0xFFC3CAD5),
    bronze = Color(0xFFD8945A),
)

private val LightBrand = BrandColors(
    dark = false,
    win = Color(0xFF0E8A56),
    loss = Color(0xFFC62828),
    draw = Color(0xFF5B616D),
    gold = Color(0xFFC88A12),
    silver = Color(0xFF8E97A6),
    bronze = Color(0xFFB06A33),
)

val LocalBrand = staticCompositionLocalOf { DarkBrand }

// --- Type ---------------------------------------------------------------------------------------------------------
// Manrope (SIL OFL) as a variable font: one file for every weight. Chinese falls back to the system font.

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, line: Int, weight: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

val AppTypography = Typography(
    displayLarge = style(52, 58, 800, -1.2),
    displayMedium = style(42, 48, 800, -0.9),
    displaySmall = style(34, 40, 800, -0.6),
    headlineLarge = style(30, 36, 800, -0.5),
    headlineMedium = style(26, 32, 800, -0.4),
    headlineSmall = style(22, 28, 800, -0.2),
    titleLarge = style(20, 26, 700, -0.1),
    titleMedium = style(16, 22, 700),
    titleSmall = style(14, 20, 700, 0.1),
    bodyLarge = style(16, 24, 500),
    bodyMedium = style(14, 20, 500),
    bodySmall = style(12, 16, 500, 0.1),
    labelLarge = style(14, 20, 700, 0.1),
    labelMedium = style(12, 16, 700, 0.3),
    labelSmall = style(11, 14, 700, 0.4),
)

/** Digits of equal width for clocks and scores. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ChessOnlineTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalBrand provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
            // Text outside a Surface would otherwise be black, unreadable on the dark background.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}

// --- Board --------------------------------------------------------------------------------------------------------

/**
 * Board colours do not follow the app theme on purpose: pieces must stay readable in dark mode,
 * which was a long-standing bug in the reference app.
 */
@Immutable
data class BoardPalette(
    val light: Color,
    val dark: Color,
    val lastMove: Color = Color(0x8AF2D25C),
    val selected: Color = Color(0x99F2C14E),
    val check: Color = Color(0xFFE5283C),
    val target: Color = Color(0x38141414),
) {
    companion object {
        val Classic = BoardPalette(light = Color(0xFFF0D9B5), dark = Color(0xFFB58863))
        val Green = BoardPalette(
            light = Color(0xFFEEEED2), dark = Color(0xFF769656),
            lastMove = Color(0x9EF5F682), selected = Color(0xB3BACA44),
        )
        val Blue = BoardPalette(light = Color(0xFFDDE5EF), dark = Color(0xFF7896BA), lastMove = Color(0x80A9D6F5))
        val Gray = BoardPalette(light = Color(0xFFE3E6EB), dark = Color(0xFF8C96A6))
        val Royal = BoardPalette(light = Color(0xFFEDE4F8), dark = Color(0xFF8C6BC2), lastMove = Color(0x8AF5C2E6))
        val Night = BoardPalette(
            light = Color(0xFFA7B2C4), dark = Color(0xFF55627A),
            lastMove = Color(0x8A7FC7FF), selected = Color(0x9988D1FF), target = Color(0x4D0B0F14),
        )

        fun of(theme: BoardTheme) = when (theme) {
            BoardTheme.CLASSIC -> Classic
            BoardTheme.GREEN -> Green
            BoardTheme.BLUE -> Blue
            BoardTheme.GRAY -> Gray
            BoardTheme.ROYAL -> Royal
            BoardTheme.NIGHT -> Night
        }
    }
}
