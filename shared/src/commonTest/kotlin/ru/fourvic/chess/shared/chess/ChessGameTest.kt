package ru.fourvic.chess.shared.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChessGameTest {

    private fun ChessGame.playAll(vararg uci: String) = uci.forEach { move ->
        assertTrue(play(assertNotNull(ChessMove.fromUci(move))), "move $move must be legal")
    }

    @Test
    fun startingPositionHasTwentyLegalMoves() {
        val game = ChessGame()
        assertEquals(20, game.legalMoves().size)
        assertEquals(PlayerColor.WHITE, game.sideToMove)
        assertEquals(GameStatus.ONGOING, game.status)
    }

    @Test
    fun illegalMoveIsRejectedAndPositionIsUnchanged() {
        val game = ChessGame()
        val before = game.fen
        assertFalse(game.play(ChessMove("e2", "e5")))
        assertEquals(before, game.fen)
        assertTrue(game.moves.isEmpty())
    }

    @Test
    fun enPassantCaptureRemovesThePawnThatMovedTwoSquares() {
        // White pawn d2, black pawn c4: after d2-d4 Black may take on d3.
        val game = ChessGame("4k3/8/8/8/2p5/8/3P4/4K3 w - - 0 1")
        game.playAll("d2d4")
        assertTrue("d3" in game.legalTargets("c4"))

        assertFalse(game.lastMoveWasCapture)
        game.playAll("c4d3")
        assertTrue(game.lastMoveWasCapture, "en passant counts as a capture")
        assertNull(game.pieceAt("d4"), "the d4 pawn is captured en passant")
        assertEquals(BoardPiece(PlayerColor.BLACK, PieceKind.PAWN), game.pieceAt("d3"))
    }

    @Test
    fun enPassantIsOnlyAvailableOnTheVeryNextMove() {
        val game = ChessGame("4k3/8/8/8/2p5/8/3P4/4K3 w - - 0 1")
        game.playAll("d2d4", "e8e7", "e1e2")
        assertFalse("d3" in game.legalTargets("c4"))
    }

    @Test
    fun castlingMovesBothKingAndRook() {
        val game = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        game.playAll("e1g1")
        assertEquals(BoardPiece(PlayerColor.WHITE, PieceKind.KING), game.pieceAt("g1"))
        assertEquals(BoardPiece(PlayerColor.WHITE, PieceKind.ROOK), game.pieceAt("f1"))
        assertNull(game.pieceAt("h1"))
    }

    @Test
    fun promotionOffersFourPiecesAndPlaysTheChosenOne() {
        val game = ChessGame("8/P7/8/8/8/8/8/k6K w - - 0 1")
        assertTrue(game.isPromotion("a7", "a8"))
        assertEquals(4, game.legalMoves().count { it.from == "a7" && it.to == "a8" })

        game.playAll("a7a8n")
        assertEquals(BoardPiece(PlayerColor.WHITE, PieceKind.KNIGHT), game.pieceAt("a8"))
    }

    @Test
    fun foolsMateIsCheckmate() {
        val game = ChessGame()
        game.playAll("f2f3", "e7e5", "g2g4", "d8h4")
        assertTrue(game.isCheck)
        assertEquals(GameStatus.CHECKMATE, game.status)
        assertTrue(game.legalMoves().isEmpty())
        assertFalse(game.play(ChessMove("a2", "a3")), "no moves after the game is over")
    }

    @Test
    fun stalemateIsDetected() {
        val game = ChessGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameStatus.STALEMATE, game.status)
    }

    @Test
    fun threefoldRepetitionIsADraw() {
        val game = ChessGame()
        game.playAll("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(GameStatus.DRAW_THREEFOLD_REPETITION, game.status)
    }

    @Test
    fun bareKingsAreInsufficientMaterial() {
        val game = ChessGame("8/8/8/4k3/8/8/8/4K3 w - - 0 1")
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, game.status)
    }

    @Test
    fun undoRestoresThePreviousPosition() {
        val game = ChessGame()
        val start = game.fen
        game.playAll("e2e4")
        assertTrue(game.undo())
        assertEquals(start, game.fen)
        assertFalse(game.undo())
    }

    @Test
    fun uciParsing() {
        assertEquals(ChessMove("e2", "e4"), ChessMove.fromUci("e2e4"))
        assertEquals(ChessMove("a7", "a8", Promotion.QUEEN), ChessMove.fromUci("A7A8Q"))
        assertEquals("e7e8n", ChessMove("e7", "e8", Promotion.KNIGHT).uci)
        assertNull(ChessMove.fromUci("e2e9"))
        assertNull(ChessMove.fromUci("e7e8k"))
        assertNull(ChessMove.fromUci("hello"))
    }

    @Test
    fun movesAreWrittenInAlgebraicNotation() {
        val game = ChessGame()
        game.playAll("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1", "f8c5", "f3e5", "c6e5", "d2d4", "c5d4", "d1d4")
        assertEquals(
            listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6", "O-O", "Bc5", "Nxe5", "Nxe5", "d4", "Bxd4", "Qxd4"),
            game.sanMoves,
        )
        game.undo()
        assertEquals("Bxd4", game.sanMoves.last())
    }

    @Test
    fun notationShowsChecksMatesPromotionsAndAmbiguity() {
        val mate = ChessGame()
        mate.playAll("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(listOf("f3", "e5", "g4", "Qh4#"), mate.sanMoves)

        // Both rooks can reach d1: the file tells them apart; on one file, the rank does. Then a capture-promotion.
        val endgame = ChessGame("2r1k3/1P6/8/8/8/8/4K3/R6R w - - 0 1")
        endgame.playAll("a1d1", "e8f7", "b7c8q")
        assertEquals(listOf("Rad1", "Kf7", "bxc8=Q"), endgame.sanMoves)
        val sameFile = ChessGame("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1")
        sameFile.playAll("a5a3")
        assertEquals(listOf("R5a3"), sameFile.sanMoves)

        val castling = ChessGame("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1")
        castling.playAll("e8c8")
        assertEquals(listOf("O-O-O"), castling.sanMoves)
    }
}
