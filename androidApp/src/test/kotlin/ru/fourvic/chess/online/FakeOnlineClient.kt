package ru.fourvic.chess.online

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.RatingInfo
import ru.fourvic.chess.shared.protocol.ServerMessage

/** Records what the app sends; tests push server messages through [messages] or straight into the view model. */
class FakeOnlineClient(me: PlayerInfo, ratings: List<RatingInfo> = emptyList()) : OnlineClient {
    override val state: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.CONNECTED)
    override val welcome: MutableStateFlow<ServerMessage.Welcome?> = MutableStateFlow(ServerMessage.Welcome(me, "token", ratings))
    override val messages: MutableSharedFlow<ServerMessage> = MutableSharedFlow(extraBufferCapacity = 64)
    val sent = mutableListOf<ClientMessage>()
    var starts = 0

    override fun start() {
        starts++
    }

    override fun stop() = Unit

    override fun send(message: ClientMessage): Boolean {
        if (state.value != ConnectionState.CONNECTED) return false
        sent += message
        return true
    }
}
