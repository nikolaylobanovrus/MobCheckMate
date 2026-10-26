package ru.fourvic.chess.shared.engine

import com.github.bhlangonijr.chesslib.move.Move
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.toChessMove
import kotlin.math.exp
import kotlin.random.Random

/** Engine evaluation of a position. */
data class Analysis(
    val bestMove: ChessMove?,
    /** Centipawns from the side to move's point of view; see [mateIn]. */
    val scoreCp: Int,
    /** Moves to mate: positive when the side to move mates, negative when it gets mated, null otherwise. */
    val mateIn: Int?,
    val depth: Int,
    val nodes: Long,
)

/**
 * Computer opponent. Runs entirely on the device without network access.
 * Blocking: call it off the main thread. Each call works on its own copy of the position.
 */
class ChessBot(private val random: Random = Random.Default) {

    /** Picks a move for the side to move at [level], or null when the game is over. */
    fun chooseMove(game: ChessGame, level: BotLevel): ChessMove? {
        if (game.status.isFinished) return null
        val board = game.boardCopy()
        val inOpening = board.moveCounter <= OPENING_MOVES

        if (level.marginCp == 0 && !inOpening) {
            val searcher = Searcher(board, level.quiescence, ttSizeLog2 = 17)
            return searcher.iterativeDeepening(level.depth, level.timeMillis).move?.toChessMove()
        }

        // Strongest level in the opening: vary between near-equal moves so games do not repeat.
        val depth = if (level.marginCp == 0) OPENING_DEPTH else level.depth
        val margin = if (level.marginCp == 0) OPENING_MARGIN_CP else level.marginCp
        val scored = Searcher(board, level.quiescence, ttSizeLog2 = 15).scoreRootMoves(depth, level.timeMillis)
        val move = if (scored.isEmpty()) {
            Searcher(game.boardCopy(), level.quiescence, ttSizeLog2 = 12).iterativeDeepening(1, null).move
        } else {
            pick(scored, margin, level)
        }
        return move?.toChessMove()
    }

    /** Best move and score with a depth and time budget; for hints, analysis and tests. */
    fun analyze(game: ChessGame, maxDepth: Int = 64, timeMillis: Long? = 1_000): Analysis {
        val searcher = Searcher(game.boardCopy(), useQuiescence = true, ttSizeLog2 = 17)
        val result = searcher.iterativeDeepening(maxDepth, timeMillis)
        val mateIn = when {
            result.score > Searcher.MATE_BOUND -> (Searcher.MATE - result.score + 1) / 2
            result.score < -Searcher.MATE_BOUND -> -(Searcher.MATE + result.score) / 2
            else -> null
        }
        return Analysis(result.move?.toChessMove(), result.score, mateIn, result.depth, searcher.nodes)
    }

    private fun pick(scored: List<Pair<Move, Int>>, margin: Int, level: BotLevel): Move {
        val best = scored.first().second
        if (level.blunderChance > 0 && random.nextDouble() < level.blunderChance) {
            val mistakes = scored.drop(1).filter { it.second >= best - level.blunderWindowCp }
            if (mistakes.isNotEmpty()) return mistakes[random.nextInt(mistakes.size)].first
        }
        val pool = scored.filter { it.second >= best - margin }
        val weights = pool.map { exp(-(best - it.second) / (margin / 2.0)) }
        var r = random.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return pool[i].first
        }
        return pool.last().first
    }

    private companion object {
        /** Full moves from the position's move number, so games started from a FEN count correctly. */
        const val OPENING_MOVES = 6
        const val OPENING_DEPTH = 4
        const val OPENING_MARGIN_CP = 15
    }
}
