package ru.fourvic.chess

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.fourvic.chess.online.OnlineClient
import ru.fourvic.chess.online.PrefsTokenStore
import ru.fourvic.chess.online.WebSocketOnlineClient
import ru.fourvic.chess.settings.SettingsRepository

/** Holds what lives as long as the process: settings and the single connection to the game server. */
class ChessApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val online: OnlineClient by lazy {
        WebSocketOnlineClient(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            tokens = PrefsTokenStore(this),
            serverUrl = { settings.settings.value.serverUrl.ifBlank { BuildConfig.SERVER_URL } },
        )
    }
}
