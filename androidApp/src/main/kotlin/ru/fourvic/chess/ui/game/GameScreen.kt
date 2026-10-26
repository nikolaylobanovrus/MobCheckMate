package ru.fourvic.chess.ui.game

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ru.fourvic.chess.shared.engine.BotLevel
import kotlin.random.Random

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel(), showSetupOnStart: Boolean = false) {
    val state = viewModel.state
    var showSetup by rememberSaveable { mutableStateOf(showSetupOnStart) }
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
                Text(opponentText(state.opponent), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::undo, enabled = state.canUndo) { Text(stringResource(R.string.undo)) }
                    OutlinedButton(onClick = viewModel::flipBoard) { Text(stringResource(R.string.flip_board)) }
                }
                Button(onClick = { showSetup = true }) { Text(stringResource(R.string.new_game)) }
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
    if (showSetup) {
        NewGameDialog(
            current = state.opponent,
            onStart = { opponent ->
                showSetup = false
                viewModel.newGame(opponent)
            },
            onDismiss = { showSetup = false },
        )
    }
}

@Composable
private fun statusText(state: GameUiState): String = when (state.status) {
    GameStatus.ONGOING -> when {
        state.botThinking -> stringResource(R.string.computer_thinking)
        state.checkedKing != null -> stringResource(R.string.check)
        state.sideToMove == PlayerColor.WHITE -> stringResource(R.string.white_to_move)
        else -> stringResource(R.string.black_to_move)
    }
    // The side to move is the one that has been mated.
    GameStatus.CHECKMATE -> when (val opponent = state.opponent) {
        is Opponent.Computer -> if (state.sideToMove == opponent.humanColor) {
            stringResource(R.string.checkmate_computer_wins)
        } else {
            stringResource(R.string.checkmate_you_win)
        }
        Opponent.Friend -> if (state.sideToMove == PlayerColor.WHITE) {
            stringResource(R.string.checkmate_black_wins)
        } else {
            stringResource(R.string.checkmate_white_wins)
        }
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

@Composable
private fun opponentText(opponent: Opponent): String = when (opponent) {
    Opponent.Friend -> stringResource(R.string.opponent_friend_label)
    is Opponent.Computer -> stringResource(
        R.string.opponent_computer_label,
        opponent.level.number,
        levelName(opponent.level),
        opponent.level.approximateRating,
    )
}

@Composable
private fun levelName(level: BotLevel): String = stringResource(
    when (level) {
        BotLevel.LEVEL_1 -> R.string.level_1
        BotLevel.LEVEL_2 -> R.string.level_2
        BotLevel.LEVEL_3 -> R.string.level_3
        BotLevel.LEVEL_4 -> R.string.level_4
        BotLevel.LEVEL_5 -> R.string.level_5
    },
)

private enum class ColorChoice { WHITE, BLACK, RANDOM }

/** Choice of opponent, bot level and colour before a game. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewGameDialog(
    current: Opponent,
    onStart: (Opponent) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentComputer = current as? Opponent.Computer
    var vsComputer by rememberSaveable { mutableStateOf(current !is Opponent.Friend) }
    var levelNumber by rememberSaveable { mutableStateOf(currentComputer?.level?.number ?: 2) }
    var color by rememberSaveable {
        mutableStateOf(if (currentComputer?.humanColor == PlayerColor.BLACK) ColorChoice.BLACK else ColorChoice.WHITE)
    }
    val level = BotLevel.of(levelNumber)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_game)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.opponent), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vsComputer,
                        onClick = { vsComputer = true },
                        label = { Text(stringResource(R.string.opponent_computer)) },
                    )
                    FilterChip(
                        selected = !vsComputer,
                        onClick = { vsComputer = false },
                        label = { Text(stringResource(R.string.opponent_friend)) },
                    )
                }
                if (vsComputer) {
                    Text(stringResource(R.string.level), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotLevel.entries.forEach { candidate ->
                            FilterChip(
                                selected = candidate == level,
                                onClick = { levelNumber = candidate.number },
                                label = { Text(candidate.number.toString()) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.level_description, levelName(level), level.approximateRating),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(R.string.your_color), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ColorChoice.WHITE to R.string.color_white,
                            ColorChoice.BLACK to R.string.color_black,
                            ColorChoice.RANDOM to R.string.color_random,
                        ).forEach { (choice, label) ->
                            FilterChip(
                                selected = color == choice,
                                onClick = { color = choice },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val opponent = if (vsComputer) {
                    val humanColor = when (color) {
                        ColorChoice.WHITE -> PlayerColor.WHITE
                        ColorChoice.BLACK -> PlayerColor.BLACK
                        ColorChoice.RANDOM -> if (Random.nextBoolean()) PlayerColor.WHITE else PlayerColor.BLACK
                    }
                    Opponent.Computer(level, humanColor)
                } else {
                    Opponent.Friend
                }
                onStart(opponent)
            }) { Text(stringResource(R.string.play)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
