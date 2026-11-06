package ru.fourvic.chess.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.random.Random
import ru.fourvic.chess.R
import ru.fourvic.chess.audio.MoveSounds
import ru.fourvic.chess.shared.chess.BoardPiece
import ru.fourvic.chess.shared.chess.GameStatus
import ru.fourvic.chess.shared.chess.PieceKind
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.ui.board.ChessBoard
import ru.fourvic.chess.ui.common.ActionButton
import ru.fourvic.chess.ui.common.AppTopBar
import ru.fourvic.chess.ui.common.Avatar
import ru.fourvic.chess.ui.common.PrimaryButton
import ru.fourvic.chess.ui.common.RoundIconButton
import ru.fourvic.chess.ui.common.SecondaryButton
import ru.fourvic.chess.ui.common.SegmentedControl
import ru.fourvic.chess.ui.common.SectionTitle
import ru.fourvic.chess.ui.common.pressable
import ru.fourvic.chess.ui.theme.LocalBrand

/** Game on this device, against the computer or a friend. */
@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel(),
    showSetupOnStart: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    val state = viewModel.state
    var showSetup by rememberSaveable { mutableStateOf(showSetupOnStart) }
    MoveSounds(key = state.ply, sound = soundFor(state))
    val opponent = state.opponent
    val balance = remember(state.pieces) { materialBalance(state.pieces) }
    val brand = LocalBrand.current

    val title = stringResource(if (opponent is Opponent.Computer) R.string.home_computer else R.string.home_friend)
    val subtitle = when (opponent) {
        is Opponent.Computer -> stringResource(R.string.level_short, opponent.level.number, levelName(opponent.level))
        Opponent.Friend -> stringResource(R.string.opponent_friend_label)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title, onBack, subtitle = subtitle) {
                RoundIconButton(R.drawable.ic_swap_vert, stringResource(R.string.flip_board), viewModel::flipBoard)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val topColor = if (state.flipped) PlayerColor.WHITE else PlayerColor.BLACK
            val strip = @Composable { color: PlayerColor ->
                val ongoing = state.status == GameStatus.ONGOING
                when {
                    opponent is Opponent.Computer && color != opponent.humanColor -> PlayerStrip(
                        name = stringResource(R.string.opponent_computer),
                        detail = stringResource(R.string.level_rating, levelName(opponent.level), opponent.level.approximateRating),
                        color = color,
                        balance = balance,
                        avatarIcon = R.drawable.ic_smart_toy_filled,
                        avatarGradient = brand.computer,
                        toMove = ongoing && state.sideToMove == color,
                    )
                    opponent is Opponent.Computer -> PlayerStrip(
                        name = stringResource(R.string.you),
                        detail = stringResource(if (color == PlayerColor.WHITE) R.string.color_white else R.string.color_black),
                        color = color,
                        balance = balance,
                        avatarGradient = brand.tournaments,
                        toMove = ongoing && state.sideToMove == color,
                    )
                    else -> PlayerStrip(
                        name = stringResource(if (color == PlayerColor.WHITE) R.string.color_white else R.string.color_black),
                        detail = stringResource(R.string.opponent_friend),
                        color = color,
                        balance = balance,
                        avatarGradient = if (color == PlayerColor.WHITE) brand.friend else brand.computer,
                        toMove = ongoing && state.sideToMove == color,
                    )
                }
            }
            GameLayout(
                top = { strip(topColor) },
                bottom = { strip(if (topColor == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE) },
                board = { modifier ->
                    ChessBoard(
                        state = state,
                        onSquareTapped = viewModel::onSquareTapped,
                        contentDescription = stringResource(R.string.board_description),
                        modifier = modifier,
                        movableColor = (opponent as? Opponent.Computer)?.humanColor ?: state.sideToMove,
                    )
                },
                actions = {
                    ActionBar {
                        ActionButton(R.drawable.ic_add, stringResource(R.string.new_game), { showSetup = true })
                        ActionButton(R.drawable.ic_undo, stringResource(R.string.undo), viewModel::undo, enabled = state.canUndo)
                        ActionButton(R.drawable.ic_swap_vert, stringResource(R.string.flip_board), viewModel::flipBoard)
                    }
                },
            ) {
                if (state.status.isFinished) {
                    val result = offlineResult(state)
                    ResultCard(result.title, result.outcome, subtitle = result.reason) {
                        PrimaryButton(stringResource(R.string.play_again), { viewModel.newGame(opponent) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_replay)
                        SecondaryButton(stringResource(R.string.change_settings), { showSetup = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_settings)
                    }
                } else {
                    StatusLine(
                        statusText(state),
                        thinking = state.botThinking,
                        alert = state.checkedKing != null,
                    )
                }
                MoveList(state.san)
            }
            if (state.status == GameStatus.CHECKMATE && opponent is Opponent.Computer && state.sideToMove != opponent.humanColor) {
                Confetti()
            }
        }
    }

    state.pendingPromotion?.let { PromotionDialog(color = state.sideToMove, onChosen = viewModel::onPromotionChosen) }
    if (showSetup) {
        NewGameSheet(
            current = state.opponent,
            onStart = { next ->
                showSetup = false
                viewModel.newGame(next)
            },
            onDismiss = { showSetup = false },
        )
    }
}

private data class OfflineResult(val title: String, val reason: String, val outcome: Outcome)

@Composable
private fun offlineResult(state: GameUiState): OfflineResult {
    val reason = when (state.status) {
        GameStatus.CHECKMATE -> stringResource(R.string.reason_checkmate)
        GameStatus.STALEMATE -> stringResource(R.string.stalemate)
        GameStatus.DRAW_THREEFOLD_REPETITION -> stringResource(R.string.draw_repetition)
        GameStatus.DRAW_INSUFFICIENT_MATERIAL -> stringResource(R.string.draw_insufficient_material)
        GameStatus.DRAW_FIFTY_MOVES -> stringResource(R.string.draw_fifty_moves)
        GameStatus.ONGOING -> ""
    }
    if (state.status != GameStatus.CHECKMATE) return OfflineResult(stringResource(R.string.online_draw), reason, Outcome.DRAW)
    // The side to move is the one that has been mated.
    val winner = if (state.sideToMove == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
    return when (val opponent = state.opponent) {
        is Opponent.Computer -> if (winner == opponent.humanColor) {
            OfflineResult(stringResource(R.string.game_victory), reason, Outcome.WIN)
        } else {
            OfflineResult(stringResource(R.string.game_defeat), reason, Outcome.LOSS)
        }
        Opponent.Friend -> OfflineResult(
            stringResource(if (winner == PlayerColor.WHITE) R.string.white_wins else R.string.black_wins),
            reason,
            Outcome.WIN,
        )
    }
}

@Composable
private fun statusText(state: GameUiState): String = when {
    state.botThinking -> stringResource(R.string.computer_thinking)
    state.checkedKing != null -> stringResource(R.string.check)
    state.opponent is Opponent.Computer && state.sideToMove == state.opponent.humanColor ->
        stringResource(R.string.online_your_move)
    state.sideToMove == PlayerColor.WHITE -> stringResource(R.string.white_to_move)
    else -> stringResource(R.string.black_to_move)
}

@Composable
internal fun levelName(level: BotLevel): String = stringResource(
    when (level) {
        BotLevel.LEVEL_1 -> R.string.level_1
        BotLevel.LEVEL_2 -> R.string.level_2
        BotLevel.LEVEL_3 -> R.string.level_3
        BotLevel.LEVEL_4 -> R.string.level_4
        BotLevel.LEVEL_5 -> R.string.level_5
    },
)

private enum class ColorChoice { WHITE, RANDOM, BLACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewGameSheet(current: Opponent, onStart: (Opponent) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        NewGameSetup(current, onStart, Modifier.navigationBarsPadding())
    }
}

/** Choice of opponent, bot level and colour before a game. */
@Composable
internal fun NewGameSetup(current: Opponent, onStart: (Opponent) -> Unit, modifier: Modifier = Modifier) {
    val currentComputer = current as? Opponent.Computer
    var vsComputer by rememberSaveable { mutableStateOf(current !is Opponent.Friend) }
    var levelNumber by rememberSaveable { mutableIntStateOf(currentComputer?.level?.number ?: 2) }
    var color by rememberSaveable {
        mutableStateOf(if (currentComputer?.humanColor == PlayerColor.BLACK) ColorChoice.BLACK else ColorChoice.WHITE)
    }
    val brand = LocalBrand.current

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.new_game), style = MaterialTheme.typography.headlineSmall)
        SegmentedControl(
            items = listOf(stringResource(R.string.opponent_computer), stringResource(R.string.opponent_friend_short)),
            icons = listOf(R.drawable.ic_smart_toy, R.drawable.ic_group),
            selected = if (vsComputer) 0 else 1,
            onSelect = { vsComputer = it == 0 },
        )
        if (vsComputer) {
            SectionTitle(stringResource(R.string.level))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotLevel.entries.forEach { level ->
                    val selected = level.number == levelNumber
                    Column(
                        Modifier
                            .width(118.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .pressable { levelNumber = level.number }
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(20.dp))
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Avatar(level.number.toString(), size = 44.dp, gradient = brand.computer)
                        Text(levelName(level), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
                        Text("≈${level.approximateRating}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SectionTitle(stringResource(R.string.your_color))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ColorChoice.WHITE to R.string.color_white, ColorChoice.RANDOM to R.string.color_random, ColorChoice.BLACK to R.string.color_black)
                    .forEach { (choice, label) ->
                        val selected = choice == color
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .pressable { color = choice }
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(20.dp))
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row {
                                if (choice != ColorChoice.BLACK) KingPicture(PlayerColor.WHITE)
                                if (choice != ColorChoice.WHITE) KingPicture(PlayerColor.BLACK)
                            }
                            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
            }
        } else {
            Text(stringResource(R.string.opponent_friend_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        PrimaryButton(
            stringResource(R.string.play),
            onClick = {
                val opponent = if (vsComputer) {
                    val humanColor = when (color) {
                        ColorChoice.WHITE -> PlayerColor.WHITE
                        ColorChoice.BLACK -> PlayerColor.BLACK
                        ColorChoice.RANDOM -> if (Random.nextBoolean()) PlayerColor.WHITE else PlayerColor.BLACK
                    }
                    Opponent.Computer(BotLevel.of(levelNumber), humanColor)
                } else {
                    Opponent.Friend
                }
                onStart(opponent)
            },
            modifier = Modifier.fillMaxWidth(),
            icon = R.drawable.ic_play_arrow,
        )
    }
}

@Composable
private fun KingPicture(color: PlayerColor) {
    PieceTile(BoardPiece(color, PieceKind.KING), 34.dp, Modifier.padding(horizontal = 2.dp))
}
