package ru.fourvic.chess.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.online.OnlineClient
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.PlayerInfo
import ru.fourvic.chess.shared.protocol.ServerMessage

enum class AccountRequest { SAVE, SIGN_IN, CHANGE_PASSWORD, RENAME }

enum class AccountError { INVALID_NAME, NAME_TAKEN, WEAK_PASSWORD, WRONG_PASSWORD, TOO_MANY_ATTEMPTS, SLOW_DOWN, OFFLINE, OTHER }

data class AccountUiState(
    val connection: ConnectionState = ConnectionState.OFFLINE,
    val me: PlayerInfo? = null,
    /** Saved with a name and password, so it can be signed in to on other devices. */
    val registered: Boolean = false,
    val pending: AccountRequest? = null,
    val error: AccountError? = null,
    /** The request that has just succeeded, for a short confirmation. */
    val done: AccountRequest? = null,
    val deleted: Boolean = false,
)

/**
 * The online account in Settings: a guest saves it with a name and password, signs in to a saved account on another
 * device, changes the password or name, signs out, or deletes it. There is no email, so no personal data and no
 * password reset.
 */
class AccountViewModel(private val client: OnlineClient) : ViewModel() {

    var state by mutableStateOf(AccountUiState())
        private set

    init {
        client.start()
        viewModelScope.launch {
            client.state.collect {
                // A save or rename cannot finish without the connection; signing in reconnects on purpose.
                val down = it == ConnectionState.OFFLINE || it == ConnectionState.REPLACED
                val lost = down && state.pending != null && state.pending != AccountRequest.SIGN_IN
                state = if (lost) state.copy(connection = it, pending = null, error = AccountError.OFFLINE) else state.copy(connection = it)
            }
        }
        viewModelScope.launch {
            client.welcome.collect { w -> state = state.copy(me = w?.player, registered = w?.registered == true) }
        }
        viewModelScope.launch { client.messages.collect(::onMessage) }
    }

    fun save(name: String, password: String) = request(AccountRequest.SAVE, ClientMessage.Register(name.trim(), password))

    fun signIn(name: String, password: String) = request(AccountRequest.SIGN_IN, ClientMessage.Login(name.trim(), password))

    fun changePassword(old: String, new: String) = request(AccountRequest.CHANGE_PASSWORD, ClientMessage.ChangePassword(old, new))

    fun rename(name: String) = request(AccountRequest.RENAME, ClientMessage.Rename(name.trim()))

    fun signOut() {
        state = state.copy(pending = null, error = null, done = null)
        client.signOut()
    }

    fun delete() {
        client.send(ClientMessage.DeleteAccount)
    }

    /** After the server address has changed (debug builds). */
    fun reconnect() {
        client.stop()
        client.start()
    }

    fun retry() = client.start()

    /** Hides an error or confirmation once the user has seen it. */
    fun dismiss() {
        state = state.copy(error = null, done = null)
    }

    private fun request(kind: AccountRequest, message: ClientMessage) {
        state = if (client.send(message)) {
            state.copy(pending = kind, error = null, done = null)
        } else {
            state.copy(pending = null, error = AccountError.OFFLINE, done = null)
        }
    }

    internal fun onMessage(message: ServerMessage) {
        val pending = state.pending
        when (message) {
            is ServerMessage.Welcome -> when {
                pending == AccountRequest.SAVE || pending == AccountRequest.RENAME -> finish(pending)
                // After signing in the device connects again and the new welcome is for the saved account; a guest's
                // welcome means the connection dropped before the answer came.
                pending == AccountRequest.SIGN_IN ->
                    state = if (message.registered) state.copy(pending = null, error = null, done = pending)
                    else state.copy(pending = null, error = AccountError.OFFLINE)
            }
            ServerMessage.PasswordChanged -> if (pending == AccountRequest.CHANGE_PASSWORD) finish(pending)
            // Other errors belong to games and tournaments.
            is ServerMessage.Error -> if (pending != null && message.code in ACCOUNT_ERRORS) {
                state = state.copy(pending = null, error = errorOf(message.code))
            }
            ServerMessage.AccountDeleted -> state = state.copy(deleted = true, pending = null)
            else -> Unit
        }
    }

    private fun finish(request: AccountRequest) {
        state = state.copy(pending = null, error = null, done = request)
    }

    private fun errorOf(code: ErrorCode) = when (code) {
        ErrorCode.INVALID_NAME -> AccountError.INVALID_NAME
        ErrorCode.NAME_TAKEN -> AccountError.NAME_TAKEN
        ErrorCode.WEAK_PASSWORD -> AccountError.WEAK_PASSWORD
        ErrorCode.WRONG_PASSWORD -> AccountError.WRONG_PASSWORD
        ErrorCode.TOO_MANY_ATTEMPTS -> AccountError.TOO_MANY_ATTEMPTS
        ErrorCode.RATE_LIMITED -> AccountError.SLOW_DOWN
        else -> AccountError.OTHER
    }

    companion object {
        /** Same rules as on the server, checked here first to explain them. */
        val NAME = Regex("^[\\p{L}\\p{N}_\\- ]{3,20}$")
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 128

        private val ACCOUNT_ERRORS = setOf(
            ErrorCode.INVALID_NAME, ErrorCode.NAME_TAKEN, ErrorCode.WEAK_PASSWORD, ErrorCode.WRONG_PASSWORD,
            ErrorCode.TOO_MANY_ATTEMPTS, ErrorCode.BAD_REQUEST, ErrorCode.RATE_LIMITED,
        )
    }
}
