package ru.fourvic.chess.server.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.fourvic.chess.server.accounts.AccountService
import ru.fourvic.chess.server.storage.Account
import ru.fourvic.chess.server.storage.GameRecord
import ru.fourvic.chess.server.storage.Storage
import ru.fourvic.chess.server.storage.StoredRating
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.RatingInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.rating.GameOutcome
import ru.fourvic.chess.shared.rating.Glicko2
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.roundToInt

/** A live client connection. [send] must not block. */
interface ClientConnection {
    fun send(message: ServerMessage)
    fun close()
}

/** Notified about every finished game, for example by tournaments. */
fun interface GameEndListener {
    fun gameEnded(room: GameRoom, result: GameResult, reason: EndReason)
}

/**
 * The game server: connections, matchmaking, game rooms and ratings. Thread-safe; it never holds its own lock
 * while calling into a room, so rooms can call back without deadlocks.
 */
class GameServer(
    private val storage: Storage,
    private val accounts: AccountService,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val roomSettings: RoomSettings = RoomSettings(),
    private val random: SecureRandom = SecureRandom(),
) : RoomEvents {
    private val log = LoggerFactory.getLogger(GameServer::class.java)
    private val glicko = Glicko2()

    private val connections = ConcurrentHashMap<String, ClientConnection>()
    private val rooms = ConcurrentHashMap<String, GameRoom>()
    private val activeGame = ConcurrentHashMap<String, String>()
    private val seeks = LinkedHashMap<String, SeekEntry>()
    private val endListeners = CopyOnWriteArrayList<GameEndListener>()

    private data class SeekEntry(val accountId: String, val clock: Clock, val rated: Boolean, val rating: Int, val since: Long)

    init {
        scope.launch {
            while (isActive) {
                delay(1_000)
                matchSeeks()
                cleanupRooms()
            }
        }
    }

    fun addGameEndListener(listener: GameEndListener) {
        endListeners += listener
    }

    fun isOnline(accountId: String) = connections.containsKey(accountId)
    fun activeGameOf(accountId: String): GameRoom? = activeGame[accountId]?.let { rooms[it] }
    fun room(id: String): GameRoom? = rooms[id]

    // --- Connections ------------------------------------------------------------------------------------------

    fun connected(account: Account, token: String, connection: ClientConnection) {
        connections.put(account.id, connection)?.takeIf { it !== connection }?.close()
        connection.send(welcome(account, token))
        val room = activeGameOf(account.id) ?: return
        connection.send(ServerMessage.Game(room.snapshot()))
        room.setConnected(account.id, true)
        room.colorOf(account.id)?.let { color -> room.opponentClaimNotice(color)?.let(connection::send) }
    }

    fun disconnected(accountId: String, connection: ClientConnection) {
        if (!connections.remove(accountId, connection)) return
        synchronized(seeks) { seeks.remove(accountId) }
        activeGameOf(accountId)?.setConnected(accountId, false)
    }

    override fun send(accountId: String, message: ServerMessage) {
        connections[accountId]?.send(message)
    }

    // --- Messages ---------------------------------------------------------------------------------------------

    fun handle(account: Account, message: ClientMessage) {
        val id = account.id
        val error: ServerMessage.Error? = when (message) {
            is ClientMessage.Hello -> null
            is ClientMessage.Seek -> seek(id, message.clock, message.rated)
            ClientMessage.CancelSeek -> {
                synchronized(seeks) { seeks.remove(id) }
                send(id, ServerMessage.SeekCancelled)
                null
            }
            is ClientMessage.Move -> roomFor(id, message.gameId)?.move(id, message.uci, message.ply) ?: noGame(id, message.gameId)
            is ClientMessage.Resign -> roomFor(id, message.gameId)?.resign(id) ?: noGame(id, message.gameId)
            is ClientMessage.Abort -> roomFor(id, message.gameId)?.abort(id) ?: noGame(id, message.gameId)
            is ClientMessage.OfferDraw -> roomFor(id, message.gameId)?.offerDraw(id) ?: noGame(id, message.gameId)
            is ClientMessage.DeclineDraw -> roomFor(id, message.gameId)?.declineDraw(id) ?: noGame(id, message.gameId)
            is ClientMessage.Rematch -> roomFor(id, message.gameId)?.rematch(id) ?: noGame(id, message.gameId)
            is ClientMessage.Chat -> roomFor(id, message.gameId)?.chat(id, message.phrase) ?: noGame(id, message.gameId)
            is ClientMessage.Ping -> {
                send(id, ServerMessage.Pong(message.clientTime, now()))
                null
            }
            is ClientMessage.Rename -> {
                val renamed = accounts.rename(id, message.name)
                if (renamed == null) {
                    ServerMessage.Error(ErrorCode.BAD_REQUEST, "Name must be 3-20 letters, digits, spaces, _ or -")
                } else {
                    send(id, welcome(renamed, token = ""))
                    null
                }
            }
            ClientMessage.DeleteAccount -> {
                deleteAccount(id)
                null
            }
        }
        error?.let { send(id, it) }
    }

    private fun roomFor(accountId: String, gameId: String): GameRoom? = rooms[gameId]?.takeIf { it.colorOf(accountId) != null }

    private fun noGame(accountId: String, gameId: String): ServerMessage.Error? {
        log.debug("{} referred to unknown game {}", accountId, gameId)
        return ServerMessage.Error(ErrorCode.NO_SUCH_GAME, "No such game")
    }

    private fun deleteAccount(accountId: String) {
        activeGameOf(accountId)?.resign(accountId)
        synchronized(seeks) { seeks.remove(accountId) }
        accounts.delete(accountId)
        send(accountId, ServerMessage.AccountDeleted)
        connections.remove(accountId)?.close()
    }

    // --- Matchmaking ------------------------------------------------------------------------------------------

    private fun seek(accountId: String, clock: Clock, rated: Boolean): ServerMessage.Error? {
        if (activeGameOf(accountId)?.isOver == false) return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Finish your game first")
        val rating = storage.rating(accountId, clock.speed).rating.rating.roundToInt()
        synchronized(seeks) { seeks[accountId] = SeekEntry(accountId, clock, rated, rating, now()) }
        send(accountId, ServerMessage.Seeking(clock))
        matchSeeks()
        return null
    }

    /** Pairs waiting players with the same time control; the accepted rating gap grows while they wait. */
    fun matchSeeks() {
        val pairs = mutableListOf<Pair<SeekEntry, SeekEntry>>()
        synchronized(seeks) {
            val t = now()
            val waiting = seeks.values.toMutableList()
            var i = 0
            while (i < waiting.size) {
                val a = waiting[i]
                val partner = waiting.drop(i + 1).firstOrNull { b ->
                    b.clock == a.clock && b.rated == a.rated &&
                        abs(a.rating - b.rating) <= minOf(window(a, t), window(b, t))
                }
                if (partner != null) {
                    pairs += a to partner
                    waiting.remove(partner)
                    seeks.remove(a.accountId)
                    seeks.remove(partner.accountId)
                }
                i++
            }
        }
        for ((a, b) in pairs) {
            val aWhite = random.nextBoolean()
            startGame(if (aWhite) a.accountId else b.accountId, if (aWhite) b.accountId else a.accountId, a.clock, a.rated)
        }
    }

    private fun window(seek: SeekEntry, t: Long): Int {
        val waitedSeconds = (t - seek.since) / 1000
        return if (waitedSeconds >= 20) Int.MAX_VALUE else (150 + 50 * waitedSeconds).toInt()
    }

    // --- Games ------------------------------------------------------------------------------------------------

    /** Starts a game between two accounts and sends it to both. Also used by tournaments and rematches. */
    fun startGame(whiteId: String, blackId: String, clock: Clock, rated: Boolean, tournamentId: String? = null): GameRoom? {
        val whiteAccount = storage.findAccount(whiteId) ?: return null
        val blackAccount = storage.findAccount(blackId) ?: return null
        val room = GameRoom(
            id = newGameId(),
            white = playerInfo(whiteAccount, clock.speed),
            black = playerInfo(blackAccount, clock.speed),
            clock = clock,
            rated = rated,
            scope = scope,
            now = now,
            events = this,
            settings = roomSettings,
            tournamentId = tournamentId,
        )
        rooms[room.id] = room
        activeGame[whiteId] = room.id
        activeGame[blackId] = room.id
        val start = ServerMessage.Game(room.snapshot())
        send(whiteId, start)
        send(blackId, start)
        // A player who is offline when the game is created (for example in a tournament) starts the claim timer.
        if (!isOnline(whiteId)) room.setConnected(whiteId, false)
        if (!isOnline(blackId)) room.setConnected(blackId, false)
        return room
    }

    override fun gameEnded(room: GameRoom, result: GameResult, reason: EndReason): Pair<Int?, Int?> {
        activeGame.remove(room.white.id, room.id)
        activeGame.remove(room.black.id, room.id)
        var diffs: Pair<Int?, Int?> = null to null
        if (room.rated && result != GameResult.ABORTED) diffs = updateRatings(room, result)
        if (result != GameResult.ABORTED) {
            runCatching {
                storage.saveGame(
                    GameRecord(
                        id = room.id, whiteId = room.white.id, blackId = room.black.id, clock = room.clock,
                        rated = room.rated, moves = room.moveList, result = result, reason = reason,
                        startedAt = room.startedAt, endedAt = now(), tournamentId = room.tournamentId,
                    ),
                )
            }.onFailure { log.error("Cannot save game {}", room.id, it) }
        }
        // Listeners may start new games; run them after the room has released its lock.
        scope.launch { endListeners.forEach { it.gameEnded(room, result, reason) } }
        return diffs
    }

    override fun rematchAgreed(room: GameRoom) {
        if (activeGameOf(room.white.id)?.isOver == false || activeGameOf(room.black.id)?.isOver == false) return
        startGame(room.black.id, room.white.id, room.clock, room.rated)
    }

    private fun updateRatings(room: GameRoom, result: GameResult): Pair<Int?, Int?> {
        val speed = room.clock.speed
        val white = storage.rating(room.white.id, speed)
        val black = storage.rating(room.black.id, speed)
        val whiteScore = when (result) {
            GameResult.WHITE_WON -> 1.0
            GameResult.BLACK_WON -> 0.0
            else -> 0.5
        }
        val newWhite = glicko.update(white.rating, listOf(GameOutcome(black.rating, whiteScore)))
        val newBlack = glicko.update(black.rating, listOf(GameOutcome(white.rating, 1 - whiteScore)))
        storage.saveRating(room.white.id, speed, StoredRating(newWhite, white.games + 1))
        storage.saveRating(room.black.id, speed, StoredRating(newBlack, black.games + 1))
        return (newWhite.rating - white.rating.rating).roundToInt() to (newBlack.rating - black.rating.rating).roundToInt()
    }

    /** Ended games stay for a few minutes so that players can offer a rematch. */
    private fun cleanupRooms() {
        val t = now()
        rooms.values.removeIf { it.isOver && t - it.endedAt > ROOM_KEEP_MS }
    }

    private fun playerInfo(account: Account, speed: Speed): PlayerInfo {
        val stored = storage.rating(account.id, speed)
        return PlayerInfo(account.id, account.name, stored.rating.rating.roundToInt(), stored.provisional)
    }

    private fun welcome(account: Account, token: String): ServerMessage.Welcome {
        val ratings = Speed.entries.map { speed ->
            val r = storage.rating(account.id, speed)
            RatingInfo(speed, r.rating.rating.roundToInt(), r.games, r.provisional)
        }
        val blitz = storage.rating(account.id, Speed.BLITZ)
        return ServerMessage.Welcome(
            PlayerInfo(account.id, account.name, blitz.rating.rating.roundToInt(), blitz.provisional),
            token,
            ratings,
        )
    }

    private fun newGameId(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private companion object {
        const val ROOM_KEEP_MS = 10 * 60_000L
    }
}
