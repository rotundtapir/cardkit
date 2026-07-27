// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.GameRules
import io.github.rotundtapir.cardkit.core.Seat
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RolloutTest {

    /** Two seats alternately add their action to a total; the game ends at 10. */
    private object CountUp : GameRules<Int, Int, Int> {
        override fun currentActor(state: Int): Seat? = if (state >= 10) null else Seat(state % 2)
        override fun isTerminal(state: Int): Boolean = state >= 10
        override fun view(state: Int, seat: Seat): Int = state
        override fun legalActions(state: Int, seat: Seat): List<Int> = listOf(1, 2)
        override fun apply(state: Int, seat: Seat, action: Int): Int = state + action
    }

    @Test
    fun `plays forward until the stop condition holds`() {
        val end = rollout(CountUp, start = 0, policy = { _, _ -> 2 }, random = Random(1)) { it >= 6 }
        assertEquals(6, end)
    }

    @Test
    fun `stops when the game has no current actor`() {
        val end = rollout(CountUp, start = 9, policy = { _, _ -> 1 }, random = Random(1)) { false }
        assertEquals(10, end) // reached terminal; currentActor == null broke the loop
    }

    @Test
    fun `an already-stopped state is returned unchanged`() {
        val end = rollout(CountUp, start = 4, policy = { _, _ -> 1 }, random = Random(1)) { true }
        assertEquals(4, end)
    }
}
