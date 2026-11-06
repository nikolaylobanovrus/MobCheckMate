package ru.fourvic.chess.server.storage

import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.rating.Rating
import java.util.concurrent.ConcurrentHashMap

/**
 * A player. Everyone starts as a guest known by a secret token on the device; a player who saves the account
 * picks a unique name and a password ([passwordHash]) to sign in on other devices.
 */
data class Account(val id: String, val name: String, val createdAt: Long, val passwordHash: String? = null) {
    val registered: Boolean get() = passwordHash != null
}

/** Names of saved accounts are unique regardless of letter case. */
fun loginOf(name: String): String = name.trim().lowercase()

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
    /** Id of the computer side, if the opponent was a bot; kept apart in statistics and fair-play checks. */
    val botId: String? = null,
)

/** Persistence for accounts, ratings and finished games. Implementations must be thread-safe. */
interface Storage {
    /** A guest account with its first session. */
    fun createAccount(id: String, name: String, tokenHash: String, now: Long): Account
    fun findAccountByTokenHash(tokenHash: String): Account?
    fun findAccount(id: String): Account?

    /** The saved account with this name, ignoring case. */
    fun findAccountByLogin(login: String): Account?

    /** Another device signed in: one more token for the same account. */
    fun addSession(accountId: String, tokenHash: String, now: Long)

    /** Renames the account; a saved account keeps its name unique. False when another saved account has it. */
    fun rename(id: String, name: String): Boolean

    /** Saves a guest account under [name] with a password. False when another saved account has the name. */
    fun register(id: String, name: String, passwordHash: String): Boolean

    fun setPassword(id: String, passwordHash: String)

    /** Deletes the account, its sessions and ratings; finished games keep the moves but lose the link to the player. */
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
    private val logins = ConcurrentHashMap<String, String>()
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
    override fun findAccountByLogin(login: String) = logins[login]?.let { accounts[it] }

    override fun addSession(accountId: String, tokenHash: String, now: Long) {
        tokens[tokenHash] = accountId
    }

    @Synchronized
    override fun rename(id: String, name: String): Boolean {
        val account = accounts[id] ?: return false
        if (account.registered) {
            val owner = logins.putIfAbsent(loginOf(name), id)
            if (owner != null && owner != id) return false
            if (loginOf(account.name) != loginOf(name)) logins.remove(loginOf(account.name), id)
        }
        accounts[id] = account.copy(name = name)
        return true
    }

    @Synchronized
    override fun register(id: String, name: String, passwordHash: String): Boolean {
        val account = accounts[id] ?: return false
        if (logins.putIfAbsent(loginOf(name), id) != null) return false
        accounts[id] = account.copy(name = name, passwordHash = passwordHash)
        return true
    }

    override fun setPassword(id: String, passwordHash: String) {
        accounts.computeIfPresent(id) { _, a -> a.copy(passwordHash = passwordHash) }
    }

    @Synchronized
    override fun deleteAccount(id: String) {
        accounts.remove(id)
        tokens.entries.removeIf { it.value == id }
        logins.entries.removeIf { it.value == id }
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
