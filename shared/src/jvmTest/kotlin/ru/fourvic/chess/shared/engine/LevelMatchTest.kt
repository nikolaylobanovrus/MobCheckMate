package ru.fourvic.chess.shared.engine

import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PlayerColor
import kotlin.random.Random
import kotlin.test.Test

/**
 * Manual strength check: each level plays the next one with alternating colours.
 * Skipped unless run with `./gradlew :shared:jvmTest -PengineMatch=4 --tests '*LevelMatchTest*'`
 * where the number is games per pair. Prints a score table; it does not assert, the numbers are for tuning.
 */
class LevelMatchTest {

    @Test
    fun levelsPlayEachOther() {
        val games = System.getProperty("engineMatch")?.toIntOrNull() ?: return
        val pairs = (System.getProperty("enginePairs") ?: "1-2,2-3,3-4,4-5").split(",").map { p ->
            val (a, b) = p.split("-").map { BotLevel.of(it.trim().toInt()) }
            a to b
        }
        for ((weaker, stronger) in pairs) {
            var strongerPoints = 0.0
            val results = StringBuilder()
            for (g in 0 until games) {
                val strongerIsWhite = g % 2 == 0
                val result = play(
                    white = if (strongerIsWhite) stronger else weaker,
                    black = if (strongerIsWhite) weaker else stronger,
                    seed = 1000L * weaker.number + g,
                )
                val strongerScore = when (result) {
                    PlayerColor.WHITE -> if (strongerIsWhite) 1.0 else 0.0
                    PlayerColor.BLACK -> if (strongerIsWhite) 0.0 else 1.0
                    null -> 0.5
                }
                strongerPoints += strongerScore
                results.append(if (strongerScore == 1.0) "1" else if (strongerScore == 0.0) "0" else "=")
            }
            println("MATCH level ${stronger.number} vs ${weaker.number}: $strongerPoints / $games  [$results]")
        }
    }

    /** Returns the winner, or null for a draw (including the move cap). */
    private fun play(white: BotLevel, black: BotLevel, seed: Long): PlayerColor? {
        val game = ChessGame()
        val whiteBot = ChessBot(Random(seed))
        val blackBot = ChessBot(Random(seed + 1))
        while (!game.status.isFinished && game.moves.size < MAX_PLIES) {
            val toMove = game.sideToMove
            val move = (if (toMove == PlayerColor.WHITE) whiteBot.chooseMove(game, white) else blackBot.chooseMove(game, black))
                ?: break
            check(game.play(move)) { "illegal move ${move.uci}" }
        }
        return when (game.status) {
            GameStatus.CHECKMATE -> if (game.sideToMove == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
            else -> null
        }
    }

    private companion object {
        const val MAX_PLIES = 240
    }
}
