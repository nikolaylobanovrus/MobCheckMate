package ru.fourvic.chess.server.bots

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fourvic.chess.server.game.GameRoom
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.engine.ChessBot
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.ServerMessage
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.TimeSource

/** When the server falls back to a computer opponent. */
data class BotSettings(
    val enabled: Boolean = true,
    /** A seek waits this long for a person before a computer opponent takes it... */
    val waitMs: Long = 8_000,
    /** ...plus up to this much, so the wait does not look the same every time. */
    val waitJitterMs: Long = 6_000,
)

/**
 * Plays one side of one game the way a person would: it thinks for a time that depends on its clock, greets and
 * congratulates now and then, accepts draws in level positions late in the game, resigns hopeless positions and
 * sometimes takes or offers a rematch.
 *
 * The room sends messages while holding its lock, so they are queued and handled one by one on the bot's own
 * coroutine; every call back into the room happens from there or from a delayed job, never inside the lock.
 */
class BotPlayer(
    val persona: BotPersona,
    val room: GameRoom,
    private val scope: CoroutineScope,
    private val engineContext: CoroutineContext,
    private val random: Random,
) {
    private val engine = ChessBot(random)
    /** Guards [game]: the worker updates it, draw answers read it from another coroutine. */
    private val lock = Any()
    private val inbox = Channel<ServerMessage>(Channel.UNLIMITED)
    private val color: PlayerColor = if (room.white.id == persona.id) PlayerColor.WHITE else PlayerColor.BLACK
    private val incrementMs = room.clock.incrementSeconds * 1000L
    private var game = ChessGame()
    private var myMs = room.clock.initialSeconds * 1000L
    private var moveJob: Job? = null
    private var hopelessMoves = 0
    private var greeted = false
    private val worker = scope.launch { for (message in inbox) handle(message) }

    fun receive(message: ServerMessage) {
        inbox.trySend(message)
    }

    fun close() {
        moveJob?.cancel()
        inbox.close()
        worker.cancel()
    }

    private fun handle(message: ServerMessage) {
        when (message) {
            is ServerMessage.Game -> if (message.game.gameId == room.id) sync(message.game)
            is ServerMessage.Moved -> if (message.gameId == room.id) {
                synchronized(lock) {
                    if (message.ply == game.moves.size + 1) ChessMove.fromUci(message.uci)?.let(game::play)
                }
                myMs = if (color == PlayerColor.WHITE) message.whiteMs else message.blackMs
                maybeMove()
            }
            is ServerMessage.DrawOffered -> if (message.gameId == room.id && message.by != color) later(1_000, 3_500) { answerDraw() }
            is ServerMessage.RematchOffered -> if (message.gameId == room.id && message.by != color && random.nextDouble() < 0.65) {
                later(2_000, 5_000) { room.rematch(persona.id) }
            }
            is ServerMessage.Chat -> if (message.gameId == room.id && message.by != color) reply(message.phrase)
            is ServerMessage.Ended -> if (message.gameId == room.id) {
                moveJob?.cancel()
                afterGame(message.result)
            }
            else -> Unit
        }
    }

    private fun sync(snapshot: GameSnapshot) {
        val restored = ChessGame().also { g -> snapshot.moves.forEach { uci -> ChessMove.fromUci(uci)?.let(g::play) } }
        synchronized(lock) { game = restored }
        myMs = if (color == PlayerColor.WHITE) snapshot.whiteMs else snapshot.blackMs
        if (!greeted) {
            greeted = true
            if (random.nextDouble() < 0.3) later(1_500, 4_000) { chat(listOf(QuickPhrase.HELLO, QuickPhrase.GOOD_LUCK).random(random)) }
        }
        maybeMove()
    }

    private fun maybeMove() {
        val position = synchronized(lock) { game.copy() }
        if (position.status.isFinished || position.sideToMove != color) return
        val ply = position.moves.size
        val clockMs = myMs
        moveJob?.cancel()
        moveJob = scope.launch {
            val started = TimeSource.Monotonic.markNow()
            if (wantsToResign(position)) {
                delay(random.nextLong(1_500, 4_000))
                room.resign(persona.id)
                return@launch
            }
            val move = withContext(engineContext) { engine.chooseMove(position, persona.level) } ?: return@launch
            val think = BotTiming.thinkMs(ply, clockMs, incrementMs, position.legalMoves().size, random)
            delay((think - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
            room.move(persona.id, move.uci, ply)
        }
    }

    /** People resign clearly lost positions instead of playing them out, though not always at once. */
    private suspend fun wantsToResign(position: ChessGame): Boolean {
        if (position.moves.size < MIN_PLIES_TO_RESIGN) return false
        val analysis = withContext(engineContext) { engine.analyze(position, maxDepth = 4, timeMillis = 200) }
        val lost = (analysis.mateIn ?: 0) < 0 || analysis.scoreCp < -RESIGN_BELOW_CP
        hopelessMoves = if (lost) hopelessMoves + 1 else 0
        return hopelessMoves >= 3 && random.nextDouble() < 0.5
    }

    /** Accepts a draw late in a game where it is not better; declines otherwise. */
    private suspend fun answerDraw() {
        val position = synchronized(lock) { game.copy() }
        if (position.status.isFinished) return
        val analysis = withContext(engineContext) { engine.analyze(position, maxDepth = 4, timeMillis = 300) }
        val forMe = if (position.sideToMove == color) analysis.scoreCp else -analysis.scoreCp
        if (position.moves.size >= MIN_PLIES_FOR_DRAW && forMe <= DRAW_IF_NOT_BETTER_CP) {
            room.offerDraw(persona.id)
        } else {
            room.declineDraw(persona.id)
        }
    }

    private fun afterGame(result: GameResult) {
        if (result == GameResult.ABORTED) return
        if (random.nextDouble() < 0.35) later(1_000, 3_000) { chat(listOf(QuickPhrase.GOOD_GAME, QuickPhrase.THANKS).random(random)) }
        val lost = (result == GameResult.WHITE_WON && color == PlayerColor.BLACK) ||
            (result == GameResult.BLACK_WON && color == PlayerColor.WHITE)
        if (lost && random.nextDouble() < 0.25) later(3_000, 6_000) { room.rematch(persona.id) }
    }

    private fun reply(phrase: QuickPhrase) {
        if (random.nextDouble() >= 0.5) return
        val answer = when (phrase) {
            QuickPhrase.HELLO -> QuickPhrase.HELLO
            QuickPhrase.GOOD_LUCK, QuickPhrase.HAVE_FUN -> listOf(QuickPhrase.THANKS, QuickPhrase.GOOD_LUCK).random(random)
            QuickPhrase.GOOD_GAME -> listOf(QuickPhrase.GOOD_GAME, QuickPhrase.THANKS).random(random)
            QuickPhrase.NICE_MOVE -> QuickPhrase.THANKS
            QuickPhrase.BYE -> QuickPhrase.BYE
            QuickPhrase.OOPS, QuickPhrase.THANKS -> return
        }
        later(1_200, 3_500) { chat(answer) }
    }

    private fun chat(phrase: QuickPhrase) {
        room.chat(persona.id, phrase)
    }

    private fun later(fromMs: Long, untilMs: Long, action: suspend () -> Unit) {
        scope.launch {
            delay(random.nextLong(fromMs, untilMs))
            action()
        }
    }

    private companion object {
        const val MIN_PLIES_TO_RESIGN = 30
        const val RESIGN_BELOW_CP = 800
        const val MIN_PLIES_FOR_DRAW = 30
        const val DRAW_IF_NOT_BETTER_CP = 30
    }
}

/** How long a person would think about a move. */
internal object BotTiming {
    fun thinkMs(ply: Int, clockMs: Long, incrementMs: Long, legalMoves: Int, random: Random): Long {
        if (ply < 2) return random.nextLong(800, 3_500)
        if (legalMoves == 1) return random.nextLong(250, 900)
        val fullMoves = ply / 2
        val movesLeft = maxOf(12, 40 - fullMoves / 2)
        val budget = clockMs / movesLeft + incrementMs * 3 / 4
        val phase = if (fullMoves < 8) 0.4 else 1.0 // the opening is played from memory
        val think = (budget * phase * random.nextDouble(0.35, 1.7)).toLong()
        // Never more than a sixth of the time left: short of time, people play fast too.
        return think.coerceIn(MIN_THINK_MS, maxOf(MIN_THINK_MS, clockMs / 6))
    }

    private const val MIN_THINK_MS = 300L
}
