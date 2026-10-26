package ru.fourvic.chess.shared.tournament

import ru.fourvic.chess.shared.tournament.ArenaOutcome.DRAW
import ru.fourvic.chess.shared.tournament.ArenaOutcome.LOSS
import ru.fourvic.chess.shared.tournament.ArenaOutcome.WIN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaTest {

    private val long = 60

    @Test
    fun winsAreDoubledAfterTwoInARow() {
        val points = mutableListOf<Int>()
        var state = ArenaPlayerState()
        repeat(4) {
            val scored = ArenaScoring.score(state, ArenaGame(WIN, long))
            points += scored.points
            state = scored.state
        }
        assertEquals(listOf(2, 2, 4, 4), points)
        assertEquals(12, state.score)
        assertEquals(4, state.longestWinStreak)
    }

    @Test
    fun lossEndsTheFire() {
        val state = ArenaScoring.replay(listOf(ArenaGame(WIN, long), ArenaGame(WIN, long), ArenaGame(LOSS, long)))
        assertFalse(state.onFire)
        assertEquals(2, ArenaScoring.score(state, ArenaGame(WIN, long)).points)
    }

    @Test
    fun drawOnFireIsDoubledAndEndsTheFire() {
        val onFire = ArenaScoring.replay(listOf(ArenaGame(WIN, long), ArenaGame(WIN, long)))
        val scored = ArenaScoring.score(onFire, ArenaGame(DRAW, long))
        assertEquals(2, scored.points)
        assertFalse(scored.state.onFire)
    }

    @Test
    fun shortDrawScoresNothing() {
        val scored = ArenaScoring.score(ArenaPlayerState(), ArenaGame(DRAW, plies = 19))
        assertEquals(0, scored.points)
        assertEquals(1, ArenaScoring.score(ArenaPlayerState(), ArenaGame(DRAW, plies = 20)).points)
    }

    @Test
    fun onlyTheFirstDrawInASeriesScoresUntilAWin() {
        val games = listOf(
            ArenaGame(DRAW, long), // 1
            ArenaGame(DRAW, long), // 0, series continues
            ArenaGame(LOSS, long), // 0, a loss does not end the series
            ArenaGame(DRAW, long), // 0
            ArenaGame(WIN, long), // 2, series ends
            ArenaGame(DRAW, long), // 1
        )
        assertEquals(4, ArenaScoring.replay(games).score)
    }

    @Test
    fun berserkWinNeedsSevenMoves() {
        assertEquals(3, ArenaScoring.score(ArenaPlayerState(), ArenaGame(WIN, plies = 14, berserk = true)).points)
        assertEquals(2, ArenaScoring.score(ArenaPlayerState(), ArenaGame(WIN, plies = 13, berserk = true)).points)
    }

    @Test
    fun berserkBonusIsNotDoubledOnFire() {
        val onFire = ArenaScoring.replay(listOf(ArenaGame(WIN, long), ArenaGame(WIN, long)))
        assertEquals(5, ArenaScoring.score(onFire, ArenaGame(WIN, long, berserk = true)).points)
    }

    @Test
    fun berserkAvailability() {
        assertTrue(TimeControl(180, 2).berserkAllowed)
        assertTrue(TimeControl(60, 2).berserkAllowed)
        assertFalse(TimeControl(60, 3).berserkAllowed)
        assertFalse(TimeControl(0, 1).berserkAllowed)
        assertEquals(TimeControl(90, 0), TimeControl(180, 2).berserked())
        assertFailsWith<IllegalStateException> { TimeControl(0, 2).berserked() }
        assertEquals("3+2", TimeControl(180, 2).toString())
    }
}
