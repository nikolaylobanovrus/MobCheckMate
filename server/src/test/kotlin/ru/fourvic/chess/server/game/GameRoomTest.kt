package ru.fourvic.chess.server.game

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameRoomTest {

    private class Events : RoomEvents {
        val sent = mutableListOf<Pair<String, ServerMessage>>()
        var ended: Pair<GameResult, EndReason>? = null
        var rematches = 0
        override fun send(accountId: String, message: ServerMessage) {
            sent += accountId to message
        }
        override fun gameEnded(room: GameRoom, result: GameResult, reason: EndReason): Pair<Int?, Int?> {
            ended = result to reason
            return 7 to -7
        }
        override fun rematchAgreed(room: GameRoom) {
            rematches++
        }
        inline fun <reified T : ServerMessage> to(id: String) = sent.filter { it.first == id }.map { it.second }.filterIsInstance<T>()
    }

    private val white = PlayerInfo("w", "White", 1500)
    private val black = PlayerInfo("b", "Black", 1500)
    private val settings = RoomSettings(firstMoveMs = 30_000, disconnectGraceMs = 30_000, lagCompensationMs = 100)

    private fun TestScope.room(events: Events, clock: Clock = Clock(60, 2), tournamentId: String? = null) = GameRoom(
        id = "g1", white = white, black = black, clock = clock, rated = true,
        scope = backgroundScope, now = { testScheduler.currentTime }, events = events, settings = settings,
        tournamentId = tournamentId,
    )

    private fun GameRoom.play(vararg moves: String) = moves.forEachIndexed { _, uci ->
        val mover = if (moveList.size % 2 == 0) "w" else "b"
        assertNull(move(mover, uci, moveList.size), "move $uci")
    }

    @Test
    fun noFirstMoveAbortsTheGame() = runTest {
        val events = Events()
        val room = room(events)
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(GameResult.ABORTED to EndReason.ABORTED, events.ended)
    }

    @Test
    fun clocksStartAfterBothFirstMovesAndAddIncrement() = runTest {
        val events = Events()
        val room = room(events)
        advanceTimeBy(5_000)
        room.play("e2e4")
        advanceTimeBy(5_000)
        room.play("e7e5")
        val afterFirst = room.snapshot()
        assertEquals(60_000, afterFirst.whiteMs, "first moves cost nothing")
        assertTrue(afterFirst.clockRunning)

        advanceTimeBy(3_100)
        room.play("g1f3")
        // 3.1 s spent minus 0.1 s lag compensation, plus 2 s increment.
        assertEquals(60_000 - 3_000 + 2_000, room.snapshot().whiteMs)
    }

    @Test
    fun runningOutOfTimeLoses() = runTest {
        val events = Events()
        val room = room(events, Clock(60, 0))
        room.play("e2e4", "e7e5")
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(GameResult.BLACK_WON to EndReason.TIMEOUT, events.ended)
        assertEquals(0, room.snapshot().whiteMs)
    }

    @Test
    fun moveArrivingAfterTheFlagLoses() = runTest {
        val events = Events()
        val room = room(events, Clock(10, 0))
        room.play("e2e4", "e7e5")
        advanceTimeBy(10_500) // the timer fires first; a late move must not be accepted
        runCurrent()
        assertNotNull(room.move("w", "g1f3", 2))
        assertEquals(GameResult.BLACK_WON to EndReason.TIMEOUT, events.ended)
    }

    @Test
    fun matingMaterialRuleUsedForFlagFalls() {
        // Rooms always start from the initial position, so the flag-vs-lone-king rule is checked on the helper.
        val game = ru.fourvic.chess.shared.chess.ChessGame("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")
        assertFalse(game.hasMatingMaterial(PlayerColor.BLACK))
        assertTrue(game.hasMatingMaterial(PlayerColor.WHITE))
        assertFalse(ru.fourvic.chess.shared.chess.ChessGame("4k3/8/8/8/8/8/8/1N2K3 w - - 0 1").hasMatingMaterial(PlayerColor.WHITE))
        assertTrue(ru.fourvic.chess.shared.chess.ChessGame("4k3/8/8/8/8/8/8/1NN1K3 w - - 0 1").hasMatingMaterial(PlayerColor.WHITE))
    }

    @Test
    fun disconnectedPlayerLosesAfterTheGracePeriodUnlessTheyReturn() = runTest {
        val events = Events()
        // A long clock, so that White (to move) does not flag while the test waits.
        val room = room(events, Clock(600, 0))
        room.play("e2e4", "e7e5")
        room.setConnected("b", false)
        val notice = events.to<ServerMessage.OpponentConnection>("w").single()
        assertEquals(30_000L, notice.claimInMs)

        advanceTimeBy(10_000)
        room.setConnected("b", true)
        assertTrue(events.to<ServerMessage.OpponentConnection>("w").last().connected)
        advanceTimeBy(30_000)
        runCurrent()
        assertNull(events.ended)

        room.setConnected("b", false)
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(GameResult.WHITE_WON to EndReason.ABANDONED, events.ended)
    }

    @Test
    fun drawByAgreementAndOfferCancelledByAMove() = runTest {
        val events = Events()
        val room = room(events)
        room.play("e2e4", "e7e5")
        room.offerDraw("w")
        assertEquals(PlayerColor.WHITE, room.snapshot().drawOfferBy)
        room.play("g1f3") // White moving does not cancel White's own offer
        assertEquals(PlayerColor.WHITE, room.snapshot().drawOfferBy)
        room.play("b8c6") // black moving declines it
        assertNull(room.snapshot().drawOfferBy)

        room.offerDraw("b")
        room.offerDraw("w")
        assertEquals(GameResult.DRAW to EndReason.AGREEMENT, events.ended)
    }

    @Test
    fun abortOnlyBeforeBothHaveMoved() = runTest {
        val events = Events()
        val room = room(events)
        room.play("e2e4", "e7e5")
        assertEquals(ErrorCode.BAD_REQUEST, room.abort("w")?.code)
        val events2 = Events()
        val room2 = room(events2)
        room2.play("e2e4")
        assertNull(room2.abort("b"))
        assertEquals(GameResult.ABORTED to EndReason.ABORTED, events2.ended)
    }

    @Test
    fun duplicateMoveFramesAreIgnoredAndTurnsAreChecked() = runTest {
        val events = Events()
        val room = room(events)
        assertNull(room.move("w", "e2e4", 0))
        assertNull(room.move("w", "e2e4", 0), "the same frame again is ignored")
        assertEquals(1, room.moveList.size)
        assertEquals(ErrorCode.NOT_YOUR_TURN, room.move("w", "d2d4", 1)?.code)
        assertEquals(ErrorCode.ILLEGAL_MOVE, room.move("b", "e7e4", 1)?.code)
        assertEquals(ErrorCode.NO_SUCH_GAME, room.move("x", "e7e5", 1)?.code)
    }

    @Test
    fun checkmateEndsTheGameAndRematchNeedsBothPlayers() = runTest {
        val events = Events()
        val room = room(events)
        room.play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameResult.BLACK_WON to EndReason.CHECKMATE, events.ended)
        val ended = events.to<ServerMessage.Ended>("w").single()
        assertEquals(7, ended.whiteRatingDiff)

        room.rematch("w")
        assertEquals(0, events.rematches)
        room.rematch("b")
        assertEquals(1, events.rematches)
    }

    @Test
    fun quickChatIsRateLimited() = runTest {
        val events = Events()
        val room = room(events)
        assertNull(room.chat("w", QuickPhrase.GOOD_LUCK))
        assertEquals(ErrorCode.RATE_LIMITED, room.chat("w", QuickPhrase.HELLO)?.code)
        advanceTimeBy(1_600)
        assertNull(room.chat("w", QuickPhrase.HELLO))
        assertEquals(2, events.to<ServerMessage.Chat>("b").size)
    }

    @Test
    fun tournamentGamesCannotBeAbortedAndNotStartingLoses() = runTest {
        val events = Events()
        val room = room(events, tournamentId = "arena")
        assertEquals(ErrorCode.BAD_REQUEST, room.abort("w")?.code)
        room.play("e2e4")
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(GameResult.WHITE_WON to EndReason.NO_START, events.ended)
        assertEquals(ErrorCode.BAD_REQUEST, room.rematch("w")?.code, "the arena pairs the next game itself")
    }

    @Test
    fun berserkHalvesTheClockAndDropsTheIncrement() = runTest {
        val events = Events()
        assertEquals(ErrorCode.BAD_REQUEST, room(events).berserk("w")?.code, "not outside tournaments")

        val room = room(events, Clock(180, 2), tournamentId = "arena")
        assertNull(room.berserk("w"))
        assertEquals(90_000, room.snapshot().whiteMs)
        assertEquals(listOf(PlayerColor.WHITE), room.snapshot().berserk)
        room.play("e2e4")
        assertEquals(ErrorCode.BAD_REQUEST, room.berserk("w")?.code, "too late after the own first move")
        assertNull(room.berserk("b"), "Black may still go berserk before replying")
        room.play("e7e5")
        advanceTimeBy(1_100)
        room.play("g1f3")
        assertEquals(89_000, room.snapshot().whiteMs, "no increment after berserk")
        advanceTimeBy(1_100)
        room.play("b8c6")
        assertEquals(89_000, room.snapshot().blackMs)
        assertEquals(2, events.to<ServerMessage.Berserked>("w").size)
    }
}
