package ru.fourvic.chess.shared.engine

import com.github.bhlangonijr.chesslib.Bitboard
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import kotlin.math.abs
import kotlin.math.max

/**
 * Static evaluation in centipawns from the side to move's point of view.
 *
 * Tapered between middlegame and endgame by remaining material. Piece-square tables are generated from
 * simple formulas (centralisation, pawn advance, king shelter) rather than copied from other engines.
 */
internal object Evaluator {

    /** Indexed by [PieceType.ordinal]: pawn, knight, bishop, rook, queen, king. */
    val MG_VALUE = intArrayOf(100, 320, 330, 500, 900, 0)
    val EG_VALUE = intArrayOf(120, 300, 320, 520, 940, 0)
    private val PHASE_WEIGHT = intArrayOf(0, 1, 1, 2, 4, 0)
    private const val TOTAL_PHASE = 24

    private val PASSED_MG = intArrayOf(0, 5, 10, 15, 25, 40, 60, 0)
    private val PASSED_EG = intArrayOf(0, 10, 20, 35, 60, 90, 130, 0)

    private const val FILE_A = 0x0101010101010101L

    // Piece-square tables from White's side, indexed [pieceType][square].
    private val PST_MG = Array(6) { IntArray(64) }
    private val PST_EG = Array(6) { IntArray(64) }

    private val fileMask = LongArray(8) { FILE_A shl it }
    private val adjacentFiles = LongArray(8) { f ->
        (if (f > 0) fileMask[f - 1] else 0L) or (if (f < 7) fileMask[f + 1] else 0L)
    }

    /** Squares in front of a pawn on its own and adjacent files; [side][square]. */
    private val passedMask = Array(2) { LongArray(64) }

    private val squares = Array(64) { Square.squareAt(it) }

    init {
        for (sq in 0 until 64) {
            val f = sq % 8
            val r = sq / 8
            val centre = max(abs(f - 3.5), abs(r - 3.5)) - 0.5 // 0 in the centre .. 3 on the edge
            val centreFile = when (f) { 3, 4 -> 12; 2, 5 -> 6; else -> 0 }

            PST_MG[0][sq] = if (r in 1..6) (r - 1) * 4 + if (r in 2..4) centreFile else 0 else 0
            PST_EG[0][sq] = if (r in 1..6) (r - 1) * 12 else 0
            PST_MG[1][sq] = (25 - 12 * centre).toInt()
            PST_EG[1][sq] = (15 - 10 * centre).toInt()
            PST_MG[2][sq] = (10 - 5 * centre).toInt()
            PST_EG[2][sq] = (10 - 5 * centre).toInt()
            PST_MG[3][sq] = (if (r == 6) 20 else 0) + (if (f == 3 || f == 4) 5 else 0)
            PST_EG[3][sq] = if (r == 6) 10 else 0
            PST_MG[4][sq] = (5 - 3 * centre).toInt()
            PST_EG[4][sq] = (10 - 5 * centre).toInt()
            PST_MG[5][sq] = if (r == 0) {
                when (f) { 1, 2, 6 -> 25; 0, 7 -> 15; else -> 0 }
            } else {
                -15 * r
            }
            PST_EG[5][sq] = (20 - 10 * centre).toInt()

            var whiteFront = 0L
            for (rr in r + 1..7) whiteFront = whiteFront or (0xFFL shl (rr * 8))
            var blackFront = 0L
            for (rr in 0 until r) blackFront = blackFront or (0xFFL shl (rr * 8))
            val files = fileMask[f] or adjacentFiles[f]
            passedMask[0][sq] = whiteFront and files
            passedMask[1][sq] = blackFront and files
        }
    }

    fun evaluate(board: Board): Int {
        var mg = 0
        var eg = 0
        var phase = 0
        val occupied = board.getBitboard()

        for (side in 0..1) {
            val sign = if (side == 0) 1 else -1
            val us = if (side == 0) Side.WHITE else Side.BLACK
            val own = board.getBitboard(us)
            val ownPawns = board.getBitboard(Piece.make(us, PieceType.PAWN))
            val enemyPawns = board.getBitboard(Piece.make(us.flip(), PieceType.PAWN))
            var sideMg = 0
            var sideEg = 0

            for (type in 0..5) {
                val pieceType = PieceType.entries[type]
                var bb = board.getBitboard(Piece.make(us, pieceType))
                val count = bb.countOneBits()
                sideMg += MG_VALUE[type] * count
                sideEg += EG_VALUE[type] * count
                phase += PHASE_WEIGHT[type] * count
                while (bb != 0L) {
                    val sq = bb.countTrailingZeroBits()
                    bb = bb and (bb - 1)
                    val rel = if (side == 0) sq else sq xor 56
                    sideMg += PST_MG[type][rel]
                    sideEg += PST_EG[type][rel]
                    val square = squares[sq]
                    when (type) {
                        0 -> {
                            val f = sq % 8
                            if (passedMask[side][sq] and enemyPawns == 0L) {
                                val rank = rel / 8
                                sideMg += PASSED_MG[rank]
                                sideEg += PASSED_EG[rank]
                            }
                            if (adjacentFiles[f] and ownPawns == 0L) {
                                sideMg -= 10
                                sideEg -= 15
                            }
                        }
                        1 -> {
                            val mobility = Bitboard.getKnightAttacks(square, own.inv()).countOneBits() - 4
                            sideMg += 4 * mobility
                            sideEg += 4 * mobility
                        }
                        2 -> {
                            val mobility = (Bitboard.getBishopAttacks(occupied, square) and own.inv()).countOneBits() - 6
                            sideMg += 4 * mobility
                            sideEg += 5 * mobility
                        }
                        3 -> {
                            val mobility = (Bitboard.getRookAttacks(occupied, square) and own.inv()).countOneBits() - 7
                            sideMg += 2 * mobility
                            sideEg += 4 * mobility
                            val file = fileMask[sq % 8]
                            if (file and ownPawns == 0L) {
                                if (file and enemyPawns == 0L) {
                                    sideMg += 18; sideEg += 10
                                } else {
                                    sideMg += 9; sideEg += 5
                                }
                            }
                        }
                        4 -> {
                            val mobility = (Bitboard.getQueenAttacks(occupied, square) and own.inv()).countOneBits() - 13
                            sideMg += mobility
                            sideEg += 2 * mobility
                        }
                        5 -> {
                            // Pawn shelter in front of a king that stays on its back rank.
                            if (rel / 8 == 0) {
                                val f = sq % 8
                                val shieldFiles = fileMask[f] or adjacentFiles[f]
                                val shieldRanks = if (side == 0) 0xFFFF00L else 0x00FFFF0000000000L
                                val shield = (ownPawns and shieldFiles and shieldRanks).countOneBits()
                                sideMg += 10 * minOf(shield, 3)
                            }
                        }
                    }
                }
            }

            // Doubled pawns.
            for (f in 0..7) {
                val onFile = (ownPawns and fileMask[f]).countOneBits()
                if (onFile > 1) {
                    sideMg -= 12 * (onFile - 1)
                    sideEg -= 20 * (onFile - 1)
                }
            }
            // Bishop pair.
            if (board.getBitboard(Piece.make(us, PieceType.BISHOP)).countOneBits() >= 2) {
                sideMg += 25
                sideEg += 45
            }

            mg += sign * sideMg
            eg += sign * sideEg
        }

        val p = minOf(phase, TOTAL_PHASE)
        var score = (mg * p + eg * (TOTAL_PHASE - p)) / TOTAL_PHASE
        if (board.sideToMove == Side.BLACK) score = -score
        return score + TEMPO
    }

    private const val TEMPO = 10

    fun pieceValue(piece: Piece): Int =
        if (piece == Piece.NONE) 0 else MG_VALUE[piece.pieceType?.ordinal ?: return 0]
}
