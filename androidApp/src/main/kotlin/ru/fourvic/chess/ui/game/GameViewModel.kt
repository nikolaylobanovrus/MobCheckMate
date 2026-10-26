package ru.fourvic.chess.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fourvic.chess.shared.chess.BoardPiece
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.chess.Promotion
import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.shared.engine.ChessBot

/** Who plays against the user on this device. */
sealed interface Opponent {
    /** Two people take turns on one device. */
    data object Friend : Opponent

    /** The computer plays the colour the user did not pick. */
    data class Computer(val level: BotLevel, val humanColor: PlayerColor) : Opponent
}

/** Everything the game screen needs to draw one frame. */
data class GameUiState(
    val pieces: Map<String, BoardPiece> = emptyMap(),
    val selected: String? = null,
    val targets: Set<String> = emptySet(),
    val lastMove: ChessMove? = null,
    val sideToMove: PlayerColor = PlayerColor.WHITE,
    val status: GameStatus = GameStatus.ONGOING,
    val checkedKing: String? = null,
    val flipped: Boolean = false,
    val pendingPromotion: Pair<String, String>? = null,
    val canUndo: Boolean = false,
    val opponent: Opponent = Opponent.Friend,
    val botThinking: Boolean = false,
    /** Number of moves played; changes with every move, so the UI can react (sounds). */
    val ply: Int = 0,
    val lastMoveWasCapture: Boolean = false,
)

/**
 * Game on one device: against a friend or against the built-in bot.
 * The bot thinks on [botDispatcher]; the user cannot move while it is the bot's turn.
 */
class GameViewModel(
    private val startFen: String = ChessGame.START_FEN,
    private val bot: ChessBot = ChessBot(),
    private val botDispatcher: CoroutineDispatcher = Dispatchers.Default,
    initialOpponent: Opponent = Opponent.Friend,
) : ViewModel() {

    private var game = ChessGame(startFen)
    private var opponent: Opponent = initialOpponent
    private var botJob: Job? = null

    var state by mutableStateOf(snapshot(flipped = isHumanBlack(initialOpponent)))
        private set

    init {
        startBotIfItsTurn()
    }

    fun onSquareTapped(square: String) {
        val current = state
        if (current.status.isFinished || current.pendingPromotion != null || !isHumanTurn()) return

        val selected = current.selected
        if (selected != null && square in current.targets) {
            if (game.isPromotion(selected, square)) {
                state = current.copy(pendingPromotion = selected to square)
            } else {
                play(ChessMove(selected, square))
            }
            return
        }

        val piece = current.pieces[square]
        state = if (piece != null && piece.color == current.sideToMove && square != selected) {
            current.copy(selected = square, targets = game.legalTargets(square))
        } else {
            current.copy(selected = null, targets = emptySet())
        }
    }

    fun onPromotionChosen(promotion: Promotion?) {
        val (from, to) = state.pendingPromotion ?: return
        if (promotion == null) {
            state = state.copy(pendingPromotion = null, selected = null, targets = emptySet())
        } else {
            play(ChessMove(from, to, promotion))
        }
    }

    /** Takes back the user's last move; against the computer this also takes back the computer's reply. */
    fun undo() {
        cancelBot()
        val computer = opponent as? Opponent.Computer
        if (game.undo() && computer != null && game.sideToMove != computer.humanColor) {
            game.undo()
        }
        refresh()
        startBotIfItsTurn()
    }

    /** Starts over against [newOpponent]; the board turns so the user's pieces are at the bottom. */
    fun newGame(newOpponent: Opponent = opponent) {
        cancelBot()
        opponent = newOpponent
        game = ChessGame(startFen)
        state = snapshot(flipped = if (newOpponent is Opponent.Computer) isHumanBlack(newOpponent) else state.flipped)
        startBotIfItsTurn()
    }

    fun flipBoard() {
        state = state.copy(flipped = !state.flipped)
    }

    private fun play(move: ChessMove) {
        game.play(move)
        refresh()
        startBotIfItsTurn()
    }

    private fun isHumanTurn(): Boolean {
        val computer = opponent as? Opponent.Computer ?: return true
        return !state.botThinking && game.sideToMove == computer.humanColor
    }

    private fun startBotIfItsTurn() {
        val computer = opponent as? Opponent.Computer ?: return
        if (game.status.isFinished || game.sideToMove == computer.humanColor) return
        val position = game.copy()
        state = state.copy(botThinking = true, selected = null, targets = emptySet())
        botJob = viewModelScope.launch {
            val move = withContext(botDispatcher) { bot.chooseMove(position, computer.level) }
            if (move != null) game.play(move)
            refresh()
        }
    }

    private fun cancelBot() {
        botJob?.cancel()
        botJob = null
    }

    private fun refresh() {
        state = snapshot(flipped = state.flipped)
    }

    private fun snapshot(flipped: Boolean): GameUiState {
        val pieces = ChessGame.SQUARES_FROM_WHITE
            .mapNotNull { square -> game.pieceAt(square)?.let { square to it } }
            .toMap()
        val checkedKing = if (game.isCheck) {
            pieces.entries.firstOrNull { (_, p) -> p.kind == PieceKind.KING && p.color == game.sideToMove }?.key
        } else {
            null
        }
        return GameUiState(
            pieces = pieces,
            lastMove = game.moves.lastOrNull(),
            sideToMove = game.sideToMove,
            status = game.status,
            checkedKing = checkedKing,
            flipped = flipped,
            canUndo = canUndo(),
            opponent = opponent,
            botThinking = false,
            ply = game.moves.size,
            lastMoveWasCapture = game.lastMoveWasCapture,
        )
    }

    /** Against the computer there must be a move of the user's to take back. */
    private fun canUndo(): Boolean {
        val computer = opponent as? Opponent.Computer ?: return game.moves.isNotEmpty()
        val userMoves = if (computer.humanColor == PlayerColor.WHITE) {
            (game.moves.size + 1) / 2
        } else {
            game.moves.size / 2
        }
        return userMoves > 0
    }

    private fun isHumanBlack(opponent: Opponent) = (opponent as? Opponent.Computer)?.humanColor == PlayerColor.BLACK
}
