package ru.fourvic.chess.server

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import ru.fourvic.chess.server.accounts.AccountService
import ru.fourvic.chess.server.game.ClientConnection
import ru.fourvic.chess.server.game.GameServer
import ru.fourvic.chess.server.game.RoomSettings
import ru.fourvic.chess.server.storage.InMemoryStorage
import ru.fourvic.chess.server.storage.JdbcStorage
import ru.fourvic.chess.server.storage.Storage
import ru.fourvic.chess.server.tournament.ArenaManager
import ru.fourvic.chess.server.tournament.ArenaSettings
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.PROTOCOL_VERSION
import ru.fourvic.chess.shared.protocol.ProtocolJson
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.decodeClientMessage
import ru.fourvic.chess.shared.protocol.encode
import kotlin.time.Duration.Companion.seconds

const val SERVER_VERSION = "0.3.0"

/** Settings from environment variables; without DATABASE_URL everything is kept in memory. */
data class ServerConfig(
    val port: Int = 8080,
    val databaseUrl: String? = null,
    val databaseUser: String? = null,
    val databasePassword: String? = null,
    val rooms: RoomSettings = RoomSettings(),
    val arenas: ArenaSettings = ArenaSettings(),
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            databaseUrl = env["DATABASE_URL"],
            databaseUser = env["DATABASE_USER"],
            databasePassword = env["DATABASE_PASSWORD"],
        )
    }
}

fun main() {
    val config = ServerConfig.fromEnv()
    val storage = config.databaseUrl?.let { JdbcStorage.connect(it, config.databaseUser, config.databasePassword) }
        ?: InMemoryStorage().also { LoggerFactory.getLogger("main").warn("DATABASE_URL is not set: data is kept in memory") }
    embeddedServer(Netty, port = config.port) { module(storage, config.rooms, config.arenas) }.start(wait = true)
}

@Serializable
data class Health(val status: String, val version: String, val protocol: Int)

fun Application.module(
    storage: Storage = InMemoryStorage(),
    rooms: RoomSettings = RoomSettings(),
    arenaSettings: ArenaSettings = ArenaSettings(),
) {
    val accounts = AccountService(storage)
    val server = GameServer(storage, accounts, scope = this, roomSettings = rooms)
    val arenas = ArenaManager(server, storage, scope = this, settings = arenaSettings)
    val log = LoggerFactory.getLogger("ws")

    install(ContentNegotiation) { json(ProtocolJson) }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 16 * 1024L
    }

    routing {
        get("/health") { call.respond(Health("ok", SERVER_VERSION, PROTOCOL_VERSION)) }
        get("/") { call.respondText(LANDING, ContentType.Text.Html) }
        // Privacy policy, terms and the account deletion page required by the stores.
        staticResources("/", "site")

        webSocket("/ws") {
            val hello = withTimeoutOrNull(10.seconds) {
                (incoming.receiveCatching().getOrNull() as? Frame.Text)?.readText()
                    ?.let { runCatching { decodeClientMessage(it) }.getOrNull() } as? ClientMessage.Hello
            }
            if (hello == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected hello"))
                return@webSocket
            }
            if (hello.protocol != PROTOCOL_VERSION) {
                send(Frame.Text(ServerMessage.Error(ErrorCode.UNSUPPORTED_VERSION, "Update the app").encode()))
                close(CloseReason(CloseReason.Codes.NORMAL, "Unsupported protocol"))
                return@webSocket
            }
            val session = accounts.authenticate(hello.token, hello.locale)
            val account = session.account

            // A dedicated writer keeps a slow client from blocking the game logic.
            val outbox = Channel<ServerMessage>(capacity = 512)
            val connection = object : ClientConnection {
                override fun send(message: ServerMessage) {
                    if (outbox.trySend(message).isFailure) outbox.close()
                }

                override fun close() {
                    outbox.close()
                }
            }
            val writer = launch {
                for (message in outbox) send(Frame.Text(message.encode()))
                close(CloseReason(CloseReason.Codes.NORMAL, "Bye"))
            }
            server.connected(account, session.token, connection)
            val limiter = RateLimiter(capacity = 40, refillPerSecond = 10.0)
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    if (!limiter.tryAcquire(System.currentTimeMillis())) {
                        connection.send(ServerMessage.Error(ErrorCode.RATE_LIMITED, "Slow down"))
                        continue
                    }
                    val message = runCatching { decodeClientMessage(frame.readText()) }.getOrNull()
                    if (message == null) {
                        connection.send(ServerMessage.Error(ErrorCode.BAD_REQUEST, "Cannot parse message"))
                        continue
                    }
                    runCatching {
                        if (message is ClientMessage.TournamentCommand) arenas.handle(account, message) else server.handle(account, message)
                    }
                        .onFailure { log.error("Failed to handle {} from {}", message::class.simpleName, account.id, it) }
                }
            } finally {
                server.disconnected(account.id, connection)
                outbox.close()
                writer.join()
            }
        }
    }
}

/** Token bucket: [capacity] messages at once, refilled at [refillPerSecond]. */
class RateLimiter(private val capacity: Int, private val refillPerSecond: Double) {
    private var tokens = capacity.toDouble()
    private var last = -1L

    fun tryAcquire(nowMs: Long): Boolean {
        if (last >= 0) tokens = minOf(capacity.toDouble(), tokens + (nowMs - last) / 1000.0 * refillPerSecond)
        last = nowMs
        if (tokens < 1) return false
        tokens -= 1
        return true
    }
}

private val LANDING = """
<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Шахматы онлайн</title></head><body style="font-family:sans-serif;max-width:640px;margin:40px auto;padding:0 16px">
<h1>Шахматы онлайн</h1><p>Сервер игры работает.</p>
<ul><li><a href="/privacy.html">Политика конфиденциальности</a></li><li><a href="/terms.html">Пользовательское соглашение</a></li>
<li><a href="/delete-account.html">Удаление аккаунта</a></li></ul></body></html>
""".trimIndent()
