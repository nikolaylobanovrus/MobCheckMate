package ru.fourvic.chess.ui.puzzles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.R
import ru.fourvic.chess.audio.MoveSounds
import ru.fourvic.chess.shared.chess.BoardPiece
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.ui.board.ChessBoard
import ru.fourvic.chess.ui.common.ActionButton
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.AppTopBar
import ru.fourvic.chess.ui.common.Pill
import ru.fourvic.chess.ui.common.SegmentedControl
import ru.fourvic.chess.ui.common.TabHeader
import ru.fourvic.chess.ui.game.ActionBar
import ru.fourvic.chess.ui.game.GameLayout
import ru.fourvic.chess.ui.game.PieceTile
import ru.fourvic.chess.ui.game.PromotionDialog
import ru.fourvic.chess.ui.game.soundFor
import ru.fourvic.chess.ui.theme.LocalBrand

/** Puzzles: training at the user's level and the daily puzzle. As a tab it has no back button and leaves [bottomSpace]. */
@Composable
fun PuzzleScreen(viewModel: PuzzleViewModel, onBack: (() -> Unit)?, bottomSpace: Dp = 0.dp) {
    val state = viewModel.state
    MoveSounds(key = state.board.ply to state.board.pieces.hashCode(), sound = soundFor(state.board))
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        val trailing = @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.streak > 0) {
                    Pill(state.streak.toString(), icon = R.drawable.ic_local_fire_department_filled, container = scheme.primaryContainer, content = scheme.onPrimaryContainer)
                }
                Pill(
                    state.userRating.toString() + (state.ratingChange?.let { if (it >= 0) " +$it" else " $it" } ?: ""),
                    icon = R.drawable.ic_extension_filled,
                )
            }
        }
        if (onBack != null) {
            AppTopBar(stringResource(R.string.home_puzzles), onBack) { trailing() }
        } else {
            TabHeader(stringResource(R.string.home_puzzles)) { trailing() }
        }
        SegmentedControl(
            items = listOf(stringResource(R.string.puzzle_training), stringResource(R.string.puzzle_daily)),
            icons = listOf(R.drawable.ic_extension, R.drawable.ic_calendar_today),
            selected = if (state.mode == PuzzleMode.TRAINING) 0 else 1,
            onSelect = { viewModel.switchMode(if (it == 0) PuzzleMode.TRAINING else PuzzleMode.DAILY) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 620.dp).align(Alignment.CenterHorizontally),
        )
        if (state.phase == PuzzlePhase.LOADING) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        GameLayout(
            modifier = Modifier.padding(bottom = bottomSpace),
            top = { Feedback(state) },
            board = { modifier ->
                ChessBoard(
                    state = state.board,
                    onSquareTapped = viewModel::onSquareTapped,
                    contentDescription = stringResource(R.string.board_description),
                    modifier = modifier,
                    movableColor = state.solverColor.takeIf { !state.finished },
                )
            },
            bottom = {},
            actions = {
                ActionBar {
                    if (state.finished) {
                        ActionButton(R.drawable.ic_skip_next, stringResource(R.string.puzzle_next), viewModel::next, highlighted = true)
                    } else {
                        ActionButton(R.drawable.ic_lightbulb, stringResource(R.string.puzzle_hint), viewModel::hint)
                        ActionButton(R.drawable.ic_visibility, stringResource(R.string.puzzle_solution), viewModel::showSolution)
                        ActionButton(R.drawable.ic_skip_next, stringResource(R.string.puzzle_skip), viewModel::next)
                    }
                }
            },
        ) {
            if (state.finished) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.puzzle_rating_label, state.puzzleRating ?: 0), icon = R.drawable.ic_bar_chart)
                }
                val names = themeNames(state.themes)
                if (names.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.forEach { Pill(it, container = scheme.surfaceContainer, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
    if (state.board.pendingPromotion != null) PromotionDialog(color = state.solverColor, onChosen = viewModel::onPromotionChosen)
}

/** Whose move it is and how the last attempt went, in a card that changes colour with the result. */
@Composable
private fun Feedback(state: PuzzleUiState) {
    val brand = LocalBrand.current
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(state.phase, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "feedback") { phase ->
        val (container, content) = when (phase) {
            PuzzlePhase.CORRECT -> brand.win.copy(alpha = 0.16f) to brand.win
            PuzzlePhase.SOLVED -> brand.gold.copy(alpha = 0.18f) to brand.gold
            PuzzlePhase.WRONG -> brand.loss.copy(alpha = 0.16f) to brand.loss
            else -> scheme.surfaceContainerHigh to scheme.onSurface
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(container).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (phase) {
                PuzzlePhase.CORRECT -> AppIcon(R.drawable.ic_check, size = 28.dp, tint = content)
                PuzzlePhase.SOLVED -> AppIcon(R.drawable.ic_emoji_events_filled, size = 28.dp, tint = content)
                PuzzlePhase.WRONG -> AppIcon(R.drawable.ic_close, size = 28.dp, tint = content)
                PuzzlePhase.REVEALED -> AppIcon(R.drawable.ic_visibility, size = 28.dp, tint = content)
                else -> PieceTile(BoardPiece(state.solverColor, PieceKind.KING), 34.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(phaseTitle(phase, state.solverColor), style = MaterialTheme.typography.titleMedium, color = content)
                val detail = when {
                    phase == PuzzlePhase.SOLVED && state.ratingChange != null ->
                        stringResource(R.string.online_rating_change, state.ratingChange.let { if (it >= 0) "+$it" else "$it" })
                    phase == PuzzlePhase.SOLVING -> stringResource(R.string.puzzle_find_hint)
                    state.streak > 0 && phase == PuzzlePhase.SOLVED -> pluralStringResource(R.plurals.puzzle_streak, state.streak, state.streak)
                    else -> null
                }
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun phaseTitle(phase: PuzzlePhase, solver: PlayerColor): String = when (phase) {
    PuzzlePhase.LOADING -> ""
    PuzzlePhase.SOLVING -> stringResource(if (solver == PlayerColor.WHITE) R.string.puzzle_find_white else R.string.puzzle_find_black)
    PuzzlePhase.CORRECT -> stringResource(R.string.puzzle_correct)
    PuzzlePhase.WRONG -> stringResource(R.string.puzzle_wrong)
    PuzzlePhase.SOLVED -> stringResource(R.string.puzzle_solved)
    PuzzlePhase.REVEALED -> stringResource(R.string.puzzle_revealed)
}

/** Localised names for the most common Lichess themes; unknown themes are left out. */
@Composable
private fun themeNames(themes: List<String>): List<String> = themes.mapNotNull { theme ->
    when (theme) {
        "mateIn1" -> R.string.theme_mate_in_1
        "mateIn2" -> R.string.theme_mate_in_2
        "mateIn3" -> R.string.theme_mate_in_3
        "fork" -> R.string.theme_fork
        "pin" -> R.string.theme_pin
        "skewer" -> R.string.theme_skewer
        "hangingPiece" -> R.string.theme_hanging_piece
        "discoveredAttack" -> R.string.theme_discovered_attack
        "sacrifice" -> R.string.theme_sacrifice
        "backRankMate" -> R.string.theme_back_rank_mate
        "promotion" -> R.string.theme_promotion
        "endgame" -> R.string.theme_endgame
        "opening" -> R.string.theme_opening
        "middlegame" -> R.string.theme_middlegame
        "deflection" -> R.string.theme_deflection
        "attraction" -> R.string.theme_attraction
        "trappedPiece" -> R.string.theme_trapped_piece
        else -> null
    }
}.take(4).map { stringResource(it) }
