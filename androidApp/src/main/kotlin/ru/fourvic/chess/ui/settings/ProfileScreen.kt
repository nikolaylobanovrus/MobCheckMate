package ru.fourvic.chess.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.R
import ru.fourvic.chess.settings.AppLanguage
import ru.fourvic.chess.settings.AppSettings
import ru.fourvic.chess.settings.BoardTheme
import ru.fourvic.chess.settings.Languages
import ru.fourvic.chess.settings.ThemeMode
import ru.fourvic.chess.shared.protocol.Speed
import ru.fourvic.chess.ui.BottomBarSpace
import ru.fourvic.chess.ui.common.AppIcon
import ru.fourvic.chess.ui.common.Avatar
import ru.fourvic.chess.ui.common.Pill
import ru.fourvic.chess.ui.common.SectionTitle
import ru.fourvic.chess.ui.common.SegmentedControl
import ru.fourvic.chess.ui.common.SurfaceCard
import ru.fourvic.chess.ui.common.TabHeader
import ru.fourvic.chess.ui.common.pressable
import ru.fourvic.chess.ui.common.speedColor
import ru.fourvic.chess.ui.common.speedIcon
import ru.fourvic.chess.ui.online.OnlineUiState
import ru.fourvic.chess.ui.online.speedName
import ru.fourvic.chess.ui.theme.BoardPalette

/** The Profile tab: the player, the online account and every setting. */
@Composable
fun ProfileScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    account: AccountViewModel,
    online: OnlineUiState,
    puzzleRating: Int,
    language: AppLanguage = Languages.current,
    onLanguage: (AppLanguage) -> Unit = Languages::set,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        TabHeader(stringResource(R.string.tab_profile))
        Column(
            Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerCard(account.state, online, puzzleRating)

            AccountSection(account, settings, onChange)

            SectionTitle(stringResource(R.string.settings_appearance))
            SurfaceCard(contentPadding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    icons = listOf(R.drawable.ic_contrast, R.drawable.ic_light_mode, R.drawable.ic_dark_mode),
                    selected = settings.themeMode.ordinal,
                    onSelect = { index -> onChange { it.copy(themeMode = ThemeMode.entries[index]) } },
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_board_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                BoardThemePicker(settings.boardTheme) { theme -> onChange { it.copy(boardTheme = theme) } }
                Spacer(Modifier.height(6.dp))
                SettingSwitch(R.drawable.ic_grid_view, R.string.settings_coordinates, settings.showCoordinates) { v -> onChange { it.copy(showCoordinates = v) } }
                SettingSwitch(R.drawable.ic_visibility, R.string.settings_legal_moves, settings.showLegalMoves) { v -> onChange { it.copy(showLegalMoves = v) } }
            }

            SectionTitle(stringResource(R.string.settings_language))
            SurfaceCard(contentPadding = PaddingValues(6.dp)) {
                AppLanguage.entries.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingRow(R.drawable.ic_language, item.nativeName, onClick = { onLanguage(item) }) {
                        if (item == language) AppIcon(R.drawable.ic_check, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_sound_and_haptics))
            SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SettingSwitch(R.drawable.ic_volume_up, R.string.settings_sound, settings.sound) { v -> onChange { it.copy(sound = v) } }
                SettingSwitch(R.drawable.ic_vibration, R.string.settings_haptics, settings.haptics) { v -> onChange { it.copy(haptics = v) } }
            }

            AboutSection()
            Spacer(Modifier.navigationBarsPadding().height(BottomBarSpace))
        }
    }
}

@Composable
private fun PlayerCard(account: AccountUiState, online: OnlineUiState, puzzleRating: Int) {
    val me = account.me ?: online.me
    SurfaceCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(me?.name ?: "?", seed = me?.id ?: "me", size = 64.dp, country = me?.country)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(me?.name ?: stringResource(R.string.settings_account_offline_short), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (me != null) {
                    val scheme = MaterialTheme.colorScheme
                    if (account.registered) {
                        Pill(stringResource(R.string.account_saved_badge), icon = R.drawable.ic_lock, container = scheme.secondaryContainer, content = scheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Pill(stringResource(R.string.account_guest_badge), icon = R.drawable.ic_person, container = scheme.surfaceContainerHighest, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val ratings = online.ratings.associateBy { it.speed }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Speed.BULLET, Speed.BLITZ, Speed.RAPID).forEach { speed ->
                RatingTile(speed, ratings[speed]?.let { it.rating.toString() + if (it.provisional) "?" else "" } ?: "—", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RatingTile(Speed.CLASSICAL, ratings[Speed.CLASSICAL]?.let { it.rating.toString() + if (it.provisional) "?" else "" } ?: "—", Modifier.weight(1f))
            Column(
                Modifier.weight(2f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcon(R.drawable.ic_extension_filled, size = 16.dp, tint = Color(0xFFFFB443))
                    Text(stringResource(R.string.home_puzzles), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(puzzleRating.toString(), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun RatingTile(speed: Speed, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(speedIcon(speed), size = 16.dp, tint = speedColor(speed))
            Text(speedName(speed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

/** Mini boards to pick the colours from. */
@Composable
private fun BoardThemePicker(selected: BoardTheme, onSelect: (BoardTheme) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BoardTheme.entries.forEach { theme ->
            val palette = BoardPalette.of(theme)
            val isSelected = theme == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .pressable { onSelect(theme) }
                        .border(3.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                        .padding(5.dp)
                        .clip(RoundedCornerShape(11.dp)),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cell = size.width / 4
                        for (row in 0 until 4) for (column in 0 until 4) {
                            drawRect(if ((row + column) % 2 == 0) palette.light else palette.dark, Offset(column * cell, row * cell), Size(cell, cell))
                        }
                    }
                }
                Text(
                    stringResource(boardThemeName(theme)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun boardThemeName(theme: BoardTheme) = when (theme) {
    BoardTheme.CLASSIC -> R.string.theme_classic
    BoardTheme.GREEN -> R.string.theme_green
    BoardTheme.BLUE -> R.string.theme_blue
    BoardTheme.GRAY -> R.string.theme_gray
    BoardTheme.ROYAL -> R.string.theme_royal
    BoardTheme.NIGHT -> R.string.theme_night
}

/** A row of a settings card: icon, title, optional subtitle and something on the right. */
@Composable
internal fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable () -> Unit = {},
) {
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
    Row(
        (if (onClick != null) base.pressable(onClick = onClick) else base).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(icon, size = 22.dp, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun ColumnScope.SettingSwitch(@DrawableRes icon: Int, label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(icon, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
        )
    }
}
