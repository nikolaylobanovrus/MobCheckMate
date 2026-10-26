package com.mobcheckmate.shared.chess

/** Side to move or owner of a piece. */
@kotlinx.serialization.Serializable
enum class PlayerColor { WHITE, BLACK }

enum class PieceKind { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

/** A piece standing on the board. */
data class BoardPiece(val color: PlayerColor, val kind: PieceKind)

/** Pieces a pawn may promote to. */
enum class Promotion(val uciSuffix: Char) {
    QUEEN('q'), ROOK('r'), BISHOP('b'), KNIGHT('n'),
}

/**
 * A move in coordinate form, for example `e2e4` or `e7e8q`.
 * Squares are lower-case algebraic names (`a1`..`h8`).
 */
data class ChessMove(val from: String, val to: String, val promotion: Promotion? = null) {
    val uci: String get() = from + to + (promotion?.uciSuffix ?: "")

    companion object {
        private val SQUARE = Regex("[a-h][1-8]")

        /** Parses a UCI move such as `e2e4` or `a7a8q`. Returns null for malformed input. */
        fun fromUci(uci: String): ChessMove? {
            val text = uci.trim().lowercase()
            if (text.length !in 4..5) return null
            val from = text.substring(0, 2)
            val to = text.substring(2, 4)
            if (!SQUARE.matches(from) || !SQUARE.matches(to)) return null
            val promotion = if (text.length == 5) {
                Promotion.entries.firstOrNull { it.uciSuffix == text[4] } ?: return null
            } else {
                null
            }
            return ChessMove(from, to, promotion)
        }
    }
}

/** Result state of a position. */
@kotlinx.serialization.Serializable
enum class GameStatus(val isFinished: Boolean) {
    ONGOING(false),
    CHECKMATE(true),
    STALEMATE(true),
    DRAW_THREEFOLD_REPETITION(true),
    DRAW_INSUFFICIENT_MATERIAL(true),
    DRAW_FIFTY_MOVES(true),
}
