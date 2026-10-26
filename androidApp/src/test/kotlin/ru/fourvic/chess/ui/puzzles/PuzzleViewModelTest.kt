package ru.fourvic.chess.ui.puzzles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import ru.fourvic.chess.puzzles.MemoryPuzzleProgressStore
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.puzzle.Puzzle
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PuzzleViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    // Mate in one for Black after White's Rf1: Rxf1#.
    private val mateInOne = Puzzle.parseLine("2DVeh\t7k/p5p1/5n2/2p2R2/P2R3P/6p1/6P1/4r2K w - - 1 32\tf5f1 e1f1\t1200\tendgame,mateIn1")!!

    // Two solver moves: after Kxd2, Black plays Kd8, White checks Ra8+, Black answers Kc7.
    private val twoStep = Puzzle(
        "two", "4k3/8/8/8/8/8/3q4/R3K2R w KQ - 0 1",
        listOf("e1d2", "e8d8", "a1a8", "d8c7").map { ChessMove.fromUci(it)!! }, 1200, emptyList(),
    )

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg puzzles: Puzzle, store: MemoryPuzzleProgressStore = MemoryPuzzleProgressStore(), day: Long = 100) =
        PuzzleViewModel(
            source = { puzzles.toList() },
            store = store,
            today = { day },
            random = Random(1),
            ioDispatcher = dispatcher,
            replyDelayMs = 10,
        )

    @Test
    fun solvingRaisesTheRating() = runTest(dispatcher) {
        val store = MemoryPuzzleProgressStore()
        val vm = viewModel(mateInOne, store = store)
        advanceUntilIdle()
        assertEquals(PuzzlePhase.SOLVING, vm.state.phase)
        assertEquals(PlayerColor.BLACK, vm.state.solverColor)
        assertTrue(vm.state.board.flipped)
        assertEquals(ChessMove("f5", "f1"), vm.state.board.lastMove, "the opponent's move is shown")

        vm.onSquareTapped("e1")
        vm.onSquareTapped("f1")
        assertEquals(PuzzlePhase.SOLVED, vm.state.phase)
        assertTrue((vm.state.ratingChange ?: 0) > 0)
        assertEquals(1, store.load().solved)
    }

    @Test
    fun wrongMoveCostsRatingAtOnceAndOnlyOnce() = runTest(dispatcher) {
        val store = MemoryPuzzleProgressStore()
        val vm = viewModel(mateInOne, store = store)
        advanceUntilIdle()
        vm.onSquareTapped("e1")
        vm.onSquareTapped("e2")
        assertEquals(PuzzlePhase.WRONG, vm.state.phase)
        assertEquals(1, store.load().failed)
        val afterFailure = store.load().rating.rating

        vm.onSquareTapped("e1")
        vm.onSquareTapped("f1")
        assertEquals(PuzzlePhase.SOLVED, vm.state.phase)
        assertEquals(afterFailure, store.load().rating.rating, "solving after a mistake does not win rating back")
        assertEquals(0, store.load().solved)
    }

    @Test
    fun opponentRepliesAfterAPause() = runTest(dispatcher) {
        val vm = viewModel(twoStep)
        advanceUntilIdle()
        vm.onSquareTapped("e8")
        vm.onSquareTapped("d8")
        assertEquals(PuzzlePhase.CORRECT, vm.state.phase)
        advanceUntilIdle()
        assertEquals(PuzzlePhase.SOLVING, vm.state.phase)
        assertEquals(ChessMove("a1", "a8"), vm.state.board.lastMove)
        vm.onSquareTapped("d8")
        vm.onSquareTapped("c7")
        assertEquals(PuzzlePhase.SOLVED, vm.state.phase)
    }

    @Test
    fun hintHighlightsThePieceAndCountsAsAFailure() = runTest(dispatcher) {
        val store = MemoryPuzzleProgressStore()
        val vm = viewModel(mateInOne, store = store)
        advanceUntilIdle()
        vm.hint()
        assertEquals("e1", vm.state.board.selected)
        assertEquals(1, store.load().failed)
    }

    @Test
    fun showSolutionPlaysTheRemainingMoves() = runTest(dispatcher) {
        val vm = viewModel(twoStep)
        advanceUntilIdle()
        vm.showSolution()
        advanceUntilIdle()
        assertEquals(PuzzlePhase.REVEALED, vm.state.phase)
        assertEquals(ChessMove("d8", "c7"), vm.state.board.lastMove)
    }

    @Test
    fun dailyPuzzleStartsAStreak() = runTest(dispatcher) {
        val store = MemoryPuzzleProgressStore()
        val vm = viewModel(mateInOne, store = store, day = 500)
        advanceUntilIdle()
        vm.switchMode(PuzzleMode.DAILY)
        vm.onSquareTapped("e1")
        vm.onSquareTapped("f1")
        assertEquals(1, vm.state.streak)
        assertTrue(vm.state.dailySolvedToday)
        assertEquals(500L, store.load().lastDailyDay)
    }

    @Test
    fun nextLoadsAnotherPuzzle() = runTest(dispatcher) {
        val vm = viewModel(mateInOne, twoStep)
        advanceUntilIdle()
        vm.showSolution()
        advanceUntilIdle()
        vm.next()
        assertEquals(PuzzlePhase.SOLVING, vm.state.phase)
        assertNull(vm.state.ratingChange)
        assertNotNull(vm.state.puzzleRating)
    }
}
