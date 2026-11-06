package ru.fourvic.chess.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.fourvic.chess.online.FakeOnlineClient
import ru.fourvic.chess.puzzles.MemoryPuzzleProgressStore
import ru.fourvic.chess.settings.AppLanguage
import ru.fourvic.chess.settings.AppSettings
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.puzzle.Puzzle
import ru.fourvic.chess.shared.puzzle.PuzzleProgress
import ru.fourvic.chess.shared.rating.Rating
import ru.fourvic.chess.settings.SettingsRepository
import ru.fourvic.chess.ui.BottomBarSpace
import ru.fourvic.chess.ui.BottomBar
import ru.fourvic.chess.ui.ChessApp
import ru.fourvic.chess.ui.Tab
import ru.fourvic.chess.ui.home.HomeScreen
import ru.fourvic.chess.ui.home.WelcomeScreen
import ru.fourvic.chess.ui.puzzles.PuzzleScreen
import ru.fourvic.chess.ui.puzzles.PuzzleViewModel
import ru.fourvic.chess.ui.settings.AccountSection
import ru.fourvic.chess.ui.settings.AccountViewModel
import ru.fourvic.chess.ui.settings.ProfileScreen
import ru.fourvic.chess.ui.theme.ChessOnlineTheme
import kotlin.random.Random

/** Screenshots of the tabs, the welcome screen and the account. English and dark unless the name says otherwise. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val me = PlayerInfo("me", "Magnus_88", 1542, country = "NO")

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            ChessOnlineTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Home(name: String = me.name) {
        HomeScreen(
            online = Samples.online(me.copy(name = name)),
            puzzles = Samples.puzzles(),
            tournaments = Samples.tournaments(),
            onOnline = {}, onQuickPlay = {}, onComputer = {}, onFriend = {}, onDailyPuzzle = {}, onPuzzles = {},
            onTournaments = {}, onProfile = {},
            hour = 19,
            liveCountdown = false,
        )
    }

    @Test
    fun home() = shot("home") { Home() }

    @Test
    fun homeLight() = shot("home_light", dark = false) { Home() }

    @Test
    @Config(qualifiers = "+ru")
    fun homeRussian() = shot("home_ru") { Home("Николай") }

    @Test
    @Config(qualifiers = "+zh")
    fun homeChinese() = shot("home_zh") { Home("棋手48213") }

    /** The Play tab with the floating bottom bar. */
    @Test
    fun homeWithBottomBar() = shot("shell") {
        Box(Modifier.fillMaxSize()) {
            Home()
            Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(selected = Tab.PLAY, onSelect = {}) }
        }
    }

    /** The whole app after the welcome screen comes up with its tabs (no picture: the greeting follows the clock). */
    @Test
    fun shellShowsTheTabs() {
        val settings = SettingsRepository(RuntimeEnvironment.getApplication()).apply { update { it.copy(onboarded = true) } }
        compose.setContent { ChessOnlineTheme(darkTheme = true) { ChessApp(settings, FakeOnlineClient(me, Samples.ratings, registered = true)) } }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.assertCountEquals(4)
        listOf("Play", "Puzzles", "Tournaments", "Profile").forEachIndexed { index, label -> tabs[index].assertTextEquals(label) }
    }

    @Test
    fun welcome() = shot("welcome") { WelcomeScreen(AppLanguage.ENGLISH, onLanguage = {}, onStart = {}) }

    @Test
    fun profile() {
        val account = AccountViewModel(FakeOnlineClient(me, Samples.ratings, registered = true))
        shot("profile") {
            ProfileScreen(
                settings = AppSettings(),
                onChange = {},
                account = account,
                online = Samples.online(me),
                puzzleRating = 1630,
                language = AppLanguage.ENGLISH,
                onLanguage = {},
            )
        }
    }

    /** The account card for a guest (above) and for a saved account (below). */
    @Test
    fun account() {
        val guest = AccountViewModel(FakeOnlineClient(PlayerInfo("g", "Player48213", 1500, provisional = true)))
        val saved = AccountViewModel(FakeOnlineClient(me, registered = true))
        shot("account") {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountSection(guest, AppSettings(), onChange = {}, showServerAddress = false)
                AccountSection(saved, AppSettings(), onChange = {}, showServerAddress = false)
            }
        }
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
        shot("puzzle") {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                PuzzleScreen(vm, onBack = null, bottomSpace = BottomBarSpace)
            }
        }
    }

    @Test
    @Config(qualifiers = "+ru")
    fun puzzleSolvedRussian() {
        val puzzle = Puzzle.parseLine("00008\tr6k/pp2r2p/4Rp1Q/3p4/8/1N1P2R1/PqP2bPP/7K b - - 0 24\tf2g3 e6e7 b2b1 b3c1 b1c1 h6c1\t1800\tcrushing,hangingPiece,long,middlegame")!!
        val vm = PuzzleViewModel(
            source = { listOf(puzzle) },
            store = MemoryPuzzleProgressStore(PuzzleProgress(rating = Rating(1430.0, 90.0, 0.06), streak = 2, lastDailyDay = 0)),
            random = Random(1),
            ioDispatcher = Dispatchers.Unconfined,
            replyDelayMs = 0,
        )
        vm.showSolution()
        shot("puzzle_solved_ru", dark = false) {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                PuzzleScreen(vm, onBack = null, bottomSpace = BottomBarSpace)
            }
        }
    }
}
