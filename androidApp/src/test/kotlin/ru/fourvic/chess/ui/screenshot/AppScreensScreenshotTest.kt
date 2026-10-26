package ru.fourvic.chess.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.fourvic.chess.puzzles.MemoryPuzzleProgressStore
import ru.fourvic.chess.settings.AppSettings
import ru.fourvic.chess.settings.BoardTheme
import ru.fourvic.chess.shared.puzzle.Puzzle
import ru.fourvic.chess.shared.puzzle.PuzzleProgress
import ru.fourvic.chess.shared.rating.Rating
import ru.fourvic.chess.ui.home.HomeScreen
import ru.fourvic.chess.ui.puzzles.PuzzleScreen
import ru.fourvic.chess.ui.puzzles.PuzzleViewModel
import ru.fourvic.chess.ui.settings.SettingsScreen
import ru.fourvic.chess.ui.theme.ChessOnlineTheme
import kotlin.random.Random

/** Screenshots of the home, settings and puzzle screens in Russian. See GameScreenScreenshotTest for commands. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class AppScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun home() {
        compose.setContent {
            ChessOnlineTheme(darkTheme = false) {
                HomeScreen(onAction = {}, puzzleSubtitle = "Задача дня ждёт вас · серия 3 дня")
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/home_ru.png")
    }

    @Test
    fun settings() {
        compose.setContent {
            ChessOnlineTheme(darkTheme = false) {
                SettingsScreen(settings = AppSettings(boardTheme = BoardTheme.GREEN), onChange = {}, onBack = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_ru.png")
    }

    @Test
    fun puzzle() {
        val puzzle = Puzzle.parseLine(
            "0009B\tr2qr1k1/b1p2ppp/pp4n1/P1P1p3/4P1n1/B2P2Pb/3NBP1P/RN1QR1K1 b - - 1 16\tb6c5 e2g4 h3g4 d1g4\t1500\tadvantage,middlegame,short",
        )!!
        val vm = PuzzleViewModel(
            source = { listOf(puzzle) },
            store = MemoryPuzzleProgressStore(PuzzleProgress(rating = Rating(1430.0, 90.0, 0.06), streak = 2, lastDailyDay = 0)),
            random = Random(1),
            ioDispatcher = Dispatchers.Unconfined,
        )
        compose.setContent { ChessOnlineTheme(darkTheme = false) { PuzzleScreen(vm, onBack = {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/puzzle_ru.png")
    }
}
