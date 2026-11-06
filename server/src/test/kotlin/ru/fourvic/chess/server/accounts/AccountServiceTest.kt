package ru.fourvic.chess.server.accounts

import ru.fourvic.chess.server.storage.InMemoryStorage
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.server.storage.StoredRating
import ru.fourvic.chess.shared.rating.Rating
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccountServiceTest {

    private var now = 1_000_000L
    private val storage = InMemoryStorage()
    // Few iterations keep the tests fast; production uses the default 600 000.
    private val accounts = AccountService(storage, { now }, SecureRandom(), PasswordHasher(iterations = 1_000))

    private fun <T> AccountService.Result<T>.ok(): T = (this as AccountService.Result.Ok<T>).value
    private fun AccountService.Result<*>.problem() = (this as AccountService.Result.Failed).problem

    @Test
    fun guestSavesTheAccountAndSignsInOnAnotherDevice() {
        val phone = accounts.authenticate(null, "ru")
        storage.saveRating(phone.account.id, Speed.BLITZ, StoredRating(Rating(1650.0, 70.0, 0.06), 12))

        val saved = accounts.register(phone.account.id, "  Magnus ", "correct horse").ok()
        assertEquals("Magnus", saved.name)
        assertTrue(saved.registered)
        assertEquals(AccountService.Problem.ALREADY_SAVED, accounts.register(phone.account.id, "Other", "correct horse").problem())

        val tablet = accounts.login("MAGNUS", "correct horse").ok()
        assertEquals(phone.account.id, tablet.account.id, "the same account, name case does not matter")
        assertNotEquals(phone.token, tablet.token, "each device gets its own token")
        assertEquals(phone.account.id, accounts.authenticate(phone.token).account.id, "the first device stays signed in")
        assertEquals(phone.account.id, accounts.authenticate(tablet.token).account.id)
        assertEquals(1650.0, storage.rating(tablet.account.id, Speed.BLITZ).rating.rating, "ratings come along")
    }

    @Test
    fun namesAndPasswordsAreChecked() {
        val a = accounts.authenticate(null).account
        val b = accounts.authenticate(null).account
        assertEquals(AccountService.Problem.INVALID_NAME, accounts.register(a.id, "x", "long enough").problem())
        assertEquals(AccountService.Problem.WEAK_PASSWORD, accounts.register(a.id, "Magnus", "short").problem())
        accounts.register(a.id, "Magnus", "long enough").ok()
        assertEquals(AccountService.Problem.NAME_TAKEN, accounts.register(b.id, "magnus", "long enough").problem())
        assertEquals(AccountService.Problem.NAME_TAKEN, accounts.rename(b.id, "MAGNUS").problem(), "guests cannot take a saved name")
        assertEquals("Magnus fan", accounts.rename(b.id, "Magnus fan").ok().name)
    }

    @Test
    fun wrongPasswordsLockTheNameForAWhile() {
        val a = accounts.authenticate(null).account
        accounts.register(a.id, "Magnus", "long enough").ok()
        repeat(AccountService.MAX_FAILURES) {
            assertEquals(AccountService.Problem.WRONG_PASSWORD, accounts.login("Magnus", "guess $it").problem())
        }
        assertEquals(AccountService.Problem.TOO_MANY_ATTEMPTS, accounts.login("Magnus", "long enough").problem(), "even the right one")
        assertEquals(AccountService.Problem.WRONG_PASSWORD, accounts.login("Nobody", "long enough").problem(), "unknown names look the same")

        now += AccountService.FAILURE_WINDOW_MS + 1
        accounts.login("Magnus", "long enough").ok()
    }

    @Test
    fun passwordCanBeChangedWithTheOldOne() {
        val a = accounts.authenticate(null).account
        assertEquals(AccountService.Problem.NOT_SAVED, accounts.changePassword(a.id, "x", "long enough").problem())
        accounts.register(a.id, "Magnus", "long enough").ok()
        assertEquals(AccountService.Problem.WRONG_PASSWORD, accounts.changePassword(a.id, "wrong one", "new password").problem())
        accounts.changePassword(a.id, "long enough", "new password").ok()
        assertEquals(AccountService.Problem.WRONG_PASSWORD, accounts.login("Magnus", "long enough").problem())
        accounts.login("Magnus", "new password").ok()
    }

    @Test
    fun passwordHashesAreSaltedAndVerified() {
        val hasher = PasswordHasher(iterations = 1_000)
        val one = hasher.hash("secret password")
        assertNotEquals(one, hasher.hash("secret password"), "random salt")
        assertTrue(one.startsWith("pbkdf2-sha256\$1000\$"))
        assertTrue(hasher.verify("secret password", one))
        assertTrue(!hasher.verify("Secret password", one))
        assertTrue(!hasher.verify("secret password", "garbage"))
    }
}
