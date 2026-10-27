package ru.fourvic.chess.ui.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.online.FakeOnlineClient
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineViewModelTest {

    private val me = PlayerInfo("me", "Игрок 1", 1500, provisional = true)
    private val opponent = PlayerInfo("op", "Игрок 2", 1520)
    private val client = FakeOnlineClient(me)
    private var now = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OnlineViewModel(client, nowMs = { now }, tickerEnabled = false)

    private fun snapshot(
        id: String = "g1",
        white: PlayerInfo = me,
        black: PlayerInfo = opponent,
        moves: List<String> = emptyList(),
        whiteMs: Long = 180_000,
        blackMs: Long = 180_000,
    ) = GameSnapshot(
        gameId = id, white = white, black = black, clock = Clock(180, 2), rated = true, moves = moves,
        whiteMs = whiteMs, blackMs = blackMs, clockRunning = moves.size >= 2,
        firstMoveMs = if (moves.size < 2) 30_000 else null,
    )

    private fun OnlineViewModel.tap(vararg squares: String) = squares.forEach(::onSquareTapped)

    @Test
    fun connectsAndSeeksAGame() {
        val vm = viewModel()
        assertEquals(1, client.starts)
        assertEquals(me, vm.state.me)
        assertEquals(ClientMessage.RequestGame, client.sent.single(), "asks for a game that may already be running")

        vm.seek(Clock(180, 2))
        assertEquals(ClientMessage.Seek(Clock(180, 2), rated = true), client.sent.last())
        assertEquals(OnlinePhase.SEEKING, vm.state.phase)

        now += 5_000
        vm.tick()
        assertEquals(5, vm.state.seekSeconds)

        client.messages.tryEmit(ServerMessage.Game(snapshot(white = opponent, black = me)))
        assertEquals(OnlinePhase.PLAYING, vm.state.phase)
        assertEquals(PlayerColor.BLACK, vm.state.myColor)
        assertTrue(vm.state.board.flipped)
        assertEquals(opponent, vm.state.opponent)
        assertEquals(30, vm.state.firstMoveSeconds)
    }

    @Test
    fun cancellingTheSeekReturnsToTheLobby() {
        val vm = viewModel()
        vm.seek(Clock.LOBBY.first())
        vm.cancelSeek()
        assertEquals(ClientMessage.CancelSeek, client.sent.last())
        assertEquals(OnlinePhase.LOBBY, vm.state.phase)
    }

    @Test
    fun ownMoveIsShownAtOnceAndOpponentMoveFollows() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        vm.tap("e2", "e4")
        assertEquals(PieceKind.PAWN, vm.state.board.pieces["e4"]?.kind)
        assertEquals(ClientMessage.Move("g1", "e2e4", 0), client.sent.last())
        assertFalse(vm.state.myTurn)

        vm.onMessage(ServerMessage.Moved("g1", "e2e4", 1, 180_000, 180_000)) // confirmation
        vm.onMessage(ServerMessage.Moved("g1", "e7e5", 2, 180_000, 180_000))
        assertEquals(PieceKind.PAWN, vm.state.board.pieces["e5"]?.kind)
        assertTrue(vm.state.myTurn)
        assertTrue(vm.state.clockRunning)
        assertNull(vm.state.firstMoveSeconds)
    }

    @Test
    fun rejectedMoveIsTakenBack() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        vm.tap("e2", "e4")
        vm.onMessage(ServerMessage.Error(ErrorCode.ILLEGAL_MOVE, "no"))
        assertNull(vm.state.board.pieces["e4"])
        assertEquals(PieceKind.PAWN, vm.state.board.pieces["e2"]?.kind)
        assertTrue(vm.state.myTurn)
    }

    @Test
    fun moveMadeWhileOfflineIsSentAfterReconnect() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        client.state.value = ConnectionState.OFFLINE
        vm.tap("d2", "d4")
        assertTrue(client.sent.none { it is ClientMessage.Move })
        assertEquals(PieceKind.PAWN, vm.state.board.pieces["d4"]?.kind)

        client.state.value = ConnectionState.CONNECTED
        vm.onMessage(ServerMessage.Game(snapshot())) // the server never saw the move
        assertEquals(ClientMessage.Move("g1", "d2d4", 0), client.sent.last())
        assertEquals(PieceKind.PAWN, vm.state.board.pieces["d4"]?.kind)
    }

    @Test
    fun moveSeenByTheServerBeforeTheDisconnectIsNotSentTwice() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        client.state.value = ConnectionState.OFFLINE
        vm.tap("d2", "d4")
        client.state.value = ConnectionState.CONNECTED
        vm.onMessage(ServerMessage.Game(snapshot(moves = listOf("d2d4"))))
        assertTrue(client.sent.none { it is ClientMessage.Move })
        assertEquals(1, vm.state.board.ply)
    }

    @Test
    fun premoveIsPlayedWhenTheOpponentHasMoved() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        vm.tap("e2", "e4")
        vm.onMessage(ServerMessage.Moved("g1", "e2e4", 1, 180_000, 180_000))
        vm.tap("g1", "f3")
        assertEquals(ChessMove("g1", "f3"), vm.state.premove)

        vm.onMessage(ServerMessage.Moved("g1", "e7e5", 2, 180_000, 180_000))
        assertEquals(ClientMessage.Move("g1", "g1f3", 2), client.sent.last())
        assertNull(vm.state.premove)
        assertEquals(PieceKind.KNIGHT, vm.state.board.pieces["f3"]?.kind)
    }

    @Test
    fun illegalPremoveIsDropped() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        vm.tap("e2", "e4")
        vm.onMessage(ServerMessage.Moved("g1", "e2e4", 1, 180_000, 180_000))
        vm.tap("e4", "e5") // will be blocked by the reply
        vm.onMessage(ServerMessage.Moved("g1", "e7e5", 2, 180_000, 180_000))
        assertEquals(ClientMessage.Move("g1", "e2e4", 0), client.sent.last())
        assertTrue(vm.state.myTurn)
    }

    @Test
    fun clockRunsOnlyForTheSideToMove() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot(moves = listOf("e2e4", "e7e5"), whiteMs = 60_000, blackMs = 50_000)))
        now += 1_500
        vm.tick()
        assertEquals(58_500, vm.state.whiteMs)
        assertEquals(50_000, vm.state.blackMs)
        assertEquals(58_500, vm.state.myMs)
    }

    @Test
    fun opponentDisconnectShowsCountdown() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot(moves = listOf("e2e4", "e7e5"))))
        vm.onMessage(ServerMessage.OpponentConnection("g1", connected = false, claimInMs = 30_000))
        assertEquals(30, vm.state.opponentAwaySeconds)
        now += 10_000
        vm.tick()
        assertEquals(20, vm.state.opponentAwaySeconds)
        vm.onMessage(ServerMessage.OpponentConnection("g1", connected = true))
        assertNull(vm.state.opponentAwaySeconds)
    }

    @Test
    fun drawOfferCanBeAccepted() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot(moves = listOf("e2e4", "e7e5"))))
        vm.onMessage(ServerMessage.DrawOffered("g1", PlayerColor.BLACK))
        assertTrue(vm.state.opponentOffersDraw)
        vm.offerDraw()
        assertEquals(ClientMessage.OfferDraw("g1"), client.sent.last())
        vm.onMessage(ServerMessage.Ended("g1", GameResult.DRAW, EndReason.AGREEMENT, 0, 0))
        assertEquals(OnlinePhase.FINISHED, vm.state.phase)
        assertFalse(vm.state.opponentOffersDraw)
    }

    @Test
    fun resultShowsOwnRatingChange() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot(white = opponent, black = me, moves = listOf("e2e4", "e7e5"))))
        vm.resign()
        assertEquals(ClientMessage.Resign("g1"), client.sent.last())
        vm.onMessage(ServerMessage.Ended("g1", GameResult.WHITE_WON, EndReason.RESIGNATION, 6, -6))
        assertEquals(OnlinePhase.FINISHED, vm.state.phase)
        assertEquals(-6, vm.state.myRatingDiff)
        assertFalse(vm.state.clockRunning)
    }

    @Test
    fun rematchStartsANewGameWithColoursSwapped() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot(moves = listOf("e2e4", "e7e5"))))
        vm.onMessage(ServerMessage.Chat("g1", PlayerColor.BLACK, QuickPhrase.GOOD_LUCK))
        vm.onMessage(ServerMessage.Ended("g1", GameResult.BLACK_WON, EndReason.TIMEOUT, -7, 7))
        vm.onMessage(ServerMessage.RematchOffered("g1", PlayerColor.BLACK))
        assertTrue(vm.state.opponentWantsRematch)
        vm.rematch()
        assertEquals(ClientMessage.Rematch("g1"), client.sent.last())

        vm.onMessage(ServerMessage.Game(snapshot(id = "g2", white = opponent, black = me)))
        assertEquals("g2", vm.state.gameId)
        assertEquals(PlayerColor.BLACK, vm.state.myColor)
        assertEquals(OnlinePhase.PLAYING, vm.state.phase)
        assertTrue(vm.state.chat.isEmpty())
        assertNull(vm.state.myRatingDiff)
        assertFalse(vm.state.rematchRequested)
        assertEquals(0, vm.state.board.ply)
    }

    @Test
    fun abortIsOfferedOnlyBeforeBothSidesMoved() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        assertTrue(vm.state.canAbort)
        vm.abort()
        assertEquals(ClientMessage.Abort("g1"), client.sent.last())
        vm.onMessage(ServerMessage.Game(snapshot(moves = listOf("e2e4", "e7e5"))))
        assertFalse(vm.state.canAbort)
    }

    @Test
    fun messagesForOtherGamesAreIgnored() {
        val vm = viewModel()
        vm.onMessage(ServerMessage.Game(snapshot()))
        vm.onMessage(ServerMessage.Moved("other", "e2e4", 1, 1, 1))
        vm.onMessage(ServerMessage.Ended("other", GameResult.DRAW, EndReason.AGREEMENT))
        assertEquals(0, vm.state.board.ply)
        assertEquals(OnlinePhase.PLAYING, vm.state.phase)
    }
}
