package com.mobcheckmate.chess.ui.theme

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
object BoardColors {
    val light = Color(0xFFEEDDBB)
    val dark = Color(0xFFB58863)
    val selected = Color(0x9946A35A)
    val lastMove = Color(0x66E8D44D)
    val check = Color(0xAAD7263D)
    val target = Color(0x66202020)
}
