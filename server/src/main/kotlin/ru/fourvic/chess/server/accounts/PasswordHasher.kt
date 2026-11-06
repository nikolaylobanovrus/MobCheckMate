package ru.fourvic.chess.server.accounts

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Passwords are stored only as PBKDF2-HMAC-SHA256 hashes with a random salt, in the form
 * `pbkdf2-sha256$<iterations>$<salt>$<hash>`. The iteration count follows the OWASP recommendation; it is kept in
 * every hash, so it can be raised later without breaking old ones.
 */
class PasswordHasher(private val iterations: Int = 600_000, private val random: SecureRandom = SecureRandom()) {

    fun hash(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return listOf(PREFIX, iterations.toString(), b64(salt), b64(derive(password, salt, iterations))).joinToString("$")
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val rounds = parts[1].toIntOrNull() ?: return false
        val expected = Base64.getDecoder().decode(parts[3])
        return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[2]), rounds), expected)
    }

    private fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val PREFIX = "pbkdf2-sha256"
    }
}
