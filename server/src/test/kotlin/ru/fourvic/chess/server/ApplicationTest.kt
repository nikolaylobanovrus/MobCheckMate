package ru.fourvic.chess.server

import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.ProtocolJson
import ru.fourvic.chess.shared.protocol.ServerMessage
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class ApplicationTest {

    @Test
    fun healthReportsOk() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun sandboxValidatesMovesOnTheServer() = testApplication {
        application { module() }
        val ws = createClient { install(ClientWebSockets) }

        ws.webSocket("/ws/sandbox") {
            suspend fun receive(): ServerMessage =
                ProtocolJson.decodeFromString(ServerMessage.serializer(), (incoming.receive() as Frame.Text).readText())

            val initial = assertIs<ServerMessage.State>(receive())
            assertEquals(PlayerColor.WHITE, initial.sideToMove)

            send(Frame.Text("""{"type":"move","uci":"e2e4"}"""))
            val afterE4 = assertIs<ServerMessage.State>(receive())
            assertEquals(PlayerColor.BLACK, afterE4.sideToMove)
            assertEquals("e2e4", afterE4.lastMove)

            send(Frame.Text("""{"type":"move","uci":"e2e4"}"""))
            assertEquals(ErrorCode.ILLEGAL_MOVE, assertIs<ServerMessage.Error>(receive()).code)

            send(Frame.Text("""{"type":"jump"}"""))
            assertEquals(ErrorCode.BAD_REQUEST, assertIs<ServerMessage.Error>(receive()).code)
        }
    }

    @Test
    fun sandboxReportsCheckmate() {
        val session = SandboxSession()
        listOf("f2f3", "e7e5", "g2g4").forEach { session.handle("""{"type":"move","uci":"$it"}""") }
        val final = assertIs<ServerMessage.State>(session.handle("""{"type":"move","uci":"d8h4"}"""))
        assertEquals(GameStatus.CHECKMATE, final.status)
        assertEquals(4, final.ply)
    }

    @Test
    fun sandboxRejectsBrokenPosition() {
        val session = SandboxSession()
        val reply = session.handle("""{"type":"new_game","fen":"not a fen"}""")
        assertEquals(ErrorCode.INVALID_POSITION, assertIs<ServerMessage.Error>(reply).code)
    }
}
