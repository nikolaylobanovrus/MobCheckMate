package ru.fourvic.chess.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.BuildConfig
import ru.fourvic.chess.R

/** Destinations reachable from the home screen. */
enum class HomeAction { ONLINE, COMPUTER, FRIEND, PUZZLES, TOURNAMENTS, SETTINGS }

@Composable
fun HomeScreen(onAction: (HomeAction) -> Unit, puzzleSubtitle: String? = null) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyMedium)
            val items = listOf(
                Triple(HomeAction.ONLINE, R.string.home_online, R.string.home_online_hint),
                Triple(HomeAction.COMPUTER, R.string.home_computer, R.string.home_computer_hint),
                Triple(HomeAction.FRIEND, R.string.home_friend, R.string.home_friend_hint),
                Triple(HomeAction.PUZZLES, R.string.home_puzzles, R.string.home_puzzles_hint),
                Triple(HomeAction.TOURNAMENTS, R.string.home_tournaments, R.string.home_tournaments_hint),
                Triple(HomeAction.SETTINGS, R.string.home_settings, R.string.home_settings_hint),
            )
            for ((action, title, hint) in items) {
                val primary = action == HomeAction.ONLINE
                Card(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    colors = if (primary) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        val subtitle = if (action == HomeAction.PUZZLES && puzzleSubtitle != null) puzzleSubtitle else stringResource(hint)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                stringResource(R.string.build_label, BuildConfig.STORE, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
