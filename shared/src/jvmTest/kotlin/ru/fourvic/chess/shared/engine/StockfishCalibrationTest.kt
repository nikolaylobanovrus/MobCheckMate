package ru.fourvic.chess.shared.engine

import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PlayerColor
import java.io.BufferedReader
import java.io.Writer
import kotlin.math.log10
import kotlin.random.Random
import kotlin.test.Test

/**
 * Manual calibration of bot levels against Stockfish with UCI_LimitStrength (UCI_Elo 1320..3190).
 * Stockfish runs as a separate local process on the developer's machine and is never bundled.
 *
 * ./gradlew :shared:jvmTest --tests '*StockfishCalibrationTest*' -PstockfishPath=/path/to/stockfish \
 *     -PstockfishElo=1320 -PengineLevels=3,4,5 -PengineMatch=10
 */
class StockfishCalibrationTest {

    @Test
    fun calibrateAgainstStockfish() {
        val path = System.getProperty("stockfishPath") ?: return
        val games = System.getProperty("engineMatch")?.toIntOrNull() ?: 10
        val elos = (System.getProperty("stockfishElo") ?: "1320").split(",").map { it.trim().toInt() }
        val levels = (System.getProperty("engineLevels") ?: "3,4,5").split(",").map { BotLevel.of(it.trim().toInt()) }
        val moveTimeMs = System.getProperty("stockfishMoveTime")?.toIntOrNull() ?: 500

        Uci(path).use { stockfish ->
            for (elo in elos) {
                stockfish.command("setoption name UCI_LimitStrength value true")
                stockfish.command("setoption name UCI_Elo value $elo")
                for (level in levels) {
                    var points = 0.0
                    val results = StringBuilder()
                    for (g in 0 until games) {
                        val botIsWhite = g % 2 == 0
                        val winner = play(stockfish, level, botIsWhite, moveTimeMs, seed = 7_000L + 100 * level.number + g)
                        val score = when (winner) {
                            null -> 0.5
                            PlayerColor.WHITE -> if (botIsWhite) 1.0 else 0.0
                            PlayerColor.BLACK -> if (botIsWhite) 0.0 else 1.0
                        }
                        points += score
                        results.append(if (score == 1.0) "1" else if (score == 0.0) "0" else "=")
                    }
                    println("CALIBRATION level ${level.number} vs Stockfish UCI_Elo $elo: $points / $games [$results] => ${estimate(elo, points, games)}")
                }
            }
        }
    }

    private fun estimate(opponentElo: Int, points: Double, games: Int): String {
        val p = points / games
        return when {
            p <= 0.0 -> "below ${opponentElo - 400}"
            p >= 1.0 -> "above ${opponentElo + 400}"
            else -> "about ${(opponentElo + 400 * log10(p / (1 - p))).toInt()}"
        }
    }

    private fun play(stockfish: Uci, level: BotLevel, botIsWhite: Boolean, moveTimeMs: Int, seed: Long): PlayerColor? {
        val game = ChessGame()
        val bot = ChessBot(Random(seed))
        stockfish.command("ucinewgame")
        while (!game.status.isFinished && game.moves.size < MAX_PLIES) {
            val botToMove = (game.sideToMove == PlayerColor.WHITE) == botIsWhite
            val move = if (botToMove) {
                bot.chooseMove(game, level) ?: break
            } else {
                val moves = game.moves.joinToString(" ") { it.uci }
                stockfish.command("position startpos" + if (moves.isEmpty()) "" else " moves $moves")
                ChessMove.fromUci(stockfish.go(moveTimeMs)) ?: break
            }
            check(game.play(move)) { "illegal move ${move.uci}" }
        }
        return if (game.status == GameStatus.CHECKMATE) {
            if (game.sideToMove == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
        } else {
            null
        }
    }

    private class Uci(path: String) : AutoCloseable {
        private val process = ProcessBuilder(path).redirectErrorStream(true).start()
        private val input: BufferedReader = process.inputStream.bufferedReader()
        private val output: Writer = process.outputStream.bufferedWriter()

        init {
            send("uci")
            readUntil { it == "uciok" }
            command("setoption name Threads value 1")
        }

        fun command(text: String) {
            send(text)
            send("isready")
            readUntil { it == "readyok" }
        }

        fun go(moveTimeMs: Int): String {
            send("go movetime $moveTimeMs")
            return readUntil { it.startsWith("bestmove") }.split(" ")[1]
        }

        private fun send(text: String) {
            output.write(text + "\n")
            output.flush()
        }

        private fun readUntil(done: (String) -> Boolean): String {
            while (true) {
                val line = input.readLine() ?: error("Stockfish exited")
                if (done(line)) return line
            }
        }

        override fun close() {
            send("quit")
            process.waitFor()
        }
    }

    private companion object {
        const val MAX_PLIES = 300
    }
}
