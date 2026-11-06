package ru.fourvic.chess.ui.game

import ru.fourvic.chess.shared.chess.BoardPiece
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PieceKind
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialBalanceTest {

    private fun pieces(fen: String, vararg moves: String): Map<String, BoardPiece> {
        val game = ChessGame(fen)
        moves.forEach { check(game.play(ChessMove.fromUci(it)!!)) }
        return ChessGame.SQUARES_FROM_WHITE.mapNotNull { square -> game.pieceAt(square)?.let { square to it } }.toMap()
    }

    @Test
    fun startingPositionIsEven() {
        assertEquals(MaterialBalance.EVEN, materialBalance(pieces(ChessGame.START_FEN)))
    }

    @Test
    fun capturesShowOnTheSideThatIsUp() {
        // 1. e4 d5 2. exd5 Qxd5 3. Nc3 Qxa2?? 4. Rxa2: White is up a queen for a pawn and a pawn.
        val balance = materialBalance(pieces(ChessGame.START_FEN, "e2e4", "d7d5", "e4d5", "d8d5", "b1c3", "d5a2", "a1a2"))
        assertEquals(listOf(PieceKind.QUEEN), balance.white)
        assertEquals(listOf(PieceKind.PAWN), balance.black)
        assertEquals(8, balance.score)
    }

    @Test
    fun promotionCountsAsTheNewPiece() {
        // White has promoted to a second queen; Black still has all pawns but no queen.
        val balance = materialBalance(pieces("4k3/pppppppp/8/8/8/8/1PPPPPPP/QQ2K3 w - - 0 1"))
        assertEquals(listOf(PieceKind.QUEEN, PieceKind.QUEEN), balance.white)
        assertEquals(listOf(PieceKind.PAWN), balance.black)
        assertEquals(17, balance.score)
    }
}
