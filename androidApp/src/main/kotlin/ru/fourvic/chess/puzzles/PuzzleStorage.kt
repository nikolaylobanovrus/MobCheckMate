package ru.fourvic.chess.puzzles

import android.content.Context
import androidx.core.content.edit
import ru.fourvic.chess.shared.puzzle.Puzzle
import ru.fourvic.chess.shared.puzzle.PuzzleProgress
import ru.fourvic.chess.shared.rating.Rating

/** Where puzzles come from: the bundled asset in the app, a fixed list in tests. */
fun interface PuzzleSource {
    fun load(): List<Puzzle>
}

/** Puzzle progress and recently seen puzzles, persisted between launches. */
interface PuzzleProgressStore {
    fun load(): PuzzleProgress
    fun save(progress: PuzzleProgress)
    fun recentIds(): List<String>
    fun saveRecentIds(ids: List<String>)
}

class AssetPuzzleSource(private val context: Context) : PuzzleSource {
    override fun load(): List<Puzzle> =
        context.assets.open("puzzles.tsv").bufferedReader().useLines { lines -> lines.mapNotNull(Puzzle::parseLine).toList() }
}

class PrefsPuzzleProgressStore(context: Context) : PuzzleProgressStore {
    private val prefs = context.getSharedPreferences("puzzles", Context.MODE_PRIVATE)

    override fun load(): PuzzleProgress {
        val d = PuzzleProgress()
        return PuzzleProgress(
            rating = Rating(
                rating = prefs.getFloat("rating", d.rating.rating.toFloat()).toDouble(),
                deviation = prefs.getFloat("deviation", d.rating.deviation.toFloat()).toDouble(),
                volatility = prefs.getFloat("volatility", d.rating.volatility.toFloat()).toDouble(),
            ),
            solved = prefs.getInt("solved", 0),
            failed = prefs.getInt("failed", 0),
            lastDailyDay = prefs.getLong("last_daily_day", -1).takeIf { it >= 0 },
            streak = prefs.getInt("streak", 0),
            bestStreak = prefs.getInt("best_streak", 0),
        )
    }

    override fun save(progress: PuzzleProgress) {
        prefs.edit {
            putFloat("rating", progress.rating.rating.toFloat())
            putFloat("deviation", progress.rating.deviation.toFloat())
            putFloat("volatility", progress.rating.volatility.toFloat())
            putInt("solved", progress.solved)
            putInt("failed", progress.failed)
            putLong("last_daily_day", progress.lastDailyDay ?: -1)
            putInt("streak", progress.streak)
            putInt("best_streak", progress.bestStreak)
        }
    }

    override fun recentIds(): List<String> =
        prefs.getString("recent", "").orEmpty().split(',').filter { it.isNotEmpty() }

    override fun saveRecentIds(ids: List<String>) {
        prefs.edit { putString("recent", ids.joinToString(",")) }
    }
}

/** In-memory store for tests and previews. */
class MemoryPuzzleProgressStore(private var progress: PuzzleProgress = PuzzleProgress()) : PuzzleProgressStore {
    private var recent = emptyList<String>()
    override fun load() = progress
    override fun save(progress: PuzzleProgress) {
        this.progress = progress
    }
    override fun recentIds() = recent
    override fun saveRecentIds(ids: List<String>) {
        recent = ids
    }
}
