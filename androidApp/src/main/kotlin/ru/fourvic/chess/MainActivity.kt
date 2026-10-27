package ru.fourvic.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.fourvic.chess.ui.ChessApp
import ru.fourvic.chess.ui.theme.ChessOnlineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ChessApplication
        setContent {
            ChessOnlineTheme {
                ChessApp(app.settings, app.online)
            }
        }
    }
}
