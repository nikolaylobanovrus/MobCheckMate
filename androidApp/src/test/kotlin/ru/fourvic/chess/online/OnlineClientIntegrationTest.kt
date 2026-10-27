package ru.fourvic.chess.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class MemoryTokenStore : TokenStore {
    var token: String? = null
    override fun load() = token
    override fun save(token: String?) {
        this.token = token
    }
}

/**
 * The real WebSocket client against a running server. Skipped unless a server address is given:
 * `./gradlew :server:run` and then `./gradlew :androidApp:testPlayDebugUnitTest -PonlineServer=ws://localhost:8080/ws
 * --tests '*OnlineClientIntegrationTest*'`.
 */
class OnlineClientIntegrationTest {

    private class Player(scope: CoroutineScope, url: String) {
        val tokens = MemoryTokenStore()
        val client = WebSocketOnlineClient(scope, tokens) { url }
        val inbox = Channel<ServerMessage>(Channel.UNLIMITED)

        init {
            scope.launch { client.messages.collect { inbox.send(it) } }
        }

        suspend inline fun <reified T : ServerMessage> next(): T {
            while (true) {
                val message = inbox.receive()
                if (message is T) return message
            }
        }

        suspend fun connected() = client.state.first { it == ConnectionState.CONNECTED }
    }

    @Test
    fun twoPlayersFindEachOtherPlayAndReconnect(): Unit = runBlocking {
        val url = System.getProperty("onlineServer")
        assumeTrue("Set -PonlineServer to run against a server", !url.isNullOrBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            withTimeout(20_000) {
                val a = Player(scope, url)
                val b = Player(scope, url)
                a.client.start()
                b.client.start()
                a.connected()
                b.connected()
                val idA = assertNotNull(a.client.welcome.value).player.id
                assertNotNull(a.tokens.token)

                val clock = Clock(60, 0)
                assertTrue(a.client.send(ClientMessage.Seek(clock, rated = true)))
                assertTrue(b.client.send(ClientMessage.Seek(clock, rated = true)))
                val game = a.next<ServerMessage.Game>().game
                assertEquals(game.gameId, b.next<ServerMessage.Game>().game.gameId)
                val (white, black) = if (game.white.id == idA) a to b else b to a

                white.client.send(ClientMessage.Move(game.gameId, "e2e4", 0))
                assertEquals("e2e4", black.next<ServerMessage.Moved>().uci)
                assertEquals(1, white.next<ServerMessage.Moved>().ply) // the server confirms White's own move
                black.client.send(ClientMessage.Move(game.gameId, "e7e5", 1))
                assertEquals(2, white.next<ServerMessage.Moved>().ply)

                // White drops the connection and comes back with the saved token: same account, same game.
                white.client.stop()
                white.client.start()
                white.connected()
                val resumed = white.next<ServerMessage.Game>().game
                assertEquals(game.gameId, resumed.gameId)
                assertEquals(listOf("e2e4", "e7e5"), resumed.moves)

                white.client.send(ClientMessage.Resign(game.gameId))
                val ended = black.next<ServerMessage.Ended>()
                assertEquals(EndReason.RESIGNATION, ended.reason)
                assertNotNull(ended.whiteRatingDiff)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun serverListsScheduledArenasAndJoinsOne(): Unit = runBlocking {
        val url = System.getProperty("onlineServer")
        assumeTrue("Set -PonlineServer to run against a server", !url.isNullOrBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            withTimeout(20_000) {
                val a = Player(scope, url)
                a.client.start()
                a.connected()
                a.client.send(ClientMessage.ListTournaments)
                val list = a.next<ServerMessage.Tournaments>().tournaments
                assertTrue(list.any { it.schedule == "hourly_bullet" } && list.any { it.schedule == "hourly_blitz" })
                val arena = list.first()
                a.client.send(ClientMessage.JoinTournament(arena.id))
                val state = a.next<ServerMessage.Tournament>()
                assertEquals(arena.id, state.info.id)
                assertNotNull(state.me)
                a.client.send(ClientMessage.PauseTournament(arena.id))
                assertTrue(a.next<ServerMessage.Tournament>().me!!.paused)
            }
        } finally {
            scope.cancel()
        }
    }
}
