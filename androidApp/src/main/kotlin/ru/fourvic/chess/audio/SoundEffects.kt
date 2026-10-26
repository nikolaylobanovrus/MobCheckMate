package ru.fourvic.chess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.fourvic.chess.R
import ru.fourvic.chess.settings.LocalAppSettings

enum class Sound { MOVE, CAPTURE, CHECK, GAME_END }

/** Short UI sounds played through a SoundPool. */
class SoundEffects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = mapOf(
        Sound.MOVE to pool.load(context, R.raw.move, 1),
        Sound.CAPTURE to pool.load(context, R.raw.capture, 1),
        Sound.CHECK to pool.load(context, R.raw.check, 1),
        Sound.GAME_END to pool.load(context, R.raw.game_end, 1),
    )

    fun play(sound: Sound) {
        ids[sound]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun release() = pool.release()
}

/**
 * Plays a sound whenever the position changes: [key] identifies the position (for example the move count),
 * [sound] says which sound fits it, or null for none.
 */
@Composable
fun MoveSounds(key: Any?, sound: Sound?) {
    val context = LocalContext.current
    val enabled = LocalAppSettings.current.sound
    val effects = remember { SoundEffects(context.applicationContext) }
    DisposableEffect(effects) { onDispose { effects.release() } }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(key) {
        if (first[0]) {
            first[0] = false // do not play for the position the screen opens with
        } else if (enabled && sound != null) {
            effects.play(sound)
        }
    }
}
