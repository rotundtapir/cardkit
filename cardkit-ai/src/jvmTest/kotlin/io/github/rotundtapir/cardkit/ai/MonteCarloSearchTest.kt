// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

class MonteCarloSearchTest {

    private fun limits(
        maxDeterminizations: Int = 64,
        minDeterminizations: Int = 4,
        batchSize: Int = 4,
        timeBudgetEnabled: Boolean = false,
    ) = SearchLimits(maxDeterminizations, minDeterminizations, batchSize, timeBudgetEnabled)

    @Test
    fun `finds the best arm on a deterministic bandit`() = runTest {
        val best = MonteCarloSearch(limits()).best(
            arms = listOf(0.1, 0.9, 0.5),
            budget = 1.seconds,
            sampleWorld = { },
            evaluate = { _, arm -> arm },
        )
        assertEquals(0.9, best)
    }

    @Test
    fun `no arms yields null`() = runTest {
        val best = MonteCarloSearch(limits()).best(
            arms = emptyList<String>(),
            budget = 1.seconds,
            sampleWorld = { },
            evaluate = { _, _ -> 0.0 },
        )
        assertNull(best)
    }

    @Test
    fun `a forced move returns without sampling`() = runTest {
        var samples = 0
        val best = MonteCarloSearch(limits()).best(
            arms = listOf("only"),
            budget = 1.seconds,
            sampleWorld = { samples++ },
            evaluate = { _, _ -> 0.0 },
        )
        assertEquals("only", best)
        assertEquals(0, samples)
    }

    @Test
    fun `racing eliminates a dominated arm early`() = runTest {
        val evaluations = mutableMapOf<String, Int>()
        val random = Random(11)
        val best = MonteCarloSearch(limits(maxDeterminizations = 100, minDeterminizations = 4, batchSize = 2)).best(
            arms = listOf("good", "bad"),
            budget = 1.seconds,
            sampleWorld = { random.nextDouble() * 0.01 },
            evaluate = { noise, arm ->
                evaluations[arm] = (evaluations[arm] ?: 0) + 1
                (if (arm == "good") 1.0 else 0.0) + noise
            },
        )
        assertEquals("good", best)
        // The dominated arm stops being evaluated as soon as its confidence interval drops away,
        // ending the search itself (one live arm) far short of the determinization cap.
        assertTrue(evaluations.getValue("bad") < 20, "expected early elimination, got $evaluations")
    }

    @Test
    fun `minWorlds floor is honored past an expired budget`() = runTest {
        var samples = 0
        MonteCarloSearch(limits(minDeterminizations = 100, timeBudgetEnabled = true)).best(
            arms = listOf("a", "b"),
            budget = Duration.ZERO, // already expired: only the floor keeps sampling alive
            minWorlds = 10,
            sampleWorld = { samples++ },
            evaluate = { _, arm -> if (arm == "a") 1.0 else 0.0 },
        )
        assertEquals(10, samples)
    }

    @Test
    fun `same seed yields the same decision`() = runTest {
        suspend fun run(seed: Long): Int? {
            val random = Random(seed)
            return MonteCarloSearch(limits(maxDeterminizations = 32)).best(
                arms = listOf(0, 1, 2, 3),
                budget = 1.seconds,
                sampleWorld = { random.nextDouble() },
                evaluate = { _, arm -> arm * 0.05 + random.nextDouble() },
            )
        }
        assertEquals(run(7), run(7))
    }
}
