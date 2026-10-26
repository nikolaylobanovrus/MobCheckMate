package com.mobcheckmate.shared.tournament

/** Time control in seconds, for example 3+2 is `TimeControl(180, 2)`. */
data class TimeControl(val initialSeconds: Int, val incrementSeconds: Int) {
    init {
        require(initialSeconds >= 0 && incrementSeconds >= 0) { "Time control must not be negative" }
        require(initialSeconds > 0 || incrementSeconds > 0) { "Time control must not be 0+0" }
    }

    /**
     * Berserk halves the player's own clock. It is not offered without base time, or when the
     * increment in seconds is more than twice the base time in minutes (the increment would dominate).
     */
    val berserkAllowed: Boolean
        get() = initialSeconds > 0 && incrementSeconds * 60 <= 2 * initialSeconds

    /** The clock of a player who went berserk: half of the base time and no increment. */
    fun berserked(): TimeControl {
        check(berserkAllowed) { "Berserk is not allowed for $this" }
        return TimeControl(initialSeconds / 2, 0)
    }

    override fun toString(): String {
        val minutes = if (initialSeconds % 60 == 0) "${initialSeconds / 60}" else "${initialSeconds / 60.0}"
        return "$minutes+$incrementSeconds"
    }
}

enum class ArenaOutcome { WIN, DRAW, LOSS }

/** A finished arena game from one player's point of view. */
data class ArenaGame(
    val outcome: ArenaOutcome,
    /** Half-moves played by both sides. */
    val plies: Int,
    val berserk: Boolean = false,
)

/** Running arena standing of one player. Immutable: every game produces a new state. */
data class ArenaPlayerState(
    val score: Int = 0,
    val games: Int = 0,
    val wins: Int = 0,
    val consecutiveWins: Int = 0,
    val longestWinStreak: Int = 0,
    /** True after a draw until the next win: further draws in a row score nothing. */
    val inDrawSeries: Boolean = false,
) {
    /** "On fire" after two wins in a row: the next results are worth double. */
    val onFire: Boolean get() = consecutiveWins >= 2
}

/** Points earned for one game and the player's state after it. */
data class ScoredGame(val points: Int, val state: ArenaPlayerState)

/**
 * Arena scoring rules from docs/TOURNAMENTS.md, section 3.
 *
 * - Win 2, draw 1, loss 0; doubled while on fire (after two consecutive wins).
 * - A draw shorter than [MIN_PLIES_FOR_DRAW_POINTS] scores nothing.
 * - Only the first draw of a series scores; the series ends with a win.
 * - A berserk win adds 1 point if the game lasted at least [MIN_PLIES_FOR_BERSERK_BONUS] plies.
 */
object ArenaScoring {
    const val WIN_POINTS = 2
    const val DRAW_POINTS = 1
    const val BERSERK_BONUS = 1

    /** 10 full moves. */
    const val MIN_PLIES_FOR_DRAW_POINTS = 20

    /** 7 full moves. */
    const val MIN_PLIES_FOR_BERSERK_BONUS = 14

    fun score(state: ArenaPlayerState, game: ArenaGame): ScoredGame {
        val multiplier = if (state.onFire) 2 else 1
        return when (game.outcome) {
            ArenaOutcome.WIN -> {
                val bonus = if (game.berserk && game.plies >= MIN_PLIES_FOR_BERSERK_BONUS) BERSERK_BONUS else 0
                val points = WIN_POINTS * multiplier + bonus
                val streak = state.consecutiveWins + 1
                ScoredGame(
                    points,
                    state.copy(
                        score = state.score + points,
                        games = state.games + 1,
                        wins = state.wins + 1,
                        consecutiveWins = streak,
                        longestWinStreak = maxOf(state.longestWinStreak, streak),
                        inDrawSeries = false,
                    ),
                )
            }

            ArenaOutcome.DRAW -> {
                val counts = game.plies >= MIN_PLIES_FOR_DRAW_POINTS && !state.inDrawSeries
                val points = if (counts) DRAW_POINTS * multiplier else 0
                ScoredGame(
                    points,
                    state.copy(
                        score = state.score + points,
                        games = state.games + 1,
                        consecutiveWins = 0,
                        inDrawSeries = true,
                    ),
                )
            }

            ArenaOutcome.LOSS -> ScoredGame(
                0,
                state.copy(games = state.games + 1, consecutiveWins = 0),
            )
        }
    }

    /** Replays a player's games in order and returns the final state. */
    fun replay(games: List<ArenaGame>): ArenaPlayerState =
        games.fold(ArenaPlayerState()) { state, game -> score(state, game).state }
}
