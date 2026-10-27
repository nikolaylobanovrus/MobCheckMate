package ru.fourvic.chess.server.storage

import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.rating.Rating
import java.util.concurrent.ConcurrentHashMap

data class Account(val id: String, val name: String, val createdAt: Long)

data class StoredRating(val rating: Rating = Rating(), val games: Int = 0) {
    val provisional: Boolean get() = rating.isProvisional()
}

data class GameRecord(
    val id: String,
    val whiteId: String,
    val blackId: String,
    val clock: Clock,
    val rated: Boolean,
    val moves: List<String>,
    val result: GameResult,
    val reason: EndReason,
    val startedAt: Long,
    val endedAt: Long,
    val tournamentId: String? = null,
)

/** Persistence for accounts, ratings and finished games. Implementations must be thread-safe. */
interface Storage {
    fun createAccount(id: String, name: String, tokenHash: String, now: Long): Account
    fun findAccountByTokenHash(tokenHash: String): Account?
    fun findAccount(id: String): Account?
    fun rename(id: String, name: String)

    /** Deletes the account and its ratings; finished games keep the moves but lose the link to the player. */
    fun deleteAccount(id: String)

    fun rating(accountId: String, speed: Speed): StoredRating
    fun saveRating(accountId: String, speed: Speed, rating: StoredRating)
    fun saveGame(record: GameRecord)
    fun gamesOf(accountId: String, limit: Int = 50): List<GameRecord>
}

/** For development and tests: everything is lost on restart. */
class InMemoryStorage : Storage {
    private val accounts = ConcurrentHashMap<String, Account>()
    private val tokens = ConcurrentHashMap<String, String>()
    private val ratings = ConcurrentHashMap<Pair<String, Speed>, StoredRating>()
    private val games = java.util.concurrent.CopyOnWriteArrayList<GameRecord>()

    override fun createAccount(id: String, name: String, tokenHash: String, now: Long): Account {
        val account = Account(id, name, now)
        accounts[id] = account
        tokens[tokenHash] = id
        return account
    }

    override fun findAccountByTokenHash(tokenHash: String) = tokens[tokenHash]?.let { accounts[it] }
    override fun findAccount(id: String) = accounts[id]

    override fun rename(id: String, name: String) {
        accounts.computeIfPresent(id) { _, a -> a.copy(name = name) }
    }

    override fun deleteAccount(id: String) {
        accounts.remove(id)
        tokens.entries.removeIf { it.value == id }
        ratings.keys.removeIf { it.first == id }
        games.replaceAll { g ->
            g.copy(whiteId = if (g.whiteId == id) DELETED else g.whiteId, blackId = if (g.blackId == id) DELETED else g.blackId)
        }
    }

    override fun rating(accountId: String, speed: Speed) = ratings[accountId to speed] ?: StoredRating()
    override fun saveRating(accountId: String, speed: Speed, rating: StoredRating) {
        ratings[accountId to speed] = rating
    }

    override fun saveGame(record: GameRecord) {
        games += record
    }

    override fun gamesOf(accountId: String, limit: Int) =
        games.filter { it.whiteId == accountId || it.blackId == accountId }.takeLast(limit).reversed()

    companion object {
        /** Placeholder id kept in game records after a player deleted the account. */
        const val DELETED = "deleted"
    }
}
