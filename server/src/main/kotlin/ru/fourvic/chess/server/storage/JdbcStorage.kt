package ru.fourvic.chess.server.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.rating.Rating
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL storage (also runs on H2 in PostgreSQL mode for tests). Creates its tables on start.
 * Personal data of Russian users must stay in a database located in Russia (152-FZ).
 */
class JdbcStorage(private val dataSource: DataSource) : Storage, AutoCloseable {

    init {
        connection { c ->
            c.createStatement().use { st ->
                SCHEMA.forEach { st.execute(it) }
            }
        }
    }

    override fun createAccount(id: String, name: String, tokenHash: String, now: Long): Account {
        connection { c ->
            c.prepareStatement("INSERT INTO accounts (id, name, token_hash, created_at) VALUES (?, ?, ?, ?)").use {
                it.setString(1, id); it.setString(2, name); it.setString(3, tokenHash); it.setLong(4, now)
                it.executeUpdate()
            }
        }
        return Account(id, name, now)
    }

    override fun findAccountByTokenHash(tokenHash: String): Account? = connection { c ->
        c.prepareStatement("SELECT id, name, created_at FROM accounts WHERE token_hash = ?").use {
            it.setString(1, tokenHash)
            it.executeQuery().use { rs -> if (rs.next()) account(rs) else null }
        }
    }

    override fun findAccount(id: String): Account? = connection { c ->
        c.prepareStatement("SELECT id, name, created_at FROM accounts WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) account(rs) else null }
        }
    }

    override fun rename(id: String, name: String) {
        connection { c ->
            c.prepareStatement("UPDATE accounts SET name = ? WHERE id = ?").use {
                it.setString(1, name); it.setString(2, id); it.executeUpdate()
            }
        }
    }

    override fun deleteAccount(id: String) {
        connection { c ->
            c.autoCommit = false
            try {
                c.prepareStatement("DELETE FROM ratings WHERE account_id = ?").use { it.setString(1, id); it.executeUpdate() }
                c.prepareStatement("UPDATE games SET white_id = ? WHERE white_id = ?").use {
                    it.setString(1, InMemoryStorage.DELETED); it.setString(2, id); it.executeUpdate()
                }
                c.prepareStatement("UPDATE games SET black_id = ? WHERE black_id = ?").use {
                    it.setString(1, InMemoryStorage.DELETED); it.setString(2, id); it.executeUpdate()
                }
                c.prepareStatement("DELETE FROM accounts WHERE id = ?").use { it.setString(1, id); it.executeUpdate() }
                c.commit()
            } catch (e: Exception) {
                c.rollback()
                throw e
            } finally {
                c.autoCommit = true
            }
        }
    }

    override fun rating(accountId: String, speed: Speed): StoredRating = connection { c ->
        c.prepareStatement("SELECT rating, deviation, volatility, games FROM ratings WHERE account_id = ? AND speed = ?").use {
            it.setString(1, accountId); it.setString(2, speed.name)
            it.executeQuery().use { rs ->
                if (rs.next()) {
                    StoredRating(Rating(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)), rs.getInt(4))
                } else {
                    StoredRating()
                }
            }
        }
    }

    override fun saveRating(accountId: String, speed: Speed, rating: StoredRating) {
        connection { c ->
            val updated = c.prepareStatement(
                "UPDATE ratings SET rating = ?, deviation = ?, volatility = ?, games = ? WHERE account_id = ? AND speed = ?",
            ).use {
                it.setDouble(1, rating.rating.rating); it.setDouble(2, rating.rating.deviation)
                it.setDouble(3, rating.rating.volatility); it.setInt(4, rating.games)
                it.setString(5, accountId); it.setString(6, speed.name)
                it.executeUpdate()
            }
            if (updated == 0) {
                c.prepareStatement(
                    "INSERT INTO ratings (account_id, speed, rating, deviation, volatility, games) VALUES (?, ?, ?, ?, ?, ?)",
                ).use {
                    it.setString(1, accountId); it.setString(2, speed.name)
                    it.setDouble(3, rating.rating.rating); it.setDouble(4, rating.rating.deviation)
                    it.setDouble(5, rating.rating.volatility); it.setInt(6, rating.games)
                    it.executeUpdate()
                }
            }
        }
    }

    override fun saveGame(record: GameRecord) {
        connection { c ->
            c.prepareStatement(
                """INSERT INTO games (id, white_id, black_id, initial_seconds, increment_seconds, rated, moves, result,
                   reason, started_at, ended_at, tournament_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            ).use {
                it.setString(1, record.id); it.setString(2, record.whiteId); it.setString(3, record.blackId)
                it.setInt(4, record.clock.initialSeconds); it.setInt(5, record.clock.incrementSeconds)
                it.setBoolean(6, record.rated); it.setString(7, record.moves.joinToString(" "))
                it.setString(8, record.result.name); it.setString(9, record.reason.name)
                it.setLong(10, record.startedAt); it.setLong(11, record.endedAt); it.setString(12, record.tournamentId)
                it.executeUpdate()
            }
        }
    }

    override fun gamesOf(accountId: String, limit: Int): List<GameRecord> = connection { c ->
        c.prepareStatement(
            """SELECT id, white_id, black_id, initial_seconds, increment_seconds, rated, moves, result, reason,
               started_at, ended_at, tournament_id FROM games WHERE white_id = ? OR black_id = ?
               ORDER BY ended_at DESC LIMIT ?""",
        ).use {
            it.setString(1, accountId); it.setString(2, accountId); it.setInt(3, limit)
            it.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            GameRecord(
                                id = rs.getString(1), whiteId = rs.getString(2), blackId = rs.getString(3),
                                clock = Clock(rs.getInt(4), rs.getInt(5)), rated = rs.getBoolean(6),
                                moves = rs.getString(7).split(' ').filter { m -> m.isNotEmpty() },
                                result = GameResult.valueOf(rs.getString(8)), reason = EndReason.valueOf(rs.getString(9)),
                                startedAt = rs.getLong(10), endedAt = rs.getLong(11), tournamentId = rs.getString(12),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    private fun account(rs: ResultSet) = Account(rs.getString(1), rs.getString(2), rs.getLong(3))

    private inline fun <T> connection(block: (Connection) -> T): T = dataSource.connection.use(block)

    companion object {
        private val SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS accounts (
                id VARCHAR(32) PRIMARY KEY, name VARCHAR(40) NOT NULL,
                token_hash VARCHAR(64) NOT NULL UNIQUE, created_at BIGINT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS ratings (
                account_id VARCHAR(32) NOT NULL, speed VARCHAR(16) NOT NULL,
                rating DOUBLE PRECISION NOT NULL, deviation DOUBLE PRECISION NOT NULL,
                volatility DOUBLE PRECISION NOT NULL, games INT NOT NULL, PRIMARY KEY (account_id, speed))""",
            """CREATE TABLE IF NOT EXISTS games (
                id VARCHAR(32) PRIMARY KEY, white_id VARCHAR(32) NOT NULL, black_id VARCHAR(32) NOT NULL,
                initial_seconds INT NOT NULL, increment_seconds INT NOT NULL, rated BOOLEAN NOT NULL,
                moves TEXT NOT NULL, result VARCHAR(16) NOT NULL, reason VARCHAR(40) NOT NULL,
                started_at BIGINT NOT NULL, ended_at BIGINT NOT NULL, tournament_id VARCHAR(32))""",
            "CREATE INDEX IF NOT EXISTS games_white ON games (white_id)",
            "CREATE INDEX IF NOT EXISTS games_black ON games (black_id)",
        )

        /** Pooled storage for a JDBC URL such as jdbc:postgresql://host:5432/chess. */
        fun connect(url: String, user: String?, password: String?): JdbcStorage {
            val config = HikariConfig().apply {
                jdbcUrl = url
                user?.let { username = it }
                password?.let { this.password = it }
                maximumPoolSize = 10
                poolName = "chess"
            }
            return JdbcStorage(HikariDataSource(config))
        }
    }
}
