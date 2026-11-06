package ru.fourvic.chess

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.fourvic.chess.settings.Languages
import ru.fourvic.chess.settings.ThemeMode
import ru.fourvic.chess.ui.ChessApp
import ru.fourvic.chess.ui.theme.ChessOnlineTheme

/** The only activity. AppCompat gives it the per-app language and the light/dark choice on every Android version. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ChessApplication
        // English until the user picks a language on the welcome screen. It has to be set here, not in the
        // Application: AppCompat reaches the system's per-app language service through a created activity, and on
        // Android 12 and older it reads the saved choice only now. Setting it recreates the activity once.
        if (!app.settings.settings.value.onboarded) Languages.ensureChosen()
        setContent {
            val settings by app.settings.settings.collectAsState()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Transparent bars with icons that stay readable on the current background.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ChessOnlineTheme(darkTheme = dark) {
                ChessApp(app.settings, app.online)
            }
        }
    }
}
