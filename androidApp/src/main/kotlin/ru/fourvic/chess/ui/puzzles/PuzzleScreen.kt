package ru.fourvic.chess.ui.puzzles

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.R
import ru.fourvic.chess.audio.MoveSounds
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.ui.board.ChessBoard
import ru.fourvic.chess.ui.common.ScreenScaffold
import ru.fourvic.chess.ui.game.PromotionDialog
import ru.fourvic.chess.ui.game.soundFor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PuzzleScreen(viewModel: PuzzleViewModel, onBack: () -> Unit) {
    val state = viewModel.state
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    MoveSounds(key = state.board.ply to state.board.pieces.hashCode(), sound = soundFor(state.board))

    ScreenScaffold(title = stringResource(R.string.home_puzzles), onBack = onBack) { padding ->
        if (state.phase == PuzzlePhase.LOADING) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        val board = @Composable { modifier: Modifier ->
            ChessBoard(
                state = state.board,
                onSquareTapped = viewModel::onSquareTapped,
                contentDescription = stringResource(R.string.board_description),
                modifier = modifier,
            )
        }
        val panel = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == PuzzleMode.TRAINING,
                        onClick = { viewModel.switchMode(PuzzleMode.TRAINING) },
                        label = { Text(stringResource(R.string.puzzle_training)) },
                    )
                    FilterChip(
                        selected = state.mode == PuzzleMode.DAILY,
                        onClick = { viewModel.switchMode(PuzzleMode.DAILY) },
                        label = { Text(stringResource(R.string.puzzle_daily)) },
                    )
                }
                Text(phaseText(state), style = MaterialTheme.typography.titleLarge)
                Text(
                    buildString {
                        append(stringResource(R.string.puzzle_your_rating, state.userRating))
                        state.ratingChange?.let { append(if (it >= 0) " (+$it)" else " ($it)") }
                        if (state.streak > 0) {
                            append(" · ")
                            append(pluralStringResource(R.plurals.puzzle_streak, state.streak, state.streak))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.finished) {
                    Text(
                        stringResource(R.string.puzzle_info, state.puzzleRating ?: 0, themeNames(state.themes)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.finished) {
                        Button(onClick = viewModel::next) { Text(stringResource(R.string.puzzle_next)) }
                    } else {
                        OutlinedButton(onClick = viewModel::hint) { Text(stringResource(R.string.puzzle_hint)) }
                        OutlinedButton(onClick = viewModel::showSolution) { Text(stringResource(R.string.puzzle_solution)) }
                    }
                }
            }
        }
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                board(Modifier.fillMaxSize().weight(1f, fill = false))
                panel()
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                board(Modifier.fillMaxWidth().widthIn(max = 600.dp))
                panel()
            }
        }
    }
    if (state.board.pendingPromotion != null) PromotionDialog(onChosen = viewModel::onPromotionChosen)
}

@Composable
private fun phaseText(state: PuzzleUiState): String = when (state.phase) {
    PuzzlePhase.LOADING -> ""
    PuzzlePhase.SOLVING -> stringResource(
        if (state.solverColor == PlayerColor.WHITE) R.string.puzzle_find_white else R.string.puzzle_find_black,
    )
    PuzzlePhase.CORRECT -> stringResource(R.string.puzzle_correct)
    PuzzlePhase.WRONG -> stringResource(R.string.puzzle_wrong)
    PuzzlePhase.SOLVED -> stringResource(R.string.puzzle_solved)
    PuzzlePhase.REVEALED -> stringResource(R.string.puzzle_revealed)
}

/** Localised names for the most common Lichess themes; unknown themes are left out. */
@Composable
private fun themeNames(themes: List<String>): String {
    val names = themes.mapNotNull { theme ->
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
    }.take(3).map { stringResource(it) }
    return names.joinToString(", ")
}
