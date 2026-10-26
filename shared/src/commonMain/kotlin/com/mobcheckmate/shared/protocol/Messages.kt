package com.mobcheckmate.shared.protocol

import com.mobcheckmate.shared.chess.GameStatus
import com.mobcheckmate.shared.chess.PlayerColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WebSocket protocol between the app and the server. Every frame is one JSON object with a `type` field.
 * Version 0: a sandbox game against nobody, used to exercise server-side move validation.
 */
@Serializable
sealed interface ClientMessage {

    /** Start a new game from the given position, or from the initial position. */
    @Serializable
    @SerialName("new_game")
    data class NewGame(val fen: String? = null) : ClientMessage

    /** Play a move in UCI notation, for example `e2e4` or `e7e8q`. */
    @Serializable
    @SerialName("move")
    data class Move(val uci: String) : ClientMessage
}

@Serializable
sealed interface ServerMessage {

    @Serializable
    @SerialName("state")
    data class State(
        val fen: String,
        val sideToMove: PlayerColor,
        val status: GameStatus,
        val lastMove: String? = null,
        val ply: Int = 0,
    ) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(val code: ErrorCode, val message: String) : ServerMessage
}

@Serializable
enum class ErrorCode { BAD_REQUEST, ILLEGAL_MOVE, INVALID_POSITION }

/** JSON settings shared by the client and the server. */
val ProtocolJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}
