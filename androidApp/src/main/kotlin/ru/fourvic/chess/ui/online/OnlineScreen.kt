package ru.fourvic.chess.ui.online

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import ru.fourvic.chess.ui.common.ActionButton
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.AppTopBar
import ru.fourvic.chess.ui.common.Avatar
import ru.fourvic.chess.ui.common.InfoBanner
import ru.fourvic.chess.ui.common.LiveDot
import ru.fourvic.chess.ui.common.Pill
import ru.fourvic.chess.ui.common.PrimaryButton
import ru.fourvic.chess.ui.common.SecondaryButton
import ru.fourvic.chess.ui.common.SectionTitle
import ru.fourvic.chess.ui.common.SegmentedControl
import ru.fourvic.chess.ui.common.SurfaceCard
import ru.fourvic.chess.ui.common.Tone
import ru.fourvic.chess.ui.common.pressable
import ru.fourvic.chess.ui.common.speedColor
import ru.fourvic.chess.ui.common.speedIcon
import ru.fourvic.chess.ui.game.ActionBar
import ru.fourvic.chess.ui.game.ClockFace
import ru.fourvic.chess.ui.game.Confetti
import ru.fourvic.chess.ui.game.GameLayout
import ru.fourvic.chess.ui.game.MaterialBalance
import ru.fourvic.chess.ui.game.MoveList
import ru.fourvic.chess.ui.game.Outcome
import ru.fourvic.chess.ui.game.PlayerStrip
import ru.fourvic.chess.ui.game.PromotionDialog
import ru.fourvic.chess.ui.game.ResultCard
import ru.fourvic.chess.ui.game.StatusLine
import ru.fourvic.chess.ui.game.materialBalance
import ru.fourvic.chess.ui.game.soundFor
import ru.fourvic.chess.ui.theme.LocalBrand

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

    val subtitle = when (state.phase) {
        OnlinePhase.PLAYING, OnlinePhase.FINISHED -> state.clock?.let { "${it.label} · ${speedName(it.speed)} · " + stringResource(if (state.rated) R.string.online_rated else R.string.online_casual) }
        else -> null
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(stringResource(R.string.home_online), onBack = leave, subtitle = subtitle) },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        when (state.phase) {
            OnlinePhase.LOBBY -> Lobby(state, viewModel, modifier)
            OnlinePhase.SEEKING -> Seeking(state, viewModel::cancelSeek, modifier)
            OnlinePhase.PLAYING, OnlinePhase.FINISHED -> Game(state, viewModel, modifier)
        }
    }

    if (state.board.pendingPromotion != null) PromotionDialog(color = state.myColor, onChosen = viewModel::onPromotionChosen)
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

@Composable
private fun Lobby(state: OnlineUiState, viewModel: OnlineViewModel, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val width = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        ConnectionCard(state, viewModel::reconnect, width)
        if (state.error == ErrorCode.UNSUPPORTED_VERSION) {
            InfoBanner(stringResource(R.string.online_update_required), width, icon = R.drawable.ic_info, tone = Tone.DANGER)
        }
        SegmentedControl(
            items = listOf(stringResource(R.string.online_rated), stringResource(R.string.online_casual)),
            icons = listOf(R.drawable.ic_trending_up, R.drawable.ic_handshake),
            selected = if (state.rated) 0 else 1,
            onSelect = { viewModel.setRated(it == 0) },
            modifier = width,
        )
        val connected = state.connection == ConnectionState.CONNECTED
        Column(width, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.online_choose_time))
            Clock.LOBBY.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { clock -> ClockTile(clock, enabled = connected, modifier = Modifier.weight(1f)) { viewModel.seek(clock) } }
                }
            }
        }
    }
}

@Composable
private fun ClockTile(clock: Clock, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = speedColor(clock.speed)
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .pressable(enabled = enabled, onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            AppIcon(speedIcon(clock.speed), size = 18.dp, tint = accent)
        }
        Text(clock.label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
        Text(speedName(clock.speed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectionCard(state: OnlineUiState, onRetry: () -> Unit, modifier: Modifier) {
    val me = state.me
    SurfaceCard(modifier) {
        when {
            state.connection == ConnectionState.CONNECTED && me != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(me.name, seed = me.id, size = 52.dp, country = me.country)
                    Column(Modifier.weight(1f)) {
                        Text(me.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(if (state.registered) R.string.online_name_hint else R.string.online_guest_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Speed.BULLET, Speed.BLITZ, Speed.RAPID, Speed.CLASSICAL).forEach { speed ->
                        val rating = state.ratings.firstOrNull { it.speed == speed }
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(speedIcon(speed), size = 16.dp, tint = speedColor(speed))
                            Text(
                                rating?.let { it.rating.toString() + if (it.provisional) "?" else "" } ?: "—",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
            state.connection == ConnectionState.REPLACED -> {
                Text(stringResource(R.string.online_replaced), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.online_replaced_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.online_play_here), onRetry, icon = R.drawable.ic_devices)
            }
            state.connection == ConnectionState.OFFLINE -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(R.drawable.ic_wifi_off, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.online_offline), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.online_offline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                SecondaryButton(stringResource(R.string.online_retry), onRetry, icon = R.drawable.ic_refresh)
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveDot(color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.online_connecting), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// --- Seeking --------------------------------------------------------------------------------------------------

/** Radar-like rings around the player's avatar while the server looks for an opponent. */
@Composable
private fun Seeking(state: OnlineUiState, onCancel: () -> Unit, modifier: Modifier) {
    val brand = LocalBrand.current
    val transition = rememberInfiniteTransition(label = "radar")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    Column(
        modifier.navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            val ring = brand.online.start
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until 3) {
                    val t = (wave + i / 3f) % 1f
                    drawCircle(ring.copy(alpha = (1f - t) * 0.5f), radius = size.minDimension / 2 * (0.35f + 0.65f * t), style = Stroke(width = 3.dp.toPx()))
                }
                drawCircle(ring.copy(alpha = 0.12f), radius = size.minDimension * 0.3f)
            }
            val me = state.me
            Avatar(me?.name ?: "?", seed = me?.id ?: "me", size = 88.dp, country = me?.country)
        }
        Text(stringResource(R.string.online_seeking_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        state.seeking?.let { clock ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("${clock.label} · ${speedName(clock.speed)}", icon = speedIcon(clock.speed))
                Pill(stringResource(if (state.rated) R.string.online_rated else R.string.online_casual))
            }
        }
        Text(
            if (state.connection != ConnectionState.CONNECTED) stringResource(R.string.online_connecting) else stringResource(R.string.online_seeking_time, state.seekSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(stringResource(R.string.cancel), onCancel, icon = R.drawable.ic_close)
    }
}

// --- Game -----------------------------------------------------------------------------------------------------

@Composable
internal fun Game(state: OnlineUiState, viewModel: OnlineViewModel, modifier: Modifier) {
    val opponentColor = if (state.myColor == PlayerColor.WHITE) PlayerColor.BLACK else PlayerColor.WHITE
    val running = state.phase == OnlinePhase.PLAYING && state.clockRunning
    val balance = remember(state.board.pieces) { materialBalance(state.board.pieces) }
    var confirmResign by remember { mutableStateOf(false) }
    val won = state.result == GameResult.WHITE_WON && state.myColor == PlayerColor.WHITE ||
        state.result == GameResult.BLACK_WON && state.myColor == PlayerColor.BLACK

    Box(modifier) {
        GameLayout(
            top = {
                PlayerLine(state.opponent, opponentColor, balance, ClockFace(state.opponentMs, running), state.board.sideToMove == opponentColor, opponentColor in state.berserk)
            },
            bottom = {
                val me = if (state.myColor == PlayerColor.WHITE) state.white else state.black
                PlayerLine(me, state.myColor, balance, ClockFace(state.myMs, running), state.board.sideToMove == state.myColor, state.myColor in state.berserk)
            },
            board = { m ->
                ChessBoard(
                    state = state.board,
                    onSquareTapped = viewModel::onSquareTapped,
                    contentDescription = stringResource(R.string.board_description),
                    modifier = m,
                    movableColor = state.myColor.takeIf { state.phase == OnlinePhase.PLAYING },
                )
            },
            actions = {
                if (state.phase == OnlinePhase.PLAYING) {
                    ActionBar {
                        if (state.canBerserk) {
                            ActionButton(R.drawable.ic_local_fire_department_filled, stringResource(R.string.arena_berserk), viewModel::berserk, container = MaterialTheme.colorScheme.error)
                        }
                        if (state.canAbort) {
                            ActionButton(R.drawable.ic_close, stringResource(R.string.online_abort), { viewModel.abort() })
                        } else {
                            ActionButton(R.drawable.ic_flag, stringResource(R.string.online_resign), { confirmResign = true })
                            ActionButton(
                                R.drawable.ic_handshake,
                                stringResource(if (state.myDrawOffer) R.string.online_draw_sent else R.string.online_offer_draw),
                                viewModel::offerDraw,
                                enabled = !state.myDrawOffer && !state.opponentOffersDraw,
                            )
                        }
                        PhraseButton(viewModel::chat)
                    }
                }
            },
        ) {
            if (state.phase == OnlinePhase.FINISHED) {
                OnlineResult(state, viewModel)
            } else {
                StatusLine(statusText(state), alert = state.myTurn && state.board.checkedKing != null)
                state.opponentAwaySeconds?.let {
                    InfoBanner(stringResource(R.string.online_opponent_left, it), icon = R.drawable.ic_schedule, tone = Tone.WARNING)
                }
                when (state.connection) {
                    ConnectionState.CONNECTED -> Unit
                    ConnectionState.REPLACED -> InfoBanner(stringResource(R.string.online_replaced), icon = R.drawable.ic_devices, tone = Tone.WARNING) {
                        TextButton(onClick = viewModel::reconnect) { Text(stringResource(R.string.online_play_here)) }
                    }
                    else -> InfoBanner(stringResource(R.string.online_reconnecting), icon = R.drawable.ic_wifi_off, tone = Tone.DANGER)
                }
                if (state.opponentOffersDraw) {
                    InfoBanner(stringResource(R.string.online_draw_offered), icon = R.drawable.ic_handshake, tone = Tone.INFO) {
                        TextButton(onClick = viewModel::offerDraw) { Text(stringResource(R.string.online_accept)) }
                        TextButton(onClick = viewModel::declineDraw) { Text(stringResource(R.string.online_decline)) }
                    }
                }
            }
            state.chat.takeLast(2).forEach { line -> ChatBubble(line) }
            MoveList(state.board.san)
        }
        if (won) Confetti()
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            icon = { AppIcon(R.drawable.ic_flag) },
            title = { Text(stringResource(R.string.online_resign_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmResign = false
                    viewModel.resign()
                }) { Text(stringResource(R.string.online_resign), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PlayerLine(
    player: PlayerInfo?,
    color: PlayerColor,
    balance: MaterialBalance,
    clock: ClockFace,
    toMove: Boolean,
    berserk: Boolean,
) {
    PlayerStrip(
        name = player?.name ?: "…",
        detail = player?.let { it.rating.toString() + if (it.provisional) "?" else "" } ?: "",
        color = color,
        balance = balance,
        seed = player?.id ?: color.name,
        country = player?.country,
        toMove = toMove,
        clock = clock,
        badge = if (berserk) stringResource(R.string.arena_berserk_mark) else null,
    )
}

@Composable
private fun ChatBubble(line: ChatLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start) {
        Text(
            phraseText(line.phrase),
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (line.mine) 18.dp else 4.dp,
                        bottomEnd = if (line.mine) 4.dp else 18.dp,
                    ),
                )
                .background(if (line.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun OnlineResult(state: OnlineUiState, viewModel: OnlineViewModel) {
    val brand = LocalBrand.current
    val outcome = when (state.result) {
        GameResult.WHITE_WON -> if (state.myColor == PlayerColor.WHITE) Outcome.WIN else Outcome.LOSS
        GameResult.BLACK_WON -> if (state.myColor == PlayerColor.BLACK) Outcome.WIN else Outcome.LOSS
        GameResult.DRAW -> Outcome.DRAW
        GameResult.ABORTED, null -> Outcome.NEUTRAL
    }
    ResultCard(
        title = resultTitle(state),
        outcome = outcome,
        subtitle = state.reason?.let { reasonText(it, state) },
        extra = {
            state.myRatingDiff?.let { diff ->
                val up = diff >= 0
                Pill(
                    stringResource(R.string.online_rating_change, if (up) "+$diff" else "$diff"),
                    icon = if (up) R.drawable.ic_trending_up else R.drawable.ic_trending_down,
                    container = (if (up) brand.win else brand.loss).copy(alpha = 0.16f),
                    content = if (up) brand.win else brand.loss,
                )
            }
        },
    ) {
        if (state.tournamentId != null) {
            // The arena pairs the next game by itself; the view switches to it when it starts.
            Text(stringResource(R.string.arena_next_game_soon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(stringResource(R.string.arena_to_table), viewModel::backToLobby, Modifier.fillMaxWidth(), icon = R.drawable.ic_emoji_events)
            return@ResultCard
        }
        if (state.opponentWantsRematch && !state.rematchRequested) {
            InfoBanner(stringResource(R.string.online_rematch_offered), icon = R.drawable.ic_replay, tone = Tone.INFO)
        }
        PrimaryButton(
            stringResource(if (state.rematchRequested) R.string.online_rematch_sent else R.string.online_rematch),
            viewModel::rematch,
            Modifier.fillMaxWidth(),
            icon = R.drawable.ic_replay,
            enabled = !state.rematchRequested,
        )
        state.clock?.let { clock ->
            SecondaryButton(
                stringResource(R.string.online_new_opponent),
                {
                    viewModel.backToLobby()
                    viewModel.seek(clock)
                },
                Modifier.fillMaxWidth(),
                icon = R.drawable.ic_skip_next,
            )
        }
        TextButton(onClick = viewModel::backToLobby) { Text(stringResource(R.string.online_to_lobby)) }
    }
}

@Composable
private fun PhraseButton(onPhrase: (QuickPhrase) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ActionButton(R.drawable.ic_chat_bubble, stringResource(R.string.online_phrases), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(18.dp)) {
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
