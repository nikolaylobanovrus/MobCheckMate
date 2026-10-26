package ru.fourvic.chess.shared.puzzle

import ru.fourvic.chess.shared.rating.GameOutcome
import ru.fourvic.chess.shared.rating.Glicko2
import ru.fourvic.chess.shared.rating.Rating
import kotlin.random.Random

/** The solver's puzzle record, persisted by the app. */
data class PuzzleProgress(
    val rating: Rating = Rating(1200.0, 350.0, 0.06),
    val solved: Int = 0,
    val failed: Int = 0,
    /** Epoch day of the last solved daily puzzle, or null. */
    val lastDailyDay: Long? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
) {
    /** Streak as the user should see it today: it is lost after a missed day. */
    fun currentStreak(today: Long): Int = if (lastDailyDay != null && today - lastDailyDay <= 1) streak else 0

    fun isDailySolved(today: Long) = lastDailyDay == today
}

object PuzzleRating {
    /** Puzzle ratings in the Lichess set are stable; a fixed deviation keeps updates moderate. */
    private const val PUZZLE_DEVIATION = 80.0
    private val glicko = Glicko2(tau = 0.5)

    /** Records one finished puzzle: a win if solved without mistakes, otherwise a loss. */
    fun record(progress: PuzzleProgress, puzzle: Puzzle, solvedCleanly: Boolean): PuzzleProgress {
        val opponent = Rating(puzzle.rating.toDouble(), PUZZLE_DEVIATION, 0.06)
        val updated = glicko.update(progress.rating, listOf(GameOutcome(opponent, if (solvedCleanly) 1.0 else 0.0)))
        return progress.copy(
            rating = updated.copy(deviation = updated.deviation.coerceAtLeast(MIN_DEVIATION)),
            solved = progress.solved + if (solvedCleanly) 1 else 0,
            failed = progress.failed + if (solvedCleanly) 0 else 1,
        )
    }

    /** Daily puzzle solved today: extends the streak if yesterday's was solved too. */
    fun recordDaily(progress: PuzzleProgress, today: Long): PuzzleProgress {
        if (progress.lastDailyDay == today) return progress
        val streak = if (progress.lastDailyDay == today - 1) progress.streak + 1 else 1
        return progress.copy(lastDailyDay = today, streak = streak, bestStreak = maxOf(progress.bestStreak, streak))
    }

    /** Never settles completely, so the rating keeps following the solver's form. */
    private const val MIN_DEVIATION = 60.0
}

/** Chooses puzzles from a list sorted by rating. */
class PuzzleSelector(puzzles: List<Puzzle>, private val random: Random = Random.Default) {
    private val sorted = puzzles.sortedBy { it.rating }

    val size: Int get() = sorted.size

    /** A random puzzle close to [rating], skipping ids in [exclude]; the window widens until one is found. */
    fun next(rating: Int, exclude: Set<String> = emptySet()): Puzzle? {
        if (sorted.isEmpty()) return null
        var window = 75
        while (window <= 3000) {
            val candidates = sorted.filter { kotlin.math.abs(it.rating - rating) <= window && it.id !in exclude }
            if (candidates.isNotEmpty()) return candidates[random.nextInt(candidates.size)]
            window *= 2
        }
        return sorted[random.nextInt(sorted.size)]
    }

    /** The same puzzle for everyone on a given day, from the mid-rated popular part of the set. */
    fun daily(epochDay: Long): Puzzle? {
        val pool = sorted.filter { it.rating in 1000..1900 }.ifEmpty { sorted }
        if (pool.isEmpty()) return null
        val index = ((epochDay * 2_654_435_761L) and 0x7FFF_FFFFL) % pool.size
        return pool[index.toInt()]
    }
}
