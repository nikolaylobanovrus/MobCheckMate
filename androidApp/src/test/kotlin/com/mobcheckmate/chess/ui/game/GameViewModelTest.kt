package com.mobcheckmate.chess.ui.game

import com.mobcheckmate.shared.chess.BoardPiece
import com.mobcheckmate.shared.chess.ChessMove
import com.mobcheckmate.shared.chess.GameStatus
import com.mobcheckmate.shared.chess.PieceKind
import com.mobcheckmate.shared.chess.PlayerColor
import com.mobcheckmate.shared.chess.Promotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameViewModelTest {

    private fun GameViewModel.tap(vararg squares: String) = squares.forEach(::onSquareTapped)

    @Test
    fun tappingAPieceShowsItsTargetsAndTappingATargetMovesIt() {
        val vm = GameViewModel()
        vm.tap("e2")
        assertEquals("e2", vm.state.selected)
        assertEquals(setOf("e3", "e4"), vm.state.targets)

        vm.tap("e4")
        assertEquals(BoardPiece(PlayerColor.WHITE, PieceKind.PAWN), vm.state.pieces["e4"])
        assertNull(vm.state.pieces["e2"])
        assertEquals(PlayerColor.BLACK, vm.state.sideToMove)
        assertEquals(ChessMove("e2", "e4"), vm.state.lastMove)
        assertNull(vm.state.selected)
    }

    @Test
    fun opponentPiecesCannotBeSelected() {
        val vm = GameViewModel()
        vm.tap("e7")
        assertNull(vm.state.selected)
        assertTrue(vm.state.targets.isEmpty())
    }

    @Test
    fun enPassantByTaps() {
        val vm = GameViewModel("4k3/8/8/8/2p5/8/3P4/4K3 w - - 0 1")
        vm.tap("d2", "d4", "c4", "d3")
        assertNull(vm.state.pieces["d4"])
        assertEquals(BoardPiece(PlayerColor.BLACK, PieceKind.PAWN), vm.state.pieces["d3"])
    }

    @Test
    fun promotionAsksForAPieceAndCanBeCancelled() {
        val vm = GameViewModel("8/P7/8/8/8/8/8/k6K w - - 0 1")
        vm.tap("a7", "a8")
        assertEquals("a7" to "a8", vm.state.pendingPromotion)

        vm.onPromotionChosen(null)
        assertNull(vm.state.pendingPromotion)
        assertEquals(BoardPiece(PlayerColor.WHITE, PieceKind.PAWN), vm.state.pieces["a7"])

        vm.tap("a7", "a8")
        vm.onPromotionChosen(Promotion.KNIGHT)
        assertEquals(BoardPiece(PlayerColor.WHITE, PieceKind.KNIGHT), vm.state.pieces["a8"])
    }

    @Test
    fun checkmateEndsTheGameAndBlocksInput() {
        val vm = GameViewModel()
        vm.tap("f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")
        assertEquals(GameStatus.CHECKMATE, vm.state.status)
        assertEquals("e1", vm.state.checkedKing)
        vm.tap("a2")
        assertNull(vm.state.selected)
    }

    @Test
    fun undoAndNewGame() {
        val vm = GameViewModel()
        assertFalse(vm.state.canUndo)
        vm.tap("e2", "e4")
        vm.flipBoard()
        vm.undo()
        assertEquals(BoardPiece(PlayerColor.WHITE, PieceKind.PAWN), vm.state.pieces["e2"])
        assertTrue(vm.state.flipped, "undo keeps the board orientation")

        vm.tap("d2", "d4")
        vm.newGame()
        assertEquals(32, vm.state.pieces.size)
        assertFalse(vm.state.canUndo)
    }
}
