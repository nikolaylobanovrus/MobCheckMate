package ru.fourvic.chess.ui.online

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.R
import ru.fourvic.chess.audio.MoveSounds
import ru.fourvic.chess.audio.Sound
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.EndReason
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.QuickPhrase
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.ui.board.ChessBoard
import ru.fourvic.chess.ui.common.ScreenScaffold
import ru.fourvic.chess.ui.common.withFlag
import ru.fourvic.chess.ui.game.PromotionDialog
import ru.fourvic.chess.ui.game.soundFor

@Composable
fun OnlineScreen(viewModel: OnlineViewModel, onBack: () -> Unit) {
    val state = viewModel.state
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val inGame = state.phase == OnlinePhase.PLAYING
    val leave = { if (inGame) confirmLeave = true else onBack() }
    BackHandler(enabled = inGame) { confirmLeave = true }
    MoveSounds(
        key = state.board.ply to state.result,
        sound = if (state.result != null && state.result != GameResult.ABORTED) Sound.GAME_END else soundFor(state.board),
    )

    ScreenScaffold(title = stringResource(R.string.home_online), onBack = leave) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (state.phase) {
            OnlinePhase.LOBBY -> Lobby(state, viewModel, modifier)
            OnlinePhase.SEEKING -> Seeking(state, viewModel::cancelSeek, modifier)
            OnlinePhase.PLAYING, OnlinePhase.FINISHED -> Game(state, viewModel, modifier)
        }
    }

    if (state.board.pendingPromotion != null) PromotionDialog(onChosen = viewModel::onPromotionChosen)
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.online_leave_title)) },
            text = { Text(stringResource(R.string.online_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onBack()
                }) { Text(stringResource(R.string.online_leave)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// --- Lobby ----------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lobby(state: OnlineUiState, viewModel: OnlineViewModel, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectionCard(state, viewModel::reconnect)
        if (state.error == ErrorCode.UNSUPPORTED_VERSION) {
            Text(stringResource(R.string.online_update_required), color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.rated,
                onClick = { viewModel.setRated(true) },
                label = { Text(stringResource(R.string.online_rated)) },
            )
            FilterChip(
                selected = !state.rated,
                onClick = { viewModel.setRated(false) },
                label = { Text(stringResource(R.string.online_casual)) },
            )
        }
        Text(stringResource(R.string.online_choose_time), style = MaterialTheme.typography.titleSmall)
        val connected = state.connection == ConnectionState.CONNECTED
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Clock.LOBBY.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { clock ->
                        OutlinedButton(
                            onClick = { viewModel.seek(clock) },
                            enabled = connected,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(72.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(clock.label, style = MaterialTheme.typography.titleLarge)
                                Text(speedName(clock.speed), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: OnlineUiState, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val me = state.me
            when {
                state.connection == ConnectionState.CONNECTED && me != null -> {
                    Text(withFlag(me.name, me.country), style = MaterialTheme.typography.titleMedium)
                    val names = Speed.entries.associateWith { speedName(it) }
                    Text(
                        state.ratings.joinToString("   ") { r ->
                            "${names[r.speed]} ${r.rating}${if (r.provisional) "?" else ""}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(R.string.online_name_hint), style = MaterialTheme.typography.bodySmall)
                }
                state.connection == ConnectionState.OFFLINE -> {
                    Text(stringResource(R.string.online_offline), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.online_offline_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.online_retry)) }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.online_connecting), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Seeking(state: OnlineUiState, onCancel: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        val clock = state.seeking
        Text(
            stringResource(
                R.string.online_seeking,
                clock?.label ?: "",
                stringResource(if (state.rated) R.string.online_rated else R.string.online_casual),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.online_seeking_time, state.seekSeconds), style = MaterialTheme.typography.bodyMedium)
        if (state.connection != ConnectionState.CONNECTED) {
            Text(stringResource(R.string.online_connecting), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

// --- Game -----------------------------------------------------------------------------------------------------

@Composable
internal fun Game(state: OnlineUiState, viewModel: OnlineViewModel, modifier: Modifier) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val opponentColor = if (state.myColor == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
    val running = state.phase == OnlinePhase.PLAYING && state.clockRunning
    val board = @Composable { m: Modifier ->
        ChessBoard(
            state = state.board,
            onSquareTapped = viewModel::onSquareTapped,
            contentDescription = stringResource(R.string.board_description),
            modifier = m,
        )
    }
    val opponentBar = @Composable {
        PlayerBar(
            state.opponent,
            state.opponentMs,
            active = running && state.board.sideToMove == opponentColor,
            berserk = opponentColor in state.berserk,
        )
    }
    val myBar = @Composable {
        PlayerBar(
            if (state.myColor == PlayerColor.WHITE) state.white else state.black,
            state.myMs,
            active = running && state.board.sideToMove == state.myColor,
            berserk = state.myColor in state.berserk,
        )
    }
    val panel = @Composable { GamePanel(state, viewModel) }

    if (landscape) {
        Row(
            modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            board(Modifier.fillMaxSize().weight(1f, fill = false))
            Column(
                Modifier.widthIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                opponentBar()
                panel()
                myBar()
            }
        }
    } else {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.widthIn(max = 600.dp)) { opponentBar() }
            board(Modifier.fillMaxWidth().widthIn(max = 600.dp))
            Box(Modifier.widthIn(max = 600.dp)) { myBar() }
            panel()
        }
    }
}

@Composable
private fun PlayerBar(player: PlayerInfo?, ms: Long, active: Boolean, berserk: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                player?.let { withFlag(it.name, it.country) } ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (player != null) {
                Text(
                    buildString {
                        append(player.rating)
                        if (player.provisional) append('?')
                        if (berserk) append(" · ").append(stringResource(R.string.arena_berserk_mark))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (berserk) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        }
        val low = ms < 10_000
        val background = when {
            active && low -> MaterialTheme.colorScheme.errorContainer
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Surface(color = background, shape = RoundedCornerShape(8.dp)) {
            Text(
                formatClock(ms),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamePanel(state: OnlineUiState, viewModel: OnlineViewModel) {
    var confirmResign by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.phase == OnlinePhase.FINISHED) {
            ResultCard(state, viewModel)
        } else {
            Text(statusText(state), style = MaterialTheme.typography.titleMedium)
            state.opponentAwaySeconds?.let {
                Banner(stringResource(R.string.online_opponent_left, it))
            }
            if (state.connection != ConnectionState.CONNECTED) Banner(stringResource(R.string.online_reconnecting))
            if (state.opponentOffersDraw) {
                Banner(stringResource(R.string.online_draw_offered)) {
                    TextButton(onClick = viewModel::offerDraw) { Text(stringResource(R.string.online_accept)) }
                    TextButton(onClick = viewModel::declineDraw) { Text(stringResource(R.string.online_decline)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                if (state.canBerserk) {
                    Button(
                        onClick = viewModel::berserk,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.arena_berserk)) }
                }
                if (state.canAbort) {
                    OutlinedButton(onClick = { viewModel.abort() }) { Text(stringResource(R.string.online_abort)) }
                } else {
                    OutlinedButton(onClick = { confirmResign = true }) { Text(stringResource(R.string.online_resign)) }
                    OutlinedButton(
                        onClick = viewModel::offerDraw,
                        enabled = !state.myDrawOffer && !state.opponentOffersDraw,
                    ) {
                        Text(stringResource(if (state.myDrawOffer) R.string.online_draw_sent else R.string.online_offer_draw))
                    }
                }
                PhraseButton(viewModel::chat)
            }
        }
        state.chat.takeLast(2).forEach { line ->
            Text(
                stringResource(
                    if (line.mine) R.string.online_chat_mine else R.string.online_chat_opponent,
                    phraseText(line.phrase),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(stringResource(R.string.online_resign_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmResign = false
                    viewModel.resign()
                }) { Text(stringResource(R.string.online_resign)) }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ResultCard(state: OnlineUiState, viewModel: OnlineViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(resultTitle(state), style = MaterialTheme.typography.titleLarge)
            state.reason?.let { Text(reasonText(it, state), style = MaterialTheme.typography.bodyMedium) }
            state.myRatingDiff?.let { diff ->
                Text(
                    stringResource(R.string.online_rating_change, if (diff >= 0) "+$diff" else "$diff"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (state.tournamentId != null) {
                // The arena pairs the next game by itself; the view switches to it when it starts.
                Text(stringResource(R.string.arena_next_game_soon), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::backToLobby) { Text(stringResource(R.string.arena_to_table)) }
                return@Column
            }
            if (state.opponentWantsRematch && !state.rematchRequested) {
                Text(stringResource(R.string.online_rematch_offered), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::rematch, enabled = !state.rematchRequested) {
                    Text(stringResource(if (state.rematchRequested) R.string.online_rematch_sent else R.string.online_rematch))
                }
                state.clock?.let { clock ->
                    OutlinedButton(onClick = {
                        viewModel.backToLobby()
                        viewModel.seek(clock)
                    }) { Text(stringResource(R.string.online_new_opponent)) }
                }
            }
            TextButton(onClick = viewModel::backToLobby) { Text(stringResource(R.string.online_to_lobby)) }
        }
    }
}

@Composable
private fun Banner(text: String, actions: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
private fun PhraseButton(onPhrase: (QuickPhrase) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.online_phrases)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            QuickPhrase.entries.forEach { phrase ->
                DropdownMenuItem(
                    text = { Text(phraseText(phrase)) },
                    onClick = {
                        open = false
                        onPhrase(phrase)
                    },
                )
            }
        }
    }
}

// --- Texts ----------------------------------------------------------------------------------------------------

@Composable
private fun statusText(state: OnlineUiState): String {
    val first = state.firstMoveSeconds
    return when {
        first != null && state.myTurn -> stringResource(R.string.online_first_move, first)
        first != null -> stringResource(R.string.online_first_move_opponent, first)
        state.myTurn && state.board.checkedKing != null -> stringResource(R.string.check)
        state.myTurn -> stringResource(R.string.online_your_move)
        state.premove != null -> stringResource(R.string.online_premove_set)
        else -> stringResource(R.string.online_opponent_move)
    }
}

@Composable
private fun resultTitle(state: OnlineUiState): String = when (state.result) {
    GameResult.WHITE_WON -> stringResource(if (state.myColor == PlayerColor.WHITE) R.string.online_you_won else R.string.online_you_lost)
    GameResult.BLACK_WON -> stringResource(if (state.myColor == PlayerColor.BLACK) R.string.online_you_won else R.string.online_you_lost)
    GameResult.DRAW -> stringResource(R.string.online_draw)
    GameResult.ABORTED, null -> stringResource(R.string.online_aborted)
}

@Composable
private fun reasonText(reason: EndReason, state: OnlineUiState): String {
    val iWon = (state.result == GameResult.WHITE_WON && state.myColor == PlayerColor.WHITE) ||
        (state.result == GameResult.BLACK_WON && state.myColor == PlayerColor.BLACK)
    return stringResource(
        when (reason) {
            EndReason.CHECKMATE -> R.string.reason_checkmate
            EndReason.RESIGNATION -> if (iWon) R.string.reason_opponent_resigned else R.string.reason_you_resigned
            EndReason.TIMEOUT -> if (iWon) R.string.reason_opponent_timeout else R.string.reason_you_timeout
            EndReason.STALEMATE -> R.string.stalemate
            EndReason.REPETITION -> R.string.draw_repetition
            EndReason.INSUFFICIENT_MATERIAL -> R.string.draw_insufficient_material
            EndReason.FIFTY_MOVES -> R.string.draw_fifty_moves
            EndReason.AGREEMENT -> R.string.reason_agreement
            EndReason.ABANDONED -> if (iWon) R.string.reason_opponent_left else R.string.reason_you_left
            EndReason.ABORTED -> R.string.reason_aborted
            EndReason.TIMEOUT_VS_INSUFFICIENT_MATERIAL -> R.string.reason_timeout_vs_insufficient
            EndReason.NO_START -> if (iWon) R.string.reason_opponent_no_start else R.string.reason_you_no_start
        },
    )
}

@Composable
internal fun phraseText(phrase: QuickPhrase): String = stringResource(
    when (phrase) {
        QuickPhrase.HELLO -> R.string.phrase_hello
        QuickPhrase.GOOD_LUCK -> R.string.phrase_good_luck
        QuickPhrase.HAVE_FUN -> R.string.phrase_have_fun
        QuickPhrase.NICE_MOVE -> R.string.phrase_nice_move
        QuickPhrase.OOPS -> R.string.phrase_oops
        QuickPhrase.THANKS -> R.string.phrase_thanks
        QuickPhrase.GOOD_GAME -> R.string.phrase_good_game
        QuickPhrase.BYE -> R.string.phrase_bye
    },
)

@Composable
internal fun speedName(speed: Speed): String = stringResource(
    when (speed) {
        Speed.BULLET -> R.string.speed_bullet
        Speed.BLITZ -> R.string.speed_blitz
        Speed.RAPID -> R.string.speed_rapid
        Speed.CLASSICAL -> R.string.speed_classical
    },
)

/** 5:03 normally; 0:09.4 under ten seconds, when every tenth matters. */
internal fun formatClock(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    val totalSeconds = clamped / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (clamped < 10_000) {
        "%d:%02d.%d".format(minutes, seconds, (clamped % 1000) / 100)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
