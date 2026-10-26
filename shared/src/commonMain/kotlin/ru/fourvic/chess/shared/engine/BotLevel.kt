package ru.fourvic.chess.shared.engine

/**
 * Strength levels of the computer opponent.
 *
 * Weak levels search shallowly and pick among near-best moves, sometimes making a human-like mistake.
 * [approximateRating] is rounded down from matches against Stockfish with UCI_LimitStrength (October 2026):
 * level 1 lost all games to Elo 1320, level 2 scored 50 % against 1320, level 4 50 % against 1800,
 * level 5 67 % against 2000. Phones search slower, so the time-limited levels 4 and 5 are labelled lower.
 */
enum class BotLevel(
    val number: Int,
    val approximateRating: Int,
    /** Search depth in plies; the strongest level searches deeper until its time runs out. */
    internal val depth: Int,
    internal val timeMillis: Long,
    /** Without quiescence search the bot does not see recaptures and hangs pieces. */
    internal val quiescence: Boolean,
    /** Moves within this many centipawns of the best are candidates; 0 always plays the best move. */
    internal val marginCp: Int,
    /** Probability of a mistake: a random move up to [blunderWindowCp] worse than the best. */
    internal val blunderChance: Double,
    internal val blunderWindowCp: Int,
) {
    LEVEL_1(1, 800, depth = 1, timeMillis = 400, quiescence = false, marginCp = 250, blunderChance = 0.25, blunderWindowCp = 900),
    LEVEL_2(2, 1200, depth = 2, timeMillis = 500, quiescence = true, marginCp = 120, blunderChance = 0.12, blunderWindowCp = 500),
    LEVEL_3(3, 1500, depth = 3, timeMillis = 600, quiescence = true, marginCp = 60, blunderChance = 0.06, blunderWindowCp = 300),
    LEVEL_4(4, 1750, depth = 4, timeMillis = 700, quiescence = true, marginCp = 25, blunderChance = 0.03, blunderWindowCp = 200),
    LEVEL_5(5, 2000, depth = 64, timeMillis = 900, quiescence = true, marginCp = 0, blunderChance = 0.0, blunderWindowCp = 0),
    ;

    companion object {
        fun of(number: Int): BotLevel = entries.first { it.number == number }
    }
}
