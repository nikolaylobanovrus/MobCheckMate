package ru.fourvic.chess.ui.tournaments

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import ru.fourvic.chess.ui.common.ScreenScaffold
import ru.fourvic.chess.ui.game.PromotionDialog
import ru.fourvic.chess.ui.game.soundFor
import ru.fourvic.chess.ui.online.Game
import ru.fourvic.chess.ui.online.OnlinePhase
import ru.fourvic.chess.ui.online.OnlineViewModel
import ru.fourvic.chess.ui.online.speedName

/**
 * Tournaments: the list with private arenas by code, one tournament's table, and the user's arena games.
 * [liveCountdown] keeps countdowns running; screenshot tests switch it off.
 */
@Composable
fun TournamentsScreen(
    viewModel: TournamentViewModel,
    gameViewModel: OnlineViewModel,
    onBack: () -> Unit,
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
            else -> onBack()
        }
    }
    BackHandler(enabled = selected != null) { back() }
    MoveSounds(
        key = game.board.ply to game.result,
        sound = if (game.result != null && game.result != GameResult.ABORTED) Sound.GAME_END else soundFor(game.board),
    )

    val title = state.detail?.let { tournamentName(it) } ?: stringResource(R.string.home_tournaments)
    ScreenScaffold(title = title, onBack = back) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            showGame -> Column(modifier) {
                state.detail?.let { info ->
                    Text(
                        statusLine(info, remaining(info.msLeft, state.receivedAt, now)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Game(game, gameViewModel, Modifier.weight(1f))
            }
            selected != null -> Detail(state, viewModel, now, modifier)
            else -> TournamentList(state, viewModel, now, modifier)
        }
    }

    if (showGame && game.board.pendingPromotion != null) PromotionDialog(onChosen = gameViewModel::onPromotionChosen)
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
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionNotice(state.connection, viewModel::refreshList)
        PrivateArenaCard(state, onJoin = viewModel::joinByCode, onCreate = { creating = true })
        val groups = listOf(
            TournamentStatus.STARTED to R.string.arena_section_started,
            TournamentStatus.CREATED to R.string.arena_section_created,
            TournamentStatus.FINISHED to R.string.arena_section_finished,
        )
        for ((status, label) in groups) {
            val items = state.list.filter { it.status == status }
            if (items.isEmpty()) continue
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
            items.forEach { info ->
                TournamentRow(info, remaining(info.msLeft, state.listReceivedAt, now)) { viewModel.open(info.id) }
            }
        }
        if (state.list.isEmpty() && !state.loading) {
            Text(stringResource(R.string.arena_empty), style = MaterialTheme.typography.bodyMedium)
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
    if (connection == ConnectionState.CONNECTED) return
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (connection == ConnectionState.CONNECTING) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.online_connecting))
            } else {
                Text(stringResource(R.string.online_offline), modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.online_retry)) }
            }
        }
    }
}

@Composable
private fun PrivateArenaCard(state: TournamentsUiState, onJoin: (String) -> Unit, onCreate: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.arena_private), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    label = { Text(stringResource(R.string.arena_code_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onJoin(code) }, enabled = code.length == 6) { Text(stringResource(R.string.arena_join_code)) }
            }
            when (state.error) {
                TournamentError.NOT_FOUND -> Text(stringResource(R.string.arena_not_found), color = MaterialTheme.colorScheme.error)
                TournamentError.REJECTED -> Text(stringResource(R.string.arena_rejected), color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
            OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.arena_create)) }
        }
    }
}

@Composable
private fun TournamentRow(info: TournamentInfo, msLeft: Long, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tournamentName(info),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(info.clock.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(summary(info), style = MaterialTheme.typography.bodySmall)
            Text(statusLine(info, msLeft), style = MaterialTheme.typography.bodyMedium, color = statusColor(info))
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
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summary(info), style = MaterialTheme.typography.bodyMedium)
                Text(
                    statusLine(info, remaining(info.msLeft, state.receivedAt, now)),
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor(info),
                )
                if (info.private) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.arena_code, info.id),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        val shareText = stringResource(R.string.arena_share_text, info.name, info.id)
                        val chooserTitle = stringResource(R.string.arena_share)
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                            context.startActivity(Intent.createChooser(send, chooserTitle))
                        }) { Text(chooserTitle) }
                    }
                }
            }
        }

        if (info.status != TournamentStatus.FINISHED) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !state.joined -> Button(onClick = viewModel::join, enabled = !state.busy) { Text(stringResource(R.string.arena_join)) }
                    state.me?.paused == true -> Button(onClick = viewModel::join, enabled = !state.busy) {
                        Text(stringResource(R.string.arena_resume))
                    }
                    else -> OutlinedButton(onClick = viewModel::pause, enabled = !state.busy) { Text(stringResource(R.string.arena_pause)) }
                }
                val hint = when {
                    !state.joined -> null
                    state.me?.paused == true -> R.string.arena_paused_hint
                    info.status == TournamentStatus.CREATED -> R.string.arena_joined_before_start
                    else -> R.string.arena_waiting
                }
                if (hint == R.string.arena_waiting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        state.me?.let { me ->
            Text(
                stringResource(R.string.arena_my_place, me.rank, me.score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(stringResource(R.string.arena_table), style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                if (state.standings.isEmpty()) {
                    Text(stringResource(R.string.arena_no_players), modifier = Modifier.padding(16.dp))
                }
                state.standings.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider()
                    StandingRow(line, mine = line.player.id == state.me?.player?.id)
                }
            }
        }
        Text(stringResource(R.string.arena_rules), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StandingRow(line: Standing, mine: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${line.rank}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.weight(1f)) {
            Text(
                line.player.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(line.player.rating)
                    if (line.player.provisional) append('?')
                    if (line.sheet.isNotEmpty()) append("   ").append(line.sheet.joinToString(" "))
                    if (line.paused) append("   ").append(stringResource(R.string.arena_paused_mark))
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (line.fire) {
            Text(stringResource(R.string.arena_fire_mark), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Text("${line.score}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
private fun tournamentName(info: TournamentInfo): String = when (info.schedule) {
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
