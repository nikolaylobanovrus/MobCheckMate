package ru.fourvic.chess.ui.game

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.fourvic.chess.BuildConfig
import ru.fourvic.chess.R
import ru.fourvic.chess.ui.board.ChessBoard
import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.chess.Promotion

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val state = viewModel.state
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold { padding ->
        val board = @Composable { modifier: Modifier ->
            ChessBoard(
                state = state,
                onSquareTapped = viewModel::onSquareTapped,
                contentDescription = stringResource(R.string.board_description),
                modifier = modifier,
            )
        }
        val controls = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(statusText(state), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::undo, enabled = state.canUndo) { Text(stringResource(R.string.undo)) }
                    OutlinedButton(onClick = viewModel::flipBoard) { Text(stringResource(R.string.flip_board)) }
                }
                Button(onClick = viewModel::newGame) { Text(stringResource(R.string.new_game)) }
                Text(
                    stringResource(R.string.build_label, BuildConfig.STORE, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                board(Modifier.fillMaxSize().weight(1f, fill = false))
                controls()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                board(Modifier.fillMaxWidth().widthIn(max = 600.dp))
                controls()
            }
        }
    }

    if (state.pendingPromotion != null) {
        PromotionDialog(onChosen = viewModel::onPromotionChosen)
    }
}

@Composable
private fun statusText(state: GameUiState): String = when (state.status) {
    GameStatus.ONGOING -> when {
        state.checkedKing != null -> stringResource(R.string.check)
        state.sideToMove == PlayerColor.WHITE -> stringResource(R.string.white_to_move)
        else -> stringResource(R.string.black_to_move)
    }
    // The side to move is the one that has been mated.
    GameStatus.CHECKMATE -> if (state.sideToMove == PlayerColor.WHITE) {
        stringResource(R.string.checkmate_black_wins)
    } else {
        stringResource(R.string.checkmate_white_wins)
    }
    GameStatus.STALEMATE -> stringResource(R.string.stalemate)
    GameStatus.DRAW_THREEFOLD_REPETITION -> stringResource(R.string.draw_repetition)
    GameStatus.DRAW_INSUFFICIENT_MATERIAL -> stringResource(R.string.draw_insufficient_material)
    GameStatus.DRAW_FIFTY_MOVES -> stringResource(R.string.draw_fifty_moves)
}

@Composable
private fun PromotionDialog(onChosen: (Promotion?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChosen(null) },
        title = { Text(stringResource(R.string.promotion_title)) },
        text = {
            Column {
                listOf(
                    Promotion.QUEEN to R.string.piece_queen,
                    Promotion.ROOK to R.string.piece_rook,
                    Promotion.BISHOP to R.string.piece_bishop,
                    Promotion.KNIGHT to R.string.piece_knight,
                ).forEach { (promotion, label) ->
                    TextButton(onClick = { onChosen(promotion) }) { Text(stringResource(label)) }
                }
            }
        },
        confirmButton = {},
    )
}
