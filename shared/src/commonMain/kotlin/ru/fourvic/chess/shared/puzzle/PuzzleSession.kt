package ru.fourvic.chess.shared.puzzle

import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PlayerColor

enum class AttemptResult {
    /** Right move; the opponent's reply is waiting in [PuzzleSession.pendingReply]. */
    CORRECT,

    /** Right move and the puzzle is complete. */
    SOLVED,

    /** Wrong move; the position is unchanged. */
    WRONG,
}

/**
 * Solving one puzzle. The opponent's first move is played on creation. After each correct solver move the
 * opponent's reply is not played automatically: the UI calls [playReply] (usually after a short pause).
 * The first wrong move marks the puzzle as failed; the solver may keep trying.
 */
class PuzzleSession(val puzzle: Puzzle) {

    val game = ChessGame(puzzle.fen).also { check(it.play(puzzle.moves[0])) { "Bad opening move in ${puzzle.id}" } }

    /** The colour the solver plays. */
    val solverColor: PlayerColor = game.sideToMove

    /** Index in [Puzzle.moves] of the next expected move. */
    private var index = 1

    var failed = false
        private set

    val isSolved: Boolean get() = index >= puzzle.moves.size

    /** Opponent's answer to the solver's last correct move, if it has not been played yet. */
    val pendingReply: ChessMove?
        get() = if (!isSolved && game.sideToMove != solverColor) puzzle.moves[index] else null

    /** The move the solver should play now, for hints and "show solution". */
    val expectedMove: ChessMove?
        get() = if (!isSolved && game.sideToMove == solverColor) puzzle.moves[index] else null

    fun attempt(move: ChessMove): AttemptResult {
        val expected = expectedMove ?: return AttemptResult.WRONG
        val accepted = move == expected || deliversMate(move)
        if (!accepted) {
            failed = true
            return AttemptResult.WRONG
        }
        check(game.play(move))
        index++
        // Any mate ends the puzzle, even if the database line continues differently.
        if (game.status == GameStatus.CHECKMATE) index = puzzle.moves.size
        return if (isSolved) AttemptResult.SOLVED else AttemptResult.CORRECT
    }

    /** Plays the opponent's reply after a correct move. */
    fun playReply(): ChessMove? {
        val reply = pendingReply ?: return null
        check(game.play(reply)) { "Bad reply ${reply.uci} in ${puzzle.id}" }
        index++
        return reply
    }

    /** Plays the expected move for the solver (when showing the solution); counts as a failure. */
    fun revealNextMove(): ChessMove? {
        val move = expectedMove ?: return null
        failed = true
        attempt(move)
        return move
    }

    private fun deliversMate(move: ChessMove): Boolean {
        val probe = game.copy()
        return probe.play(move) && probe.status == GameStatus.CHECKMATE
    }
}
