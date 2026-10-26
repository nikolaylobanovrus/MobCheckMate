package com.mobcheckmate.chess.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mobcheckmate.shared.chess.BoardPiece
import com.mobcheckmate.shared.chess.ChessGame
import com.mobcheckmate.shared.chess.ChessMove
import com.mobcheckmate.shared.chess.GameStatus
import com.mobcheckmate.shared.chess.PieceKind
import com.mobcheckmate.shared.chess.PlayerColor
import com.mobcheckmate.shared.chess.Promotion

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
)

/** Local game on one device (pass and play). Online play replaces this source of moves later. */
class GameViewModel(private val startFen: String = ChessGame.START_FEN) : ViewModel() {

    private var game = ChessGame(startFen)

    var state by mutableStateOf(snapshot())
        private set

    fun onSquareTapped(square: String) {
        val current = state
        if (current.status.isFinished || current.pendingPromotion != null) return

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

    fun undo() {
        if (game.undo()) state = snapshot(flipped = state.flipped)
    }

    fun newGame() {
        game = ChessGame(startFen)
        state = snapshot(flipped = state.flipped)
    }

    fun flipBoard() {
        state = state.copy(flipped = !state.flipped)
    }

    private fun play(move: ChessMove) {
        game.play(move)
        state = snapshot(flipped = state.flipped)
    }

    private fun snapshot(flipped: Boolean = false): GameUiState {
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
            canUndo = game.moves.isNotEmpty(),
        )
    }
}
