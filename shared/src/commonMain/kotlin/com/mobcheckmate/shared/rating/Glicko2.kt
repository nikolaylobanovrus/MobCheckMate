package com.mobcheckmate.shared.rating

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** A player's Glicko-2 rating on the familiar Elo-like scale. */
data class Rating(
    val rating: Double = 1500.0,
    val deviation: Double = 350.0,
    val volatility: Double = 0.06,
) {
    /** A rating is provisional while its deviation is high, for example right after registration. */
    fun isProvisional(maxDeviation: Double = 110.0): Boolean = deviation > maxDeviation
}

/** One game in a rating period: the opponent's rating before the period and our score (1, 0.5 or 0). */
data class GameOutcome(val opponent: Rating, val score: Double)

/**
 * Glicko-2 rating system as described by Mark Glickman,
 * "Example of the Glicko-2 system" (http://www.glicko.net/glicko/glicko2.pdf).
 *
 * @param tau system constant that limits volatility change; 0.3..1.2, smaller is more conservative.
 */
class Glicko2(private val tau: Double = 0.5) {

    /** Returns the new rating after a rating period that contains [games]. */
    fun update(player: Rating, games: List<GameOutcome>): Rating {
        val mu = (player.rating - BASE) / SCALE
        val phi = player.deviation / SCALE
        val sigma = player.volatility

        if (games.isEmpty()) {
            val newPhi = sqrt(phi * phi + sigma * sigma)
            return player.copy(deviation = (newPhi * SCALE).coerceAtMost(MAX_DEVIATION))
        }

        var vInverse = 0.0
        var deltaSum = 0.0
        for (game in games) {
            val muJ = (game.opponent.rating - BASE) / SCALE
            val phiJ = game.opponent.deviation / SCALE
            val g = g(phiJ)
            val e = expectedScore(mu, muJ, g)
            vInverse += g * g * e * (1 - e)
            deltaSum += g * (game.score - e)
        }
        val v = 1 / vInverse
        val delta = v * deltaSum

        val newSigma = newVolatility(phi, sigma, v, delta)
        val phiStar = sqrt(phi * phi + newSigma * newSigma)
        val newPhi = 1 / sqrt(1 / (phiStar * phiStar) + 1 / v)
        val newMu = mu + newPhi * newPhi * deltaSum

        return Rating(
            rating = newMu * SCALE + BASE,
            deviation = (newPhi * SCALE).coerceAtMost(MAX_DEVIATION),
            volatility = newSigma,
        )
    }

    private fun g(phi: Double): Double = 1 / sqrt(1 + 3 * phi * phi / (PI * PI))

    private fun expectedScore(mu: Double, muJ: Double, g: Double): Double = 1 / (1 + exp(-g * (mu - muJ)))

    /** Step 5 of the paper: the Illinois variant of regula falsi. */
    private fun newVolatility(phi: Double, sigma: Double, v: Double, delta: Double): Double {
        val a = ln(sigma * sigma)
        fun f(x: Double): Double {
            val ex = exp(x)
            val num = ex * (delta * delta - phi * phi - v - ex)
            val den = 2 * (phi * phi + v + ex) * (phi * phi + v + ex)
            return num / den - (x - a) / (tau * tau)
        }

        var lower = a
        var upper = if (delta * delta > phi * phi + v) {
            ln(delta * delta - phi * phi - v)
        } else {
            var k = 1
            while (f(a - k * tau) < 0) k++
            a - k * tau
        }
        var fLower = f(lower)
        var fUpper = f(upper)
        while (abs(upper - lower) > EPSILON) {
            val c = lower + (lower - upper) * fLower / (fUpper - fLower)
            val fC = f(c)
            if (fC * fUpper <= 0) {
                lower = upper
                fLower = fUpper
            } else {
                fLower /= 2
            }
            upper = c
            fUpper = fC
        }
        return exp(lower / 2)
    }

    private companion object {
        const val BASE = 1500.0
        const val SCALE = 173.7178
        const val EPSILON = 0.000001
        const val MAX_DEVIATION = 350.0
    }
}
