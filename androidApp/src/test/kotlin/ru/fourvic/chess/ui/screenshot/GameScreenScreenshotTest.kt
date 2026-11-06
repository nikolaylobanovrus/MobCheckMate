package ru.fourvic.chess.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import ru.fourvic.chess.ui.game.GameScreen
import ru.fourvic.chess.ui.game.GameViewModel
import ru.fourvic.chess.ui.game.NewGameSetup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.ui.game.Opponent
import ru.fourvic.chess.ui.theme.ChessOnlineTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.shared.engine.ChessBot
import kotlin.random.Random

/**
 * Screenshots of the game screen rendered on the JVM.
 * Record: `./gradlew :androidApp:recordRoborazziPlayDebug`; compare: `verifyRoborazziPlayDebug`.
 * Images go to `androidApp/src/test/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class GameScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun italianGameWithKnightSelected() = GameViewModel().apply {
        listOf("e2", "e4", "e7", "e5", "g1", "f3", "b8", "c6", "f1", "c4", "g8", "f6", "f3")
            .forEach(::onSquareTapped)
    }

    @Test
    fun lightTheme() {
        compose.setContent { ChessOnlineTheme(darkTheme = false) { GameScreen(italianGameWithKnightSelected()) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/game_light.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkThemeKeepsBlackPiecesReadable() {
        compose.setContent { ChessOnlineTheme(darkTheme = true) { GameScreen(italianGameWithKnightSelected()) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/game_dark.png")
    }

    @Test
    @Config(qualifiers = "+ru")
    fun russianCheckmate() {
        val vm = GameViewModel().apply {
            listOf("f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4").forEach(::onSquareTapped)
        }
        compose.setContent { ChessOnlineTheme(darkTheme = true) { GameScreen(vm) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/game_checkmate_ru.png")
    }

    @Test
    @Config(qualifiers = "+zh")
    fun chineseGameAgainstTheComputer() {
        // Unconfined: the bot answers synchronously, so the screenshot is deterministic.
        val vm = GameViewModel(
            bot = ChessBot(Random(5)),
            botDispatcher = Dispatchers.Unconfined,
            initialOpponent = Opponent.Computer(BotLevel.LEVEL_3, PlayerColor.WHITE),
        )
        listOf("e2", "e4", "g1", "f3").forEach(vm::onSquareTapped)
        compose.setContent { ChessOnlineTheme(darkTheme = true) { GameScreen(vm) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/game_vs_computer_zh.png")
    }

    @Test
    fun newGameSheet() {
        compose.setContent {
            ChessOnlineTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 24.dp)) {
                    NewGameSetup(current = Opponent.Computer(BotLevel.LEVEL_3, PlayerColor.WHITE), onStart = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/new_game_sheet.png")
    }
}
