package ru.fourvic.chess.shared.engine

import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.GameStatus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ChessBotTest {

    private fun game(fen: String, vararg uci: String) = ChessGame(fen).apply {
        uci.forEach { assertTrue(play(ChessMove.fromUci(it)!!), "move $it") }
    }

    @Test
    fun findsBackRankMateInOne() {
        val game = game("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1")
        val analysis = ChessBot().analyze(game, maxDepth = 4, timeMillis = null)
        assertEquals(ChessMove("d1", "d8"), analysis.bestMove)
        assertEquals(1, analysis.mateIn)
    }

    @Test
    fun everyLevelFromThreeUpMatesInOne() {
        for (level in listOf(BotLevel.LEVEL_3, BotLevel.LEVEL_4, BotLevel.LEVEL_5)) {
            val game = game("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", "g1f1", "g8h8", "f1g1", "h8g8")
            val move = ChessBot(Random(1)).chooseMove(game, level)
            game.play(assertNotNull(move))
            assertEquals(GameStatus.CHECKMATE, game.status, "level ${level.number} played ${move.uci}")
        }
    }

    @Test
    fun findsKingAndRookMateInTwo() {
        // 1.Kb6 Kb8 2.Rh8#
        val game = game("k7/8/2K5/8/8/8/8/7R w - - 0 1")
        val bot = ChessBot(Random(7))
        val analysis = bot.analyze(game, maxDepth = 6, timeMillis = null)
        assertEquals(2, analysis.mateIn)

        game.play(assertNotNull(analysis.bestMove))
        for (reply in game.legalMoves()) {
            val line = game.copy()
            line.play(reply)
            line.play(assertNotNull(bot.analyze(line, maxDepth = 4, timeMillis = null).bestMove))
            assertEquals(GameStatus.CHECKMATE, line.status, "after ${reply.uci}")
        }
    }

    @Test
    fun takesAnUndefendedQueen() {
        for (level in listOf(BotLevel.LEVEL_3, BotLevel.LEVEL_5)) {
            val game = game("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1")
            assertEquals(ChessMove("d2", "d5"), ChessBot(Random(3)).chooseMove(game, level), "level ${level.number}")
        }
    }

    @Test
    fun doesNotTakeAKnightDefendedByAPawn() {
        // Rxd5 exd5 gives a rook for a knight; quiescence search must see the recapture.
        val game = game("4k3/8/4p3/3n4/8/8/3R4/4K3 w - - 0 1")
        for (level in listOf(BotLevel.LEVEL_4, BotLevel.LEVEL_5)) {
            val move = ChessBot(Random(5)).chooseMove(game, level)
            assertTrue(move != ChessMove("d2", "d5"), "level ${level.number} took a defended knight")
        }
    }

    @Test
    fun weakLevelsVaryTheirMovesAndStrongestVariesInTheOpening() {
        val start = ChessGame()
        val level1 = (1..20).mapNotNull { ChessBot(Random(it)).chooseMove(start, BotLevel.LEVEL_1) }.toSet()
        val level5 = (1..12).mapNotNull { ChessBot(Random(it)).chooseMove(start, BotLevel.LEVEL_5) }.toSet()
        assertTrue(level1.size >= 4, "level 1 opening moves: $level1")
        assertTrue(level5.size >= 2, "level 5 opening moves: $level5")
    }

    @Test
    fun sameSeedGivesTheSameMoveOnDepthLimitedLevels() {
        val game = ChessGame()
        val a = ChessBot(Random(42)).chooseMove(game, BotLevel.LEVEL_2)
        val b = ChessBot(Random(42)).chooseMove(game, BotLevel.LEVEL_2)
        assertEquals(a, b)
    }

    @Test
    fun strongestLevelAnswersWithinItsTimeBudget() {
        // A busy middlegame after the opening phase.
        val game = game("r1bq1rk1/pp2bppp/2n1pn2/3p4/2PP4/2N1PN2/PP1B1PPP/R2QKB1R w KQ - 0 8")
        val mark = TimeSource.Monotonic.markNow()
        val analysis = ChessBot().analyze(game, timeMillis = BotLevel.LEVEL_5.timeMillis)
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        println("ENGINE depth=${analysis.depth} nodes=${analysis.nodes} ms=$elapsed nps=${analysis.nodes * 1000 / maxOf(elapsed, 1)}")
        assertNotNull(analysis.bestMove)
        // The budget is the requirement; the depth reached depends on the hardware and is only printed.
        assertTrue(elapsed < 1_500, "took $elapsed ms")

        val botMark = TimeSource.Monotonic.markNow()
        assertNotNull(ChessBot().chooseMove(game, BotLevel.LEVEL_5))
        assertTrue(botMark.elapsedNow().inWholeMilliseconds < 1_500)
    }

    @Test
    fun noMoveWhenTheGameIsOver() {
        val mated = game(ChessGame.START_FEN, "f2f3", "e7e5", "g2g4", "d8h4")
        assertNull(ChessBot().chooseMove(mated, BotLevel.LEVEL_5))
    }

    @Test
    fun interruptedRootScoringStillCoversEveryMove() {
        // A small node budget stops the deep pass; the last complete shallow pass must cover all 20 moves.
        val searcher = Searcher(ChessGame().boardCopy())
        val scored = searcher.scoreRootMoves(depth = 5, timeMillis = null, maxNodes = 3_000)
        assertEquals(20, scored.size)
        assertEquals(scored.sortedByDescending { it.second }, scored)
    }

    @Test
    fun reachesUsefulDepthWithinAFixedNodeBudget() {
        // Hardware-independent: 200 000 nodes is what a phone searches in about one second.
        val board = ChessGame("r1bq1rk1/pp2bppp/2n1pn2/3p4/2PP4/2N1PN2/PP1B1PPP/R2QKB1R w KQ - 0 8").boardCopy()
        val result = Searcher(board).iterativeDeepening(maxDepth = 64, timeMillis = null, maxNodes = 200_000)
        assertNotNull(result.move)
        assertTrue(result.depth >= 4, "depth ${result.depth}")
    }
}
