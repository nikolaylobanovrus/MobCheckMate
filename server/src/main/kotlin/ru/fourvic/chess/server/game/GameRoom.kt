package ru.fourvic.chess.server.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.ServerMessage

data class RoomSettings(
    /** Time to make the first move before the game is aborted. */
    val firstMoveMs: Long = 30_000,
    /** A player who stays disconnected this long loses (or the game is aborted before both have moved). */
    val disconnectGraceMs: Long = 30_000,
    /** Network delay forgiven on every move so that players far from the server are not punished. */
    val lagCompensationMs: Long = 100,
    val chatIntervalMs: Long = 1_500,
)

/** What a room needs from the server around it. */
interface RoomEvents {
    fun send(accountId: String, message: ServerMessage)

    /** Called once at the end; returns rating changes for White and Black, or nulls for unrated games. */
    fun gameEnded(room: GameRoom, result: GameResult, reason: EndReason): Pair<Int?, Int?>

    fun rematchAgreed(room: GameRoom)
}

/**
 * One game with server-authoritative clocks. Clocks start after both sides have made their first move;
 * until then a first-move timer applies. All state changes happen under the room's lock, and every timer
 * remembers the move count it was set for, so a stale timer does nothing.
 */
class GameRoom(
    val id: String,
    val white: PlayerInfo,
    val black: PlayerInfo,
    val clock: Clock,
    val rated: Boolean,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val events: RoomEvents,
    private val settings: RoomSettings = RoomSettings(),
    val tournamentId: String? = null,
    /** The side played by a computer opponent, if any; it gets no rating of its own. */
    val bot: PlayerColor? = null,
) {
    val startedAt: Long = now()
    private val game = ChessGame()
    private val moves = mutableListOf<String>()
    private var whiteMs = clock.initialSeconds * 1000L
    private var blackMs = clock.initialSeconds * 1000L
    private var turnStartedAt = startedAt
    private var result: GameResult? = null
    private var reason: EndReason? = null
    var endedAt: Long = 0
        private set
    private var drawOfferBy: PlayerColor? = null
    private val increments = mutableMapOf(
        PlayerColor.WHITE to clock.incrementSeconds * 1000L,
        PlayerColor.BLACK to clock.incrementSeconds * 1000L,
    )
    private val berserkers = mutableSetOf<PlayerColor>()
    private val rematchOffers = mutableSetOf<PlayerColor>()
    private var timerJob: Job? = null
    private val claimJobs = mutableMapOf<PlayerColor, Pair<Job, Long>>()
    private val lastChatAt = mutableMapOf<PlayerColor, Long>()

    init {
        synchronized(this) { scheduleTimer() }
    }

    val isOver: Boolean get() = synchronized(this) { result != null }
    val moveList: List<String> get() = synchronized(this) { moves.toList() }
    val finalResult: GameResult? get() = synchronized(this) { result }
    val finalReason: EndReason? get() = synchronized(this) { reason }
    val berserked: Set<PlayerColor> get() = synchronized(this) { berserkers.toSet() }

    fun colorOf(accountId: String): PlayerColor? = when (accountId) {
        white.id -> PlayerColor.WHITE
        black.id -> PlayerColor.BLACK
        else -> null
    }

    fun player(color: PlayerColor) = if (color == PlayerColor.WHITE) white else black

    fun snapshot(): GameSnapshot = synchronized(this) {
        val t = now()
        val (w, b) = clocksAt(t)
        GameSnapshot(
            gameId = id, white = white, black = black, clock = clock, rated = rated, moves = moves.toList(),
            whiteMs = w, blackMs = b,
            clockRunning = result == null && moves.size >= 2,
            firstMoveMs = if (result == null && moves.size < 2) (turnStartedAt + settings.firstMoveMs - t).coerceAtLeast(0) else null,
            result = result, reason = reason, drawOfferBy = drawOfferBy, tournamentId = tournamentId,
            berserk = PlayerColor.entries.filter { it in berserkers },
        )
    }

    /** Returns an error to send back to the mover, or null when the move was applied (or was a duplicate). */
    fun move(accountId: String, uci: String, ply: Int): ServerMessage.Error? = synchronized(this) {
        val color = colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
        if (result != null) return error(ErrorCode.NO_SUCH_GAME, "The game is over")
        if (ply < moves.size) return null // the same move sent twice after a reconnect
        if (ply > moves.size) return error(ErrorCode.BAD_REQUEST, "Unexpected ply $ply")
        if (game.sideToMove != color) return error(ErrorCode.NOT_YOUR_TURN, "Not your turn")
        val move = ChessMove.fromUci(uci) ?: return error(ErrorCode.BAD_REQUEST, "Malformed move")
        val t = now()

        val clockRunning = moves.size >= 2
        var left = remaining(color)
        if (clockRunning) {
            val spent = (t - turnStartedAt - settings.lagCompensationMs).coerceAtLeast(0)
            left -= spent
            if (left <= 0) {
                setRemaining(color, 0)
                flag(color)
                return null
            }
        }
        if (!game.play(move)) return error(ErrorCode.ILLEGAL_MOVE, "Illegal move $uci")
        if (clockRunning) setRemaining(color, left + increments.getValue(color))
        moves += move.uci
        turnStartedAt = t
        if (drawOfferBy != null && drawOfferBy != color) drawOfferBy = null // moving declines the offer

        broadcast(ServerMessage.Moved(id, move.uci, moves.size, whiteMs, blackMs))
        when (game.status) {
            GameStatus.CHECKMATE -> end(winner(color), EndReason.CHECKMATE)
            GameStatus.STALEMATE -> end(GameResult.DRAW, EndReason.STALEMATE)
            GameStatus.DRAW_THREEFOLD_REPETITION -> end(GameResult.DRAW, EndReason.REPETITION)
            GameStatus.DRAW_INSUFFICIENT_MATERIAL -> end(GameResult.DRAW, EndReason.INSUFFICIENT_MATERIAL)
            GameStatus.DRAW_FIFTY_MOVES -> end(GameResult.DRAW, EndReason.FIFTY_MOVES)
            GameStatus.ONGOING -> scheduleTimer()
        }
        null
    }

    fun resign(accountId: String): ServerMessage.Error? = synchronized(this) {
        val color = colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
        if (result != null) return error(ErrorCode.NO_SUCH_GAME, "The game is over")
        end(winner(color.opposite()), EndReason.RESIGNATION)
        null
    }

    /** Only before both players have moved; the game then ends without a rating change. */
    fun abort(accountId: String): ServerMessage.Error? = synchronized(this) {
        colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
        if (result != null) return error(ErrorCode.NO_SUCH_GAME, "The game is over")
        // Otherwise players could dodge tournament opponents; not moving loses instead.
        if (tournamentId != null) return error(ErrorCode.BAD_REQUEST, "Tournament games cannot be aborted")
        if (moves.size >= 2) return error(ErrorCode.BAD_REQUEST, "Too late to abort")
        end(GameResult.ABORTED, EndReason.ABORTED)
        null
    }

    fun offerDraw(accountId: String): ServerMessage.Error? = synchronized(this) {
        val color = colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
        if (result != null) return error(ErrorCode.NO_SUCH_GAME, "The game is over")
        when (drawOfferBy) {
            color.opposite() -> end(GameResult.DRAW, EndReason.AGREEMENT)
            null -> {
                drawOfferBy = color
                broadcast(ServerMessage.DrawOffered(id, color))
            }
            else -> Unit // already offered
        }
        null
    }

    fun declineDraw(accountId: String): ServerMessage.Error? = synchronized(this) {
        val color = colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
        if (drawOfferBy == color.opposite()) {
            drawOfferBy = null
            broadcast(ServerMessage.DrawDeclined(id))
        }
        null
    }

    fun rematch(accountId: String): ServerMessage.Error? {
        var agreed = false
        synchronized(this) {
            val color = colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
            if (result == null) return error(ErrorCode.BAD_REQUEST, "The game is still going")
            if (tournamentId != null) return error(ErrorCode.BAD_REQUEST, "The tournament pairs the next game")
            if (!rematchOffers.add(color)) return null
            if (rematchOffers.size == 2) agreed = true else broadcast(ServerMessage.RematchOffered(id, color))
        }
        if (agreed) events.rematchAgreed(this)
        return null
    }

    /** Arena only, before the player's own first move: half the base time and no increment. */
    fun berserk(accountId: String): ServerMessage.Error? = synchronized(this) {
        val color = colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
        if (result != null) return error(ErrorCode.NO_SUCH_GAME, "The game is over")
        if (tournamentId == null || !clock.berserkAllowed) return error(ErrorCode.BAD_REQUEST, "Berserk is not allowed")
        val ownMovesPlayed = if (color == PlayerColor.WHITE) moves.isNotEmpty() else moves.size >= 2
        if (ownMovesPlayed) return error(ErrorCode.BAD_REQUEST, "Too late to go berserk")
        if (!berserkers.add(color)) return null
        setRemaining(color, clock.berserked().initialSeconds * 1000L)
        increments[color] = 0
        broadcast(ServerMessage.Berserked(id, color, whiteMs, blackMs))
        null
    }

    fun chat(accountId: String, phrase: QuickPhrase): ServerMessage.Error? = synchronized(this) {
        val color = colorOf(accountId) ?: return error(ErrorCode.NO_SUCH_GAME, "Not your game")
        val t = now()
        val last = lastChatAt[color]
        if (last != null && t - last < settings.chatIntervalMs) return error(ErrorCode.RATE_LIMITED, "Too many messages")
        lastChatAt[color] = t
        broadcast(ServerMessage.Chat(id, color, phrase))
        null
    }

    /** Tracks connections: a player who stays away for the grace period loses the game. */
    fun setConnected(accountId: String, connected: Boolean): Unit = synchronized(this) {
        val color = colorOf(accountId) ?: return
        if (result != null) return
        val opponent = player(color.opposite())
        if (connected) {
            val pending = claimJobs.remove(color) ?: return
            pending.first.cancel()
            events.send(opponent.id, ServerMessage.OpponentConnection(id, connected = true))
        } else {
            if (color in claimJobs) return
            val grace = settings.disconnectGraceMs
            val job = scope.launch {
                delay(grace)
                abandoned(color)
            }
            claimJobs[color] = job to now() + grace
            events.send(opponent.id, ServerMessage.OpponentConnection(id, connected = false, claimInMs = grace))
        }
    }

    /** Pending claim against the opponent of [color], to repeat after [color] reconnects. */
    fun opponentClaimNotice(color: PlayerColor): ServerMessage.OpponentConnection? = synchronized(this) {
        val claim = claimJobs[color.opposite()] ?: return null
        ServerMessage.OpponentConnection(id, connected = false, claimInMs = (claim.second - now()).coerceAtLeast(0))
    }

    private fun abandoned(color: PlayerColor): Unit = synchronized(this) {
        if (result != null || claimJobs.remove(color) == null) return
        when {
            moves.size >= 2 -> end(winner(color.opposite()), EndReason.ABANDONED)
            tournamentId != null -> end(winner(color.opposite()), EndReason.NO_START)
            else -> end(GameResult.ABORTED, EndReason.ABORTED)
        }
    }

    private fun scheduleTimer() {
        timerJob?.cancel()
        val generation = moves.size
        val delayMs = if (moves.size < 2) settings.firstMoveMs else remaining(game.sideToMove)
        timerJob = scope.launch {
            delay(delayMs)
            onTimer(generation)
        }
    }

    private fun onTimer(generation: Int): Unit = synchronized(this) {
        if (result != null || moves.size != generation) return
        if (moves.size < 2) {
            // In a tournament the player who does not start loses; elsewhere the game is simply cancelled.
            if (tournamentId != null) {
                end(winner(game.sideToMove.opposite()), EndReason.NO_START)
            } else {
                end(GameResult.ABORTED, EndReason.ABORTED)
            }
            return
        }
        val side = game.sideToMove
        val left = remaining(side) - (now() - turnStartedAt)
        if (left > 0) {
            timerJob = scope.launch {
                delay(left)
                onTimer(generation)
            }
            return
        }
        setRemaining(side, 0)
        flag(side)
    }

    private fun flag(loser: PlayerColor) {
        // Lichess rule: a flag fall is a draw when the opponent could never mate.
        if (game.hasMatingMaterial(loser.opposite())) {
            end(winner(loser.opposite()), EndReason.TIMEOUT)
        } else {
            end(GameResult.DRAW, EndReason.TIMEOUT_VS_INSUFFICIENT_MATERIAL)
        }
    }

    private fun end(outcome: GameResult, why: EndReason) {
        if (result != null) return
        val t = now()
        if (moves.size >= 2) {
            val (w, b) = clocksAt(t)
            whiteMs = w
            blackMs = b
        }
        result = outcome
        reason = why
        endedAt = t
        drawOfferBy = null
        timerJob?.cancel()
        claimJobs.values.forEach { it.first.cancel() }
        claimJobs.clear()
        val (whiteDiff, blackDiff) = events.gameEnded(this, outcome, why)
        broadcast(ServerMessage.Ended(id, outcome, why, whiteDiff, blackDiff))
    }

    private fun clocksAt(t: Long): Pair<Long, Long> {
        if (result != null || moves.size < 2) return whiteMs to blackMs
        val elapsed = t - turnStartedAt
        return if (game.sideToMove == PlayerColor.WHITE) {
            (whiteMs - elapsed).coerceAtLeast(0) to blackMs
        } else {
            whiteMs to (blackMs - elapsed).coerceAtLeast(0)
        }
    }

    private fun remaining(color: PlayerColor) = if (color == PlayerColor.WHITE) whiteMs else blackMs

    private fun setRemaining(color: PlayerColor, ms: Long) {
        if (color == PlayerColor.WHITE) whiteMs = ms else blackMs = ms
    }

    private fun broadcast(message: ServerMessage) {
        events.send(white.id, message)
        events.send(black.id, message)
    }

    private fun error(code: ErrorCode, text: String) = ServerMessage.Error(code, text)

    private fun winner(color: PlayerColor) = if (color == PlayerColor.WHITE) GameResult.WHITE_WON else GameResult.BLACK_WON
}

fun PlayerColor.opposite() = if (this == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
