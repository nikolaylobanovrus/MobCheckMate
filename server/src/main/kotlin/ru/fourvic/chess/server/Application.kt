package ru.fourvic.chess.server

import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.ProtocolJson
import ru.fourvic.chess.shared.protocol.ServerMessage
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

@Serializable
data class Health(val status: String, val version: String)

fun Application.module() {
    install(ContentNegotiation) { json(ProtocolJson) }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 16 * 1024L
    }

    routing {
        get("/health") {
            call.respond(Health(status = "ok", version = SERVER_VERSION))
        }

        // Development sandbox: one private game per connection, every move validated on the server.
        webSocket("/ws/sandbox") {
            val session = SandboxSession()
            send(Frame.Text(session.encode(session.state())))
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                send(Frame.Text(session.encode(session.handle(frame.readText()))))
            }
        }
    }
}

const val SERVER_VERSION = "0.1.0"

/** Holds the game of one sandbox connection and turns client messages into server replies. */
class SandboxSession {
    private var game = ChessGame()

    fun handle(text: String): ServerMessage {
        val message = try {
            ProtocolJson.decodeFromString(ClientMessage.serializer(), text)
        } catch (e: SerializationException) {
            return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Cannot parse message")
        } catch (e: IllegalArgumentException) {
            return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Cannot parse message")
        }
        return when (message) {
            is ClientMessage.NewGame -> newGame(message.fen)
            is ClientMessage.Move -> move(message.uci)
        }
    }

    fun state(): ServerMessage.State = ServerMessage.State(
        fen = game.fen,
        sideToMove = game.sideToMove,
        status = game.status,
        lastMove = game.moves.lastOrNull()?.uci,
        ply = game.moves.size,
    )

    fun encode(message: ServerMessage): String = ProtocolJson.encodeToString(ServerMessage.serializer(), message)

    private fun newGame(fen: String?): ServerMessage {
        game = try {
            if (fen == null) ChessGame() else ChessGame(fen)
        } catch (e: RuntimeException) {
            return ServerMessage.Error(ErrorCode.INVALID_POSITION, "Cannot load position")
        }
        return state()
    }

    private fun move(uci: String): ServerMessage {
        val move = ChessMove.fromUci(uci)
            ?: return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Malformed move '$uci'")
        if (!game.play(move)) return ServerMessage.Error(ErrorCode.ILLEGAL_MOVE, "Illegal move '$uci'")
        return state()
    }
}
