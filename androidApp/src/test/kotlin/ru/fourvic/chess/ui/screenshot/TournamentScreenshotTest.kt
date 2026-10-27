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
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Standing
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.ui.online.OnlineViewModel
import ru.fourvic.chess.ui.theme.ChessOnlineTheme
import ru.fourvic.chess.ui.tournaments.TournamentViewModel
import ru.fourvic.chess.ui.tournaments.TournamentsScreen

/** Screenshots of the tournament screens in Russian. See GameScreenScreenshotTest for commands. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class TournamentScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val me = PlayerInfo("me", "Николай", 1542)
    private val client = FakeOnlineClient(me)
    private val now = 0L

    private fun arena(
        id: String,
        schedule: String?,
        clock: Clock,
        minutes: Int,
        status: TournamentStatus,
        msLeft: Long,
        players: Int,
        winner: String? = null,
        name: String = "Arena",
    ) = TournamentInfo(
        id = id, name = name, clock = clock, minutes = minutes, status = status, msLeft = msLeft, players = players,
        rated = true, private = schedule == null, schedule = schedule, winner = winner,
    )

    private val running = arena("hourly_blitz-1", "hourly_blitz", Clock(180, 0), 30, TournamentStatus.STARTED, 1_354_000, 23)
    private val standings = listOf(
        Standing(1, PlayerInfo("a", "Магнус_88", 1720), 14, 6, fire = true, paused = false, sheet = listOf(2, 2, 0, 2, 4, 4)),
        Standing(2, PlayerInfo("me", "Николай", 1542), 9, 5, fire = false, paused = false, sheet = listOf(2, 0, 2, 2, 3)),
        Standing(3, PlayerInfo("b", "Ладья", 1488, provisional = true), 6, 5, fire = false, paused = true, sheet = listOf(0, 2, 2, 0, 2)),
        Standing(4, PlayerInfo("c", "Конь_в_пальто", 1601), 4, 4, fire = false, paused = false, sheet = listOf(1, 0, 2, 1)),
        Standing(5, PlayerInfo("d", "Ферзевый гамбит", 1430), 0, 3, fire = false, paused = false, sheet = listOf(0, 0, 0)),
    )

    private fun screen(vm: TournamentViewModel, game: OnlineViewModel = OnlineViewModel(client, { now }, false, arena = true)) {
        compose.setContent {
            ChessOnlineTheme(darkTheme = false) { TournamentsScreen(vm, game, onBack = {}, liveCountdown = false) }
        }
    }

    @Test
    fun list() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.onMessage(
            ServerMessage.Tournaments(
                listOf(
                    running,
                    arena("hourly_bullet-2", "hourly_bullet", Clock(60, 0), 30, TournamentStatus.CREATED, 1_954_000, 7),
                    arena("daily_blitz-3", "daily_blitz", Clock(180, 2), 60, TournamentStatus.CREATED, 6_154_000, 41),
                    arena("hourly_bullet-1", "hourly_bullet", Clock(60, 0), 30, TournamentStatus.FINISHED, 0, 31, winner = "Магнус_88"),
                ),
            ),
        )
        screen(vm)
        compose.onRoot().captureRoboImage("src/test/screenshots/tournaments_ru.png")
    }

    @Test
    fun standings() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.open(running.id)
        vm.onMessage(ServerMessage.Tournament(running, standings, standings[1]))
        screen(vm)
        compose.onRoot().captureRoboImage("src/test/screenshots/tournament_standings_ru.png")
    }

    @Test
    fun arenaGame() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.open(running.id)
        vm.onMessage(ServerMessage.Tournament(running, standings, standings[1]))
        val game = OnlineViewModel(client, { now }, false, arena = true)
        game.onMessage(
            ServerMessage.Game(
                GameSnapshot(
                    gameId = "g7", white = PlayerInfo("c", "Конь_в_пальто", 1601), black = me, clock = Clock(180, 0), rated = true,
                    moves = listOf("e2e4"), whiteMs = 90_000, blackMs = 180_000, clockRunning = false, firstMoveMs = 24_000,
                    tournamentId = running.id, berserk = listOf(PlayerColor.WHITE),
                ),
            ),
        )
        game.tick()
        screen(vm, game)
        compose.onRoot().captureRoboImage("src/test/screenshots/tournament_game_ru.png")
    }
}
