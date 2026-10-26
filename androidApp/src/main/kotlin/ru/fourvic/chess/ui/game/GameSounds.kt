package ru.fourvic.chess.ui.game

import ru.fourvic.chess.audio.Sound

/** Which sound fits the position the user just reached. */
internal fun soundFor(state: GameUiState): Sound? = when {
    state.ply == 0 -> null
    state.status.isFinished -> Sound.GAME_END
    state.checkedKing != null -> Sound.CHECK
    state.lastMoveWasCapture -> Sound.CAPTURE
    else -> Sound.MOVE
}
