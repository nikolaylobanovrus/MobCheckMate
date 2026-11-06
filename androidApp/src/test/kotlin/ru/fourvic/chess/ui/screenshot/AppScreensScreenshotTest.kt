package ru.fourvic.chess.ui.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.fourvic.chess.online.FakeOnlineClient
import ru.fourvic.chess.puzzles.MemoryPuzzleProgressStore
import ru.fourvic.chess.settings.AppSettings
import ru.fourvic.chess.settings.BoardTheme
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.puzzle.Puzzle
import ru.fourvic.chess.shared.puzzle.PuzzleProgress
import ru.fourvic.chess.shared.rating.Rating
import ru.fourvic.chess.ui.home.HomeScreen
import ru.fourvic.chess.ui.puzzles.PuzzleScreen
import ru.fourvic.chess.ui.puzzles.PuzzleViewModel
import ru.fourvic.chess.ui.settings.AccountSection
import ru.fourvic.chess.ui.settings.AccountViewModel
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

    /** The account section for a guest (above) and for a saved account (below). */
    @Test
    fun account() {
        val guest = AccountViewModel(FakeOnlineClient(PlayerInfo("g", "Игрок48213", 1500, provisional = true)))
        val savedVm = AccountViewModel(FakeOnlineClient(PlayerInfo("s", "Николай", 1542), registered = true))
        compose.setContent {
            ChessOnlineTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountSection(guest, AppSettings(), onChange = {}, showServerAddress = false)
                        AccountSection(savedVm, AppSettings(), onChange = {}, showServerAddress = false)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/account_ru.png")
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
