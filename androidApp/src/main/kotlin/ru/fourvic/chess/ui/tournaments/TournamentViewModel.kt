package ru.fourvic.chess.ui.tournaments

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.fourvic.chess.online.ConnectionState
import ru.fourvic.chess.online.OnlineClient
import ru.fourvic.chess.shared.protocol.ClientMessage
import ru.fourvic.chess.shared.protocol.Clock
import ru.fourvic.chess.shared.protocol.ErrorCode
import ru.fourvic.chess.shared.protocol.ServerMessage
import ru.fourvic.chess.shared.protocol.Standing
import ru.fourvic.chess.shared.protocol.TournamentInfo
import ru.fourvic.chess.shared.protocol.TournamentStatus

/** What the tournament screens show. Times left are counted from [receivedAt] on the device clock. */
data class TournamentsUiState(
    val connection: ConnectionState = ConnectionState.OFFLINE,
    val list: List<TournamentInfo> = emptyList(),
    val listReceivedAt: Long = 0,
    val loading: Boolean = true,
    /** The tournament open on screen, if any. */
    val selectedId: String? = null,
    val detail: TournamentInfo? = null,
    val standings: List<Standing> = emptyList(),
    val me: Standing? = null,
    val receivedAt: Long = 0,
    val error: TournamentError? = null,
    val busy: Boolean = false,
) {
    val joined: Boolean get() = me != null
    val playing: Boolean get() = me != null && !me.paused && detail?.status != TournamentStatus.FINISHED
}

enum class TournamentError { NOT_FOUND, REJECTED }

/**
 * Tournament list, private arenas by code, and one tournament's table. The user's arena games are played in a
 * separate [ru.fourvic.chess.ui.online.OnlineViewModel] in arena mode.
 *
 * Leaving the screen or sending the app to the background pauses the user so that they are not paired while away;
 * coming back to the screen resumes such an automatic pause.
 */
class TournamentViewModel(
    private val client: OnlineClient,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    var state by mutableStateOf(TournamentsUiState())
        private set

    private var awaitingOwnTournament = false
    private var autoPaused = false

    init {
        client.start()
        viewModelScope.launch { client.messages.collect(::onMessage) }
        viewModelScope.launch {
            client.state.collect {
                state = state.copy(connection = it)
                // After every (re)connect, ask again for what is on screen.
                if (it == ConnectionState.CONNECTED) resync()
            }
        }
    }

    fun refreshList() {
        client.send(ClientMessage.ListTournaments)
    }

    fun open(id: String) {
        state = state.copy(selectedId = id, detail = null, standings = emptyList(), me = null, error = null)
        client.send(ClientMessage.WatchTournament(id))
    }

    /** Back to the list; a player still in the pairing pool is paused. */
    fun close() {
        val id = state.selectedId ?: return
        if (state.playing) client.send(ClientMessage.PauseTournament(id))
        client.send(ClientMessage.WatchTournament(null))
        autoPaused = false
        state = state.copy(selectedId = null, detail = null, standings = emptyList(), me = null)
        refreshList()
    }

    fun join() {
        val id = state.selectedId ?: return
        autoPaused = false
        if (client.send(ClientMessage.JoinTournament(id))) state = state.copy(busy = true)
    }

    fun pause() {
        val id = state.selectedId ?: return
        autoPaused = false
        if (client.send(ClientMessage.PauseTournament(id))) state = state.copy(busy = true)
    }

    fun joinByCode(code: String) {
        val trimmed = code.trim().uppercase()
        if (trimmed.isEmpty()) return
        awaitingOwnTournament = true
        state = state.copy(error = null)
        client.send(ClientMessage.JoinTournament(trimmed))
    }

    fun create(name: String, clock: Clock, minutes: Int, startsInMinutes: Int, rated: Boolean) {
        awaitingOwnTournament = true
        state = state.copy(error = null)
        client.send(ClientMessage.CreateTournament(name.trim(), clock, minutes, startsInMinutes, rated))
    }

    fun dismissError() {
        state = state.copy(error = null)
    }

    /** The app went to the background: leave the pool until the user is back. */
    fun onBackground() {
        val id = state.selectedId ?: return
        if (state.playing) {
            autoPaused = true
            client.send(ClientMessage.PauseTournament(id))
        }
    }

    fun onForeground() {
        val id = state.selectedId ?: return
        if (autoPaused) {
            autoPaused = false
            client.send(ClientMessage.JoinTournament(id))
        }
    }

    internal fun onMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Tournaments -> state = state.copy(list = message.tournaments, listReceivedAt = nowMs(), loading = false)
            is ServerMessage.Tournament -> {
                if (awaitingOwnTournament) {
                    // The reply to a code or a newly created arena opens it.
                    awaitingOwnTournament = false
                    state = state.copy(selectedId = message.info.id)
                }
                if (message.info.id == state.selectedId) {
                    state = state.copy(
                        detail = message.info,
                        standings = message.standings,
                        me = message.me,
                        receivedAt = nowMs(),
                        busy = false,
                        error = null,
                    )
                }
            }
            is ServerMessage.Error -> when {
                message.code == ErrorCode.NO_SUCH_TOURNAMENT -> {
                    awaitingOwnTournament = false
                    state = state.copy(error = TournamentError.NOT_FOUND, busy = false)
                }
                awaitingOwnTournament || state.busy -> {
                    awaitingOwnTournament = false
                    state = state.copy(error = TournamentError.REJECTED, busy = false)
                }
            }
            else -> Unit
        }
    }

    private fun resync() {
        val id = state.selectedId
        if (id == null) refreshList() else client.send(ClientMessage.WatchTournament(id))
    }

    override fun onCleared() {
        if (state.playing) state.selectedId?.let { client.send(ClientMessage.PauseTournament(it)) }
    }
}
