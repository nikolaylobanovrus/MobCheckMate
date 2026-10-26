package ru.fourvic.chess.ui.puzzles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fourvic.chess.puzzles.PuzzleProgressStore
import ru.fourvic.chess.puzzles.PuzzleSource
import ru.fourvic.chess.shared.chess.ChessGame
import ru.fourvic.chess.shared.chess.ChessMove
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.chess.Promotion
import ru.fourvic.chess.shared.puzzle.AttemptResult
import ru.fourvic.chess.shared.puzzle.Puzzle
import ru.fourvic.chess.shared.puzzle.PuzzleProgress
import ru.fourvic.chess.shared.puzzle.PuzzleRating
import ru.fourvic.chess.shared.puzzle.PuzzleSelector
import ru.fourvic.chess.shared.puzzle.PuzzleSession
import ru.fourvic.chess.ui.game.GameUiState
import kotlin.random.Random

enum class PuzzleMode { TRAINING, DAILY }

enum class PuzzlePhase { LOADING, SOLVING, CORRECT, WRONG, SOLVED, REVEALED }

data class PuzzleUiState(
    val board: GameUiState = GameUiState(),
    val mode: PuzzleMode = PuzzleMode.TRAINING,
    val phase: PuzzlePhase = PuzzlePhase.LOADING,
    val solverColor: PlayerColor = PlayerColor.WHITE,
    val puzzleRating: Int? = null,
    val themes: List<String> = emptyList(),
    val userRating: Int = 1200,
    val ratingChange: Int? = null,
    val streak: Int = 0,
    val dailySolvedToday: Boolean = false,
    val hintSquare: String? = null,
) {
    val finished: Boolean get() = phase == PuzzlePhase.SOLVED || phase == PuzzlePhase.REVEALED
}

/**
 * Puzzle training and the daily puzzle. A failure (wrong move, hint or revealed solution) is recorded at once,
 * so leaving after a mistake does not protect the rating.
 */
class PuzzleViewModel(
    private val source: PuzzleSource,
    private val store: PuzzleProgressStore,
    private val today: () -> Long = { System.currentTimeMillis() / 86_400_000L },
    private val random: Random = Random.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val replyDelayMs: Long = 450,
) : ViewModel() {

    private var selector: PuzzleSelector? = null
    private var session: PuzzleSession? = null
    private var progress: PuzzleProgress = store.load()
    private var recorded = false
    private var pendingPromotion: Pair<String, String>? = null
    private var selected: String? = null
    private var replyJob: Job? = null

    var state by mutableStateOf(PuzzleUiState(userRating = progress.rating.rating.toInt(), streak = progress.currentStreak(today())))
        private set

    init {
        viewModelScope.launch {
            val puzzles = withContext(ioDispatcher) { source.load() }
            selector = PuzzleSelector(puzzles, random)
            startPuzzle(PuzzleMode.TRAINING)
        }
    }

    fun switchMode(mode: PuzzleMode) {
        if (state.phase == PuzzlePhase.LOADING && selector == null) return
        startPuzzle(mode)
    }

    fun next() = startPuzzle(PuzzleMode.TRAINING)

    fun onSquareTapped(square: String) {
        val s = session ?: return
        if (state.finished || s.expectedMove == null || pendingPromotion != null) return
        val current = selected
        val game = s.game
        if (current != null && square in game.legalTargets(current)) {
            if (game.isPromotion(current, square)) {
                pendingPromotion = current to square
                refresh()
            } else {
                attempt(ChessMove(current, square))
            }
            return
        }
        val piece = game.pieceAt(square)
        selected = if (piece != null && piece.color == s.solverColor && square != current) square else null
        refresh()
    }

    fun onPromotionChosen(promotion: Promotion?) {
        val (from, to) = pendingPromotion ?: return
        pendingPromotion = null
        if (promotion == null) {
            selected = null
            refresh()
        } else {
            attempt(ChessMove(from, to, promotion))
        }
    }

    /** Highlights the piece to move. Counts as not solved cleanly. */
    fun hint() {
        val move = session?.expectedMove ?: return
        recordFailureOnce()
        state = state.copy(hintSquare = move.from)
        refresh()
    }

    /** Plays the rest of the solution move by move. */
    fun showSolution() {
        val s = session ?: return
        if (state.finished) return
        recordFailureOnce()
        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            while (!s.isSolved) {
                if (s.pendingReply != null) s.playReply() else s.revealNextMove()
                refresh(phase = if (s.isSolved) PuzzlePhase.REVEALED else PuzzlePhase.SOLVING)
                if (!s.isSolved) delay(replyDelayMs)
            }
        }
    }

    private fun attempt(move: ChessMove) {
        val s = session ?: return
        selected = null
        when (s.attempt(move)) {
            AttemptResult.WRONG -> {
                recordFailureOnce()
                refresh(phase = PuzzlePhase.WRONG)
            }
            AttemptResult.SOLVED -> finishSolved()
            AttemptResult.CORRECT -> {
                refresh(phase = PuzzlePhase.CORRECT)
                replyJob = viewModelScope.launch {
                    delay(replyDelayMs)
                    s.playReply()
                    if (s.isSolved) finishSolved() else refresh(phase = PuzzlePhase.SOLVING)
                }
            }
        }
    }

    private fun finishSolved() {
        val s = session ?: return
        val before = progress.rating.rating
        if (!recorded) {
            progress = PuzzleRating.record(progress, s.puzzle, solvedCleanly = !s.failed)
            recorded = true
        }
        if (state.mode == PuzzleMode.DAILY) progress = PuzzleRating.recordDaily(progress, today())
        store.save(progress)
        val change = if (s.failed) state.ratingChange else (progress.rating.rating - before).toInt()
        refresh(phase = PuzzlePhase.SOLVED, ratingChange = change)
    }

    private fun recordFailureOnce() {
        val s = session ?: return
        if (recorded) return
        val before = progress.rating.rating
        progress = PuzzleRating.record(progress, s.puzzle, solvedCleanly = false)
        recorded = true
        store.save(progress)
        state = state.copy(userRating = progress.rating.rating.toInt(), ratingChange = (progress.rating.rating - before).toInt())
    }

    private fun startPuzzle(mode: PuzzleMode) {
        val chooser = selector ?: return
        replyJob?.cancel()
        val puzzle: Puzzle = when (mode) {
            PuzzleMode.DAILY -> chooser.daily(today())
            PuzzleMode.TRAINING -> chooser.next(progress.rating.rating.toInt(), store.recentIds().toSet())
        } ?: return
        if (mode == PuzzleMode.TRAINING) {
            store.saveRecentIds((store.recentIds() + puzzle.id).takeLast(RECENT_LIMIT))
        }
        session = PuzzleSession(puzzle)
        recorded = false
        selected = null
        pendingPromotion = null
        state = state.copy(mode = mode, ratingChange = null, hintSquare = null)
        refresh(phase = PuzzlePhase.SOLVING)
    }

    private fun refresh(phase: PuzzlePhase = state.phase, ratingChange: Int? = state.ratingChange) {
        val s = session ?: return
        val game: ChessGame = s.game
        val pieces = ChessGame.SQUARES_FROM_WHITE.mapNotNull { sq -> game.pieceAt(sq)?.let { sq to it } }.toMap()
        val checkedKing = if (game.isCheck) {
            pieces.entries.firstOrNull { (_, p) -> p.kind == PieceKind.KING && p.color == game.sideToMove }?.key
        } else {
            null
        }
        val sel = selected
        val hint = if (phase == PuzzlePhase.SOLVING || phase == PuzzlePhase.WRONG) state.hintSquare else null
        state = state.copy(
            board = GameUiState(
                pieces = pieces,
                // A hint highlights the piece to move until the user selects something.
                selected = sel ?: hint,
                targets = if (sel != null) game.legalTargets(sel) else emptySet(),
                lastMove = game.moves.lastOrNull(),
                sideToMove = game.sideToMove,
                status = game.status,
                checkedKing = checkedKing,
                flipped = s.solverColor == PlayerColor.BLACK,
                pendingPromotion = pendingPromotion,
                ply = game.moves.size,
                lastMoveWasCapture = game.lastMoveWasCapture,
            ),
            phase = phase,
            solverColor = s.solverColor,
            puzzleRating = s.puzzle.rating,
            themes = s.puzzle.themes,
            userRating = progress.rating.rating.toInt(),
            ratingChange = ratingChange,
            streak = progress.currentStreak(today()),
            dailySolvedToday = progress.isDailySolved(today()),
            hintSquare = hint,
        )
    }

    private companion object {
        const val RECENT_LIMIT = 500
    }
}
