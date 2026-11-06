package ru.fourvic.chess.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.fourvic.chess.R
import ru.fourvic.chess.online.OnlineClient
import ru.fourvic.chess.puzzles.AssetPuzzleSource
import ru.fourvic.chess.puzzles.PrefsPuzzleProgressStore
import ru.fourvic.chess.settings.Languages
import ru.fourvic.chess.settings.LocalAppSettings
import ru.fourvic.chess.settings.SettingsRepository
import ru.fourvic.chess.settings.applyThemeMode
import ru.fourvic.chess.shared.chess.PlayerColor
import ru.fourvic.chess.shared.engine.BotLevel
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.game.GameScreen
import ru.fourvic.chess.ui.game.GameViewModel
import ru.fourvic.chess.ui.game.Opponent
import ru.fourvic.chess.ui.home.HomeScreen
import ru.fourvic.chess.ui.home.WelcomeScreen
import ru.fourvic.chess.ui.online.OnlineScreen
import ru.fourvic.chess.ui.online.OnlineViewModel
import ru.fourvic.chess.ui.puzzles.PuzzleMode
import ru.fourvic.chess.ui.puzzles.PuzzleScreen
import ru.fourvic.chess.ui.puzzles.PuzzleViewModel
import ru.fourvic.chess.ui.settings.AccountViewModel
import ru.fourvic.chess.ui.settings.ProfileScreen
import ru.fourvic.chess.ui.tournaments.TournamentViewModel
import ru.fourvic.chess.ui.tournaments.TournamentsScreen

/** Tabs of the bottom bar. */
enum class Tab(@DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int, val label: Int) {
    PLAY(R.drawable.ic_home, R.drawable.ic_home_filled, R.string.tab_play),
    PUZZLES(R.drawable.ic_extension, R.drawable.ic_extension_filled, R.string.home_puzzles),
    TOURNAMENTS(R.drawable.ic_emoji_events, R.drawable.ic_emoji_events_filled, R.string.home_tournaments),
    PROFILE(R.drawable.ic_person, R.drawable.ic_person_filled, R.string.tab_profile),
}

/** Full-screen games opened over the tabs. */
enum class Route { COMPUTER, FRIEND, ONLINE }

/** Space a tab's content leaves at the bottom for the floating bar. */
val BottomBarSpace = 96.dp

/**
 * Root of the UI. The first launch shows the welcome screen with the language choice; after that, four tabs in a
 * floating bar and full-screen games on top of them.
 */
@Composable
fun ChessApp(settingsRepository: SettingsRepository, online: OnlineClient) {
    val settings by settingsRepository.settings.collectAsState()
    CompositionLocalProvider(LocalAppSettings provides settings) {
        if (!settings.onboarded) {
            WelcomeScreen(
                current = Languages.current,
                onLanguage = Languages::set,
                onStart = { settingsRepository.update { it.copy(onboarded = true) } },
            )
        } else {
            Shell(settingsRepository, online)
        }
    }
}

@Composable
private fun Shell(settingsRepository: SettingsRepository, online: OnlineClient) {
    val settings = LocalAppSettings.current
    val context = LocalContext.current.applicationContext
    var tab by rememberSaveable { mutableStateOf(Tab.PLAY) }
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    val route = stack.lastOrNull()?.let(Route::valueOf)
    val open: (Route) -> Unit = { stack = stack + it.name }
    val back: () -> Unit = { stack = stack.dropLast(1) }

    val onlineVm: OnlineViewModel = viewModel(key = "online") { OnlineViewModel(online) }
    val puzzles: PuzzleViewModel = viewModel(key = "puzzles") { PuzzleViewModel(AssetPuzzleSource(context), PrefsPuzzleProgressStore(context)) }
    val tournaments: TournamentViewModel = viewModel(key = "tournaments") { TournamentViewModel(online) }
    val arenaGames: OnlineViewModel = viewModel(key = "arena-games") { OnlineViewModel(online, arena = true) }
    val account: AccountViewModel = viewModel(key = "account") { AccountViewModel(online) }

    BackHandler(enabled = route != null, onBack = back)
    BackHandler(enabled = route == null && tab != Tab.PLAY && !(tab == Tab.TOURNAMENTS && tournaments.state.selectedId != null)) {
        tab = Tab.PLAY
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = route ?: tab,
            transitionSpec = {
                when {
                    targetState is Route -> (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(220)))
                        .togetherWith(fadeOut(tween(160)) + scaleOut(tween(320), targetScale = 0.96f))
                    initialState is Route -> (fadeIn(tween(220)) + scaleIn(tween(320), initialScale = 0.96f))
                        .togetherWith(slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(200)))
                    else -> fadeIn(tween(220, delayMillis = 60)).togetherWith(fadeOut(tween(120)))
                }
            },
            label = "screens",
        ) { target ->
            when (target) {
                Route.COMPUTER -> GameScreen(
                    viewModel = viewModel(key = "computer") { GameViewModel(initialOpponent = Opponent.Computer(BotLevel.LEVEL_2, PlayerColor.WHITE)) },
                    showSetupOnStart = true,
                    onBack = back,
                )
                Route.FRIEND -> GameScreen(
                    viewModel = viewModel(key = "friend") { GameViewModel(initialOpponent = Opponent.Friend) },
                    onBack = back,
                )
                Route.ONLINE -> OnlineScreen(viewModel = onlineVm, onBack = back)
                Tab.PLAY -> HomeScreen(
                    online = onlineVm.state,
                    puzzles = puzzles.state,
                    tournaments = tournaments.state,
                    onOnline = { open(Route.ONLINE) },
                    onQuickPlay = { clock: Clock ->
                        onlineVm.quickSeek(clock)
                        open(Route.ONLINE)
                    },
                    onComputer = { open(Route.COMPUTER) },
                    onFriend = { open(Route.FRIEND) },
                    onDailyPuzzle = {
                        puzzles.switchMode(PuzzleMode.DAILY)
                        tab = Tab.PUZZLES
                    },
                    onPuzzles = { tab = Tab.PUZZLES },
                    onTournaments = { tab = Tab.TOURNAMENTS },
                    onProfile = { tab = Tab.PROFILE },
                )
                Tab.PUZZLES -> PuzzleScreen(viewModel = puzzles, onBack = null, bottomSpace = BottomBarSpace)
                Tab.TOURNAMENTS -> TournamentsScreen(viewModel = tournaments, gameViewModel = arenaGames, onBack = null)
                Tab.PROFILE -> ProfileScreen(
                    settings = settings,
                    onChange = { change ->
                        settingsRepository.update(change)
                        applyThemeMode(settingsRepository.settings.value.themeMode)
                    },
                    account = account,
                    online = onlineVm.state,
                    puzzleRating = puzzles.state.userRating,
                )
            }
        }

        val showBar = route == null && !(tab == Tab.TOURNAMENTS && tournaments.state.selectedId != null)
        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            BottomBar(selected = tab, onSelect = { tab = it })
        }
    }
}

/** Floating pill with the four tabs; the selected one is filled and sits on a soft gold pill. */
@Composable
internal fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh)
                .border(1.dp, scheme.outlineVariant, CircleShape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                val pill by animateColorAsState(if (isSelected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent, label = "pill")
                val tint by animateColorAsState(if (isSelected) scheme.primary else scheme.onSurfaceVariant, label = "tint")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(pill)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppIcon(if (isSelected) tab.selectedIcon else tab.icon, size = 24.dp, tint = tint)
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
