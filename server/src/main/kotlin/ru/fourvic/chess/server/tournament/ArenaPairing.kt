package ru.fourvic.chess.server.tournament

import ru.fourvic.chess.shared.chess.PlayerColor
import kotlin.random.Random

/** A player waiting for the next arena game. [colorBalance] is games with White minus games with Black. */
internal data class PoolEntry(
    val id: String,
    val score: Int,
    val rating: Int,
    val lastOpponent: String?,
    val colorBalance: Int,
    val lastColor: PlayerColor?,
    val waitedMs: Long,
)

/**
 * Arena pairing (docs/TOURNAMENTS.md, section 3.3, first version): neighbours in the table meet, ordered by score
 * and then rating, so at the start players are paired by rating. The last opponent is skipped while someone else
 * is waiting; two players left alone meet again once both have waited [repeatAfterMs].
 */
internal object ArenaPairing {

    /** Returns pairs as (white, black). */
    fun pair(pool: List<PoolEntry>, repeatAfterMs: Long, random: Random): List<Pair<String, String>> {
        val free = pool.sortedWith(compareByDescending<PoolEntry> { it.score }.thenByDescending { it.rating }).toMutableList()
        val pairs = mutableListOf<Pair<String, String>>()
        while (free.size >= 2) {
            val a = free.removeAt(0)
            var index = free.indexOfFirst { b -> !metLastTime(a, b) }
            if (index < 0) index = free.indexOfFirst { b -> a.waitedMs >= repeatAfterMs && b.waitedMs >= repeatAfterMs }
            if (index < 0) continue
            val b = free.removeAt(index)
            pairs += if (aTakesWhite(a, b, random)) a.id to b.id else b.id to a.id
        }
        return pairs
    }

    private fun metLastTime(a: PoolEntry, b: PoolEntry) = a.lastOpponent == b.id || b.lastOpponent == a.id

    /** The player who had White less often takes it; then the one who had Black last; then a coin. */
    private fun aTakesWhite(a: PoolEntry, b: PoolEntry, random: Random): Boolean = when {
        a.colorBalance != b.colorBalance -> a.colorBalance < b.colorBalance
        a.lastColor != null && b.lastColor != null && a.lastColor != b.lastColor -> a.lastColor == PlayerColor.BLACK
        else -> random.nextBoolean()
    }
}
