package ru.fourvic.chess.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import ru.fourvic.chess.server.game.RoomSettings
import ru.fourvic.chess.server.storage.InMemoryStorage
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.decodeServerMessage
import ru.fourvic.chess.shared.protocol.encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class ApplicationTest {

    private suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) = send(Frame.Text(message.encode()))

    /** Next message of type [T], skipping others (for example pongs or connection notices). */
    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.expect(): T = withTimeout(5_000) {
        while (true) {
            val message = decodeServerMessage((incoming.receive() as Frame.Text).readText())
            if (message is T) return@withTimeout message
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun HttpClient.join(token: String? = null): Pair<DefaultClientWebSocketSession, ServerMessage.Welcome> {
        val session = webSocketSession("/ws")
        session.sendMessage(ClientMessage.Hello(token = token, locale = "ru"))
        return session to session.expect<ServerMessage.Welcome>()
    }

    @Test
    fun healthAndLegalPages() = testApplication {
        application { module(InMemoryStorage()) }
        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertTrue(health.bodyAsText().contains("\"status\":\"ok\""))
        for (page in listOf("/", "/privacy.html", "/terms.html", "/delete-account.html", "/style.css")) {
            assertEquals(HttpStatusCode.OK, client.get(page).status, page)
        }
        assertTrue(client.get("/delete-account.html").bodyAsText().contains("Удалить аккаунт"))
    }

    @Test
    fun twoPlayersFindEachOtherPlayReconnectResignAndRematch() = testApplication {
        application { module(InMemoryStorage(), RoomSettings(disconnectGraceMs = 10_000)) }
        val ws = createClient { install(ClientWebSockets) }

        val (a, welcomeA) = ws.join()
        val (b, welcomeB) = ws.join()
        assertTrue(welcomeA.player.name.startsWith("Игрок"))
        assertEquals(1500, welcomeA.player.rating)

        a.sendMessage(ClientMessage.Seek(Clock(180, 2)))
        a.expect<ServerMessage.Seeking>()
        b.sendMessage(ClientMessage.Seek(Clock(180, 2)))
        val gameA = a.expect<ServerMessage.Game>().game
        val gameB = b.expect<ServerMessage.Game>().game
        assertEquals(gameA.gameId, gameB.gameId)
        assertTrue(gameA.rated)

        val aIsWhite = gameA.white.id == welcomeA.player.id
        var white = if (aIsWhite) a else b
        var black = if (aIsWhite) b else a
        val blackToken = if (aIsWhite) welcomeB.token else welcomeA.token
        val id = gameA.gameId

        white.sendMessage(ClientMessage.Move(id, "e2e4", 0))
        assertEquals("e2e4", white.expect<ServerMessage.Moved>().uci)
        black.expect<ServerMessage.Moved>()

        // Black drops and comes back: the game is restored from the snapshot.
        black.close()
        assertFalse(white.expect<ServerMessage.OpponentConnection>().connected)
        val (blackAgain, _) = ws.join(blackToken)
        black = blackAgain
        val restored = black.expect<ServerMessage.Game>().game
        assertEquals(listOf("e2e4"), restored.moves)
        assertTrue(white.expect<ServerMessage.OpponentConnection>().connected)

        black.sendMessage(ClientMessage.Move(id, "e7e5", 1))
        white.expect<ServerMessage.Moved>()
        white.sendMessage(ClientMessage.Resign(id))
        val ended = black.expect<ServerMessage.Ended>()
        assertEquals(GameResult.BLACK_WON, ended.result)
        assertEquals(EndReason.RESIGNATION, ended.reason)
        assertTrue((ended.blackRatingDiff ?: 0) > 0)
        assertTrue((ended.whiteRatingDiff ?: 0) < 0)
        white.expect<ServerMessage.Ended>()

        white.sendMessage(ClientMessage.Rematch(id))
        black.expect<ServerMessage.RematchOffered>()
        black.sendMessage(ClientMessage.Rematch(id))
        val rematch = white.expect<ServerMessage.Game>().game
        black.expect<ServerMessage.Game>()
        assertEquals(gameA.white.id, rematch.black.id, "colours are swapped in the rematch")
        assertTrue(rematch.white.rating > rematch.black.rating, "the winner's new rating is used")

        white.sendMessage(ClientMessage.DeleteAccount)
        white.expect<ServerMessage.AccountDeleted>()
        val notice = black.expect<ServerMessage.Ended>()
        assertNotNull(notice)
    }
}
