package ru.fourvic.chess.server.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.fourvic.chess.server.accounts.AccountService
import ru.fourvic.chess.server.bots.BotPersona
import ru.fourvic.chess.server.bots.BotPlayer
import ru.fourvic.chess.server.bots.BotSettings
import ru.fourvic.chess.server.storage.Account
import ru.fourvic.chess.server.storage.GameRecord
import ru.fourvic.chess.server.storage.Storage
import ru.fourvic.chess.server.storage.StoredRating
import ru.fourvic.chess.shared.chess.PlayerColor
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
import ru.fourvic.chess.shared.rating.Rating
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

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
 *
 * When nobody else is looking for a game, a seek is taken by a computer opponent after [BotSettings.waitMs]
 * (see [BotPlayer]); tournaments never use them.
 */
class GameServer(
    private val storage: Storage,
    private val accounts: AccountService,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val roomSettings: RoomSettings = RoomSettings(),
    private val random: SecureRandom = SecureRandom(),
    private val bots: BotSettings = BotSettings(),
    private val botRandom: Random = Random.Default,
    /** Where bots run the engine; tests pass their own dispatcher to keep virtual time. */
    private val botEngineContext: CoroutineContext = Dispatchers.Default,
) : RoomEvents {
    private val log = LoggerFactory.getLogger(GameServer::class.java)
    private val glicko = Glicko2()

    private val connections = ConcurrentHashMap<String, ClientConnection>()
    private val rooms = ConcurrentHashMap<String, GameRoom>()
    private val activeGame = ConcurrentHashMap<String, String>()
    private val seeks = LinkedHashMap<String, SeekEntry>()
    private val endListeners = CopyOnWriteArrayList<GameEndListener>()
    private val botPlayers = ConcurrentHashMap<String, BotPlayer>()
    private val countries = ConcurrentHashMap<String, String>()
    private val deletionListeners = CopyOnWriteArrayList<(String) -> Unit>()

    private data class SeekEntry(
        val accountId: String,
        val clock: Clock,
        val rated: Boolean,
        val rating: Int,
        val since: Long,
        /** When a computer opponent takes the seek if no person has. */
        val botAfter: Long,
    )

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

    /** Called with the account id after an account has been deleted. */
    fun addAccountDeletedListener(listener: (String) -> Unit) {
        deletionListeners += listener
    }

    fun isOnline(accountId: String) = connections.containsKey(accountId)
    fun countryOf(accountId: String): String? = countries[accountId]
    fun activeGameOf(accountId: String): GameRoom? = activeGame[accountId]?.let { rooms[it] }
    fun room(id: String): GameRoom? = rooms[id]

    // --- Connections ------------------------------------------------------------------------------------------

    /** [country] comes from the region in the device's language settings; it is shown as a flag. */
    fun connected(account: Account, token: String, connection: ClientConnection, country: String? = null) {
        if (country != null) countries[account.id] = country
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
        val bot = botPlayers[accountId]
        if (bot != null) bot.receive(message) else connections[accountId]?.send(message)
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
            is ClientMessage.Berserk -> roomFor(id, message.gameId)?.berserk(id) ?: noGame(id, message.gameId)
            ClientMessage.RequestGame -> {
                activeGameOf(id)?.let { room -> send(id, ServerMessage.Game(room.snapshot())) }
                null
            }
            is ClientMessage.TournamentCommand -> null // handled by the arena manager
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
        deletionListeners.forEach { it(accountId) }
        send(accountId, ServerMessage.AccountDeleted)
        connections.remove(accountId)?.close()
    }

    // --- Matchmaking ------------------------------------------------------------------------------------------

    private fun seek(accountId: String, clock: Clock, rated: Boolean): ServerMessage.Error? {
        if (activeGameOf(accountId)?.isOver == false) return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Finish your game first")
        val rating = storage.rating(accountId, clock.speed).rating.rating.roundToInt()
        val t = now()
        val botAfter = if (bots.enabled) t + bots.waitMs + (if (bots.waitJitterMs > 0) botRandom.nextLong(bots.waitJitterMs) else 0) else Long.MAX_VALUE
        synchronized(seeks) { seeks[accountId] = SeekEntry(accountId, clock, rated, rating, t, botAfter) }
        send(accountId, ServerMessage.Seeking(clock))
        matchSeeks()
        return null
    }

    /**
     * Pairs waiting players with the same time control; the accepted rating gap grows while they wait.
     * Seeks nobody took in time go to computer opponents.
     */
    fun matchSeeks() {
        val pairs = mutableListOf<Pair<SeekEntry, SeekEntry>>()
        val alone = mutableListOf<SeekEntry>()
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
            seeks.values.filter { t >= it.botAfter }.forEach { seek ->
                seeks.remove(seek.accountId)
                alone += seek
            }
        }
        for (seek in alone) {
            val stored = storage.rating(seek.accountId, seek.clock.speed)
            val persona = BotPersona.create(countries[seek.accountId], seek.rating, stored.provisional, botRandom)
            startBotGame(seek.accountId, persona, seek.clock, seek.rated, botWhite = botRandom.nextBoolean())
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

    /** A game against a computer opponent that looks and plays like a person; see [BotPlayer]. */
    fun startBotGame(humanId: String, persona: BotPersona, clock: Clock, rated: Boolean, botWhite: Boolean): GameRoom? {
        val human = playerInfo(storage.findAccount(humanId) ?: return null, clock.speed)
        val room = GameRoom(
            id = newGameId(),
            white = if (botWhite) persona.player else human,
            black = if (botWhite) human else persona.player,
            clock = clock,
            rated = rated,
            scope = scope,
            now = now,
            events = this,
            settings = roomSettings,
            bot = if (botWhite) PlayerColor.WHITE else PlayerColor.BLACK,
        )
        rooms[room.id] = room
        activeGame[humanId] = room.id
        botPlayers.put(persona.id, BotPlayer(persona, room, scope, botEngineContext, botRandom))?.close()
        val start = ServerMessage.Game(room.snapshot())
        send(humanId, start)
        send(persona.id, start)
        if (!isOnline(humanId)) room.setConnected(humanId, false)
        return room
    }

    override fun gameEnded(room: GameRoom, result: GameResult, reason: EndReason): Pair<Int?, Int?> {
        activeGame.remove(room.white.id, room.id)
        activeGame.remove(room.black.id, room.id)
        var diffs: Pair<Int?, Int?> = null to null
        // A tournament game nobody started counts in the table but not for ratings.
        if (room.rated && result != GameResult.ABORTED && reason != EndReason.NO_START) diffs = updateRatings(room, result)
        if (result != GameResult.ABORTED) {
            runCatching {
                storage.saveGame(
                    GameRecord(
                        id = room.id, whiteId = room.white.id, blackId = room.black.id, clock = room.clock,
                        rated = room.rated, moves = room.moveList, result = result, reason = reason,
                        startedAt = room.startedAt, endedAt = now(), tournamentId = room.tournamentId,
                        botId = room.bot?.let { room.player(it).id },
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
        val botColor = room.bot
        if (botColor != null) {
            val bot = botPlayers[room.player(botColor).id] ?: return
            startBotGame(room.player(botColor.opposite()).id, bot.persona, room.clock, room.rated, botWhite = botColor == PlayerColor.BLACK)
            return
        }
        startGame(room.black.id, room.white.id, room.clock, room.rated)
    }

    private fun updateRatings(room: GameRoom, result: GameResult): Pair<Int?, Int?> {
        val speed = room.clock.speed
        val white = if (room.bot == PlayerColor.WHITE) botRating(room.white) else storage.rating(room.white.id, speed)
        val black = if (room.bot == PlayerColor.BLACK) botRating(room.black) else storage.rating(room.black.id, speed)
        val whiteScore = when (result) {
            GameResult.WHITE_WON -> 1.0
            GameResult.BLACK_WON -> 0.0
            else -> 0.5
        }
        val newWhite = glicko.update(white.rating, listOf(GameOutcome(black.rating, whiteScore)))
        val newBlack = glicko.update(black.rating, listOf(GameOutcome(white.rating, 1 - whiteScore)))
        // A computer opponent keeps its shown rating; only the person's rating is stored.
        if (room.bot != PlayerColor.WHITE) storage.saveRating(room.white.id, speed, StoredRating(newWhite, white.games + 1))
        if (room.bot != PlayerColor.BLACK) storage.saveRating(room.black.id, speed, StoredRating(newBlack, black.games + 1))
        return (newWhite.rating - white.rating.rating).roundToInt() to (newBlack.rating - black.rating.rating).roundToInt()
    }

    /** Ended games stay for a few minutes so that players can offer a rematch. */
    private fun cleanupRooms() {
        val t = now()
        val old = rooms.values.filter { it.isOver && t - it.endedAt > ROOM_KEEP_MS }
        for (room in old) {
            rooms.remove(room.id)
            val botId = room.bot?.let { room.player(it).id } ?: continue
            botPlayers[botId]?.takeIf { it.room === room }?.let { player ->
                botPlayers.remove(botId, player)
                player.close()
            }
        }
    }

    /** An established rating for the persona: the person's rating moves as against a real player of that strength. */
    private fun botRating(player: PlayerInfo) = StoredRating(Rating(player.rating.toDouble(), BOT_DEVIATION, 0.06), games = 100)

    private fun playerInfo(account: Account, speed: Speed): PlayerInfo {
        val stored = storage.rating(account.id, speed)
        return PlayerInfo(account.id, account.name, stored.rating.rating.roundToInt(), stored.provisional, countries[account.id])
    }

    private fun welcome(account: Account, token: String): ServerMessage.Welcome {
        val ratings = Speed.entries.map { speed ->
            val r = storage.rating(account.id, speed)
            RatingInfo(speed, r.rating.rating.roundToInt(), r.games, r.provisional)
        }
        val blitz = storage.rating(account.id, Speed.BLITZ)
        return ServerMessage.Welcome(
            PlayerInfo(account.id, account.name, blitz.rating.rating.roundToInt(), blitz.provisional, countries[account.id]),
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
        const val BOT_DEVIATION = 60.0
    }
}
