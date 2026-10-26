package ru.fourvic.chess.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BoardTheme { CLASSIC, GREEN, BLUE, GRAY }

/** User preferences that change how the board looks and sounds. */
data class AppSettings(
    val boardTheme: BoardTheme = BoardTheme.CLASSIC,
    val showCoordinates: Boolean = true,
    val showLegalMoves: Boolean = true,
    val sound: Boolean = true,
)

/** Settings stored in SharedPreferences and exposed as a flow for Compose. */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        prefs.edit()
            .putString(KEY_THEME, next.boardTheme.name)
            .putBoolean(KEY_COORDINATES, next.showCoordinates)
            .putBoolean(KEY_LEGAL_MOVES, next.showLegalMoves)
            .putBoolean(KEY_SOUND, next.sound)
            .apply()
        state.value = next
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            boardTheme = prefs.getString(KEY_THEME, null)
                ?.let { name -> BoardTheme.entries.firstOrNull { it.name == name } }
                ?: defaults.boardTheme,
            showCoordinates = prefs.getBoolean(KEY_COORDINATES, defaults.showCoordinates),
            showLegalMoves = prefs.getBoolean(KEY_LEGAL_MOVES, defaults.showLegalMoves),
            sound = prefs.getBoolean(KEY_SOUND, defaults.sound),
        )
    }

    private companion object {
        const val KEY_THEME = "board_theme"
        const val KEY_COORDINATES = "show_coordinates"
        const val KEY_LEGAL_MOVES = "show_legal_moves"
        const val KEY_SOUND = "sound"
    }
}

/** Current settings for composables deep in the tree, such as the board. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
