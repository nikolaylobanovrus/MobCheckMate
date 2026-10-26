package ru.fourvic.chess.ui.game

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
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.shared.engine.ChessBot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BotGameViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(humanColor: PlayerColor, level: BotLevel = BotLevel.LEVEL_1) = GameViewModel(
        bot = ChessBot(Random(11)),
        botDispatcher = dispatcher,
        initialOpponent = Opponent.Computer(level, humanColor),
    )

    @Test
    fun computerRepliesAfterTheUsersMove() = runTest(dispatcher) {
        val vm = viewModel(PlayerColor.WHITE)
        vm.onSquareTapped("e2")
        vm.onSquareTapped("e4")
        assertTrue(vm.state.botThinking)
        assertEquals(PlayerColor.BLACK, vm.state.sideToMove)

        advanceUntilIdle()
        assertFalse(vm.state.botThinking)
        assertEquals(PlayerColor.WHITE, vm.state.sideToMove)
        assertTrue(vm.state.lastMove != ChessMove("e2", "e4"), "the computer has replied")
    }

    @Test
    fun userCannotMoveTheComputersPieces() = runTest(dispatcher) {
        val vm = viewModel(PlayerColor.WHITE)
        vm.onSquareTapped("e2")
        vm.onSquareTapped("e4")
        vm.onSquareTapped("e7") // black pawn while the computer thinks
        assertNull(vm.state.selected)
        advanceUntilIdle()
    }

    @Test
    fun computerMovesFirstWhenTheUserTakesBlack() = runTest(dispatcher) {
        val vm = viewModel(PlayerColor.BLACK)
        assertTrue(vm.state.flipped, "black pieces at the bottom")
        assertTrue(vm.state.botThinking)
        assertFalse(vm.state.canUndo)

        advanceUntilIdle()
        assertEquals(PlayerColor.BLACK, vm.state.sideToMove)
        assertTrue(vm.state.lastMove != null, "the computer has opened the game")
        assertEquals(32, vm.state.pieces.size)
    }

    @Test
    fun undoTakesBackTheUsersMoveAndTheReply() = runTest(dispatcher) {
        val vm = viewModel(PlayerColor.WHITE)
        vm.onSquareTapped("d2")
        vm.onSquareTapped("d4")
        advanceUntilIdle()
        assertTrue(vm.state.canUndo)

        vm.undo()
        assertNull(vm.state.lastMove)
        assertEquals(PlayerColor.WHITE, vm.state.sideToMove)
        assertFalse(vm.state.canUndo)
    }

    @Test
    fun newGameStopsAThinkingComputer() = runTest(dispatcher) {
        val vm = viewModel(PlayerColor.WHITE)
        vm.onSquareTapped("e2")
        vm.onSquareTapped("e4")
        assertTrue(vm.state.botThinking)

        vm.newGame(Opponent.Friend)
        advanceUntilIdle()
        assertNull(vm.state.lastMove, "the cancelled reply must not land on the new board")
        assertEquals(Opponent.Friend, vm.state.opponent)
        assertFalse(vm.state.botThinking)
    }

    @Test
    fun switchingToBlackAgainstTheComputerFlipsTheBoardAndLetsItStart() = runTest(dispatcher) {
        val vm = GameViewModel(bot = ChessBot(Random(3)), botDispatcher = dispatcher)
        vm.newGame(Opponent.Computer(BotLevel.LEVEL_2, PlayerColor.BLACK))
        assertTrue(vm.state.flipped)
        advanceUntilIdle()
        assertEquals(PlayerColor.BLACK, vm.state.sideToMove)
    }
}
