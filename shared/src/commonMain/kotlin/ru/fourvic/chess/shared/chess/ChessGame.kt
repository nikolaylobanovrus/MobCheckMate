package ru.fourvic.chess.shared.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

/**
 * Rules of chess for one game, shared by the Android client and the server.
 *
 * Wraps kchesslib so the rest of the code base never depends on the library types directly.
 * The class is not thread-safe; the server keeps one instance per game behind a single writer.
 */
class ChessGame(private val startFen: String = START_FEN) {

    private val board = Board().apply { loadFromFen(startFen) }
    private val playedMoves = mutableListOf<ChessMove>()

    /** Current position in FEN. */
    val fen: String get() = board.fen

    val sideToMove: PlayerColor get() = board.sideToMove.toColor()

    /** Moves played since the game was created, oldest first. */
    val moves: List<ChessMove> get() = playedMoves.toList()

    val isCheck: Boolean get() = board.isKingAttacked

    fun legalMoves(): List<ChessMove> = board.legalMoves().map { it.toChessMove() }

    /** Legal destination squares for the piece on [from], for highlighting in the UI. */
    fun legalTargets(from: String): Set<String> =
        legalMoves().filter { it.from == from }.mapTo(mutableSetOf()) { it.to }

    /** True when moving from [from] to [to] is a pawn promotion and needs a piece choice. */
    fun isPromotion(from: String, to: String): Boolean =
        legalMoves().any { it.from == from && it.to == to && it.promotion != null }

    fun pieceAt(square: String): BoardPiece? = board.getPiece(square.toSquare()).toBoardPiece()

    /**
     * Plays [move] if it is legal in the current position.
     * Returns false and leaves the position unchanged otherwise.
     */
    fun play(move: ChessMove): Boolean {
        if (status.isFinished) return false
        val legal = board.legalMoves().firstOrNull { it.toChessMove() == move } ?: return false
        board.doMove(legal)
        playedMoves += move
        return true
    }

    /** Independent copy with the same start position and move history, safe to hand to another thread. */
    fun copy(): ChessGame = ChessGame(startFen).also { copy -> playedMoves.forEach { copy.play(it) } }

    /** Copy of the library board, including position history for repetition detection. For the engine. */
    internal fun boardCopy(): Board = board.clone()

    /** Takes back the last move. Returns false when there is nothing to undo. */
    fun undo(): Boolean {
        if (playedMoves.isEmpty()) return false
        board.undoMove()
        playedMoves.removeAt(playedMoves.lastIndex)
        return true
    }

    val status: GameStatus
        get() = when {
            board.isMated -> GameStatus.CHECKMATE
            board.isStaleMate -> GameStatus.STALEMATE
            board.isRepetition(3) -> GameStatus.DRAW_THREEFOLD_REPETITION
            board.isInsufficientMaterial -> GameStatus.DRAW_INSUFFICIENT_MATERIAL
            board.halfMoveCounter >= 100 -> GameStatus.DRAW_FIFTY_MOVES
            else -> GameStatus.ONGOING
        }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        /** All 64 squares from a8 to h1, row by row, as seen from White. */
        val SQUARES_FROM_WHITE: List<String> =
            (8 downTo 1).flatMap { rank -> ('a'..'h').map { file -> "$file$rank" } }
    }
}

private fun String.toSquare(): Square = Square.valueOf(uppercase())

private fun Square.toName(): String = name.lowercase()

private fun Side.toColor(): PlayerColor = if (this == Side.WHITE) PlayerColor.WHITE else PlayerColor.BLACK

internal fun Move.toChessMove(): ChessMove = ChessMove(
    from = from.toName(),
    to = to.toName(),
    promotion = when (promotion.pieceType) {
        PieceType.QUEEN -> Promotion.QUEEN
        PieceType.ROOK -> Promotion.ROOK
        PieceType.BISHOP -> Promotion.BISHOP
        PieceType.KNIGHT -> Promotion.KNIGHT
        else -> null
    },
)

private fun Piece.toBoardPiece(): BoardPiece? {
    if (this == Piece.NONE) return null
    val kind = when (pieceType) {
        PieceType.PAWN -> PieceKind.PAWN
        PieceType.KNIGHT -> PieceKind.KNIGHT
        PieceType.BISHOP -> PieceKind.BISHOP
        PieceType.ROOK -> PieceKind.ROOK
        PieceType.QUEEN -> PieceKind.QUEEN
        PieceType.KING -> PieceKind.KING
        else -> return null
    }
    val color = pieceSide?.toColor() ?: return null
    return BoardPiece(color, kind)
}
