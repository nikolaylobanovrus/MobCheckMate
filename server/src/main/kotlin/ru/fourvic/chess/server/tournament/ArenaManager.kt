package ru.fourvic.chess.server.tournament

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.fourvic.chess.server.game.GameEndListener
import ru.fourvic.chess.server.game.GameRoom
import ru.fourvic.chess.server.game.GameServer
import ru.fourvic.chess.server.game.opposite
import ru.fourvic.chess.server.storage.Account
import ru.fourvic.chess.server.storage.Storage
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Standing
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus
import ru.fourvic.chess.shared.tournament.ArenaGame
import ru.fourvic.chess.shared.tournament.ArenaOutcome
import ru.fourvic.chess.shared.tournament.ArenaPlayerState
import ru.fourvic.chess.shared.tournament.ArenaRules
import ru.fourvic.chess.shared.tournament.ArenaScoring
import kotlin.math.roundToInt
import kotlin.random.Random

/** A repeating arena. It starts every [periodMinutes], shifted by [offsetMinutes] from midnight UTC. */
data class ScheduledArena(
    val key: String,
    val name: String,
    val clock: Clock,
    val minutes: Int,
    val periodMinutes: Int,
    val offsetMinutes: Int,
) {
    companion object {
        /** docs/TOURNAMENTS.md, section 2: hourly bullet and blitz; a daily blitz and rapid in the Moscow evening. */
        val DEFAULT = listOf(
            ScheduledArena("hourly_bullet", "Hourly Bullet", Clock(60, 0), minutes = 30, periodMinutes = 60, offsetMinutes = 0),
            ScheduledArena("hourly_blitz", "Hourly Blitz", Clock(180, 0), minutes = 30, periodMinutes = 60, offsetMinutes = 30),
            ScheduledArena("daily_blitz", "Daily Blitz", Clock(180, 2), minutes = 60, periodMinutes = 1440, offsetMinutes = 16 * 60),
            ScheduledArena("daily_rapid", "Daily Rapid", Clock(600, 0), minutes = 60, periodMinutes = 1440, offsetMinutes = 17 * 60 + 30),
        )
    }
}

data class ArenaSettings(
    val schedule: List<ScheduledArena> = ScheduledArena.DEFAULT,
    /** Scheduled arenas appear in the list this long before they start. */
    val planAheadMs: Long = 2 * 3_600_000L,
    val keepFinishedMs: Long = 2 * 3_600_000L,
    val tickMs: Long = 1_000,
    val pairingIntervalMs: Long = 2_000,
    /** After a game the player sees the result for a moment before the next pairing. */
    val restMs: Long = 4_000,
    /** Two players left alone may meet again after both have waited this long. */
    val repeatAfterMs: Long = 10_000,
    /** No pairings so close to the end: games still running at the end do not count. */
    val noPairingBeforeEndMs: Long = 20_000,
    /** Table updates are sent at most this often. */
    val updateIntervalMs: Long = 2_000,
    val standingsShown: Int = 30,
    val maxActivePrivatePerCreator: Int = 3,
)

/**
 * Arena tournaments (docs/TOURNAMENTS.md, section 3): scheduled arenas and private ones joined by code. Players join
 * and pause at any time; waiting players are paired every couple of seconds; results are scored with
 * [ArenaScoring]. Tournaments live in memory; their games are stored like any other game, with the tournament id.
 *
 * All state is guarded by one lock. Calls into the game server that may start games happen outside it.
 */
class ArenaManager(
    private val games: GameServer,
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val settings: ArenaSettings = ArenaSettings(),
    private val random: Random = Random.Default,
) : GameEndListener {
    private val log = LoggerFactory.getLogger(ArenaManager::class.java)
    private val lock = Any()
    private val arenas = LinkedHashMap<String, Arena>()
    private val watchers = HashMap<String, String>()

    private class Entrant(val id: String, var name: String, var rating: Int, var provisional: Boolean, val joinedAt: Long) {
        var state = ArenaPlayerState()
        val sheet = mutableListOf<Int>()
        var paused = false
        var pauses = 0
        var availableAt = 0L
        var gameId: String? = null
        var lastOpponent: String? = null
        var colorBalance = 0
        var lastColor: PlayerColor? = null
        private var opponentRatings = 0L
        private var rated = 0
        private var decisive = 0

        /** Performance rating: average opponent rating, plus 400 per win and minus 400 per loss. */
        val performance: Int get() = if (rated == 0) 0 else ((opponentRatings + 400L * decisive) / rated).toInt()

        fun countPerformance(opponentRating: Int, outcome: ArenaOutcome) {
            opponentRatings += opponentRating
            rated++
            decisive += when (outcome) {
                ArenaOutcome.WIN -> 1
                ArenaOutcome.LOSS -> -1
                ArenaOutcome.DRAW -> 0
            }
        }
    }

    private class Arena(
        val id: String,
        val name: String,
        val clock: Clock,
        val minutes: Int,
        val startsAt: Long,
        val rated: Boolean,
        val private: Boolean,
        val schedule: String?,
        val createdBy: String?,
    ) {
        val endsAt = startsAt + minutes * 60_000L
        var status = TournamentStatus.CREATED
        val entrants = LinkedHashMap<String, Entrant>()
        var dirty = true
        var lastPushAt = 0L
        var lastPairingAt = 0L

        /** Score, then the tie-breaks of docs/TOURNAMENTS.md 3.4: performance, longest streak, wins. */
        fun ranked(): List<Entrant> = entrants.values.sortedWith(
            compareByDescending<Entrant> { it.state.score }
                .thenByDescending { it.performance }
                .thenByDescending { it.state.longestWinStreak }
                .thenByDescending { it.state.wins }
                .thenByDescending { it.rating }
                .thenBy { it.joinedAt },
        )
    }

    init {
        games.addGameEndListener(this)
        games.addAccountDeletedListener(::accountDeleted)
        synchronized(lock) { plan(now()) }
        scope.launch {
            while (isActive) {
                delay(settings.tickMs)
                runCatching { tick() }.onFailure { log.error("Arena tick failed", it) }
            }
        }
    }

    fun handle(account: Account, command: ClientMessage.TournamentCommand) {
        val id = account.id
        val error = when (command) {
            ClientMessage.ListTournaments -> {
                games.send(id, list(id))
                null
            }
            is ClientMessage.WatchTournament -> watch(id, command.id)
            is ClientMessage.JoinTournament -> join(account, command.id)
            is ClientMessage.PauseTournament -> pause(id, command.id)
            is ClientMessage.CreateTournament -> create(account, command)
        }
        error?.let { games.send(id, it) }
    }

    // --- Commands ---------------------------------------------------------------------------------------------

    private fun list(accountId: String): ServerMessage.Tournaments = synchronized(lock) {
        val t = now()
        val visible = arenas.values.filter { !it.private || it.createdBy == accountId || accountId in it.entrants }
        // Running first, then upcoming by start, then the latest finished.
        val order = compareBy<Arena> { LIST_ORDER.indexOf(it.status) }
            .thenBy { if (it.status == TournamentStatus.FINISHED) -it.endsAt else it.startsAt }
        ServerMessage.Tournaments(visible.sortedWith(order).map { it.info(t) })
    }

    private fun watch(accountId: String, tournamentId: String?): ServerMessage.Error? = synchronized(lock) {
        if (tournamentId == null) {
            watchers.remove(accountId)
            return null
        }
        val arena = find(tournamentId) ?: return noSuchTournament()
        watchers[accountId] = arena.id
        games.send(accountId, stateFor(arena, accountId, now()))
        null
    }

    private fun join(account: Account, tournamentId: String): ServerMessage.Error? = synchronized(lock) {
        val arena = find(tournamentId) ?: return noSuchTournament()
        if (arena.status == TournamentStatus.FINISHED) return ServerMessage.Error(ErrorCode.BAD_REQUEST, "The tournament is over")
        val t = now()
        val entrant = arena.entrants[account.id]
        if (entrant == null) {
            if (arena.private && arena.entrants.size >= ArenaRules.MAX_PRIVATE_PLAYERS) {
                return ServerMessage.Error(ErrorCode.BAD_REQUEST, "The tournament is full")
            }
            val rating = storage.rating(account.id, arena.clock.speed)
            arena.entrants[account.id] = Entrant(account.id, account.name, rating.rating.rating.roundToInt(), rating.provisional, t)
                .also { it.availableAt = t }
        } else if (entrant.paused) {
            // Every pause delays the next pairing a little more: 10, 20, 40, 80, then 120 seconds.
            entrant.paused = false
            val penalty = minOf(120_000L, 10_000L shl (entrant.pauses - 1).coerceIn(0, 4))
            entrant.availableAt = maxOf(entrant.availableAt, t + penalty)
        }
        watchers[account.id] = arena.id
        arena.dirty = true
        games.send(account.id, stateFor(arena, account.id, t))
        null
    }

    private fun pause(accountId: String, tournamentId: String): ServerMessage.Error? = synchronized(lock) {
        val arena = find(tournamentId) ?: return noSuchTournament()
        val entrant = arena.entrants[accountId] ?: return null
        if (!entrant.paused) {
            entrant.paused = true
            entrant.pauses++
            arena.dirty = true
        }
        games.send(accountId, stateFor(arena, accountId, now()))
        null
    }

    private fun create(account: Account, command: ClientMessage.CreateTournament): ServerMessage.Error? {
        val name = command.name.trim()
        if (!NAME.matches(name)) return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Name must be 3-30 letters, digits or spaces")
        if (!ArenaRules.allows(command.clock, command.minutes)) {
            return ServerMessage.Error(ErrorCode.BAD_REQUEST, "This duration does not fit three games with ${command.clock.label}")
        }
        if (command.startsInMinutes !in 0..60) return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Start within an hour")
        val code = synchronized(lock) {
            val active = arenas.values.count { it.createdBy == account.id && it.status != TournamentStatus.FINISHED }
            if (active >= settings.maxActivePrivatePerCreator) {
                return ServerMessage.Error(ErrorCode.BAD_REQUEST, "Too many tournaments of yours are still running")
            }
            val code = generateSequence { newCode() }.first { it !in arenas }
            arenas[code] = Arena(
                id = code, name = name, clock = command.clock, minutes = command.minutes,
                startsAt = now() + command.startsInMinutes * 60_000L, rated = command.rated,
                private = true, schedule = null, createdBy = account.id,
            )
            code
        }
        return join(account, code)
    }

    private fun find(id: String): Arena? = arenas[id.trim()] ?: arenas[id.trim().uppercase()]

    private fun noSuchTournament() = ServerMessage.Error(ErrorCode.NO_SUCH_TOURNAMENT, "No such tournament")

    private fun accountDeleted(accountId: String) = synchronized(lock) {
        watchers.remove(accountId)
        arenas.values.forEach { arena -> if (arena.entrants.remove(accountId) != null) arena.dirty = true }
    }

    // --- Time ------------------------------------------------------------------------------------------------

    /** Advances all arenas: plans, starts and finishes them, pairs waiting players and sends table updates. */
    fun tick() {
        val pairings = mutableListOf<Triple<Arena, String, String>>()
        synchronized(lock) {
            val t = now()
            plan(t)
            arenas.values.removeIf { it.status == TournamentStatus.FINISHED && t - it.endsAt > settings.keepFinishedMs }
            for (arena in arenas.values) {
                if (arena.status == TournamentStatus.CREATED && t >= arena.startsAt) {
                    arena.status = TournamentStatus.STARTED
                    arena.dirty = true
                }
                if (arena.status == TournamentStatus.STARTED && t >= arena.endsAt) {
                    arena.status = TournamentStatus.FINISHED
                    arena.dirty = true
                    log.info("Arena {} finished with {} players", arena.id, arena.entrants.size)
                }
                if (arena.status == TournamentStatus.STARTED && t - arena.lastPairingAt >= settings.pairingIntervalMs &&
                    arena.endsAt - t > settings.noPairingBeforeEndMs
                ) {
                    arena.lastPairingAt = t
                    for ((white, black) in pairable(arena, t)) {
                        arena.entrants.getValue(white).gameId = STARTING
                        arena.entrants.getValue(black).gameId = STARTING
                        pairings += Triple(arena, white, black)
                    }
                }
            }
        }
        for ((arena, white, black) in pairings) {
            val room = games.startGame(white, black, arena.clock, arena.rated, tournamentId = arena.id)
            synchronized(lock) {
                for (id in listOf(white, black)) {
                    val entrant = arena.entrants[id] ?: continue
                    if (entrant.gameId == STARTING) entrant.gameId = room?.id
                }
            }
        }
        synchronized(lock) { pushUpdates(now()) }
    }

    /** Creates the scheduled arenas that end in the future and start within [ArenaSettings.planAheadMs]. */
    private fun plan(t: Long) {
        for (schedule in settings.schedule) {
            val periodMs = schedule.periodMinutes * 60_000L
            val offsetMs = schedule.offsetMinutes * 60_000L
            var start = Math.floorDiv(t - offsetMs, periodMs) * periodMs + offsetMs
            while (start + schedule.minutes * 60_000L > t) start -= periodMs
            start += periodMs
            while (start <= t + settings.planAheadMs) {
                val id = "${schedule.key}-${start / 60_000}"
                if (id !in arenas) {
                    arenas[id] = Arena(
                        id = id, name = schedule.name, clock = schedule.clock, minutes = schedule.minutes, startsAt = start,
                        rated = true, private = false, schedule = schedule.key, createdBy = null,
                    )
                }
                start += periodMs
            }
        }
    }

    private fun pairable(arena: Arena, t: Long): List<Pair<String, String>> {
        val pool = arena.entrants.values.filter { e ->
            !e.paused && e.gameId == null && t >= e.availableAt && games.isOnline(e.id) &&
                games.activeGameOf(e.id)?.isOver != false
        }.map { e ->
            PoolEntry(e.id, e.state.score, e.rating, e.lastOpponent, e.colorBalance, e.lastColor, waitedMs = t - e.availableAt)
        }
        return ArenaPairing.pair(pool, settings.repeatAfterMs, random)
    }

    // --- Results ---------------------------------------------------------------------------------------------

    override fun gameEnded(room: GameRoom, result: GameResult, reason: EndReason) {
        val tournamentId = room.tournamentId ?: return
        synchronized(lock) {
            val arena = arenas[tournamentId] ?: return
            val t = now()
            val counts = arena.status == TournamentStatus.STARTED && t <= arena.endsAt && result != GameResult.ABORTED
            for (color in PlayerColor.entries) {
                val player = room.player(color)
                val opponent = room.player(color.opposite())
                val entrant = arena.entrants[player.id] ?: continue
                if (entrant.gameId == room.id) entrant.gameId = null
                entrant.availableAt = t + settings.restMs
                entrant.lastOpponent = opponent.id
                entrant.colorBalance += if (color == PlayerColor.WHITE) 1 else -1
                entrant.lastColor = color
                val stored = storage.rating(player.id, arena.clock.speed)
                entrant.rating = stored.rating.rating.roundToInt()
                entrant.provisional = stored.provisional
                if (!counts) continue

                val won = if (color == PlayerColor.WHITE) GameResult.WHITE_WON else GameResult.BLACK_WON
                val outcome = when (result) {
                    GameResult.DRAW -> ArenaOutcome.DRAW
                    won -> ArenaOutcome.WIN
                    else -> ArenaOutcome.LOSS
                }
                if (reason == EndReason.NO_START) {
                    // The player who did not start loses and is paused; the opponent gets nothing, so this cannot farm points.
                    if (outcome == ArenaOutcome.LOSS) {
                        entrant.state = ArenaScoring.score(entrant.state, ArenaGame(outcome, plies = 0)).state
                        entrant.sheet += 0
                        entrant.paused = true
                        entrant.pauses++
                    }
                    continue
                }
                val scored = ArenaScoring.score(entrant.state, ArenaGame(outcome, room.moveList.size, berserk = color in room.berserked))
                entrant.state = scored.state
                entrant.sheet += scored.points
                entrant.countPerformance(opponent.rating, outcome)
            }
            arena.dirty = true
        }
    }

    // --- Messages --------------------------------------------------------------------------------------------

    private fun pushUpdates(t: Long) {
        val byArena = watchers.entries.groupBy({ it.value }, { it.key })
        for (arena in arenas.values) {
            if (!arena.dirty || t - arena.lastPushAt < settings.updateIntervalMs) continue
            arena.dirty = false
            arena.lastPushAt = t
            byArena[arena.id]?.forEach { accountId -> games.send(accountId, stateFor(arena, accountId, t)) }
        }
    }

    private fun stateFor(arena: Arena, accountId: String, t: Long): ServerMessage.Tournament {
        val lines = arena.ranked().mapIndexed { index, e ->
            Standing(
                rank = index + 1,
                player = PlayerInfo(e.id, e.name, e.rating, e.provisional, games.countryOf(e.id)),
                score = e.state.score,
                games = e.state.games,
                fire = e.state.onFire,
                paused = e.paused,
                sheet = e.sheet.takeLast(SHEET_LENGTH),
            )
        }
        return ServerMessage.Tournament(arena.info(t), lines.take(settings.standingsShown), lines.firstOrNull { it.player.id == accountId })
    }

    private fun Arena.info(t: Long) = TournamentInfo(
        id = id,
        name = name,
        clock = clock,
        minutes = minutes,
        status = status,
        msLeft = when (status) {
            TournamentStatus.CREATED -> startsAt - t
            TournamentStatus.STARTED -> endsAt - t
            TournamentStatus.FINISHED -> 0
        }.coerceAtLeast(0),
        players = entrants.size,
        rated = rated,
        private = private,
        schedule = schedule,
        winner = if (status == TournamentStatus.FINISHED) ranked().firstOrNull { it.state.games > 0 }?.name else null,
    )

    private fun newCode(): String = (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    private companion object {
        const val STARTING = "starting"
        const val SHEET_LENGTH = 12
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val LIST_ORDER = listOf(TournamentStatus.STARTED, TournamentStatus.CREATED, TournamentStatus.FINISHED)
        val NAME = Regex("^[\\p{L}\\p{N} _\\-'.!#]{3,30}$")
    }
}
