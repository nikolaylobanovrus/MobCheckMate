package ru.fourvic.chess.server.accounts

import ru.fourvic.chess.server.storage.Account
import ru.fourvic.chess.server.storage.Storage
import ru.fourvic.chess.server.storage.loginOf
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Accounts without personal data. A player starts as a guest identified by a secret token kept on the device; the
 * server stores only the token's SHA-256 hash, so a database leak does not let anyone sign in as a player. To play
 * from another device the player saves the account with a unique name and a password; there is no email, so a
 * forgotten password cannot be reset.
 */
class AccountService(
    private val storage: Storage,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val passwords: PasswordHasher = PasswordHasher(random = random),
) {
    data class Session(val account: Account, val token: String, val created: Boolean)

    enum class Problem { INVALID_NAME, NAME_TAKEN, WEAK_PASSWORD, WRONG_PASSWORD, TOO_MANY_ATTEMPTS, ALREADY_SAVED, NOT_SAVED }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Failed(val problem: Problem) : Result<Nothing>
    }

    /** Failed sign-ins per name: [MAX_FAILURES] within [FAILURE_WINDOW_MS] lock the name for the rest of the window. */
    private val failures = HashMap<String, Pair<Int, Long>>()

    /** Checked when the name does not exist, so such attempts take as long as real ones. */
    private val dummyHash by lazy { passwords.hash("not a password") }

    /** Signs in with [token], or creates a new guest when the token is missing or unknown. */
    fun authenticate(token: String?, locale: String? = null): Session {
        if (token != null && token.length in 20..100) {
            storage.findAccountByTokenHash(hash(token))?.let { return Session(it, token, created = false) }
        }
        val newToken = randomToken()
        val id = randomId()
        val number = 10_000 + random.nextInt(90_000)
        val name = when {
            locale?.startsWith("ru") == true -> "Игрок$number"
            locale?.startsWith("zh") == true -> "棋手$number"
            else -> "Player$number"
        }
        return Session(storage.createAccount(id, name, hash(newToken), now()), newToken, created = true)
    }

    /** 3 to 20 letters, digits, spaces, '_' or '-'; a saved account's name stays unique. */
    fun rename(accountId: String, name: String): Result<Account> {
        val trimmed = name.trim()
        if (!NAME.matches(trimmed)) return Result.Failed(Problem.INVALID_NAME)
        val owner = storage.findAccountByLogin(loginOf(trimmed))
        if (owner != null && owner.id != accountId) return Result.Failed(Problem.NAME_TAKEN)
        if (!storage.rename(accountId, trimmed)) return Result.Failed(Problem.NAME_TAKEN)
        return storage.findAccount(accountId)?.let { Result.Ok(it) } ?: Result.Failed(Problem.NOT_SAVED)
    }

    /** Saves a guest account under a unique [name] with a password, keeping its ratings and games. */
    fun register(accountId: String, name: String, password: String): Result<Account> {
        val account = storage.findAccount(accountId) ?: return Result.Failed(Problem.NOT_SAVED)
        if (account.registered) return Result.Failed(Problem.ALREADY_SAVED)
        val trimmed = name.trim()
        if (!NAME.matches(trimmed)) return Result.Failed(Problem.INVALID_NAME)
        if (!strongEnough(password)) return Result.Failed(Problem.WEAK_PASSWORD)
        if (!storage.register(accountId, trimmed, passwords.hash(password))) return Result.Failed(Problem.NAME_TAKEN)
        return storage.findAccount(accountId)?.let { Result.Ok(it) } ?: Result.Failed(Problem.NOT_SAVED)
    }

    /** Signs in to a saved account from another device; returns a new token for that device. */
    fun login(name: String, password: String): Result<Session> {
        val login = loginOf(name)
        if (locked(login)) return Result.Failed(Problem.TOO_MANY_ATTEMPTS)
        val account = storage.findAccountByLogin(login)
        val stored = account?.passwordHash
        // An unknown name costs the same as a wrong password, so timing does not reveal which names exist.
        if (account == null || stored == null || !passwords.verify(password, stored)) {
            if (stored == null) passwords.verify(password, dummyHash)
            failed(login)
            return Result.Failed(Problem.WRONG_PASSWORD)
        }
        synchronized(failures) { failures.remove(login) }
        val token = randomToken()
        storage.addSession(account.id, hash(token), now())
        return Result.Ok(Session(account, token, created = false))
    }

    fun changePassword(accountId: String, oldPassword: String, newPassword: String): Result<Unit> {
        val account = storage.findAccount(accountId) ?: return Result.Failed(Problem.NOT_SAVED)
        val stored = account.passwordHash ?: return Result.Failed(Problem.NOT_SAVED)
        val login = loginOf(account.name)
        if (locked(login)) return Result.Failed(Problem.TOO_MANY_ATTEMPTS)
        if (!passwords.verify(oldPassword, stored)) {
            failed(login)
            return Result.Failed(Problem.WRONG_PASSWORD)
        }
        if (!strongEnough(newPassword)) return Result.Failed(Problem.WEAK_PASSWORD)
        storage.setPassword(accountId, passwords.hash(newPassword))
        return Result.Ok(Unit)
    }

    fun delete(accountId: String) = storage.deleteAccount(accountId)

    private fun strongEnough(password: String) = password.length in MIN_PASSWORD..MAX_PASSWORD

    private fun locked(login: String): Boolean = synchronized(failures) {
        val (count, since) = failures[login] ?: return false
        if (now() - since > FAILURE_WINDOW_MS) {
            failures.remove(login)
            false
        } else {
            count >= MAX_FAILURES
        }
    }

    private fun failed(login: String) = synchronized(failures) {
        val t = now()
        val (count, since) = failures[login]?.takeIf { t - it.second <= FAILURE_WINDOW_MS } ?: (0 to t)
        failures[login] = count + 1 to since
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun randomId(): String {
        val bytes = ByteArray(9).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val NAME = Regex("^[\\p{L}\\p{N}_\\- ]{3,20}$")
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 128
        const val MAX_FAILURES = 5
        const val FAILURE_WINDOW_MS = 15 * 60_000L

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
