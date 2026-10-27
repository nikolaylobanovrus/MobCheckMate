package ru.fourvic.chess.online

import android.content.Context
import androidx.core.content.edit
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.decodeServerMessage
import ru.fourvic.chess.shared.protocol.encode

enum class ConnectionState { OFFLINE, CONNECTING, CONNECTED }

/** Connection to the game server, shared by all screens. */
interface OnlineClient {
    val state: StateFlow<ConnectionState>
    val welcome: StateFlow<ServerMessage.Welcome?>
    val messages: SharedFlow<ServerMessage>

    /** Starts connecting (no-op when already started); reconnects with a growing delay until [stop]. */
    fun start()
    fun stop()

    /** Sends when connected; returns false otherwise. */
    fun send(message: ClientMessage): Boolean
}

/** Stores the secret account token on the device. */
interface TokenStore {
    fun load(): String?
    fun save(token: String?)
}

class PrefsTokenStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("online", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString("token", null)
    override fun save(token: String?) {
        prefs.edit { putString("token", token) }
    }
}

/** WebSocket client over OkHttp. The server URL is read before every connection attempt. */
class WebSocketOnlineClient(
    private val scope: CoroutineScope,
    private val tokens: TokenStore,
    private val serverUrl: () -> String,
) : OnlineClient {
    private val http = HttpClient(OkHttp) {
        install(WebSockets) { pingIntervalMillis = 15_000 }
    }
    private val _state = MutableStateFlow(ConnectionState.OFFLINE)
    private val _welcome = MutableStateFlow<ServerMessage.Welcome?>(null)
    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 128)
    @Volatile private var outbox = Channel<ClientMessage>(Channel.BUFFERED)
    @Volatile private var job: Job? = null

    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    override val welcome: StateFlow<ServerMessage.Welcome?> = _welcome.asStateFlow()
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                _state.value = ConnectionState.CONNECTING
                runCatching {
                    http.webSocket(serverUrl()) {
                        outbox = Channel(Channel.BUFFERED)
                        send(Frame.Text(ClientMessage.Hello(tokens.load(), locale = Locale.getDefault().toLanguageTag()).encode()))
                        val writer = launch { for (m in outbox) send(Frame.Text(m.encode())) }
                        try {
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                val message = runCatching { decodeServerMessage(frame.readText()) }.getOrNull() ?: continue
                                when (message) {
                                    is ServerMessage.Welcome -> {
                                        if (message.token.isNotEmpty()) tokens.save(message.token)
                                        _welcome.value = message
                                        _state.value = ConnectionState.CONNECTED
                                        backoffMs = 1_000L
                                    }
                                    ServerMessage.AccountDeleted -> {
                                        tokens.save(null)
                                        _welcome.value = null
                                    }
                                    else -> Unit
                                }
                                _messages.emit(message)
                                // Do not create a new guest account right away; the next start() does that.
                                if (message == ServerMessage.AccountDeleted) stop()
                            }
                        } finally {
                            writer.cancel()
                            outbox.close()
                        }
                    }
                }
                _state.value = ConnectionState.OFFLINE
                if (!isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _state.value = ConnectionState.OFFLINE
    }

    override fun send(message: ClientMessage): Boolean =
        _state.value == ConnectionState.CONNECTED && outbox.trySend(message).isSuccess
}
