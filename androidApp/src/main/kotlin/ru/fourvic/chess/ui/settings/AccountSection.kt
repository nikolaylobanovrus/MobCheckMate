package ru.fourvic.chess.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.fourvic.chess.BuildConfig
import ru.fourvic.chess.R
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.settings.AppSettings

private enum class AccountDialog { SAVE, SIGN_IN, CHANGE_PASSWORD, RENAME, SIGN_OUT, DELETE }

/**
 * The online account: a guest on this phone by default; saved with a name and password to play from other devices.
 * Also deletion (a store requirement) and, in debug builds, the server address.
 */
@Composable
fun AccountSection(
    viewModel: AccountViewModel,
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    showServerAddress: Boolean = BuildConfig.DEBUG,
) {
    val state = viewModel.state
    var dialog by rememberSaveable { mutableStateOf<AccountDialog?>(null) }
    // A dialog closes once its request has succeeded; errors are shown inside it.
    LaunchedEffect(state.done) { if (state.done != null) dialog = null }
    val open = { d: AccountDialog ->
        viewModel.dismiss()
        dialog = d
    }

    HorizontalDivider()
    Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleSmall)
    val me = state.me
    when {
        state.deleted -> Text(stringResource(R.string.settings_account_deleted), style = MaterialTheme.typography.bodyMedium)
        state.connection != ConnectionState.CONNECTED || me == null -> {
            if (state.pending == AccountRequest.SIGN_IN) {
                Pending()
            } else if (state.connection == ConnectionState.REPLACED) {
                Text(stringResource(R.string.online_replaced), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.online_play_here)) }
            } else {
                Text(stringResource(R.string.settings_account_offline), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.online_retry)) }
            }
        }
        else -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(me.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Badge(state.registered)
            }
            Text(
                stringResource(if (state.registered) R.string.account_saved_hint else R.string.account_guest_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.done?.let { Text(stringResource(doneText(it)), color = MaterialTheme.colorScheme.primary) }
            if (dialog == null) state.error?.let { Text(stringResource(errorText(it)), color = MaterialTheme.colorScheme.error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.registered) {
                    OutlinedButton(onClick = { open(AccountDialog.CHANGE_PASSWORD) }) { Text(stringResource(R.string.account_change_password)) }
                    OutlinedButton(onClick = { open(AccountDialog.SIGN_OUT) }) { Text(stringResource(R.string.account_sign_out)) }
                } else {
                    Button(onClick = { open(AccountDialog.SAVE) }) { Text(stringResource(R.string.account_save)) }
                    OutlinedButton(onClick = { open(AccountDialog.SIGN_IN) }) { Text(stringResource(R.string.account_sign_in)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { open(AccountDialog.RENAME) }) { Text(stringResource(R.string.settings_rename)) }
                TextButton(onClick = { open(AccountDialog.DELETE) }) {
                    Text(stringResource(R.string.settings_delete_account), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showServerAddress) {
        ServerAddress(settings.serverUrl) { url ->
            onChange { it.copy(serverUrl = url) }
            viewModel.reconnect()
        }
    }

    val close = {
        dialog = null
        viewModel.dismiss()
    }
    val busy = state.pending != null
    val error = state.error?.let { stringResource(errorText(it)) }
    when (dialog) {
        AccountDialog.SAVE -> SaveDialog(me?.name.orEmpty(), busy, error, onSave = viewModel::save, onDismiss = close)
        AccountDialog.SIGN_IN -> SignInDialog(busy, error, onSignIn = viewModel::signIn, onDismiss = close)
        AccountDialog.CHANGE_PASSWORD -> ChangePasswordDialog(busy, error, onChange = viewModel::changePassword, onDismiss = close)
        AccountDialog.RENAME -> RenameDialog(me?.name.orEmpty(), busy, error, onSave = viewModel::rename, onDismiss = close)
        AccountDialog.SIGN_OUT -> Confirm(
            title = R.string.account_sign_out_title,
            text = R.string.account_sign_out_text,
            action = R.string.account_sign_out,
            onConfirm = {
                dialog = null
                viewModel.signOut()
            },
            onDismiss = close,
        )
        AccountDialog.DELETE -> Confirm(
            title = R.string.settings_delete_title,
            text = R.string.settings_delete_text,
            action = R.string.settings_delete,
            onConfirm = {
                dialog = null
                viewModel.delete()
            },
            onDismiss = close,
        )
        null -> Unit
    }
}

private fun doneText(request: AccountRequest) = when (request) {
    AccountRequest.SAVE -> R.string.account_saved
    AccountRequest.SIGN_IN -> R.string.account_signed_in
    AccountRequest.CHANGE_PASSWORD -> R.string.account_password_changed
    AccountRequest.RENAME -> R.string.account_renamed
}

private fun errorText(error: AccountError) = when (error) {
    AccountError.INVALID_NAME -> R.string.settings_name_rules
    AccountError.NAME_TAKEN -> R.string.account_error_name_taken
    AccountError.WEAK_PASSWORD -> R.string.account_password_rules
    AccountError.WRONG_PASSWORD -> R.string.account_error_wrong_password
    AccountError.TOO_MANY_ATTEMPTS -> R.string.account_error_too_many
    AccountError.SLOW_DOWN -> R.string.account_error_slow_down
    AccountError.OFFLINE -> R.string.account_error_offline
    AccountError.OTHER -> R.string.account_error_other
}

private fun nameValid(name: String) = AccountViewModel.NAME.matches(name.trim())
private fun passwordValid(password: String) = password.length in AccountViewModel.MIN_PASSWORD..AccountViewModel.MAX_PASSWORD

/** "Guest" or "Saved" next to the name. */
@Composable
private fun Badge(registered: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (registered) colors.secondaryContainer else colors.surfaceVariant,
        contentColor = if (registered) colors.onSecondaryContainer else colors.onSurfaceVariant,
    ) {
        Text(
            stringResource(if (registered) R.string.account_saved_badge else R.string.account_guest_badge),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun Pending() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.account_wait), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A dialog with fields, a progress line while the server answers, and the server's error. */
@Composable
private fun FormDialog(
    title: Int,
    action: Int,
    enabled: Boolean,
    busy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                if (busy) Pending() else error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled && !busy) { Text(stringResource(action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, showRules: Boolean, newName: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(20)) },
        label = { Text(stringResource(R.string.settings_name)) },
        supportingText = if (showRules) ({ Text(stringResource(R.string.settings_name_rules)) }) else null,
        isError = showRules && value.isNotEmpty() && !nameValid(value),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = if (newName) ContentType.NewUsername else ContentType.Username },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    newPassword: Boolean,
    supporting: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(AccountViewModel.MAX_PASSWORD)) },
        label = { Text(stringResource(label)) },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = if (newPassword) ContentType.NewPassword else ContentType.Password },
    )
}

/** New password twice, so that a typo does not lock the player out: there is no way to reset it. */
@Composable
private fun NewPasswordFields(password: String, onPassword: (String) -> Unit, repeat: String, onRepeat: (String) -> Unit) {
    PasswordField(
        password, onPassword, R.string.account_password, newPassword = true,
        supporting = stringResource(R.string.account_password_rules),
        isError = password.isNotEmpty() && !passwordValid(password),
    )
    PasswordField(
        repeat, onRepeat, R.string.account_password_repeat, newPassword = true,
        supporting = if (repeat.isNotEmpty() && repeat != password) stringResource(R.string.account_password_mismatch) else null,
        isError = repeat.isNotEmpty() && repeat != password,
    )
}

@Composable
private fun SaveDialog(current: String, busy: Boolean, error: String?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    FormDialog(
        title = R.string.account_save_title,
        action = R.string.settings_save,
        enabled = nameValid(name) && passwordValid(password) && repeat == password,
        busy = busy,
        error = error,
        onConfirm = { onSave(name, password) },
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.account_save_text), style = MaterialTheme.typography.bodyMedium)
        NameField(name, { name = it }, showRules = true, newName = true)
        NewPasswordFields(password, { password = it }, repeat, { repeat = it })
    }
}

@Composable
private fun SignInDialog(busy: Boolean, error: String?, onSignIn: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    FormDialog(
        title = R.string.account_sign_in_title,
        action = R.string.account_sign_in_action,
        enabled = name.isNotBlank() && password.isNotEmpty(),
        busy = busy,
        error = error,
        onConfirm = { onSignIn(name, password) },
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.account_sign_in_text), style = MaterialTheme.typography.bodyMedium)
        NameField(name, { name = it }, showRules = false, newName = false)
        PasswordField(password, { password = it }, R.string.account_password, newPassword = false)
    }
}

@Composable
private fun ChangePasswordDialog(busy: Boolean, error: String?, onChange: (String, String) -> Unit, onDismiss: () -> Unit) {
    var old by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    FormDialog(
        title = R.string.account_change_password,
        action = R.string.settings_save,
        enabled = old.isNotEmpty() && passwordValid(password) && repeat == password,
        busy = busy,
        error = error,
        onConfirm = { onChange(old, password) },
        onDismiss = onDismiss,
    ) {
        PasswordField(old, { old = it }, R.string.account_password_old, newPassword = false)
        NewPasswordFields(password, { password = it }, repeat, { repeat = it })
    }
}

@Composable
private fun RenameDialog(current: String, busy: Boolean, error: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    FormDialog(
        title = R.string.settings_rename,
        action = R.string.settings_save,
        enabled = nameValid(name) && name.trim() != current,
        busy = busy,
        error = error,
        onConfirm = { onSave(name) },
        onDismiss = onDismiss,
    ) {
        NameField(name, { name = it }, showRules = true, newName = true)
    }
}

@Composable
private fun Confirm(title: Int, text: Int, action: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(action), color = MaterialTheme.colorScheme.error) }
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
