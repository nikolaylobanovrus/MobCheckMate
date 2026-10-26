package com.mobcheckmate.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mobcheckmate.chess.ui.game.GameScreen
import com.mobcheckmate.chess.ui.theme.ChessOnlineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessOnlineTheme {
                GameScreen()
            }
        }
    }
}
