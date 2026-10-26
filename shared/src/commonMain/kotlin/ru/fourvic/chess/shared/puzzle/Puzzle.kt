package ru.fourvic.chess.shared.puzzle

import ru.fourvic.chess.shared.chess.ChessMove

/**
 * A tactics puzzle in the Lichess format: [fen] is the position before the opponent's move,
 * [moves] starts with that opponent move, then alternates solver and opponent moves.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val moves: List<ChessMove>,
    val rating: Int,
    val themes: List<String>,
) {
    init {
        require(moves.size >= 2) { "Puzzle $id needs an opponent move and at least one solution move" }
    }

    companion object {
        /** Parses one line of the bundled TSV: id, fen, moves, rating, themes. Comment lines start with '#'. */
        fun parseLine(line: String): Puzzle? {
            if (line.isBlank() || line.startsWith("#")) return null
            val parts = line.split('\t')
            if (parts.size < 4) return null
            val moves = parts[2].split(' ').map { ChessMove.fromUci(it) ?: return null }
            return Puzzle(
                id = parts[0],
                fen = parts[1],
                moves = moves,
                rating = parts[3].toIntOrNull() ?: return null,
                themes = parts.getOrNull(4)?.split(',')?.filter { it.isNotEmpty() } ?: emptyList(),
            )
        }

        fun parse(text: String): List<Puzzle> = text.lineSequence().mapNotNull(::parseLine).toList()
    }
}
