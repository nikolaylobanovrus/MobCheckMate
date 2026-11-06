package ru.fourvic.chess.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BoardTheme { CLASSIC, GREEN, BLUE, GRAY, ROYAL, NIGHT }

/** Light or dark interface; [SYSTEM] follows the phone. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User preferences that change how the app looks, sounds and feels. */
data class AppSettings(
    val boardTheme: BoardTheme = BoardTheme.GREEN,
    val showCoordinates: Boolean = true,
    val showLegalMoves: Boolean = true,
    val sound: Boolean = true,
    /** A short vibration on every move. */
    val haptics: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The welcome screen with the language choice has been passed. */
    val onboarded: Boolean = false,
    /** Game server address for testing; empty means the address built into the app. */
    val serverUrl: String = "",
)

/** Settings stored in SharedPreferences and exposed as a flow for Compose. */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        prefs.edit {
            putString(KEY_THEME, next.boardTheme.name)
            putBoolean(KEY_COORDINATES, next.showCoordinates)
            putBoolean(KEY_LEGAL_MOVES, next.showLegalMoves)
            putBoolean(KEY_SOUND, next.sound)
            putBoolean(KEY_HAPTICS, next.haptics)
            putString(KEY_THEME_MODE, next.themeMode.name)
            putBoolean(KEY_ONBOARDED, next.onboarded)
            putString(KEY_SERVER_URL, next.serverUrl)
        }
        state.value = next
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            boardTheme = enumOrNull<BoardTheme>(prefs.getString(KEY_THEME, null)) ?: defaults.boardTheme,
            showCoordinates = prefs.getBoolean(KEY_COORDINATES, defaults.showCoordinates),
            showLegalMoves = prefs.getBoolean(KEY_LEGAL_MOVES, defaults.showLegalMoves),
            sound = prefs.getBoolean(KEY_SOUND, defaults.sound),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            themeMode = enumOrNull<ThemeMode>(prefs.getString(KEY_THEME_MODE, null)) ?: defaults.themeMode,
            onboarded = prefs.getBoolean(KEY_ONBOARDED, defaults.onboarded),
            serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: defaults.serverUrl,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }

    private companion object {
        const val KEY_THEME = "board_theme"
        const val KEY_COORDINATES = "show_coordinates"
        const val KEY_LEGAL_MOVES = "show_legal_moves"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_SERVER_URL = "server_url"
    }
}

/** Current settings for composables deep in the tree, such as the board. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** Languages of the interface. English is the default whatever the phone's language. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    CHINESE("zh", "中文"),
    ;

    companion object {
        fun of(tag: String?): AppLanguage? = entries.firstOrNull { tag?.startsWith(it.tag) == true }
    }
}

/**
 * The app's own language, kept by AndroidX per-app locales: Android 13+ stores it in the system (it also shows in
 * the phone's app language settings), older versions in the app. Changing it recreates the activity.
 */
object Languages {
    val current: AppLanguage
        get() = AppLanguage.of(AppCompatDelegate.getApplicationLocales()[0]?.language) ?: AppLanguage.ENGLISH

    /** Nothing chosen yet means the phone's language would be used; the app starts in English instead. */
    fun ensureChosen() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) set(AppLanguage.ENGLISH)
    }

    /**
     * The app language with the phone's region, such as "en-RU": the server takes the default player name from the
     * language and the flag next to the name from the region.
     */
    fun serverLocaleTag(): String {
        val region = android.content.res.Resources.getSystem().configuration.locales[0]?.country.orEmpty()
        return if (region.length == 2) "${current.tag}-$region" else current.tag
    }

    fun set(language: AppLanguage) {
        if (AppCompatDelegate.getApplicationLocales()[0]?.language == language.tag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}

/** Applies [mode] to the whole app, so system bars and the window background match the Compose theme. */
fun applyThemeMode(mode: ThemeMode) {
    val night = when (mode) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
    if (AppCompatDelegate.getDefaultNightMode() != night) AppCompatDelegate.setDefaultNightMode(night)
}
