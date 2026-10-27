package ru.fourvic.chess.server.storage

import org.h2.jdbcx.JdbcDataSource
import ru.fourvic.chess.server.accounts.AccountService
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.rating.Rating
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The same contract for both storages; JDBC runs on H2 in PostgreSQL mode. */
class StorageTest {

    private fun storages(): List<Storage> = listOf(
        InMemoryStorage(),
        JdbcStorage(
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            },
        ),
    )

    @Test
    fun accountsAreFoundByTokenAndCanBeRenamedAndDeleted() = storages().forEach { storage ->
        val accounts = AccountService(storage)
        val session = accounts.authenticate(null, "ru")
        assertTrue(session.created)
        assertTrue(session.account.name.startsWith("Игрок"))

        val again = accounts.authenticate(session.token)
        assertEquals(session.account.id, again.account.id)
        assertTrue(!again.created)

        assertNotEquals(session.account.id, accounts.authenticate("unknown-token-1234567890").account.id)
        assertNull(accounts.rename(session.account.id, "x"))
        assertEquals("Магнус", accounts.rename(session.account.id, "Магнус")?.name)

        storage.saveRating(session.account.id, Speed.BLITZ, StoredRating(Rating(1600.0, 80.0, 0.06), 3))
        storage.saveGame(
            GameRecord("g1", session.account.id, "other", Clock(180, 2), true, listOf("e2e4"), GameResult.WHITE_WON,
                EndReason.RESIGNATION, 1, 2),
        )
        accounts.delete(session.account.id)
        assertNull(storage.findAccount(session.account.id))
        assertEquals(1500.0, storage.rating(session.account.id, Speed.BLITZ).rating.rating, "ratings are deleted")
        assertTrue(storage.gamesOf(session.account.id).isEmpty(), "games no longer point to the player")
        assertEquals(InMemoryStorage.DELETED, storage.gamesOf("other").single().whiteId)
    }

    @Test
    fun ratingsAreSavedPerSpeedAndUpdatedInPlace() = storages().forEach { storage ->
        val id = AccountService(storage).authenticate(null).account.id
        assertEquals(1500.0, storage.rating(id, Speed.RAPID).rating.rating)
        storage.saveRating(id, Speed.RAPID, StoredRating(Rating(1550.0, 120.0, 0.06), 1))
        storage.saveRating(id, Speed.RAPID, StoredRating(Rating(1560.0, 110.0, 0.06), 2))
        val stored = storage.rating(id, Speed.RAPID)
        assertEquals(1560.0, stored.rating.rating)
        assertEquals(2, stored.games)
        assertEquals(1500.0, storage.rating(id, Speed.BULLET).rating.rating)
    }

    @Test
    fun gamesAreStoredWithTheirMoves() = storages().forEach { storage ->
        val record = GameRecord("g2", "a", "b", Clock(600, 0), false, listOf("d2d4", "d7d5"), GameResult.DRAW,
            EndReason.AGREEMENT, 10, 20, tournamentId = "t1")
        storage.saveGame(record)
        val loaded = assertNotNull(storage.gamesOf("b").singleOrNull())
        assertEquals(record, loaded)
    }
}
