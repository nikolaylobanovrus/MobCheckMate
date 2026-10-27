package ru.fourvic.chess.server.tournament

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ru.fourvic.chess.server.accounts.AccountService
import ru.fourvic.chess.server.game.ClientConnection
import ru.fourvic.chess.server.game.GameServer
import ru.fourvic.chess.server.game.RoomSettings
import ru.fourvic.chess.server.storage.Account
import ru.fourvic.chess.server.storage.InMemoryStorage
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.protocol.TournamentStatus
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArenaManagerTest {

    private class Inbox : ClientConnection {
        val messages = mutableListOf<ServerMessage>()
        override fun send(message: ServerMessage) {
            messages += message
        }
        override fun close() = Unit
        inline fun <reified T : ServerMessage> all() = messages.filterIsInstance<T>()
        inline fun <reified T : ServerMessage> last() = all<T>().last()
    }

    /** 2026-10-27 10:58 UTC: two minutes before an hourly arena. */
    private val base = 1_793_098_680_000L
    private val blitz = ScheduledArena("hourly_blitz", "Hourly Blitz", Clock(180, 0), minutes = 30, periodMinutes = 60, offsetMinutes = 0)

    private inner class World(val scope: TestScope) {
        val storage = InMemoryStorage()
        val now = { base + scope.testScheduler.currentTime }
        val accounts = AccountService(storage, now)
        val games = GameServer(storage, accounts, scope.backgroundScope, now, RoomSettings(), SecureRandom())
        val arenas = ArenaManager(
            games, storage, scope.backgroundScope, now,
            ArenaSettings(schedule = listOf(blitz), tickMs = 1_000, pairingIntervalMs = 1_000, restMs = 3_000, repeatAfterMs = 5_000),
            Random(7),
        )

        fun player(): Pair<Account, Inbox> {
            val session = accounts.authenticate(null, "ru")
            val inbox = Inbox()
            games.connected(session.account, session.token, inbox)
            return session.account to inbox
        }

        fun command(account: Account, command: ClientMessage.TournamentCommand) = arenas.handle(account, command)
        fun game(account: Account, message: ClientMessage) = games.handle(account, message)
        fun advance(ms: Long) {
            scope.advanceTimeBy(ms)
            scope.runCurrent()
        }
    }

    private val hourlyId = "hourly_blitz-${(base + 120_000) / 60_000}"

    @Test
    fun scheduledArenasAreListedBeforeTheyStart() = runTest {
        val world = World(this)
        val (a, inbox) = world.player()
        world.command(a, ClientMessage.ListTournaments)
        val list = inbox.last<ServerMessage.Tournaments>().tournaments
        assertEquals(listOf(hourlyId, "hourly_blitz-${(base + 3_720_000) / 60_000}"), list.map { it.id })
        assertEquals(TournamentStatus.CREATED, list.first().status)
        assertEquals(120_000, list.first().msLeft)
        assertEquals("hourly_blitz", list.first().schedule)
    }

    @Test
    fun joinedPlayersArePairedAtTheStartAndScored() = runTest {
        val world = World(this)
        val (a, inboxA) = world.player()
        val (b, inboxB) = world.player()
        world.command(a, ClientMessage.JoinTournament(hourlyId))
        world.command(b, ClientMessage.JoinTournament(hourlyId))
        assertEquals(2, inboxB.last<ServerMessage.Tournament>().info.players)
        assertTrue(inboxA.all<ServerMessage.Game>().isEmpty(), "no pairing before the start")

        world.advance(122_000)
        val game = inboxA.last<ServerMessage.Game>().game
        assertEquals(hourlyId, game.tournamentId)
        assertEquals(game.gameId, inboxB.last<ServerMessage.Game>().game.gameId)

        val (white, black) = colours(game, a, b)
        world.game(white, ClientMessage.Move(game.gameId, "f2f3", 0))
        world.game(black, ClientMessage.Move(game.gameId, "e7e5", 1))
        world.game(white, ClientMessage.Move(game.gameId, "g2g4", 2))
        world.game(black, ClientMessage.Move(game.gameId, "d8h4", 3))
        world.advance(2_000)

        val table = inboxA.last<ServerMessage.Tournament>()
        assertEquals(black.id, table.standings.first().player.id)
        assertEquals(2, table.standings.first().score)
        assertEquals(listOf(2), table.standings.first().sheet)
        assertEquals(0, table.standings.last().score)
        assertEquals(1, table.me?.games)

        // Alone in the arena, the two meet again after a rest and a wait, with colours swapped.
        world.advance(10_000)
        val second = inboxA.last<ServerMessage.Game>().game
        assertTrue(second.gameId != game.gameId)
        assertEquals(black.id, second.white.id)
    }

    @Test
    fun theLastOpponentIsSkippedWhenSomeoneElseWaits() = runTest {
        val world = World(this)
        val players = List(4) { world.player() }
        players.forEach { (account, _) -> world.command(account, ClientMessage.JoinTournament(hourlyId)) }
        world.advance(122_000)
        val firstGames = players.map { (_, inbox) -> inbox.last<ServerMessage.Game>().game }
        val firstOpponent = players.mapIndexed { i, (account, _) -> account.id to firstGames[i].opponentOf(account.id) }.toMap()
        firstGames.distinctBy { it.gameId }.forEach { g -> world.game(accountOf(players, g.white.id), ClientMessage.Resign(g.gameId)) }

        world.advance(5_000)
        players.forEach { (account, inbox) ->
            val next = inbox.last<ServerMessage.Game>().game
            assertTrue(next.gameId !in firstGames.map { it.gameId })
            assertTrue(next.opponentOf(account.id) != firstOpponent[account.id], "new opponent for ${account.name}")
        }
    }

    @Test
    fun pausedPlayersWaitAndPlayerWhoDoesNotStartLoses() = runTest {
        val world = World(this)
        val (a, inboxA) = world.player()
        val (b, inboxB) = world.player()
        world.command(a, ClientMessage.JoinTournament(hourlyId))
        world.command(b, ClientMessage.JoinTournament(hourlyId))
        world.command(b, ClientMessage.PauseTournament(hourlyId))
        assertTrue(inboxB.last<ServerMessage.Tournament>().me!!.paused)
        world.advance(130_000)
        assertTrue(inboxA.all<ServerMessage.Game>().isEmpty(), "a paused player is not paired")

        world.command(b, ClientMessage.JoinTournament(hourlyId))
        world.advance(11_000) // the first pause costs ten seconds
        val game = inboxA.last<ServerMessage.Game>().game

        world.advance(31_000) // nobody moves
        val ended = inboxB.last<ServerMessage.Ended>()
        assertEquals(EndReason.NO_START, ended.reason)
        assertEquals(if (game.white.id == a.id) GameResult.BLACK_WON else GameResult.WHITE_WON, ended.result)
        val whiteId = game.white.id
        world.advance(2_000)
        val table = inboxA.last<ServerMessage.Tournament>()
        val whiteLine = table.standings.first { it.player.id == whiteId }
        val blackLine = table.standings.first { it.player.id != whiteId }
        assertTrue(whiteLine.paused, "the player who did not start is paused")
        assertEquals(1, whiteLine.games)
        assertEquals(0, blackLine.games, "the opponent gets nothing for it")
        assertEquals(0, world.storage.rating(whiteId, Speed.BLITZ).games, "ratings stay")
    }

    @Test
    fun gamesFinishedAfterTheEndDoNotCount() = runTest {
        val world = World(this)
        val (a, inboxA) = world.player()
        val (b, _) = world.player()
        world.advance(120_000L + 30 * 60_000L - 25_000L) // 25 seconds before the end
        world.command(a, ClientMessage.JoinTournament(hourlyId))
        world.command(b, ClientMessage.JoinTournament(hourlyId))
        world.advance(2_000)
        val game = inboxA.last<ServerMessage.Game>().game
        val (white, black) = colours(game, a, b)
        world.game(white, ClientMessage.Move(game.gameId, "e2e4", 0))
        world.game(black, ClientMessage.Move(game.gameId, "e7e5", 1))
        world.advance(30_000)
        world.game(black, ClientMessage.Resign(game.gameId))
        world.advance(3_000)
        val table = inboxA.last<ServerMessage.Tournament>()
        assertEquals(TournamentStatus.FINISHED, table.info.status)
        assertTrue(table.standings.all { it.score == 0 && it.games == 0 })
        assertEquals(1, world.storage.rating(white.id, Speed.BLITZ).games, "the game itself is still rated")
    }

    @Test
    fun privateArenaIsJoinedByCodeAndHiddenFromOthers() = runTest {
        val world = World(this)
        val (host, hostInbox) = world.player()
        val (friend, friendInbox) = world.player()
        val (stranger, strangerInbox) = world.player()
        world.command(host, ClientMessage.CreateTournament("Клуб пятницы", Clock(180, 2), minutes = 30, startsInMinutes = 1))
        val created = hostInbox.last<ServerMessage.Tournament>()
        val code = created.info.id
        assertTrue(created.info.private)
        assertEquals(1, created.info.players)
        assertEquals(6, code.length)

        world.command(stranger, ClientMessage.ListTournaments)
        assertFalse(strangerInbox.last<ServerMessage.Tournaments>().tournaments.any { it.id == code })

        world.command(friend, ClientMessage.JoinTournament(code.lowercase()))
        assertEquals(2, friendInbox.last<ServerMessage.Tournament>().info.players)
        world.command(friend, ClientMessage.ListTournaments)
        assertTrue(friendInbox.last<ServerMessage.Tournaments>().tournaments.any { it.id == code })

        world.advance(62_000)
        assertEquals(code, hostInbox.last<ServerMessage.Game>().game.tournamentId)
    }

    @Test
    fun invalidPrivateArenasAreRejected() = runTest {
        val world = World(this)
        val (host, inbox) = world.player()
        world.command(host, ClientMessage.CreateTournament("Клуб", Clock(600, 0), minutes = 20))
        assertEquals(ErrorCode.BAD_REQUEST, inbox.last<ServerMessage.Error>().code)
        world.command(host, ClientMessage.CreateTournament("<script>", Clock(60, 0), minutes = 20))
        assertEquals(2, inbox.all<ServerMessage.Error>().size)
        world.command(host, ClientMessage.JoinTournament("NOPE42"))
        assertEquals(ErrorCode.NO_SUCH_TOURNAMENT, inbox.last<ServerMessage.Error>().code)
    }

    @Test
    fun berserkWinScoresABonusPoint() = runTest {
        val world = World(this)
        val (a, inboxA) = world.player()
        val (b, _) = world.player()
        world.command(a, ClientMessage.JoinTournament(hourlyId))
        world.command(b, ClientMessage.JoinTournament(hourlyId))
        world.advance(122_000)
        val game = inboxA.last<ServerMessage.Game>().game
        val (white, black) = colours(game, a, b)
        world.game(white, ClientMessage.Berserk(game.gameId))
        assertEquals(90_000, inboxA.last<ServerMessage.Berserked>().whiteMs)
        // Seven full moves, then Black resigns.
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "c2c3", "g8f6", "d2d4", "e5d4", "c3d4", "c5b4", "b1c3", "f6e4")
        moves.forEachIndexed { ply, uci -> world.game(if (ply % 2 == 0) white else black, ClientMessage.Move(game.gameId, uci, ply)) }
        world.game(black, ClientMessage.Resign(game.gameId))
        world.advance(2_000)
        val line = inboxA.last<ServerMessage.Tournament>().standings.first()
        assertEquals(white.id, line.player.id)
        assertEquals(3, line.score)
    }

    @Test
    fun finishedArenaNamesTheWinner() = runTest {
        val world = World(this)
        val (a, inboxA) = world.player()
        val (b, _) = world.player()
        world.command(a, ClientMessage.JoinTournament(hourlyId))
        world.command(b, ClientMessage.JoinTournament(hourlyId))
        world.advance(122_000)
        val game = inboxA.last<ServerMessage.Game>().game
        val (white, black) = colours(game, a, b)
        world.game(white, ClientMessage.Move(game.gameId, "e2e4", 0))
        world.game(black, ClientMessage.Resign(game.gameId))
        world.command(a, ClientMessage.PauseTournament(hourlyId))
        world.advance(31 * 60_000L)
        world.command(a, ClientMessage.ListTournaments)
        val finished = inboxA.last<ServerMessage.Tournaments>().tournaments.first { it.id == hourlyId }
        assertEquals(TournamentStatus.FINISHED, finished.status)
        assertEquals(white.name, finished.winner)
    }

    private fun colours(game: GameSnapshot, a: Account, b: Account): Pair<Account, Account> =
        if (game.white.id == a.id) a to b else b to a

    private fun GameSnapshot.opponentOf(id: String) = if (white.id == id) black.id else white.id

    private fun accountOf(players: List<Pair<Account, Inbox>>, id: String) = players.first { it.first.id == id }.first

    @Test
    fun pairingAvoidsRematchesAndBalancesColours() {
        fun entry(id: String, score: Int, last: String? = null, balance: Int = 0, waited: Long = 0) =
            PoolEntry(id, score, 1500, last, balance, null, waited)
        val pairs = ArenaPairing.pair(
            listOf(entry("a", 4, last = "b"), entry("b", 4, last = "a", balance = -1), entry("c", 2, balance = 1), entry("d", 0)),
            repeatAfterMs = 10_000,
            random = Random(1),
        )
        assertEquals(setOf(setOf("a", "c"), setOf("b", "d")), pairs.map { setOf(it.first, it.second) }.toSet())
        assertEquals("a" to "c", pairs.first { "a" in it.toList() }, "c had White more often")
        assertEquals("b" to "d", pairs.first { "b" in it.toList() }, "b had Black more often")

        val alone = listOf(entry("a", 2, last = "b", waited = 3_000), entry("b", 0, last = "a", waited = 3_000))
        assertTrue(ArenaPairing.pair(alone, 10_000, Random(1)).isEmpty())
        assertNotNull(ArenaPairing.pair(alone.map { it.copy(waitedMs = 12_000) }, 10_000, Random(1)).singleOrNull())
    }
}
