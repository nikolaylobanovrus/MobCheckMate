package com.mobcheckmate.shared.rating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Glicko2Test {

    @Test
    fun matchesTheWorkedExampleFromGlickmansPaper() {
        val player = Rating(1500.0, 200.0, 0.06)
        val games = listOf(
            GameOutcome(Rating(1400.0, 30.0), 1.0),
            GameOutcome(Rating(1550.0, 100.0), 0.0),
            GameOutcome(Rating(1700.0, 300.0), 0.0),
        )

        val updated = Glicko2(tau = 0.5).update(player, games)

        assertEquals(1464.06, updated.rating, 0.01)
        assertEquals(151.52, updated.deviation, 0.01)
        assertEquals(0.05999, updated.volatility, 0.00001)
    }

    @Test
    fun inactivityOnlyIncreasesDeviation() {
        val player = Rating(1700.0, 50.0, 0.06)
        val updated = Glicko2().update(player, emptyList())
        assertEquals(1700.0, updated.rating)
        assertTrue(updated.deviation > player.deviation)
    }

    @Test
    fun ratingChangeIsSymmetricForEqualPlayers() {
        val a = Rating(1500.0, 80.0, 0.06)
        val b = Rating(1500.0, 80.0, 0.06)
        val glicko = Glicko2()
        val winner = glicko.update(a, listOf(GameOutcome(b, 1.0)))
        val loser = glicko.update(b, listOf(GameOutcome(a, 0.0)))
        assertEquals(winner.rating - 1500.0, 1500.0 - loser.rating, 1e-9)
    }

    @Test
    fun newPlayersAreProvisional() {
        assertTrue(Rating().isProvisional())
        assertFalse(Rating(deviation = 60.0).isProvisional())
    }
}
