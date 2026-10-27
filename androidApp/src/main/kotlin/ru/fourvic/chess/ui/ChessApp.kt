package ru.fourvic.chess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.fourvic.chess.R
import ru.fourvic.chess.online.OnlineClient
import ru.fourvic.chess.puzzles.AssetPuzzleSource
import ru.fourvic.chess.puzzles.PrefsPuzzleProgressStore
import ru.fourvic.chess.settings.LocalAppSettings
import ru.fourvic.chess.settings.SettingsRepository
import ru.fourvic.chess.ui.game.GameScreen
import ru.fourvic.chess.ui.game.GameViewModel
import ru.fourvic.chess.ui.game.Opponent
import ru.fourvic.chess.ui.home.HomeAction
import ru.fourvic.chess.ui.home.HomeScreen
import ru.fourvic.chess.ui.online.OnlineScreen
import ru.fourvic.chess.ui.online.OnlineViewModel
import ru.fourvic.chess.ui.puzzles.PuzzleScreen
import ru.fourvic.chess.ui.puzzles.PuzzleViewModel
import ru.fourvic.chess.ui.settings.AccountSection
import ru.fourvic.chess.ui.settings.SettingsScreen
import ru.fourvic.chess.ui.tournaments.TournamentViewModel
import ru.fourvic.chess.ui.tournaments.TournamentsScreen

/** Top-level destinations. Kept as names in a saveable back stack. */
enum class Route { HOME, COMPUTER, FRIEND, PUZZLES, ONLINE, TOURNAMENTS, SETTINGS }

/** Root of the UI: provides settings and switches screens with a simple back stack. */
@Composable
fun ChessApp(settingsRepository: SettingsRepository, online: OnlineClient) {
    val settings by settingsRepository.settings.collectAsState()
    var stack by rememberSaveable { mutableStateOf(listOf(Route.HOME.name)) }
    val route = Route.valueOf(stack.last())
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    fun open(next: Route) {
        stack = stack + next.name
    }
    BackHandler(enabled = stack.size > 1, onBack = back)

    val context = LocalContext.current.applicationContext
    CompositionLocalProvider(LocalAppSettings provides settings) {
        when (route) {
            Route.HOME -> HomeScreen(puzzleSubtitle = dailyPuzzleSubtitle(PrefsPuzzleProgressStore(context)), onAction = { action ->
                open(
                    when (action) {
                        HomeAction.ONLINE -> Route.ONLINE
                        HomeAction.COMPUTER -> Route.COMPUTER
                        HomeAction.FRIEND -> Route.FRIEND
                        HomeAction.PUZZLES -> Route.PUZZLES
                        HomeAction.TOURNAMENTS -> Route.TOURNAMENTS
                        HomeAction.SETTINGS -> Route.SETTINGS
                    },
                )
            })
            Route.COMPUTER -> GameScreen(
                viewModel = viewModel(key = "computer") { GameViewModel() },
                showSetupOnStart = true,
                onBack = back,
            )
            Route.FRIEND -> GameScreen(
                viewModel = viewModel(key = "friend") { GameViewModel(initialOpponent = Opponent.Friend) },
                onBack = back,
            )
            Route.SETTINGS -> SettingsScreen(settings = settings, onChange = settingsRepository::update, onBack = back) {
                AccountSection(online, settings, settingsRepository::update)
            }
            Route.PUZZLES -> PuzzleScreen(
                viewModel = viewModel(key = "puzzles") {
                    PuzzleViewModel(AssetPuzzleSource(context), PrefsPuzzleProgressStore(context))
                },
                onBack = back,
            )
            Route.ONLINE -> OnlineScreen(viewModel = viewModel(key = "online") { OnlineViewModel(online) }, onBack = back)
            Route.TOURNAMENTS -> TournamentsScreen(
                viewModel = viewModel(key = "tournaments") { TournamentViewModel(online) },
                gameViewModel = viewModel(key = "arena-games") { OnlineViewModel(online, arena = true) },
                onBack = back,
            )
        }
    }
}

@Composable
private fun dailyPuzzleSubtitle(store: PrefsPuzzleProgressStore): String {
    val progress = store.load()
    val today = System.currentTimeMillis() / 86_400_000L
    val status = stringResource(if (progress.isDailySolved(today)) R.string.puzzle_daily_done else R.string.puzzle_daily_waiting)
    val streak = progress.currentStreak(today)
    return if (streak > 0) "$status · ${pluralStringResource(R.plurals.puzzle_streak, streak, streak)}" else status
}
