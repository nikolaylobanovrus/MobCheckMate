package ru.fourvic.chess.server.bots

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
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Speed
import java.security.SecureRandom
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BotOpponentTest {

    /** The rule real players' names follow (AccountService). */
    private val nameRule = Regex("^[\\p{L}\\p{N}_\\- ]{3,20}$")

    @Test
    fun personasLookLikeOrdinaryPlayers() {
        val random = Random(42)
        val personas = List(1_000) { BotPersona.create("RU", 1500, userProvisional = false, random) }
        personas.forEach { p ->
            assertTrue(nameRule.matches(p.name), "name '${p.name}'")
            assertTrue(p.country in NameGenerator.countries, p.country)
            assertTrue(p.rating in 1420..1580, "rating ${p.rating}")
            assertEquals(12, p.id.length)
        }
        assertEquals(personas.size, personas.map { it.id }.toSet().size, "ids are unique")
        assertTrue(personas.map { it.name }.toSet().size > 600, "names vary")
        val fromRussianSpeakingCountries = personas.count { it.country in NameGenerator.cisCountries }
        assertTrue(fromRussianSpeakingCountries > 700, "a Russian user meets mostly players from the region")

        val abroad = List(300) { BotPersona.create("DE", 1500, userProvisional = false, random) }
        assertTrue(abroad.count { it.country == "DE" } > 120)
        abroad.forEach { assertTrue(nameRule.matches(it.name), "name '${it.name}'") }
    }

    @Test
    fun strengthFollowsRating() {
        assertEquals(BotLevel.LEVEL_1, BotPersona.levelFor(700))
        assertEquals(BotLevel.LEVEL_2, BotPersona.levelFor(1250))
        assertEquals(BotLevel.LEVEL_3, BotPersona.levelFor(1500))
        assertEquals(BotLevel.LEVEL_4, BotPersona.levelFor(1700))
        assertEquals(BotLevel.LEVEL_5, BotPersona.levelFor(2100))
        val newcomer = List(200) { BotPersona.create(null, 1500, userProvisional = true, Random(it)) }
        assertTrue(newcomer.all { it.rating in 1220..1580 }, "newcomers meet slightly weaker opponents")
    }

    @Test
    fun thinkingTimeFitsTheClock() {
        val random = Random(1)
        repeat(500) {
            assertTrue(BotTiming.thinkMs(0, 60_000, 0, 20, random) in 800..3_500, "first move")
            val bullet = BotTiming.thinkMs(30, 40_000, 0, 30, random)
            assertTrue(bullet in 300..40_000 / 6, "bullet $bullet")
            val scramble = BotTiming.thinkMs(80, 3_000, 0, 30, random)
            assertTrue(scramble in 300..500, "scramble $scramble")
            assertTrue(BotTiming.thinkMs(40, 100_000, 2_000, 1, random) < 900, "only one legal move")
        }
    }

    private class Inbox : ClientConnection {
        val messages = mutableListOf<ServerMessage>()
        override fun send(message: ServerMessage) {
            messages += message
        }
        override fun close() = Unit
        inline fun <reified T : ServerMessage> all() = messages.filterIsInstance<T>()
    }

    private inner class World(val scope: TestScope, bots: BotSettings = BotSettings(waitMs = 10_000, waitJitterMs = 0)) {
        val storage = InMemoryStorage()
        val now = { scope.testScheduler.currentTime }
        val accounts = AccountService(storage, now)
        val games = GameServer(
            storage, accounts, scope.backgroundScope, now, RoomSettings(), SecureRandom(),
            bots = bots, botRandom = Random(3), botEngineContext = EmptyCoroutineContext,
        )

        fun player(country: String? = "RU"): Pair<Account, Inbox> {
            val session = accounts.authenticate(null, "ru-RU")
            val inbox = Inbox()
            games.connected(session.account, session.token, inbox, country)
            return session.account to inbox
        }

        fun advance(ms: Long) {
            scope.advanceTimeBy(ms)
            scope.runCurrent()
        }
    }

    @Test
    fun lonelySeekGetsAComputerOpponentThatPlaysLikeAPerson() = runTest {
        val world = World(this)
        val (me, inbox) = world.player()
        world.games.handle(me, ClientMessage.Seek(Clock(180, 0), rated = true))
        world.advance(9_000)
        assertTrue(inbox.all<ServerMessage.Game>().isEmpty(), "a person is waited for first")

        world.advance(2_000)
        val game = inbox.all<ServerMessage.Game>().single().game
        val myColor = if (game.white.id == me.id) PlayerColor.WHITE else PlayerColor.BLACK
        val bot = if (myColor == PlayerColor.WHITE) game.black else game.white
        assertNotEquals(me.id, bot.id)
        assertTrue(nameRule.matches(bot.name))
        assertNotNull(bot.country)
        assertEquals("RU", game.white.takeIf { it.id == me.id }?.country ?: game.black.country)
        assertTrue(game.rated)

        // Play a few moves; the bot answers after a human-like pause.
        val board = ChessGame()
        var replies = 0
        while (replies < 3) {
            if (board.sideToMove == myColor) {
                val move = board.legalMoves().first()
                world.games.handle(me, ClientMessage.Move(game.gameId, move.uci, board.moves.size))
                board.play(move)
            }
            val before = inbox.all<ServerMessage.Moved>().size
            world.advance(15_000)
            val moved = inbox.all<ServerMessage.Moved>().drop(before)
            moved.filter { it.ply == board.moves.size + 1 }.forEach { board.play(ChessMove.fromUci(it.uci)!!) }
            if (board.sideToMove == myColor) replies++ else error("the bot did not move")
        }

        // An early draw offer is declined.
        world.games.handle(me, ClientMessage.OfferDraw(game.gameId))
        world.advance(5_000)
        assertEquals(1, inbox.all<ServerMessage.DrawDeclined>().size)

        world.games.handle(me, ClientMessage.Resign(game.gameId))
        world.advance(1_000)
        val ended = inbox.all<ServerMessage.Ended>().single()
        assertEquals(EndReason.RESIGNATION, ended.reason)
        val myDiff = if (myColor == PlayerColor.WHITE) ended.whiteRatingDiff else ended.blackRatingDiff
        assertTrue(myDiff!! < 0)
        assertEquals(1, world.storage.rating(me.id, Speed.BLITZ).games)
        assertEquals(0, world.storage.rating(bot.id, Speed.BLITZ).games, "the bot has no stored rating")
        assertEquals(bot.id, world.storage.gamesOf(me.id).single().botId)

        // A rematch with the same opponent and colours swapped.
        world.games.handle(me, ClientMessage.Rematch(game.gameId))
        world.games.room(game.gameId)!!.rematch(bot.id)
        world.advance(1_000)
        val rematch: GameSnapshot = inbox.all<ServerMessage.Game>().last().game
        assertNotEquals(game.gameId, rematch.gameId)
        assertEquals(bot.id, if (myColor == PlayerColor.WHITE) rematch.white.id else rematch.black.id)
    }

    @Test
    fun peopleWhoSeekTogetherStillPlayEachOther() = runTest {
        val world = World(this)
        val (a, inboxA) = world.player()
        val (b, _) = world.player("KZ")
        world.games.handle(a, ClientMessage.Seek(Clock(60, 0)))
        world.games.handle(b, ClientMessage.Seek(Clock(60, 0)))
        val game = inboxA.all<ServerMessage.Game>().single().game
        assertEquals(setOf(a.id, b.id), setOf(game.white.id, game.black.id))
        assertEquals(setOf("RU", "KZ"), setOf(game.white.country, game.black.country))
    }

    @Test
    fun botsCanBeSwitchedOff() = runTest {
        val world = World(this, BotSettings(enabled = false))
        val (me, inbox) = world.player()
        world.games.handle(me, ClientMessage.Seek(Clock(180, 0)))
        world.advance(60_000)
        assertNull(inbox.all<ServerMessage.Game>().firstOrNull())
    }
}
