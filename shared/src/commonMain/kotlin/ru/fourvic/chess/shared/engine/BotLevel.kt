package ru.fourvic.chess.shared.engine

/**
 * Strength levels of the computer opponent.
 *
 * Weak levels search shallowly and pick among near-best moves, sometimes making a human-like mistake.
 * [approximateRating] is a target for calibration, not a measured value.
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
    LEVEL_1(1, 700, depth = 1, timeMillis = 400, quiescence = false, marginCp = 250, blunderChance = 0.25, blunderWindowCp = 900),
    LEVEL_2(2, 1000, depth = 2, timeMillis = 500, quiescence = true, marginCp = 120, blunderChance = 0.12, blunderWindowCp = 500),
    LEVEL_3(3, 1250, depth = 3, timeMillis = 600, quiescence = true, marginCp = 60, blunderChance = 0.06, blunderWindowCp = 300),
    LEVEL_4(4, 1450, depth = 4, timeMillis = 700, quiescence = true, marginCp = 25, blunderChance = 0.03, blunderWindowCp = 200),
    LEVEL_5(5, 1700, depth = 64, timeMillis = 900, quiescence = true, marginCp = 0, blunderChance = 0.0, blunderWindowCp = 0),
    ;

    companion object {
        fun of(number: Int): BotLevel = entries.first { it.number == number }
    }
}
