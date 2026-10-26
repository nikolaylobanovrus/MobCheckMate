package ru.fourvic.chess.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E4A3B)
private val Cream = Color(0xFFF4EBD9)

private val LightColors = lightColorScheme(primary = Green, onPrimary = Cream, secondary = Color(0xFF8B5E34))
private val DarkColors = darkColorScheme(primary = Color(0xFF9CC5A1), onPrimary = Color(0xFF10261A), secondary = Color(0xFFD9A066))

@Composable
fun ChessOnlineTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

/**
 * Board colours do not follow the app theme on purpose: pieces must stay readable in dark mode,
 * which was a long-standing bug in the reference app.
 */
data class BoardPalette(
    val light: Color,
    val dark: Color,
    val selected: Color = Color(0x9946A35A),
    val lastMove: Color = Color(0x66E8D44D),
    val check: Color = Color(0xAAD7263D),
    val target: Color = Color(0x66202020),
) {
    companion object {
        val Classic = BoardPalette(light = Color(0xFFEEDDBB), dark = Color(0xFFB58863))
        val Green = BoardPalette(light = Color(0xFFEEEED2), dark = Color(0xFF769656))
        val Blue = BoardPalette(light = Color(0xFFDEE3E6), dark = Color(0xFF8CA2AD))
        val Gray = BoardPalette(light = Color(0xFFE6E6E6), dark = Color(0xFF9A9A9A))

        fun of(theme: ru.fourvic.chess.settings.BoardTheme) = when (theme) {
            ru.fourvic.chess.settings.BoardTheme.CLASSIC -> Classic
            ru.fourvic.chess.settings.BoardTheme.GREEN -> Green
            ru.fourvic.chess.settings.BoardTheme.BLUE -> Blue
            ru.fourvic.chess.settings.BoardTheme.GRAY -> Gray
        }
    }
}
