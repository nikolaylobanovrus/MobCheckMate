package ru.fourvic.chess.ui.online

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.online.OnlineClient
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.chess.Promotion
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.GameSnapshot
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.RatingInfo
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.ui.game.GameUiState

enum class OnlinePhase { LOBBY, SEEKING, PLAYING, FINISHED }

data class ChatLine(val mine: Boolean, val phrase: QuickPhrase)

data class OnlineUiState(
    val connection: ConnectionState = ConnectionState.OFFLINE,
    val me: PlayerInfo? = null,
    val ratings: List<RatingInfo> = emptyList(),
    val phase: OnlinePhase = OnlinePhase.LOBBY,
    val rated: Boolean = true,
    val seeking: Clock? = null,
    val seekSeconds: Int = 0,
    val board: GameUiState = GameUiState(),
    val gameId: String? = null,
    val white: PlayerInfo? = null,
    val black: PlayerInfo? = null,
    val clock: Clock? = null,
    val myColor: PlayerColor = PlayerColor.WHITE,
    val whiteMs: Long = 0,
    val blackMs: Long = 0,
    val clockRunning: Boolean = false,
    /** Seconds left for the first move, before the clocks start. */
    val firstMoveSeconds: Int? = null,
    val opponentOffersDraw: Boolean = false,
    val myDrawOffer: Boolean = false,
    /** Seconds until the game is awarded to the user because the opponent left. */
    val opponentAwaySeconds: Int? = null,
    val chat: List<ChatLine> = emptyList(),
    val result: GameResult? = null,
    val reason: EndReason? = null,
    val myRatingDiff: Int? = null,
    val rematchRequested: Boolean = false,
    val opponentWantsRematch: Boolean = false,
    val premove: ChessMove? = null,
    val error: ErrorCode? = null,
    /** Set for arena games: no abort or rematch, berserk instead. */
    val tournamentId: String? = null,
    val berserk: List<PlayerColor> = emptyList(),
) {
    val opponent: PlayerInfo? get() = if (myColor == PlayerColor.WHITE) black else white
    val myTurn: Boolean get() = phase == OnlinePhase.PLAYING && board.sideToMove == myColor
    val canAbort: Boolean get() = phase == OnlinePhase.PLAYING && board.ply < 2 && tournamentId == null

    /** Berserk is possible in an arena game before the user's own first move. */
    val canBerserk: Boolean
        get() = phase == OnlinePhase.PLAYING && tournamentId != null && clock?.berserkAllowed == true &&
            myColor !in berserk && board.ply < (if (myColor == PlayerColor.WHITE) 1 else 2)
    val myMs: Long get() = if (myColor == PlayerColor.WHITE) whiteMs else blackMs
    val opponentMs: Long get() = if (myColor == PlayerColor.WHITE) blackMs else whiteMs
}

/**
 * Online play: lobby, seeking and the game. The user's move is shown at once and confirmed by the server;
 * a rejected move is taken back, and a move lost in a disconnect is sent again after the reconnect.
 *
 * With [arena] set, the view model plays the user's tournament games instead of casual ones.
 */
class OnlineViewModel(
    private val client: OnlineClient,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    tickerEnabled: Boolean = true,
    private val arena: Boolean = false,
) : ViewModel() {

    private var game = ChessGame()
    private var selected: String? = null
    private var pendingPromotion: Pair<String, String>? = null
    private var pendingMove: ClientMessage.Move? = null
    private var premove: ChessMove? = null
    private var syncedAt = 0L
    private var baseWhite = 0L
    private var baseBlack = 0L
    private var firstMoveDeadline: Long? = null
    private var claimDeadline: Long? = null
    private var seekStartedAt: Long? = null

    var state by mutableStateOf(OnlineUiState())
        private set

    init {
        client.start()
        viewModelScope.launch { client.state.collect { state = state.copy(connection = it) } }
        viewModelScope.launch {
            client.welcome.collect { w -> if (w != null) state = state.copy(me = w.player, ratings = w.ratings) }
        }
        viewModelScope.launch { client.messages.collect(::onMessage) }
        if (tickerEnabled) {
            viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    tick()
                }
            }
        }
    }

    // --- Lobby ------------------------------------------------------------------------------------------------

    fun setRated(rated: Boolean) {
        state = state.copy(rated = rated)
    }

    fun seek(clock: Clock) {
        if (client.send(ClientMessage.Seek(clock, state.rated))) {
            seekStartedAt = nowMs()
            state = state.copy(phase = OnlinePhase.SEEKING, seeking = clock, seekSeconds = 0, error = null)
        }
    }

    fun cancelSeek() {
        client.send(ClientMessage.CancelSeek)
        seekStartedAt = null
        state = state.copy(phase = OnlinePhase.LOBBY, seeking = null)
    }

    fun reconnect() = client.start()

    fun backToLobby() {
        selected = null
        premove = null
        pendingMove = null
        state = state.copy(phase = OnlinePhase.LOBBY, gameId = null, result = null, reason = null, chat = emptyList())
    }

    // --- Game -------------------------------------------------------------------------------------------------

    fun onSquareTapped(square: String) {
        if (state.phase != OnlinePhase.PLAYING || pendingPromotion != null) return
        val myTurn = game.sideToMove == state.myColor
        val current = selected
        val targets = if (current == null) emptySet() else targetsFrom(current, myTurn)
        if (current != null && square in targets) {
            val promotion = isPromotionSquare(current, square)
            if (myTurn) {
                if (promotion) {
                    pendingPromotion = current to square
                    refresh()
                } else {
                    makeMove(ChessMove(current, square))
                }
            } else {
                // Premoves promote to a queen; the choice dialog would interrupt the opponent's turn.
                premove = ChessMove(current, square, if (promotion) Promotion.QUEEN else null)
                selected = null
                refresh()
            }
            return
        }
        val piece = game.pieceAt(square)
        premove = null
        selected = if (piece != null && piece.color == state.myColor && square != current) square else null
        refresh()
    }

    fun onPromotionChosen(promotion: Promotion?) {
        val (from, to) = pendingPromotion ?: return
        pendingPromotion = null
        if (promotion == null) {
            selected = null
            refresh()
        } else {
            makeMove(ChessMove(from, to, promotion))
        }
    }

    fun resign() = state.gameId?.let { client.send(ClientMessage.Resign(it)) }
    fun abort() = state.gameId?.let { client.send(ClientMessage.Abort(it)) }

    /** Offers a draw, or accepts the opponent's offer. */
    fun offerDraw() {
        val id = state.gameId ?: return
        if (client.send(ClientMessage.OfferDraw(id))) state = state.copy(myDrawOffer = !state.opponentOffersDraw)
    }

    fun declineDraw() {
        val id = state.gameId ?: return
        client.send(ClientMessage.DeclineDraw(id))
        state = state.copy(opponentOffersDraw = false)
    }

    fun rematch() {
        val id = state.gameId ?: return
        if (client.send(ClientMessage.Rematch(id))) state = state.copy(rematchRequested = true)
    }

    fun berserk() {
        val id = state.gameId ?: return
        if (state.canBerserk) client.send(ClientMessage.Berserk(id))
    }

    fun chat(phrase: QuickPhrase) {
        val id = state.gameId ?: return
        client.send(ClientMessage.Chat(id, phrase))
    }

    private fun makeMove(move: ChessMove) {
        val id = state.gameId ?: return
        val ply = game.moves.size
        if (!game.play(move)) return
        selected = null
        if (state.opponentOffersDraw) state = state.copy(opponentOffersDraw = false) // moving declines the offer
        val message = ClientMessage.Move(id, move.uci, ply)
        pendingMove = message
        client.send(message) // if offline, it is sent again after the reconnect
        refresh()
    }

    // --- Server messages --------------------------------------------------------------------------------------

    internal fun onMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Seeking -> if (!arena) {
                if (seekStartedAt == null) seekStartedAt = nowMs()
                state = state.copy(phase = OnlinePhase.SEEKING, seeking = message.clock)
            }
            ServerMessage.SeekCancelled -> if (!arena) {
                seekStartedAt = null
                if (state.phase == OnlinePhase.SEEKING) state = state.copy(phase = OnlinePhase.LOBBY, seeking = null)
            }
            is ServerMessage.Game -> if ((message.game.tournamentId != null) == arena) applySnapshot(message.game)
            is ServerMessage.Berserked -> if (message.gameId == state.gameId) {
                baseWhite = message.whiteMs
                baseBlack = message.blackMs
                syncedAt = nowMs()
                state = state.copy(berserk = (state.berserk + message.by).distinct())
                tick()
            }
            is ServerMessage.Moved -> if (message.gameId == state.gameId) onMoved(message)
            is ServerMessage.Ended -> if (message.gameId == state.gameId) onEnded(message)
            is ServerMessage.DrawOffered -> if (message.gameId == state.gameId) {
                state = if (message.by == state.myColor) state.copy(myDrawOffer = true) else state.copy(opponentOffersDraw = true)
            }
            is ServerMessage.DrawDeclined -> if (message.gameId == state.gameId) {
                state = state.copy(myDrawOffer = false, opponentOffersDraw = false)
            }
            is ServerMessage.RematchOffered -> if (message.gameId == state.gameId && message.by != state.myColor) {
                state = state.copy(opponentWantsRematch = true)
            }
            is ServerMessage.OpponentConnection -> if (message.gameId == state.gameId) {
                claimDeadline = if (message.connected) null else message.claimInMs?.let { nowMs() + it }
                tick()
            }
            is ServerMessage.Chat -> if (message.gameId == state.gameId) {
                state = state.copy(chat = (state.chat + ChatLine(message.by == state.myColor, message.phrase)).takeLast(4))
            }
            is ServerMessage.Error -> onError(message)
            is ServerMessage.Welcome, is ServerMessage.Pong, ServerMessage.AccountDeleted,
            is ServerMessage.Tournaments, is ServerMessage.Tournament,
            -> Unit
        }
    }

    private fun applySnapshot(s: GameSnapshot) {
        val newGame = s.gameId != state.gameId
        game = ChessGame().also { g -> s.moves.forEach { uci -> ChessMove.fromUci(uci)?.let(g::play) } }
        val me = client.welcome.value?.player?.id ?: state.me?.id
        val myColor = if (s.white.id == me) PlayerColor.WHITE else PlayerColor.BLACK
        baseWhite = s.whiteMs
        baseBlack = s.blackMs
        syncedAt = nowMs()
        firstMoveDeadline = s.firstMoveMs?.let { syncedAt + it }
        seekStartedAt = null
        if (newGame) {
            claimDeadline = null
            premove = null
            selected = null
        }
        state = state.copy(
            phase = if (s.result != null) OnlinePhase.FINISHED else OnlinePhase.PLAYING,
            gameId = s.gameId,
            white = s.white,
            black = s.black,
            clock = s.clock,
            myColor = myColor,
            clockRunning = s.clockRunning,
            seeking = null,
            result = s.result,
            reason = s.reason,
            opponentOffersDraw = s.drawOfferBy != null && s.drawOfferBy != myColor,
            myDrawOffer = s.drawOfferBy == myColor,
            chat = if (newGame) emptyList() else state.chat,
            myRatingDiff = if (newGame) null else state.myRatingDiff,
            rematchRequested = if (newGame) false else state.rematchRequested,
            opponentWantsRematch = if (newGame) false else state.opponentWantsRematch,
            error = null,
            tournamentId = s.tournamentId,
            berserk = s.berserk,
        )
        // A move lost in a disconnect: the server has not seen it, and it is still our turn at that ply.
        val lost = pendingMove
        pendingMove = null
        if (lost != null && lost.gameId == s.gameId && s.result == null && s.moves.size == lost.ply && game.sideToMove == myColor) {
            val move = ChessMove.fromUci(lost.uci)
            if (move != null && game.play(move)) {
                pendingMove = lost
                client.send(lost)
            }
        }
        refresh()
        tick()
    }

    private fun onMoved(m: ServerMessage.Moved) {
        val last = game.moves.lastOrNull()
        when {
            m.ply == game.moves.size && last?.uci == m.uci -> pendingMove = null // our move, confirmed
            m.ply == game.moves.size + 1 -> {
                val move = ChessMove.fromUci(m.uci) ?: return
                if (!game.play(move)) return
                val mover = if (game.sideToMove == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
                // The receiver's move clears an offer; mirror the server.
                if (mover != state.myColor && state.myDrawOffer) state = state.copy(myDrawOffer = false)
            }
            else -> return
        }
        baseWhite = m.whiteMs
        baseBlack = m.blackMs
        syncedAt = nowMs()
        firstMoveDeadline = if (game.moves.size < 2) syncedAt + FIRST_MOVE_MS else null
        state = state.copy(clockRunning = game.moves.size >= 2)
        val queued = premove
        if (queued != null && game.sideToMove == state.myColor) {
            premove = null
            if (game.legalMoves().contains(queued)) makeMove(queued)
        }
        tick()
        refresh()
    }

    private fun onEnded(m: ServerMessage.Ended) {
        tick()
        state = state.copy(
            phase = OnlinePhase.FINISHED,
            result = m.result,
            reason = m.reason,
            myRatingDiff = if (state.myColor == PlayerColor.WHITE) m.whiteRatingDiff else m.blackRatingDiff,
            clockRunning = false,
            opponentOffersDraw = false,
            myDrawOffer = false,
            opponentAwaySeconds = null,
            firstMoveSeconds = null,
        )
        baseWhite = state.whiteMs
        baseBlack = state.blackMs
        claimDeadline = null
        premove = null
        pendingMove = null
        selected = null
        refresh()
    }

    private fun onError(error: ServerMessage.Error) {
        if (error.code == ErrorCode.NO_SUCH_TOURNAMENT) return // for the tournament screen
        val pending = pendingMove
        if (pending != null && error.code in setOf(ErrorCode.ILLEGAL_MOVE, ErrorCode.NOT_YOUR_TURN, ErrorCode.BAD_REQUEST)) {
            if (game.moves.size == pending.ply + 1 && game.moves.last().uci == pending.uci) game.undo()
            pendingMove = null
            refresh()
        }
        if (error.code == ErrorCode.BAD_REQUEST && state.phase == OnlinePhase.SEEKING) {
            state = state.copy(phase = OnlinePhase.LOBBY, seeking = null)
        }
        state = state.copy(error = error.code)
    }

    /** Recomputes the displayed clocks and countdowns. */
    internal fun tick() {
        val t = nowMs()
        val running = state.phase == OnlinePhase.PLAYING && state.clockRunning
        val elapsed = t - syncedAt
        val white = if (running && game.sideToMove == PlayerColor.WHITE) (baseWhite - elapsed).coerceAtLeast(0) else baseWhite
        val black = if (running && game.sideToMove == PlayerColor.BLACK) (baseBlack - elapsed).coerceAtLeast(0) else baseBlack
        state = state.copy(
            whiteMs = white,
            blackMs = black,
            firstMoveSeconds = firstMoveDeadline?.takeIf { state.phase == OnlinePhase.PLAYING && !state.clockRunning }
                ?.let { ((it - t).coerceAtLeast(0) + 999) / 1000 }?.toInt(),
            opponentAwaySeconds = claimDeadline?.let { ((it - t).coerceAtLeast(0) + 999) / 1000 }?.toInt(),
            seekSeconds = seekStartedAt?.let { ((t - it) / 1000).toInt() } ?: 0,
        )
    }

    private fun refresh() {
        val pieces = ChessGame.SQUARES_FROM_WHITE.mapNotNull { sq -> game.pieceAt(sq)?.let { sq to it } }.toMap()
        val checkedKing = if (game.isCheck) {
            pieces.entries.firstOrNull { (_, p) -> p.kind == PieceKind.KING && p.color == game.sideToMove }?.key
        } else {
            null
        }
        val sel = selected
        val myTurn = game.sideToMove == state.myColor
        val pre = premove
        state = state.copy(
            board = GameUiState(
                pieces = pieces,
                selected = sel ?: pre?.from,
                targets = if (sel != null) targetsFrom(sel, myTurn) else emptySet(),
                // A queued premove is shown like a last move so the user sees what will be played.
                lastMove = pre ?: game.moves.lastOrNull(),
                sideToMove = game.sideToMove,
                status = game.status,
                checkedKing = checkedKing,
                flipped = state.myColor == PlayerColor.BLACK,
                pendingPromotion = pendingPromotion,
                ply = game.moves.size,
                lastMoveWasCapture = game.lastMoveWasCapture,
            ),
            premove = pre,
        )
    }

    /** Legal targets on our turn; while the opponent thinks, targets as if it were our move (for premoves). */
    private fun targetsFrom(square: String, myTurn: Boolean): Set<String> {
        if (myTurn) return game.legalTargets(square)
        val parts = game.fen.split(' ').toMutableList()
        if (parts.size < 4) return emptySet()
        parts[1] = if (state.myColor == PlayerColor.WHITE) "w" else "b"
        parts[3] = "-"
        return runCatching { ChessGame(parts.joinToString(" ")).legalTargets(square) }.getOrDefault(emptySet())
    }

    private fun isPromotionSquare(from: String, to: String): Boolean {
        val piece = game.pieceAt(from) ?: return false
        return piece.kind == PieceKind.PAWN && (to.endsWith("8") || to.endsWith("1"))
    }

    override fun onCleared() {
        if (state.phase == OnlinePhase.SEEKING) client.send(ClientMessage.CancelSeek)
    }

    private companion object {
        const val TICK_MS = 200L
        const val FIRST_MOVE_MS = 30_000L
    }
}
