package ru.fourvic.chess.shared.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Alpha-beta search over a private copy of the position.
 *
 * Iterative deepening, principal variation search, quiescence search on captures, a transposition table,
 * killer and history move ordering, check extension and late move reductions. Not thread-safe: one
 * instance per search.
 */
internal class Searcher(
    private val board: Board,
    private val useQuiescence: Boolean = true,
    ttSizeLog2: Int = 16,
) {
    var nodes = 0L
        private set

    private var deadline: TimeSource.Monotonic.ValueTimeMark? = null
    private var nodeLimit = Long.MAX_VALUE
    private var stopped = false

    private val ttMask = (1 shl ttSizeLog2) - 1
    private val ttKeys = LongArray(1 shl ttSizeLog2)
    private val ttMoves = IntArray(1 shl ttSizeLog2)
    private val ttScores = IntArray(1 shl ttSizeLog2)
    private val ttDepths = ByteArray(1 shl ttSizeLog2)
    private val ttFlags = ByteArray(1 shl ttSizeLog2)

    private val killers = Array(MAX_PLY) { IntArray(2) }
    private val history = IntArray(64 * 64)

    /** Best move found so far and its score, kept across iterations. */
    data class Result(val move: Move?, val score: Int, val depth: Int)

    fun iterativeDeepening(maxDepth: Int, timeMillis: Long?, maxNodes: Long? = null): Result {
        start(timeMillis, maxNodes)
        val rootMoves = board.legalMoves().toMutableList()
        if (rootMoves.isEmpty()) return Result(null, if (board.isKingAttacked) -MATE else 0, 0)

        var best = Result(rootMoves.first(), 0, 0)
        for (depth in 1..maxDepth) {
            val result = searchRoot(rootMoves, depth)
            if (stopped && depth > 1) break
            if (result.move != null) best = result
            // Search the best move first in the next iteration.
            result.move?.let { rootMoves.remove(it); rootMoves.add(0, it) }
            if (isMate(result.score)) break
            if (stopped) break
        }
        return best
    }

    /**
     * Scores every root move with a full window at [depth]. Used by weaker levels that pick among
     * near-best moves instead of always playing the best one.
     */
    fun scoreRootMoves(depth: Int, timeMillis: Long?, maxNodes: Long? = null): List<Pair<Move, Int>> {
        start(timeMillis, maxNodes)
        // Deepen pass by pass so that on a slow device the last complete pass covers every move,
        // instead of a deep pass that scored only the first few moves in generator order.
        var order = board.legalMoves()
        var complete: List<Pair<Move, Int>> = emptyList()
        for (d in 1..depth) {
            val pass = mutableListOf<Pair<Move, Int>>()
            for (move in order) {
                board.doMove(move)
                val score = -search(d - 1, -INFINITY, INFINITY, 1)
                board.undoMove()
                if (stopped) break // the interrupted move has no reliable score
                pass += move to score
            }
            if (stopped) {
                if (complete.isEmpty()) complete = pass.sortedByDescending { it.second }
                break
            }
            complete = pass.sortedByDescending { it.second }
            order = complete.map { it.first }
        }
        return complete
    }

    private fun start(timeMillis: Long?, maxNodes: Long?) {
        nodes = 0
        stopped = false
        deadline = timeMillis?.let { TimeSource.Monotonic.markNow() + it.milliseconds }
        nodeLimit = maxNodes ?: Long.MAX_VALUE
    }

    private fun checkStop() {
        if (nodes >= nodeLimit) stopped = true
        if ((nodes and 1023L) == 0L) {
            val d = deadline
            if (d != null && d.hasPassedNow()) stopped = true
        }
    }

    private fun searchRoot(moves: List<Move>, depth: Int): Result {
        var alpha = -INFINITY
        val beta = INFINITY
        var bestMove: Move? = null
        var bestScore = -INFINITY
        for ((index, move) in moves.withIndex()) {
            board.doMove(move)
            var score = if (index == 0) {
                -search(depth - 1, -beta, -alpha, 1)
            } else {
                val s = -search(depth - 1, -alpha - 1, -alpha, 1)
                if (s > alpha && s < beta && !stopped) -search(depth - 1, -beta, -alpha, 1) else s
            }
            board.undoMove()
            if (stopped) break
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (score > alpha) alpha = score
        }
        if (bestMove != null && !stopped) store(board.incrementalHashKey, depth, bestScore, EXACT, encode(bestMove), 0)
        return Result(bestMove, bestScore, depth)
    }

    private fun search(depthIn: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        checkStop()
        if (stopped) return 0

        if (board.halfMoveCounter >= 100 || board.isRepetition(2) || board.isInsufficientMaterial) return 0
        if (ply >= MAX_PLY - 1) return Evaluator.evaluate(board)

        val inCheck = board.isKingAttacked
        val depth = if (inCheck) depthIn + 1 else depthIn
        if (depth <= 0) return if (useQuiescence) quiescence(alphaIn, beta, ply) else Evaluator.evaluate(board)

        var alpha = alphaIn
        val key = board.incrementalHashKey
        val slot = (key.toInt() xor (key ushr 32).toInt()) and ttMask
        var ttMove = 0
        if (ttKeys[slot] == key) {
            ttMove = ttMoves[slot]
            if (ttDepths[slot] >= depth) {
                val score = fromTt(ttScores[slot], ply)
                when (ttFlags[slot]) {
                    EXACT -> return score
                    LOWER -> if (score >= beta) return score
                    UPPER -> if (score <= alpha) return score
                }
            }
        }

        val moves = board.legalMoves().toMutableList()
        if (moves.isEmpty()) return if (inCheck) -MATE + ply else 0

        val scores = IntArray(moves.size) { orderScore(moves[it], ttMove, ply) }
        var bestScore = -INFINITY
        var bestMove = 0
        val originalAlpha = alpha

        for (i in moves.indices) {
            pickNext(moves, scores, i)
            val move = moves[i]
            val encoded = encode(move)
            val quiet = isQuiet(move)

            board.doMove(move)
            val givesCheck = board.isKingAttacked
            var score: Int
            if (i == 0) {
                score = -search(depth - 1, -beta, -alpha, ply + 1)
            } else {
                // Late move reduction for quiet moves searched after the promising ones.
                val reduce = depth >= 3 && i >= 4 && quiet && !inCheck && !givesCheck
                score = -search(depth - 1 - (if (reduce) 1 else 0), -alpha - 1, -alpha, ply + 1)
                if (score > alpha && !stopped) {
                    score = -search(depth - 1, -beta, -alpha, ply + 1)
                }
            }
            board.undoMove()
            if (stopped) return 0

            if (score > bestScore) {
                bestScore = score
                bestMove = encoded
            }
            if (score > alpha) alpha = score
            if (alpha >= beta) {
                if (quiet) {
                    val k = killers[ply]
                    if (k[0] != encoded) {
                        k[1] = k[0]
                        k[0] = encoded
                    }
                    val h = (encoded and 0xFFF)
                    history[h] = minOf(history[h] + depth * depth, HISTORY_MAX)
                }
                store(key, depth, bestScore, LOWER, bestMove, ply)
                return bestScore
            }
        }
        store(key, depth, bestScore, if (alpha > originalAlpha) EXACT else UPPER, bestMove, ply)
        return bestScore
    }

    private fun quiescence(alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        checkStop()
        if (stopped) return 0
        if (ply >= MAX_PLY - 1) return Evaluator.evaluate(board)

        var alpha = alphaIn
        val inCheck = board.isKingAttacked
        val standPat: Int
        val moves: MutableList<Move>
        if (inCheck) {
            moves = board.legalMoves().toMutableList()
            if (moves.isEmpty()) return -MATE + ply
            standPat = -INFINITY
        } else {
            standPat = Evaluator.evaluate(board)
            if (standPat >= beta) return standPat
            if (standPat > alpha) alpha = standPat
            moves = board.pseudoLegalCaptures().filterTo(ArrayList()) { board.isMoveLegal(it, false) }
        }

        val scores = IntArray(moves.size) { orderScore(moves[it], 0, ply) }
        var best = standPat
        for (i in moves.indices) {
            pickNext(moves, scores, i)
            val move = moves[i]
            if (!inCheck && move.promotion == Piece.NONE) {
                // Delta pruning: even winning this piece cannot lift the score to alpha.
                val gain = Evaluator.pieceValue(board.getPiece(move.to)).coerceAtLeast(100)
                if (standPat + gain + DELTA_MARGIN < alpha) continue
            }
            board.doMove(move)
            val score = -quiescence(-beta, -alpha, ply + 1)
            board.undoMove()
            if (stopped) return 0
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= beta) return best
        }
        return best
    }

    private fun orderScore(move: Move, ttMove: Int, ply: Int): Int {
        val encoded = encode(move)
        if (encoded == ttMove) return 1_000_000
        val victim = board.getPiece(move.to)
        val attacker = board.getPiece(move.from)
        if (victim != Piece.NONE) {
            return 100_000 + Evaluator.pieceValue(victim) * 10 - Evaluator.pieceValue(attacker) / 10
        }
        if (attacker.pieceType == PieceType.PAWN && move.from.file != move.to.file) {
            return 100_000 + 1000 - 10 // en passant
        }
        if (move.promotion != Piece.NONE) return 90_000 + Evaluator.pieceValue(move.promotion)
        val k = killers[ply]
        if (encoded == k[0]) return 80_000
        if (encoded == k[1]) return 79_000
        return history[encoded and 0xFFF]
    }

    private fun isQuiet(move: Move): Boolean {
        if (board.getPiece(move.to) != Piece.NONE || move.promotion != Piece.NONE) return false
        val mover = board.getPiece(move.from)
        return !(mover.pieceType == PieceType.PAWN && move.from.file != move.to.file)
    }

    /** Selection step: moves the highest-scored remaining move to position [i]. */
    private fun pickNext(moves: MutableList<Move>, scores: IntArray, i: Int) {
        var bestIndex = i
        for (j in i + 1 until scores.size) if (scores[j] > scores[bestIndex]) bestIndex = j
        if (bestIndex != i) {
            val m = moves[i]; moves[i] = moves[bestIndex]; moves[bestIndex] = m
            val s = scores[i]; scores[i] = scores[bestIndex]; scores[bestIndex] = s
        }
    }

    private fun store(key: Long, depth: Int, score: Int, flag: Byte, move: Int, ply: Int) {
        val slot = (key.toInt() xor (key ushr 32).toInt()) and ttMask
        if (ttKeys[slot] == key && ttDepths[slot] > depth && flag != EXACT) return
        ttKeys[slot] = key
        ttDepths[slot] = depth.coerceAtMost(127).toByte()
        ttScores[slot] = toTt(score, ply)
        ttFlags[slot] = flag
        ttMoves[slot] = move
    }

    private fun toTt(score: Int, ply: Int) = when {
        score > MATE_BOUND -> score + ply
        score < -MATE_BOUND -> score - ply
        else -> score
    }

    private fun fromTt(score: Int, ply: Int) = when {
        score > MATE_BOUND -> score - ply
        score < -MATE_BOUND -> score + ply
        else -> score
    }

    companion object {
        const val MATE = 30_000
        const val MATE_BOUND = MATE - 1_000
        const val INFINITY = 32_000
        const val MAX_PLY = 96
        private const val HISTORY_MAX = 60_000
        private const val DELTA_MARGIN = 200
        private const val EXACT: Byte = 0
        private const val LOWER: Byte = 1
        private const val UPPER: Byte = 2

        fun isMate(score: Int) = score > MATE_BOUND || score < -MATE_BOUND

        /** from (6 bits) | to (6 bits) | promotion piece + 1 (4 bits). */
        fun encode(move: Move): Int =
            move.from.ordinal or (move.to.ordinal shl 6) or
                ((if (move.promotion == Piece.NONE) 0 else move.promotion.ordinal + 1) shl 12)
    }
}
