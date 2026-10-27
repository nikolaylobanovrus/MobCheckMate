package ru.fourvic.chess.ui.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.fourvic.chess.R
import ru.fourvic.chess.online.FakeOnlineClient
import ru.fourvic.chess.puzzles.MemoryPuzzleProgressStore
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.shared.engine.ChessBot
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.RatingInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.protocol.Standing
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus
import ru.fourvic.chess.shared.puzzle.Puzzle
import ru.fourvic.chess.shared.puzzle.PuzzleProgress
import ru.fourvic.chess.shared.rating.Rating
import ru.fourvic.chess.ui.board.ChessBoard
import ru.fourvic.chess.ui.game.GameScreen
import ru.fourvic.chess.ui.game.GameViewModel
import ru.fourvic.chess.ui.game.Opponent
import ru.fourvic.chess.ui.home.HomeScreen
import ru.fourvic.chess.ui.online.OnlineScreen
import ru.fourvic.chess.ui.online.OnlineViewModel
import ru.fourvic.chess.ui.puzzles.PuzzleScreen
import ru.fourvic.chess.ui.puzzles.PuzzleViewModel
import ru.fourvic.chess.ui.theme.ChessOnlineTheme
import ru.fourvic.chess.ui.tournaments.TournamentViewModel
import ru.fourvic.chess.ui.tournaments.TournamentsScreen
import kotlin.random.Random

/**
 * Store listing images: 1080x1920 screenshots in Russian and English, the 512x512 icon and the 1024x500 feature
 * graphic. Not part of the regular test run:
 * `./gradlew :androidApp:recordRoborazziPlayDebug -PstoreAssets --tests '*StoreAssets*'` writes them to docs/store/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class StoreAssetsTest(private val lang: String) {

    @get:Rule
    val compose = createComposeRule()

    private val out get() = "../docs/store/$lang"
    private val ru get() = lang == "ru"
    private val me get() = PlayerInfo("me", if (ru) "Николай" else "Nick", 1542)
    private val rival get() = PlayerInfo("op", if (ru) "Магнус_88" else "Magnus_88", 1610)
    private val client get() = FakeOnlineClient(me, listOf(RatingInfo(Speed.BLITZ, 1542, 37, false)))

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Set -PstoreAssets to render store images", System.getProperty("storeAssets") != null)
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ChessOnlineTheme(darkTheme = false) { content() } }
        compose.mainClock.advanceTimeBy(700) // progress indicators past their first frame
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test
    fun onlineGame() {
        val vm = OnlineViewModel(client, { 0L }, tickerEnabled = false)
        vm.onMessage(
            ServerMessage.Game(
                GameSnapshot(
                    gameId = "g1", white = me, black = rival, clock = Clock(180, 2), rated = true,
                    moves = listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6", "c1e3", "e7e5"),
                    whiteMs = 151_000, blackMs = 139_000, clockRunning = true,
                ),
            ),
        )
        vm.onMessage(ServerMessage.Chat("g1", PlayerColor.BLACK, QuickPhrase.GOOD_LUCK))
        vm.onSquareTapped("d4")
        capture("screenshots/01_online") { OnlineScreen(vm, onBack = {}) }
    }

    @Test
    fun tournaments() {
        val vm = TournamentViewModel(client, nowMs = { 0L })
        val arenaGames = OnlineViewModel(client, { 0L }, false, arena = true)
        vm.onMessage(ServerMessage.Tournaments(arenas()))
        capture("screenshots/02_tournaments") {
            TournamentsScreen(vm, arenaGames, onBack = {}, liveCountdown = false)
        }
    }

    @Test
    fun standings() {
        val vm = TournamentViewModel(client, nowMs = { 0L })
        val arenaGames = OnlineViewModel(client, { 0L }, false, arena = true)
        val arena = arenas().first()
        vm.open(arena.id)
        val names = if (ru) listOf("Магнус_88", "Николай", "Ладья", "Конь_в_пальто", "Гамбит", "Пешка_2010", "Ферзь")
        else listOf("Magnus_88", "Nick", "Rook_Lift", "KnightRider", "Gambit", "Pawnstorm", "QueenB")
        val scores = listOf(18, 13, 11, 9, 8, 6, 4)
        val sheets = listOf(listOf(2, 2, 4, 4, 0, 2, 4), listOf(2, 0, 2, 2, 4, 3), listOf(0, 2, 2, 4, 1, 2), listOf(2, 1, 0, 2, 2, 2),
            listOf(2, 0, 2, 0, 2, 2), listOf(0, 2, 0, 2, 2), listOf(2, 0, 0, 2))
        val lines = names.mapIndexed { i, name ->
            Standing(i + 1, PlayerInfo(if (i == 1) "me" else "p$i", name, 1720 - 35 * i), scores[i], sheets[i].size,
                fire = i == 0, paused = false, sheet = sheets[i])
        }
        vm.onMessage(ServerMessage.Tournament(arena, lines, lines[1]))
        capture("screenshots/03_standings") {
            TournamentsScreen(vm, arenaGames, onBack = {}, liveCountdown = false)
        }
    }

    @Test
    fun computerGame() {
        // Giuoco Piano after 4...Nf6, White to move: a calm position rather than a scripted game against the bot.
        val vm = GameViewModel(
            startFen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R w KQkq - 3 5",
            bot = ChessBot(Random(5)),
            botDispatcher = Dispatchers.Unconfined,
            initialOpponent = Opponent.Computer(BotLevel.LEVEL_4, PlayerColor.WHITE),
        )
        vm.onSquareTapped("d2")
        capture("screenshots/04_computer") { GameScreen(vm) }
    }

    @Test
    fun puzzle() {
        val puzzle = Puzzle.parseLine(
            "0009B\tr2qr1k1/b1p2ppp/pp4n1/P1P1p3/4P1n1/B2P2Pb/3NBP1P/RN1QR1K1 b - - 1 16\tb6c5 e2g4 h3g4 d1g4\t1500\tadvantage,middlegame,short",
        )!!
        val vm = PuzzleViewModel(
            source = { listOf(puzzle) },
            store = MemoryPuzzleProgressStore(PuzzleProgress(rating = Rating(1630.0, 80.0, 0.06), streak = 5, lastDailyDay = 0)),
            random = Random(1),
            ioDispatcher = Dispatchers.Unconfined,
        )
        capture("screenshots/05_puzzles") { PuzzleScreen(vm, onBack = {}) }
    }

    @Test
    fun home() {
        capture("screenshots/06_home") {
            val subtitle = stringResource(R.string.puzzle_daily_waiting) + " · " + pluralStringResource(R.plurals.puzzle_streak, 5, 5)
            HomeScreen(onAction = {}, puzzleSubtitle = subtitle)
        }
    }

    @Test
    @Config(qualifiers = "+w1024dp-h500dp-mdpi")
    fun featureGraphic() {
        val board = GameViewModel().apply {
            listOf("e2", "e4", "e7", "e5", "g1", "f3", "b8", "c6", "f1", "b5", "a7", "a6").forEach(::onSquareTapped)
        }.state
        capture("feature_graphic") {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.app_name), color = CREAM, fontSize = 60.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp)
                        Text(
                            if (ru) "Турниры каждый час\nБот на 5 уровнях\n29 505 задач" else "Hourly tournaments\nA bot with 5 levels\n29,505 puzzles",
                            color = CREAM,
                            fontSize = 28.sp,
                            lineHeight = 38.sp,
                        )
                    }
                    ChessBoard(state = board, onSquareTapped = {}, contentDescription = "", modifier = Modifier.size(400.dp))
                }
            }
        }
    }

    private fun arenas(): List<TournamentInfo> {
        fun arena(id: String, schedule: String, clock: Clock, minutes: Int, status: TournamentStatus, msLeft: Long, players: Int, winner: String? = null) =
            TournamentInfo(id, schedule, clock, minutes, status, msLeft, players, rated = true, schedule = schedule, winner = winner)
        return listOf(
            arena("hourly_blitz-1", "hourly_blitz", Clock(180, 0), 30, TournamentStatus.STARTED, 1_354_000, 48),
            arena("hourly_bullet-2", "hourly_bullet", Clock(60, 0), 30, TournamentStatus.CREATED, 1_954_000, 17),
            arena("daily_blitz-3", "daily_blitz", Clock(180, 2), 60, TournamentStatus.CREATED, 6_154_000, 63),
            arena("daily_rapid-4", "daily_rapid", Clock(600, 0), 60, TournamentStatus.CREATED, 11_554_000, 21),
            arena("hourly_bullet-1", "hourly_bullet", Clock(60, 0), 30, TournamentStatus.FINISHED, 0, 52, winner = rival.name),
        )
    }

    private companion object {
        val CREAM = Color(0xFFF4EBD9)
    }
}

@Config(sdk = [36], qualifiers = "ru-w360dp-h640dp-xxhdpi")
class StoreAssetsRuTest : StoreAssetsTest("ru")

@Config(sdk = [36], qualifiers = "en-w360dp-h640dp-xxhdpi")
class StoreAssetsEnTest : StoreAssetsTest("en") {

    /** The 512x512 store icon: the adaptive icon's layers without a mask; the stores round the corners themselves. */
    @Test
    @Config(qualifiers = "+w512dp-h512dp-mdpi")
    fun icon() {
        compose.setContent {
            Box(Modifier.size(512.dp)) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
        }
        compose.onRoot().captureRoboImage("../docs/store/icon_512.png")
    }
}
