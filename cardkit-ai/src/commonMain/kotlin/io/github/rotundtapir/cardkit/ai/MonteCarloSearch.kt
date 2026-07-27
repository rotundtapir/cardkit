// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.yield

/**
 * Game-agnostic tuning knobs for [MonteCarloSearch]. Wall-clock budgets are per decision and are
 * passed to [MonteCarloSearch.best] by the game (which knows its own phases and stakes); these
 * limits bound the sampling itself. Tests use [timeBudgetEnabled] = false with a small
 * [maxDeterminizations] so a decision is a pure function of its inputs — reproducible anywhere.
 */
data class SearchLimits(
    /** Hard cap on sampled worlds per decision — fast devices stop well before the wall clock. */
    val maxDeterminizations: Int = 192,
    /**
     * Worlds sampled before racing elimination may start dropping arms. Games may also pass it as
     * the `minWorlds` floor of a high-stakes decision (500 and Euchre do for bids), so a slow
     * single-threaded platform never commits off a statistically meaningless handful of samples.
     */
    val minDeterminizations: Int = 32,
    /** Racing elimination runs every this-many worlds (once past [minDeterminizations]). */
    val batchSize: Int = 8,
    /** False disables the wall clock entirely: fixed-iteration deterministic mode for tests. */
    val timeBudgetEnabled: Boolean = true,
)

/**
 * Determinized flat Monte-Carlo arm selection with paired sampling and racing early-stops.
 *
 * Each decision evaluates every candidate [best] arm against the *same* sampled worlds (paired
 * sampling cancels deal variance), keeps a [RunningStat] per arm, and every [SearchLimits.batchSize]
 * worlds drops arms whose confidence interval sits wholly below the best arm's. Obvious decisions
 * collapse after a couple of batches; forced moves return without sampling at all; the budgets are
 * hard caps, not typical costs.
 *
 * [best] is `suspend` and yields between worlds so a single-threaded event loop (wasm) stays
 * responsive; on the JVM run it on a background dispatcher.
 */
class MonteCarloSearch(private val limits: SearchLimits) {

    /**
     * The best of [arms] under [evaluate] across up to [SearchLimits.maxDeterminizations] worlds
     * from [sampleWorld], or null for no arms. At least [minWorlds] worlds are sampled even past
     * the [budget] deadline (a floored decision may softly exceed its budget on very slow devices).
     * The exact loop order — sample, evaluate live arms in index order, eliminate, yield — is part
     * of the contract: callers rely on it for seeded reproducibility.
     */
    suspend fun <World, Arm> best(
        arms: List<Arm>,
        budget: Duration,
        minWorlds: Int = 1,
        sampleWorld: () -> World,
        evaluate: (World, Arm) -> Double,
    ): Arm? {
        if (arms.size <= 1) return arms.firstOrNull() // forced move: no sampling, no budget spent
        val start = TimeSource.Monotonic.markNow()
        val stats = List(arms.size) { RunningStat() }
        val live = arms.indices.toMutableList()
        var worlds = 0
        while (worlds < limits.maxDeterminizations && live.size > 1 && withinBudget(start, budget, worlds, minWorlds)) {
            val world = sampleWorld()
            // Paired sampling: every live arm scores against the same world, cancelling deal variance.
            live.forEach { stats[it].add(evaluate(world, arms[it])) }
            worlds++
            if (worlds >= limits.minDeterminizations && worlds % limits.batchSize == 0) {
                raceEliminate(live, stats)
            }
            yield() // keep the single-threaded wasm event loop responsive during long thinks
        }
        return arms[live.maxBy { stats[it].mean }]
    }

    private fun withinBudget(start: TimeMark, budget: Duration, worlds: Int, minWorlds: Int): Boolean =
        worlds < minWorlds || !limits.timeBudgetEnabled || start.elapsedNow() < budget

    /** Drops every arm whose confidence interval sits wholly below the best arm's. */
    private fun raceEliminate(live: MutableList<Int>, stats: List<RunningStat>) {
        val best = live.maxBy { stats[it].mean }
        val bestLow = stats[best].mean - RACE_Z * stats[best].standardError
        live.removeAll { it != best && stats[it].mean + RACE_Z * stats[it].standardError < bestLow }
    }

    private companion object {
        /** z-score for racing elimination: ~95% confidence before an arm is dropped. */
        const val RACE_Z = 2.0
    }
}
