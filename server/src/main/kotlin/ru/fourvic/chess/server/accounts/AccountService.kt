package ru.fourvic.chess.server.accounts

import ru.fourvic.chess.server.storage.Account
import ru.fourvic.chess.server.storage.Storage
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Guest accounts identified by a secret token kept on the device. The server stores only the token's
 * SHA-256 hash, so a database leak does not let anyone sign in as a player.
 */
class AccountService(
    private val storage: Storage,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    data class Session(val account: Account, val token: String, val created: Boolean)

    /** Signs in with [token], or creates a new guest when the token is missing or unknown. */
    fun authenticate(token: String?, locale: String? = null): Session {
        if (token != null && token.length in 20..100) {
            storage.findAccountByTokenHash(hash(token))?.let { return Session(it, token, created = false) }
        }
        val newToken = randomToken()
        val id = randomId()
        val number = 10_000 + random.nextInt(90_000)
        val name = if (locale?.startsWith("ru") == true) "Игрок$number" else "Player$number"
        return Session(storage.createAccount(id, name, hash(newToken), now()), newToken, created = true)
    }

    /** 3 to 20 letters, digits, spaces, '_' or '-'. Returns the updated account, or null for an invalid name. */
    fun rename(accountId: String, name: String): Account? {
        val trimmed = name.trim()
        if (!NAME.matches(trimmed)) return null
        storage.rename(accountId, trimmed)
        return storage.findAccount(accountId)
    }

    fun delete(accountId: String) = storage.deleteAccount(accountId)

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

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
