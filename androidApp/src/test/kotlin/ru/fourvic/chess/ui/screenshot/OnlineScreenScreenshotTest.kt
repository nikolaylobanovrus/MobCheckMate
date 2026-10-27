package ru.fourvic.chess.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.fourvic.chess.online.FakeOnlineClient
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.RatingInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.ui.online.OnlineScreen
import ru.fourvic.chess.ui.online.OnlineViewModel
import ru.fourvic.chess.ui.theme.ChessOnlineTheme

/** Screenshots of online play in Russian. See GameScreenScreenshotTest for commands. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class OnlineScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val me = PlayerInfo("me", "Николай", 1542, country = "RU")
    private val opponent = PlayerInfo("op", "Дмитрий_К", 1610, country = "KZ")
    private val ratings = listOf(
        RatingInfo(Speed.BULLET, 1500, 0, provisional = true),
        RatingInfo(Speed.BLITZ, 1542, 37, provisional = false),
        RatingInfo(Speed.RAPID, 1611, 12, provisional = false),
        RatingInfo(Speed.CLASSICAL, 1500, 0, provisional = true),
    )
    private var now = 0L

    private fun viewModel() = OnlineViewModel(FakeOnlineClient(me, ratings), nowMs = { now }, tickerEnabled = false)

    @Test
    fun lobby() {
        val vm = viewModel()
        compose.setContent { ChessOnlineTheme(darkTheme = false) { OnlineScreen(vm, onBack = {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/online_lobby_ru.png")
    }

    @Test
    fun game() {
        val vm = viewModel()
        val moves = listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6")
        vm.onMessage(
            ServerMessage.Game(
                GameSnapshot(
                    gameId = "g1", white = me, black = opponent, clock = Clock(180, 2), rated = true, moves = moves,
                    whiteMs = 151_000, blackMs = 8_400, clockRunning = true,
                ),
            ),
        )
        vm.onMessage(ServerMessage.Chat("g1", PlayerColor.BLACK, QuickPhrase.GOOD_LUCK))
        vm.onMessage(ServerMessage.DrawOffered("g1", PlayerColor.BLACK))
        vm.onSquareTapped("f1")
        compose.setContent { ChessOnlineTheme(darkTheme = false) { OnlineScreen(vm, onBack = {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/online_game_ru.png")
    }

    @Test
    fun result() {
        val vm = viewModel()
        vm.onMessage(
            ServerMessage.Game(
                GameSnapshot(
                    gameId = "g1", white = opponent, black = me, clock = Clock(300, 0), rated = true,
                    moves = listOf("f2f3", "e7e5", "g2g4", "d8h4"), whiteMs = 297_000, blackMs = 296_500, clockRunning = true,
                ),
            ),
        )
        vm.onMessage(ServerMessage.Ended("g1", GameResult.BLACK_WON, EndReason.CHECKMATE, -9, 9))
        vm.onMessage(ServerMessage.RematchOffered("g1", PlayerColor.WHITE))
        compose.setContent { ChessOnlineTheme(darkTheme = false) { OnlineScreen(vm, onBack = {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/online_result_ru.png")
    }
}
