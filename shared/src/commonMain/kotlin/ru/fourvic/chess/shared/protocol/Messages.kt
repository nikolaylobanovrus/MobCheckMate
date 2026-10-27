package ru.fourvic.chess.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.fourvic.chess.shared.chess.PlayerColor

/**
 * WebSocket protocol between the app and the game server, version [PROTOCOL_VERSION].
 * Every frame is one JSON object with a `type` field. The first client frame must be [ClientMessage.Hello].
 */
const val PROTOCOL_VERSION = 1

/** Time control in seconds, for example 3+2. */
@Serializable
data class Clock(val initialSeconds: Int, val incrementSeconds: Int) {
    init {
        require(initialSeconds in 0..10_800 && incrementSeconds in 0..180) { "Unsupported time control" }
        require(initialSeconds > 0 || incrementSeconds > 0) { "Time control must not be 0+0" }
    }

    /** Lichess-style estimate of a game's length: base time plus 40 increments. */
    val estimatedSeconds: Int get() = initialSeconds + 40 * incrementSeconds

    val speed: Speed
        get() = when {
            estimatedSeconds < 180 -> Speed.BULLET
            estimatedSeconds < 480 -> Speed.BLITZ
            estimatedSeconds < 1500 -> Speed.RAPID
            else -> Speed.CLASSICAL
        }

    val label: String get() = "${initialSeconds / 60}+$incrementSeconds"

    companion object {
        /** Time controls offered in the lobby. */
        val LOBBY = listOf(
            Clock(60, 0), Clock(120, 1), Clock(180, 0), Clock(180, 2), Clock(300, 0),
            Clock(300, 3), Clock(600, 0), Clock(600, 5), Clock(900, 10),
        )
    }
}

/** Ratings are kept separately per speed, like on Lichess. */
@Serializable
enum class Speed { BULLET, BLITZ, RAPID, CLASSICAL }

@Serializable
data class PlayerInfo(
    val id: String,
    val name: String,
    val rating: Int,
    val provisional: Boolean = false,
)

@Serializable
enum class GameResult { WHITE_WON, BLACK_WON, DRAW, ABORTED }

@Serializable
enum class EndReason {
    CHECKMATE, RESIGNATION, TIMEOUT, STALEMATE, REPETITION, INSUFFICIENT_MATERIAL, FIFTY_MOVES,
    AGREEMENT, ABANDONED, ABORTED, TIMEOUT_VS_INSUFFICIENT_MATERIAL,
}

/** Short phrases instead of free chat: no moderation needed and every phrase is localised on the device. */
@Serializable
enum class QuickPhrase { HELLO, GOOD_LUCK, HAVE_FUN, NICE_MOVE, OOPS, THANKS, GOOD_GAME, BYE }

/**
 * Full state of a game, sent when it starts and after a reconnect.
 * Clocks are the remaining time when the server sent the snapshot; the side to move is [clockRunning] for.
 */
@Serializable
data class GameSnapshot(
    val gameId: String,
    val white: PlayerInfo,
    val black: PlayerInfo,
    val clock: Clock,
    val rated: Boolean,
    val moves: List<String>,
    val whiteMs: Long,
    val blackMs: Long,
    /** False before both sides have moved: the first-move timer applies instead of the clock. */
    val clockRunning: Boolean,
    /** Milliseconds left to make the first move, while [clockRunning] is false. */
    val firstMoveMs: Long? = null,
    val result: GameResult? = null,
    val reason: EndReason? = null,
    val drawOfferBy: PlayerColor? = null,
    val tournamentId: String? = null,
)

@Serializable
data class RatingInfo(val speed: Speed, val rating: Int, val games: Int, val provisional: Boolean)

@Serializable
sealed interface ClientMessage {

    /** First frame. Without a valid [token] the server creates a guest account. */
    @Serializable
    @SerialName("hello")
    data class Hello(val token: String? = null, val protocol: Int = PROTOCOL_VERSION, val locale: String? = null) : ClientMessage

    @Serializable
    @SerialName("seek")
    data class Seek(val clock: Clock, val rated: Boolean = true) : ClientMessage

    @Serializable
    @SerialName("cancel_seek")
    data object CancelSeek : ClientMessage

    /** [ply] is the number of moves already played; a repeated frame for the same ply is ignored. */
    @Serializable
    @SerialName("move")
    data class Move(val gameId: String, val uci: String, val ply: Int) : ClientMessage

    @Serializable
    @SerialName("resign")
    data class Resign(val gameId: String) : ClientMessage

    /** Before both sides have moved the game can be cancelled without a rating change. */
    @Serializable
    @SerialName("abort")
    data class Abort(val gameId: String) : ClientMessage

    /** Offers a draw, or accepts the opponent's offer. */
    @Serializable
    @SerialName("draw_offer")
    data class OfferDraw(val gameId: String) : ClientMessage

    @Serializable
    @SerialName("draw_decline")
    data class DeclineDraw(val gameId: String) : ClientMessage

    /** Offers a rematch after the game, or accepts the opponent's offer. */
    @Serializable
    @SerialName("rematch")
    data class Rematch(val gameId: String) : ClientMessage

    @Serializable
    @SerialName("chat")
    data class Chat(val gameId: String, val phrase: QuickPhrase) : ClientMessage

    @Serializable
    @SerialName("ping")
    data class Ping(val clientTime: Long) : ClientMessage

    /** Permanently deletes the account and its data (store requirement). */
    @Serializable
    @SerialName("delete_account")
    data object DeleteAccount : ClientMessage

    @Serializable
    @SerialName("rename")
    data class Rename(val name: String) : ClientMessage
}

@Serializable
sealed interface ServerMessage {

    @Serializable
    @SerialName("welcome")
    data class Welcome(val player: PlayerInfo, val token: String, val ratings: List<RatingInfo>) : ServerMessage

    @Serializable
    @SerialName("seeking")
    data class Seeking(val clock: Clock) : ServerMessage

    @Serializable
    @SerialName("seek_cancelled")
    data object SeekCancelled : ServerMessage

    @Serializable
    @SerialName("game")
    data class Game(val game: GameSnapshot) : ServerMessage

    @Serializable
    @SerialName("moved")
    data class Moved(val gameId: String, val uci: String, val ply: Int, val whiteMs: Long, val blackMs: Long) : ServerMessage

    @Serializable
    @SerialName("ended")
    data class Ended(
        val gameId: String,
        val result: GameResult,
        val reason: EndReason,
        val whiteRatingDiff: Int? = null,
        val blackRatingDiff: Int? = null,
    ) : ServerMessage

    @Serializable
    @SerialName("draw_offered")
    data class DrawOffered(val gameId: String, val by: PlayerColor) : ServerMessage

    @Serializable
    @SerialName("draw_declined")
    data class DrawDeclined(val gameId: String) : ServerMessage

    @Serializable
    @SerialName("rematch_offered")
    data class RematchOffered(val gameId: String, val by: PlayerColor) : ServerMessage

    /** The opponent lost or regained the connection; [claimInMs] is when the game will be awarded. */
    @Serializable
    @SerialName("opponent_connection")
    data class OpponentConnection(val gameId: String, val connected: Boolean, val claimInMs: Long? = null) : ServerMessage

    @Serializable
    @SerialName("chat")
    data class Chat(val gameId: String, val by: PlayerColor, val phrase: QuickPhrase) : ServerMessage

    @Serializable
    @SerialName("pong")
    data class Pong(val clientTime: Long, val serverTime: Long) : ServerMessage

    @Serializable
    @SerialName("account_deleted")
    data object AccountDeleted : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(val code: ErrorCode, val message: String) : ServerMessage
}

@Serializable
enum class ErrorCode { BAD_REQUEST, ILLEGAL_MOVE, NOT_YOUR_TURN, NO_SUCH_GAME, UNAUTHORIZED, RATE_LIMITED, UNSUPPORTED_VERSION, NAME_TAKEN }

/** JSON settings shared by the client and the server. */
val ProtocolJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun ClientMessage.encode(): String = ProtocolJson.encodeToString(ClientMessage.serializer(), this)
fun ServerMessage.encode(): String = ProtocolJson.encodeToString(ServerMessage.serializer(), this)
fun decodeClientMessage(text: String): ClientMessage = ProtocolJson.decodeFromString(ClientMessage.serializer(), text)
fun decodeServerMessage(text: String): ServerMessage = ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
