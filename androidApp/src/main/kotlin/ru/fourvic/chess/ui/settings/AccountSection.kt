package ru.fourvic.chess.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.BuildConfig
import ru.fourvic.chess.R
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.online.OnlineClient
import ru.fourvic.chess.settings.AppSettings
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.ServerMessage

/** Server-side account: name, deletion (a store requirement) and, in debug builds, the server address. */
@Composable
fun AccountSection(
    client: OnlineClient,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val connection by client.state.collectAsState()
    val welcome by client.welcome.collectAsState()
    var deleted by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(client) {
        client.messages.collect { if (it == ServerMessage.AccountDeleted) deleted = true }
    }

    HorizontalDivider()
    Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleSmall)
    val me = welcome?.player
    when {
        deleted -> Text(stringResource(R.string.settings_account_deleted), style = MaterialTheme.typography.bodyMedium)
        connection == ConnectionState.CONNECTED && me != null -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(me.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.settings_rename)) }
            }
            OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.settings_delete_account)) }
        }
        else -> {
            Text(stringResource(R.string.settings_account_offline), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = client::start) { Text(stringResource(R.string.online_retry)) }
        }
    }

    if (BuildConfig.DEBUG) {
        ServerAddress(settings.serverUrl) { url ->
            onChange { it.copy(serverUrl = url) }
            client.stop()
            client.start()
        }
    }

    if (renaming && me != null) {
        RenameDialog(
            current = me.name,
            onSave = { name ->
                renaming = false
                client.send(ClientMessage.Rename(name))
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = { Text(stringResource(R.string.settings_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    client.send(ClientMessage.DeleteAccount)
                }) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Same rule as on the server, checked here to explain it before sending. */
private val NAME = Regex("^[\\p{L}\\p{N}_\\- ]{3,20}$")

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    val valid = NAME.matches(name.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text(stringResource(R.string.settings_name)) },
                supportingText = { Text(stringResource(R.string.settings_name_rules)) },
                isError = !valid,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = valid) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ServerAddress(current: String, onSave: (String) -> Unit) {
    var url by rememberSaveable(current) { mutableStateOf(current) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.settings_server)) },
            supportingText = { Text(stringResource(R.string.settings_server_hint, BuildConfig.SERVER_URL)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onSave(url.trim()) }, enabled = url.trim() != current) {
            Text(stringResource(R.string.settings_save))
        }
    }
}
