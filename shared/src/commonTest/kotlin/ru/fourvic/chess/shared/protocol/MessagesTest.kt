package ru.fourvic.chess.shared.protocol

import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PlayerColor
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesTest {

    @Test
    fun clientMessagesUseTypeDiscriminator() {
        val json = ProtocolJson.encodeToString(ClientMessage.serializer(), ClientMessage.Move("e2e4"))
        assertEquals("""{"type":"move","uci":"e2e4"}""", json)
        assertEquals(ClientMessage.Move("e2e4"), ProtocolJson.decodeFromString(ClientMessage.serializer(), json))
    }

    @Test
    fun serverStateRoundTrips() {
        val state = ServerMessage.State("8/8/8/8/8/8/8/8 w - - 0 1", PlayerColor.WHITE, GameStatus.ONGOING)
        val json = ProtocolJson.encodeToString(ServerMessage.serializer(), state)
        assertEquals(state, ProtocolJson.decodeFromString(ServerMessage.serializer(), json))
    }
}
