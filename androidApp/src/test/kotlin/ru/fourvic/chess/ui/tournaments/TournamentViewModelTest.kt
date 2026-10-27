package ru.fourvic.chess.ui.tournaments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.online.FakeOnlineClient
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Standing
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus
import ru.fourvic.chess.ui.online.OnlinePhase
import ru.fourvic.chess.ui.online.OnlineViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TournamentViewModelTest {

    private val me = PlayerInfo("me", "Игрок 1", 1500)
    private val client = FakeOnlineClient(me)
    private var now = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun info(id: String = "hourly_blitz-1", status: TournamentStatus = TournamentStatus.STARTED, private: Boolean = false) =
        TournamentInfo(id, "Hourly Blitz", Clock(180, 0), 30, status, msLeft = 600_000, players = 2, rated = true, private = private)

    private fun line(rank: Int, player: PlayerInfo, paused: Boolean = false) =
        Standing(rank, player, score = 4 - 2 * rank, games = 2, fire = false, paused = paused)

    @Test
    fun asksForTheListWhenConnected() {
        TournamentViewModel(client, nowMs = { now })
        assertEquals(ClientMessage.ListTournaments, client.sent.single())
    }

    @Test
    fun opensJoinsPausesAndLeaves() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.onMessage(ServerMessage.Tournaments(listOf(info())))
        assertEquals(1, vm.state.list.size)

        vm.open("hourly_blitz-1")
        assertEquals(ClientMessage.WatchTournament("hourly_blitz-1"), client.sent.last())
        vm.onMessage(ServerMessage.Tournament(info(), listOf(line(1, me)), null))
        assertFalse(vm.state.joined)

        vm.join()
        assertEquals(ClientMessage.JoinTournament("hourly_blitz-1"), client.sent.last())
        vm.onMessage(ServerMessage.Tournament(info(), listOf(line(1, me)), line(1, me)))
        assertTrue(vm.state.playing)

        vm.close()
        assertTrue(client.sent.contains(ClientMessage.PauseTournament("hourly_blitz-1")), "leaving pauses")
        assertEquals(ClientMessage.ListTournaments, client.sent.last())
        assertNull(vm.state.selectedId)
    }

    @Test
    fun backgroundPausesAndForegroundResumes() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.open("hourly_blitz-1")
        vm.onMessage(ServerMessage.Tournament(info(), listOf(line(1, me)), line(1, me)))
        vm.onBackground()
        assertEquals(ClientMessage.PauseTournament("hourly_blitz-1"), client.sent.last())
        vm.onMessage(ServerMessage.Tournament(info(), listOf(line(1, me, paused = true)), line(1, me, paused = true)))
        vm.onForeground()
        assertEquals(ClientMessage.JoinTournament("hourly_blitz-1"), client.sent.last())
    }

    @Test
    fun manualPauseIsNotUndoneByForeground() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.open("hourly_blitz-1")
        vm.onMessage(ServerMessage.Tournament(info(), emptyList(), line(1, me)))
        vm.pause()
        vm.onMessage(ServerMessage.Tournament(info(), emptyList(), line(1, me, paused = true)))
        val sent = client.sent.size
        vm.onBackground()
        vm.onForeground()
        assertEquals(sent, client.sent.size)
    }

    @Test
    fun codeOpensThePrivateArena() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.joinByCode(" ab12cd ")
        assertEquals(ClientMessage.JoinTournament("AB12CD"), client.sent.last())
        vm.onMessage(ServerMessage.Tournament(info("AB12CD", TournamentStatus.CREATED, private = true), emptyList(), line(1, me)))
        assertEquals("AB12CD", vm.state.selectedId)
        assertEquals("AB12CD", vm.state.detail?.id)
    }

    @Test
    fun unknownCodeShowsAnError() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.joinByCode("ZZZZZZ")
        vm.onMessage(ServerMessage.Error(ErrorCode.NO_SUCH_TOURNAMENT, "No such tournament"))
        assertEquals(TournamentError.NOT_FOUND, vm.state.error)
        assertNull(vm.state.selectedId)
    }

    @Test
    fun createdArenaOpens() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.create("Клуб", Clock(180, 2), 30, 2, rated = false)
        assertEquals(ClientMessage.CreateTournament("Клуб", Clock(180, 2), 30, 2, rated = false), client.sent.last())
        vm.onMessage(ServerMessage.Tournament(info("QW34ER", TournamentStatus.CREATED, private = true), emptyList(), line(1, me)))
        assertEquals("QW34ER", vm.state.selectedId)
    }

    @Test
    fun reconnectAsksAgainForTheOpenTournament() {
        val vm = TournamentViewModel(client, nowMs = { now })
        vm.open("hourly_blitz-1")
        client.state.value = ConnectionState.OFFLINE
        client.state.value = ConnectionState.CONNECTED
        assertEquals(ClientMessage.WatchTournament("hourly_blitz-1"), client.sent.last())
    }

    @Test
    fun arenaGamesGoToTheArenaViewModelOnly() {
        val casual = OnlineViewModel(client, nowMs = { now }, tickerEnabled = false)
        val arena = OnlineViewModel(client, nowMs = { now }, tickerEnabled = false, arena = true)
        val opponent = PlayerInfo("op", "Игрок 2", 1500)
        val snapshot = GameSnapshot(
            gameId = "g1", white = me, black = opponent, clock = Clock(180, 0), rated = true, moves = emptyList(),
            whiteMs = 180_000, blackMs = 180_000, clockRunning = false, firstMoveMs = 30_000, tournamentId = "hourly_blitz-1",
        )
        client.messages.tryEmit(ServerMessage.Game(snapshot))
        assertEquals(OnlinePhase.LOBBY, casual.state.phase)
        assertEquals(OnlinePhase.PLAYING, arena.state.phase)
        assertFalse(arena.state.canAbort)
        assertTrue(arena.state.canBerserk)

        arena.berserk()
        assertEquals(ClientMessage.Berserk("g1"), client.sent.last())
        arena.onMessage(ServerMessage.Berserked("g1", PlayerColor.WHITE, 90_000, 180_000))
        assertEquals(90_000, arena.state.myMs)
        assertFalse(arena.state.canBerserk)
    }
}
