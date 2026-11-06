package ru.fourvic.chess.ui.tournaments

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import ru.fourvic.chess.ui.BottomBarSpace
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.AppTopBar
import ru.fourvic.chess.ui.common.Avatar
import ru.fourvic.chess.ui.common.GradientCard
import ru.fourvic.chess.ui.common.InfoBanner
import ru.fourvic.chess.ui.common.LiveDot
import ru.fourvic.chess.ui.common.PrimaryButton
import ru.fourvic.chess.ui.common.SecondaryButton
import ru.fourvic.chess.ui.common.SectionTitle
import ru.fourvic.chess.ui.common.StatBlock
import ru.fourvic.chess.ui.common.SurfaceCard
import ru.fourvic.chess.ui.common.TabHeader
import ru.fourvic.chess.ui.common.Tone
import ru.fourvic.chess.ui.common.TypingDots
import ru.fourvic.chess.ui.common.pressable
import ru.fourvic.chess.ui.common.speedColor
import ru.fourvic.chess.ui.common.speedIcon
import ru.fourvic.chess.ui.theme.LocalBrand
import ru.fourvic.chess.ui.theme.TabularNumbers
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import ru.fourvic.chess.R
import ru.fourvic.chess.audio.MoveSounds
import ru.fourvic.chess.audio.Sound
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.GameResult
import ru.fourvic.chess.shared.protocol.Standing
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus
import ru.fourvic.chess.shared.tournament.ArenaRules
import ru.fourvic.chess.ui.game.PromotionDialog
import ru.fourvic.chess.ui.game.soundFor
import ru.fourvic.chess.ui.online.Game
import ru.fourvic.chess.ui.online.OnlinePhase
import ru.fourvic.chess.ui.online.OnlineViewModel
import ru.fourvic.chess.ui.online.speedName

/**
 * Tournaments: the list with private arenas by code, one tournament's table, and the user's arena games.
 * As a tab it has no back button on the list. [liveCountdown] keeps countdowns running; screenshot tests switch it off.
 */
@Composable
fun TournamentsScreen(
    viewModel: TournamentViewModel,
    gameViewModel: OnlineViewModel,
    onBack: (() -> Unit)?,
    liveCountdown: Boolean = true,
) {
    val state = viewModel.state
    val game = gameViewModel.state
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (liveCountdown) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                now = SystemClock.elapsedRealtime()
            }
        }
    }
    PauseWhenInBackground(viewModel)

    // An arena game in progress takes the user to its tournament, wherever they are on this screen.
    val playingIn = game.tournamentId.takeIf { game.phase == OnlinePhase.PLAYING }
    LaunchedEffect(playingIn) {
        if (playingIn != null && state.selectedId != playingIn) viewModel.open(playingIn)
    }

    val selected = state.selectedId
    val showGame = selected != null && game.tournamentId == selected &&
        (game.phase == OnlinePhase.PLAYING || game.phase == OnlinePhase.FINISHED)
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val back = {
        when {
            showGame && game.phase == OnlinePhase.PLAYING -> confirmLeave = true
            selected != null -> viewModel.close()
            else -> onBack?.invoke()
        }
    }
    BackHandler(enabled = selected != null) { back() }
    MoveSounds(
        key = game.board.ply to game.result,
        sound = if (game.result != null && game.result != GameResult.ABORTED) Sound.GAME_END else soundFor(game.board),
    )

    Column(Modifier.fillMaxSize()) {
        val detail = state.detail
        if (selected == null) {
            if (onBack != null) AppTopBar(stringResource(R.string.home_tournaments), onBack) else TabHeader(stringResource(R.string.home_tournaments))
        } else {
            AppTopBar(
                detail?.let { tournamentName(it) } ?: stringResource(R.string.home_tournaments),
                onBack = { back() },
                // In a game the table is hidden, so the countdown moves up here.
                subtitle = detail?.takeIf { showGame }?.let { statusLine(it, remaining(it.msLeft, state.receivedAt, now)) },
            )
        }
        val modifier = Modifier.fillMaxWidth().weight(1f)
        when {
            showGame -> Game(game, gameViewModel, modifier)
            selected != null -> Detail(state, viewModel, now, modifier)
            else -> TournamentList(state, viewModel, now, modifier)
        }
    }

    if (showGame && game.board.pendingPromotion != null) PromotionDialog(color = game.myColor, onChosen = gameViewModel::onPromotionChosen)
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.online_leave_title)) },
            text = { Text(stringResource(R.string.online_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.close()
                }) { Text(stringResource(R.string.online_leave)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PauseWhenInBackground(viewModel: TournamentViewModel) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(viewModel)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> current.onBackground()
                Lifecycle.Event.ON_START -> current.onForeground()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

// --- List -----------------------------------------------------------------------------------------------------

@Composable
private fun TournamentList(state: TournamentsUiState, viewModel: TournamentViewModel, now: Long, modifier: Modifier) {
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshList() }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 620.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConnectionNotice(state.connection, viewModel::reconnect)
            val live = state.list.firstOrNull { it.status == TournamentStatus.STARTED && !it.private }
            if (live != null) FeaturedArena(live, remaining(live.msLeft, state.listReceivedAt, now)) { viewModel.open(live.id) }
            val groups = listOf(
                TournamentStatus.STARTED to R.string.arena_section_started,
                TournamentStatus.CREATED to R.string.arena_section_created,
                TournamentStatus.FINISHED to R.string.arena_section_finished,
            )
            for ((status, label) in groups) {
                val items = state.list.filter { it.status == status && it != live }
                if (items.isEmpty()) continue
                SectionTitle(stringResource(label))
                items.forEach { info ->
                    TournamentRow(info, remaining(info.msLeft, state.listReceivedAt, now)) { viewModel.open(info.id) }
                }
            }
            if (state.list.isEmpty() && !state.loading) {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(R.drawable.ic_schedule, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.arena_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionTitle(stringResource(R.string.arena_private))
            PrivateArenaCard(state, onJoin = viewModel::joinByCode, onCreate = { creating = true })
            Spacer(Modifier.navigationBarsPadding().height(BottomBarSpace))
        }
    }
    if (creating) {
        CreateArenaDialog(
            onCreate = { name, clock, minutes, startsIn, rated ->
                creating = false
                viewModel.create(name, clock, minutes, startsIn, rated)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun ConnectionNotice(connection: ConnectionState, onRetry: () -> Unit) {
    when (connection) {
        ConnectionState.CONNECTED -> Unit
        ConnectionState.CONNECTING -> InfoBanner(stringResource(R.string.online_connecting), icon = R.drawable.ic_refresh)
        ConnectionState.REPLACED -> InfoBanner(stringResource(R.string.online_replaced), icon = R.drawable.ic_devices, tone = Tone.WARNING) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.online_play_here)) }
        }
        ConnectionState.OFFLINE -> InfoBanner(stringResource(R.string.online_offline), icon = R.drawable.ic_wifi_off, tone = Tone.DANGER) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.online_retry)) }
        }
    }
}

/** The arena on now, as a big gold card with a live badge. */
@Composable
private fun FeaturedArena(info: TournamentInfo, msLeft: Long, onClick: () -> Unit) {
    GradientCard(LocalBrand.current.tournaments, onClick = onClick) {
        Box(Modifier.fillMaxWidth()) {
            AppIcon(
                R.drawable.ic_emoji_events_filled,
                size = 120.dp,
                tint = Color.White.copy(alpha = 0.16f),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 20.dp, y = (-10).dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.18f)).padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveDot(color = Color.White, size = 7.dp)
                    Text(stringResource(R.string.home_tournament_live).uppercase(), style = MaterialTheme.typography.labelMedium)
                }
                Text(tournamentName(info), style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${info.clock.label} ${speedName(info.clock.speed)} · " + pluralStringResource(R.plurals.arena_players, info.players, info.players),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.arena_time_left), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                Text(formatDuration(msLeft), style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers))
            }
            Row(
                Modifier.clip(CircleShape).pressable(onClick = onClick).background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.arena_join), style = MaterialTheme.typography.titleMedium, color = Color(0xFF6B3D00))
                AppIcon(R.drawable.ic_arrow_forward, size = 18.dp, tint = Color(0xFF6B3D00))
            }
        }
    }
}

@Composable
private fun PrivateArenaCard(state: TournamentsUiState, onJoin: (String) -> Unit, onCreate: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(LocalBrand.current.friend.brush()), contentAlignment = Alignment.Center) {
                AppIcon(R.drawable.ic_lock, size = 22.dp, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.arena_private), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.arena_private_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                placeholder = { Text(stringResource(R.string.arena_code_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(stringResource(R.string.arena_join_code), { onJoin(code) }, enabled = code.length == 6)
        }
        when (state.error) {
            TournamentError.NOT_FOUND -> Text(stringResource(R.string.arena_not_found), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            TournamentError.REJECTED -> Text(stringResource(R.string.arena_rejected), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            null -> Unit
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(stringResource(R.string.arena_create), onCreate, Modifier.fillMaxWidth(), icon = R.drawable.ic_add)
    }
}

@Composable
private fun TournamentRow(info: TournamentInfo, msLeft: Long, onClick: () -> Unit) {
    val accent = speedColor(info.clock.speed)
    SurfaceCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.16f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppIcon(speedIcon(info.clock.speed), size = 18.dp, tint = accent)
                Text(info.clock.label, style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (info.private) AppIcon(R.drawable.ic_lock, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tournamentName(info), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(summary(info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (info.status == TournamentStatus.STARTED) LiveDot(color = MaterialTheme.colorScheme.error, size = 6.dp)
                    if (info.status == TournamentStatus.FINISHED) AppIcon(R.drawable.ic_military_tech_filled, size = 14.dp, tint = LocalBrand.current.gold)
                    Text(statusLine(info, msLeft), style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), color = statusColor(info))
                }
            }
            AppIcon(R.drawable.ic_chevron_right, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- One tournament -------------------------------------------------------------------------------------------

@Composable
private fun Detail(state: TournamentsUiState, viewModel: TournamentViewModel, now: Long, modifier: Modifier) {
    val info = state.detail
    if (info == null) {
        Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.error == TournamentError.NOT_FOUND) {
                Text(stringResource(R.string.arena_not_found))
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }
    val context = LocalContext.current
    val brand = LocalBrand.current
    Column(
        modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 620.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientCard(if (info.status == TournamentStatus.FINISHED) brand.computer else brand.tournaments) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(summary(info), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                        Text(
                            statusLine(info, remaining(info.msLeft, state.receivedAt, now)),
                            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                        )
                    }
                    AppIcon(R.drawable.ic_emoji_events_filled, size = 44.dp, tint = Color.White.copy(alpha = 0.9f))
                }
                if (info.private) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.arena_code, info.id), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        val shareText = stringResource(R.string.arena_share_text, info.name, info.id)
                        val chooserTitle = stringResource(R.string.arena_share)
                        Row(
                            Modifier.clip(CircleShape).pressable {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                                context.startActivity(Intent.createChooser(send, chooserTitle))
                            }.background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppIcon(R.drawable.ic_share, size = 18.dp, tint = Color(0xFF6B3D00))
                            Text(chooserTitle, style = MaterialTheme.typography.labelLarge, color = Color(0xFF6B3D00))
                        }
                    }
                }
            }

            if (info.status != TournamentStatus.FINISHED) {
                val hint = when {
                    !state.joined -> null
                    state.me?.paused == true -> R.string.arena_paused_hint
                    info.status == TournamentStatus.CREATED -> R.string.arena_joined_before_start
                    else -> R.string.arena_waiting
                }
                when {
                    !state.joined -> PrimaryButton(stringResource(R.string.arena_join), viewModel::join, Modifier.fillMaxWidth(), icon = R.drawable.ic_play_arrow, enabled = !state.busy)
                    state.me?.paused == true -> PrimaryButton(stringResource(R.string.arena_resume), viewModel::join, Modifier.fillMaxWidth(), icon = R.drawable.ic_play_arrow, enabled = !state.busy)
                    else -> SecondaryButton(stringResource(R.string.arena_pause), viewModel::pause, Modifier.fillMaxWidth(), icon = R.drawable.ic_pause, enabled = !state.busy)
                }
                if (hint != null) {
                    InfoBanner(stringResource(hint), icon = if (hint == R.string.arena_waiting) R.drawable.ic_schedule else R.drawable.ic_info, tone = Tone.NEUTRAL) {
                        if (hint == R.string.arena_waiting) Box(Modifier.padding(end = 8.dp)) { TypingDots() }
                    }
                }
            }
            state.me?.let { me ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBlock("#${me.rank}", stringResource(R.string.arena_place), Modifier.weight(1f), icon = R.drawable.ic_military_tech_filled, accent = brand.gold)
                    StatBlock(me.score.toString(), stringResource(R.string.arena_points), Modifier.weight(1f), icon = R.drawable.ic_star_filled)
                    StatBlock(me.games.toString(), stringResource(R.string.arena_games), Modifier.weight(1f), icon = R.drawable.ic_sports_esports)
                }
            }

            SectionTitle(stringResource(R.string.arena_table))
            SurfaceCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                if (state.standings.isEmpty()) {
                    Text(stringResource(R.string.arena_no_players), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.standings.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    StandingRow(line, mine = line.player.id == state.me?.player?.id)
                }
            }
            Text(stringResource(R.string.arena_rules), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StandingRow(line: Standing, mine: Boolean) {
    val brand = LocalBrand.current
    val medal = when (line.rank) {
        1 -> brand.gold
        2 -> brand.silver
        3 -> brand.bronze
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(medal ?: MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text("${line.rank}", style = MaterialTheme.typography.labelLarge, color = if (medal != null) Color(0xFF1A1206) else MaterialTheme.colorScheme.onSurface)
        }
        Avatar(line.player.name, seed = line.player.id, size = 36.dp, country = line.player.country)
        Column(Modifier.weight(1f)) {
            Text(
                line.player.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (mine) FontWeight.ExtraBold else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    line.player.rating.toString() + if (line.player.provisional) "?" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (line.paused) Text(" · " + stringResource(R.string.arena_paused_mark), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                line.sheet.takeLast(6).forEach { points -> SheetDot(points) }
            }
        }
        if (line.fire) AppIcon(R.drawable.ic_local_fire_department_filled, size = 20.dp, tint = Color(0xFFFF7A45))
        Text("${line.score}", style = MaterialTheme.typography.titleLarge.merge(TabularNumbers))
    }
}

/** Points of one game in the table: green for a win, grey for a draw, red for a loss. */
@Composable
private fun SheetDot(points: Int) {
    val brand = LocalBrand.current
    val color = when {
        points >= 2 -> brand.win
        points == 1 -> brand.draw
        else -> brand.loss
    }
    Box(Modifier.padding(start = 2.dp).size(16.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text("$points", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// --- Creating a private arena ---------------------------------------------------------------------------------

private val CREATE_CLOCKS = listOf(Clock(60, 0), Clock(120, 1), Clock(180, 0), Clock(180, 2), Clock(300, 0), Clock(300, 3), Clock(600, 0))
private val DURATIONS = listOf(20, 30, 45, 60, 90, 120)
private val START_DELAYS = listOf(1, 2, 5, 10)

/** Same rule as on the server. */
private val ARENA_NAME = Regex("^[\\p{L}\\p{N} _\\-'.!#]{3,30}$")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateArenaDialog(onCreate: (String, Clock, Int, Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    val defaultName = stringResource(R.string.arena_default_name)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var clockIndex by rememberSaveable { mutableIntStateOf(2) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var startsIn by rememberSaveable { mutableIntStateOf(2) }
    var rated by rememberSaveable { mutableStateOf(true) }
    val clock = CREATE_CLOCKS[clockIndex]
    val durations = DURATIONS.filter { ArenaRules.allows(clock, it) }
    // A longer clock may rule out the chosen duration; then the shortest one that fits is used.
    val chosenMinutes = if (minutes in durations) minutes else durations.first()
    val valid = ARENA_NAME.matches(name.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arena_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.arena_name)) },
                    supportingText = { Text(stringResource(R.string.arena_name_rules)) },
                    isError = !valid,
                    singleLine = true,
                )
                Text(stringResource(R.string.online_choose_time), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CREATE_CLOCKS.forEachIndexed { index, candidate ->
                        FilterChip(selected = index == clockIndex, onClick = { clockIndex = index }, label = { Text(candidate.label) })
                    }
                }
                Text(stringResource(R.string.arena_duration), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durations.forEach { candidate ->
                        FilterChip(
                            selected = candidate == chosenMinutes,
                            onClick = { minutes = candidate },
                            label = { Text(stringResource(R.string.arena_minutes, candidate)) },
                        )
                    }
                }
                Text(stringResource(R.string.arena_starts_after), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    START_DELAYS.forEach { candidate ->
                        FilterChip(
                            selected = candidate == startsIn,
                            onClick = { startsIn = candidate },
                            label = { Text(stringResource(R.string.arena_minutes, candidate)) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.online_rated), modifier = Modifier.weight(1f))
                    Switch(checked = rated, onCheckedChange = { rated = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, clock, chosenMinutes, startsIn, rated) }, enabled = valid) {
                Text(stringResource(R.string.arena_create_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- Texts ----------------------------------------------------------------------------------------------------

private fun remaining(msLeft: Long, receivedAt: Long, now: Long) = (msLeft - (now - receivedAt)).coerceAtLeast(0)

@Composable
internal fun tournamentName(info: TournamentInfo): String = when (info.schedule) {
    "hourly_bullet" -> stringResource(R.string.arena_hourly_bullet)
    "hourly_blitz" -> stringResource(R.string.arena_hourly_blitz)
    "daily_blitz" -> stringResource(R.string.arena_daily_blitz)
    "daily_rapid" -> stringResource(R.string.arena_daily_rapid)
    else -> info.name
}

@Composable
private fun summary(info: TournamentInfo): String = listOf(
    "${info.clock.label} ${speedName(info.clock.speed)}",
    stringResource(if (info.rated) R.string.online_rated else R.string.online_casual),
    stringResource(R.string.arena_minutes, info.minutes),
    pluralStringResource(R.plurals.arena_players, info.players, info.players),
).joinToString(" · ")

@Composable
private fun statusLine(info: TournamentInfo, msLeft: Long): String = when (info.status) {
    TournamentStatus.CREATED -> stringResource(R.string.arena_starts_in, formatDuration(msLeft))
    TournamentStatus.STARTED -> stringResource(R.string.arena_ends_in, formatDuration(msLeft))
    TournamentStatus.FINISHED -> info.winner?.let { stringResource(R.string.arena_finished_winner, it) }
        ?: stringResource(R.string.arena_finished)
}

@Composable
private fun statusColor(info: TournamentInfo) =
    if (info.status == TournamentStatus.STARTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/** 4:05 or 1:02:07. */
internal fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
