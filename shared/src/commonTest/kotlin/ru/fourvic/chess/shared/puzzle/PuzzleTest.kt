package ru.fourvic.chess.shared.puzzle

import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PlayerColor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PuzzleTest {

    // Real lines from the Lichess set (CC0).
    private val mateInOne = Puzzle.parseLine("2DVeh\t7k/p5p1/5n2/2p2R2/P2R3P/6p1/6P1/4r2K w - - 1 32\tf5f1 e1f1\t400\tendgame,mate,mateIn1")!!
    private val twoMoves = Puzzle(
        id = "test2",
        // Synthetic line: the king takes the queen, then two solver moves with an opponent check between them.
        fen = "4k3/8/8/8/8/8/3q4/R3K2R w KQ - 0 1",
        moves = listOf("e1d2", "e8d8", "a1a8", "d8c7", "a8h8").map { ChessMove.fromUci(it)!! },
        rating = 900,
        themes = listOf("test"),
    )

    @Test
    fun parsesTheBundledFormat() {
        assertEquals("2DVeh", mateInOne.id)
        assertEquals(2, mateInOne.moves.size)
        assertEquals(400, mateInOne.rating)
        assertEquals(listOf("endgame", "mate", "mateIn1"), mateInOne.themes)
        assertNull(Puzzle.parseLine("# comment"))
        assertNull(Puzzle.parseLine("broken"))
    }

    @Test
    fun opponentMovesFirstAndSolverPlaysTheOtherSide() {
        val session = PuzzleSession(mateInOne)
        assertEquals(PlayerColor.BLACK, session.solverColor)
        assertEquals(ChessMove("e1", "f1"), session.expectedMove)
    }

    @Test
    fun correctMateSolvesThePuzzle() {
        val session = PuzzleSession(mateInOne)
        assertEquals(AttemptResult.SOLVED, session.attempt(ChessMove("e1", "f1")))
        assertTrue(session.isSolved)
        assertFalse(session.failed)
    }

    @Test
    fun wrongMoveFailsButAllowsAnotherTry() {
        val session = PuzzleSession(mateInOne)
        assertEquals(AttemptResult.WRONG, session.attempt(ChessMove("e1", "e2")))
        assertTrue(session.failed)
        assertEquals(AttemptResult.SOLVED, session.attempt(ChessMove("e1", "f1")))
    }

    @Test
    fun multiMovePuzzleWaitsForTheReplyBetweenSolverMoves() {
        val session = PuzzleSession(twoMoves)
        assertEquals(PlayerColor.BLACK, session.solverColor)
        assertEquals(AttemptResult.CORRECT, session.attempt(ChessMove("e8", "d8")))
        assertEquals(ChessMove("a1", "a8"), session.pendingReply)
        assertNull(session.expectedMove)
        assertEquals(ChessMove("a1", "a8"), session.playReply())
        assertEquals(ChessMove("d8", "c7"), session.expectedMove)
        assertEquals(AttemptResult.CORRECT, session.attempt(ChessMove("d8", "c7")))
        session.playReply()
        assertTrue(session.isSolved)
    }

    @Test
    fun revealingAMoveCountsAsFailure() {
        val session = PuzzleSession(mateInOne)
        assertEquals(ChessMove("e1", "f1"), session.revealNextMove())
        assertTrue(session.failed)
        assertTrue(session.isSolved)
    }

    @Test
    fun ratingRisesOnASolveAndFallsOnAFailure() {
        val start = PuzzleProgress()
        val puzzle = mateInOne.copy(rating = 1200)
        assertTrue(PuzzleRating.record(start, puzzle, solvedCleanly = true).rating.rating > 1200)
        assertTrue(PuzzleRating.record(start, puzzle, solvedCleanly = false).rating.rating < 1200)
        assertEquals(1, PuzzleRating.record(start, puzzle, true).solved)
    }

    @Test
    fun dailyStreakGrowsOnConsecutiveDaysAndResetsAfterAGap() {
        var p = PuzzleProgress()
        p = PuzzleRating.recordDaily(p, 100)
        p = PuzzleRating.recordDaily(p, 101)
        p = PuzzleRating.recordDaily(p, 101) // solving twice on one day changes nothing
        assertEquals(2, p.streak)
        assertEquals(2, p.currentStreak(102))
        assertEquals(0, p.currentStreak(103), "a missed day breaks the streak")
        p = PuzzleRating.recordDaily(p, 104)
        assertEquals(1, p.streak)
        assertEquals(2, p.bestStreak)
    }

    @Test
    fun selectorPicksNearTheRatingAndSkipsSeenPuzzles() {
        val puzzles = (0 until 200).map { i ->
            mateInOne.copy(id = "p$i", rating = 400 + i * 10)
        }
        val selector = PuzzleSelector(puzzles, Random(1))
        val pick = assertNotNull(selector.next(1000))
        assertTrue(kotlin.math.abs(pick.rating - 1000) <= 75)
        val seen = puzzles.filter { kotlin.math.abs(it.rating - 1000) <= 150 }.map { it.id }.toSet()
        val other = assertNotNull(selector.next(1000, seen))
        assertTrue(other.id !in seen)
        assertEquals(selector.daily(20_000), selector.daily(20_000), "daily puzzle is stable for a day")
    }
}
