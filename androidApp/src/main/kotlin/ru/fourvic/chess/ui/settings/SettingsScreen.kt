package ru.fourvic.chess.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.R
import ru.fourvic.chess.settings.AppSettings
import ru.fourvic.chess.settings.BoardTheme
import ru.fourvic.chess.ui.common.ScreenScaffold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    ScreenScaffold(title = stringResource(R.string.home_settings), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_board_theme), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    BoardTheme.CLASSIC to R.string.theme_classic,
                    BoardTheme.GREEN to R.string.theme_green,
                    BoardTheme.BLUE to R.string.theme_blue,
                    BoardTheme.GRAY to R.string.theme_gray,
                ).forEach { (theme, label) ->
                    FilterChip(
                        selected = settings.boardTheme == theme,
                        onClick = { onChange { it.copy(boardTheme = theme) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            SwitchRow(R.string.settings_coordinates, settings.showCoordinates) { v -> onChange { it.copy(showCoordinates = v) } }
            SwitchRow(R.string.settings_legal_moves, settings.showLegalMoves) { v -> onChange { it.copy(showLegalMoves = v) } }
            SwitchRow(R.string.settings_sound, settings.sound) { v -> onChange { it.copy(sound = v) } }
            extraContent()
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
