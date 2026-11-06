package ru.fourvic.chess.ui.home

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.fourvic.chess.R
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus
import ru.fourvic.chess.ui.BottomBarSpace
import ru.fourvic.chess.ui.board.ChessBoard
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.Avatar
import ru.fourvic.chess.ui.common.GradientCard
import ru.fourvic.chess.ui.common.LiveDot
import ru.fourvic.chess.ui.common.Pill
import ru.fourvic.chess.ui.common.SectionTitle
import ru.fourvic.chess.ui.common.SurfaceCard
import ru.fourvic.chess.ui.common.pressable
import ru.fourvic.chess.ui.common.speedIcon
import ru.fourvic.chess.ui.online.OnlineUiState
import ru.fourvic.chess.ui.online.speedName
import ru.fourvic.chess.ui.puzzles.PuzzleUiState
import ru.fourvic.chess.ui.theme.LocalBrand
import ru.fourvic.chess.ui.tournaments.TournamentsUiState
import ru.fourvic.chess.ui.tournaments.formatDuration
import ru.fourvic.chess.ui.tournaments.tournamentName

/** Time controls offered for one-tap play on the home screen. */
val QUICK_CLOCKS = listOf(Clock(60, 0), Clock(180, 0), Clock(600, 0))

/**
 * The Play tab: who you are, one-tap online play, the other ways to play, today's puzzle and the tournament that is
 * on now. [hour] picks the greeting; screenshot tests fix it.
 */
@Composable
fun HomeScreen(
    online: OnlineUiState,
    puzzles: PuzzleUiState,
    tournaments: TournamentsUiState,
    onOnline: () -> Unit,
    onQuickPlay: (Clock) -> Unit,
    onComputer: () -> Unit,
    onFriend: () -> Unit,
    onDailyPuzzle: () -> Unit,
    onPuzzles: () -> Unit,
    onTournaments: () -> Unit,
    onProfile: () -> Unit,
    hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
    liveCountdown: Boolean = true,
) {
    val brand = LocalBrand.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (liveCountdown) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                now = SystemClock.elapsedRealtime()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val width = Modifier.widthIn(max = 620.dp).fillMaxWidth()
        Header(online, puzzles, hour, onProfile, width.padding(top = 10.dp))

        // Online: the main way to play.
        GradientCard(brand.online, width, onClick = onOnline) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painterResource(R.drawable.piece_wn),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-8).dp).size(132.dp).rotate(-12f).alpha(0.22f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectionLabel(online.connection)
                    Text(stringResource(R.string.home_online), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.home_online_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(end = 72.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_CLOCKS.forEach { clock ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .pressable { onQuickPlay(clock) }
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppIcon(speedIcon(clock.speed), size = 16.dp)
                            Text(clock.label, style = MaterialTheme.typography.titleLarge)
                        }
                        Text(speedName(clock.speed), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .pressable(onClick = onOnline)
                    .background(Color.White)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.home_all_time_controls), style = MaterialTheme.typography.titleMedium, color = Color(0xFF0C5C49))
                Spacer(Modifier.size(6.dp))
                AppIcon(R.drawable.ic_arrow_forward, size = 18.dp, tint = Color(0xFF0C5C49))
            }
        }

        Row(width, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeTile(
                gradient = brand.computer,
                icon = R.drawable.ic_smart_toy_filled,
                title = stringResource(R.string.opponent_computer),
                subtitle = stringResource(R.string.home_computer_short),
                onClick = onComputer,
                modifier = Modifier.weight(1f),
            )
            ModeTile(
                gradient = brand.friend,
                icon = R.drawable.ic_group_filled,
                title = stringResource(R.string.home_friend),
                subtitle = stringResource(R.string.home_friend_short),
                onClick = onFriend,
                modifier = Modifier.weight(1f),
            )
        }

        DailyPuzzleCard(puzzles, onDailyPuzzle, onPuzzles, width)
        TournamentCard(tournaments, now, onTournaments, width)

        Spacer(Modifier.navigationBarsPadding().height(BottomBarSpace))
    }
}

@Composable
private fun Header(online: OnlineUiState, puzzles: PuzzleUiState, hour: Int, onProfile: () -> Unit, modifier: Modifier) {
    val me = online.me
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.clip(CircleShape).pressable(onClick = onProfile)) {
            Avatar(me?.name ?: "?", seed = me?.id ?: "me", size = 48.dp, country = me?.country)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (hour) {
                        in 5..11 -> R.string.greet_morning
                        in 12..17 -> R.string.greet_afternoon
                        else -> R.string.greet_evening
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                me?.name ?: stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val blitz = online.ratings.firstOrNull { it.speed == Speed.BLITZ }
        if (puzzles.streak > 0) {
            Pill(
                puzzles.streak.toString(),
                icon = R.drawable.ic_local_fire_department_filled,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (blitz != null) {
            Pill(
                blitz.rating.toString() + if (blitz.provisional) "?" else "",
                icon = speedIcon(Speed.BLITZ),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

@Composable
private fun ConnectionLabel(connection: ConnectionState) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.18f)).padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (connection) {
            ConnectionState.CONNECTED -> {
                LiveDot(color = Color(0xFFB9FFDD), size = 7.dp)
                Text(stringResource(R.string.home_status_online), style = MaterialTheme.typography.labelMedium)
            }
            ConnectionState.CONNECTING -> {
                LiveDot(color = Color.White.copy(alpha = 0.8f), size = 7.dp)
                Text(stringResource(R.string.home_status_connecting), style = MaterialTheme.typography.labelMedium)
            }
            ConnectionState.OFFLINE, ConnectionState.REPLACED -> {
                AppIcon(R.drawable.ic_wifi_off, size = 14.dp, modifier = Modifier.padding(start = 4.dp, end = 4.dp))
                Text(stringResource(R.string.home_status_offline), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ModeTile(
    gradient: ru.fourvic.chess.ui.theme.Gradient,
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    GradientCard(gradient, modifier.height(150.dp), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            AppIcon(icon, size = 26.dp)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2)
    }
}

@Composable
private fun DailyPuzzleCard(puzzles: PuzzleUiState, onDaily: () -> Unit, onTraining: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.home_puzzles))
        SurfaceCard(onClick = onDaily) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val preview = puzzles.dailyPreview
                Box(Modifier.size(112.dp)) {
                    if (preview != null) {
                        ChessBoard(preview, onSquareTapped = {}, contentDescription = stringResource(R.string.puzzle_daily), interactive = false, cornerRadius = 12f, coordinates = false, modifier = Modifier.size(112.dp))
                    } else {
                        Box(Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(R.drawable.ic_calendar_today_filled, size = 16.dp, tint = LocalBrand.current.puzzles.end)
                        Text(stringResource(R.string.puzzle_daily), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        stringResource(if (puzzles.dailySolvedToday) R.string.puzzle_daily_done else R.string.puzzle_daily_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (puzzles.dailySolvedToday) {
                        Pill(stringResource(R.string.home_solved), icon = R.drawable.ic_check, container = scheme.secondaryContainer, content = scheme.onSecondaryContainer)
                    } else {
                        Pill(stringResource(R.string.home_solve), icon = R.drawable.ic_play_arrow, container = scheme.primary, content = scheme.onPrimary)
                    }
                }
            }
        }
        SurfaceCard(onClick = onTraining, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(LocalBrand.current.puzzles.brush()), contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_extension_filled, size = 22.dp, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.puzzle_training), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_puzzle_rating, puzzles.userRating), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                AppIcon(R.drawable.ic_chevron_right, tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TournamentCard(state: TournamentsUiState, now: Long, onOpen: () -> Unit, modifier: Modifier) {
    val brand = LocalBrand.current
    val featured: TournamentInfo? = state.list.firstOrNull { it.status == TournamentStatus.STARTED && !it.private }
        ?: state.list.filter { it.status == TournamentStatus.CREATED && !it.private }.minByOrNull { it.msLeft }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.home_tournaments))
        GradientCard(brand.tournaments, onClick = onOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_emoji_events_filled, size = 30.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (featured == null) {
                        Text(stringResource(R.string.home_tournaments), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.home_tournaments_hint), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
                    } else {
                        val live = featured.status == TournamentStatus.STARTED
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (live) LiveDot(color = Color.White, size = 7.dp)
                            Text(
                                stringResource(if (live) R.string.home_tournament_live else R.string.home_tournament_next).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Text(tournamentName(featured), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val left = (featured.msLeft - (now - state.listReceivedAt)).coerceAtLeast(0)
                        Text(
                            stringResource(if (live) R.string.arena_ends_in else R.string.arena_starts_in, formatDuration(left)) +
                                " · " + featured.clock.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
                AppIcon(R.drawable.ic_chevron_right)
            }
        }
    }
}
