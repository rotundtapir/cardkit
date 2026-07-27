// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.pacing

import io.github.rotundtapir.cardkit.ui.deal.dealTimings
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [PacingGates] over the [TableTransitions] seam, asserting the exact behaviour the
 * gates had in the 500 app (its GameViewModelTest drove them through a whole game; here the same
 * gate semantics are pinned directly): OFF-speed inertness, the deal gate arming only at hand
 * start, hand-result and trick acknowledgement gating, and the deadlock backstop.
 */
class PacingGatesTest {

    private data class FakeView(
        override val handNumber: Int = 1,
        override val trickNumber: Int = 0,
        override val trickCardCount: Int = 0,
        override val handResultCount: Int = 0,
        override val isHandStart: Boolean = false,
        override val awaitingHandResultAck: Boolean = false,
    ) : TableTransitions

    /** 500's dealPauseMillis wiring: the full deal estimate from the shared deal timings + slack. */
    private fun dealPauseMillis(speed: AnimationSpeed): Long =
        if (speed == AnimationSpeed.OFF) {
            0L
        } else {
            dealTimings(speed).run { shuffleMillis + flyBudgetMillis + flipTotalMillis(HAND_SIZE) + PAUSE_SLACK }
        }

    private fun gates(
        speed: AnimationSpeed,
        holdTricks: Boolean = false,
    ): Triple<PacingGates, MutableStateFlow<AnimationSpeed>, MutableStateFlow<Boolean>> {
        val speedFlow = MutableStateFlow(speed)
        val holdFlow = MutableStateFlow(holdTricks)
        return Triple(PacingGates(speedFlow, holdFlow, ::dealPauseMillis), speedFlow, holdFlow)
    }

    /** Launches `awaitGates(view)` and reports whether it has completed. */
    private fun TestScope.awaiting(pacing: PacingGates, view: TableTransitions): Job =
        backgroundScope.launch { pacing.awaitGates(view) }

    @Test
    fun `all gates are inert at OFF - hand start with hold on passes instantly`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.OFF, holdTricks = true)
        // A fresh-hand view AND a between-tricks view, with no signal ever raised: neither may wait.
        val handStart = FakeView(handNumber = 3, isHandStart = true)
        val betweenTricks = FakeView(handNumber = 3, trickNumber = 5)
        val job1 = awaiting(pacing, handStart)
        val job2 = awaiting(pacing, betweenTricks)
        runCurrent()
        assertTrue(job1.isCompleted, "the deal gate must be inert at OFF")
        assertTrue(job2.isCompleted, "the trick gates must be inert at OFF")
    }

    @Test
    fun `awaitHandRevealed is fully inert at OFF even with an unacknowledged result`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.OFF)
        val view = FakeView(handNumber = 2, awaitingHandResultAck = true)
        val job = backgroundScope.launch { pacing.awaitHandRevealed(view) }
        runCurrent()
        assertTrue(job.isCompleted, "awaitHandRevealed must return immediately at OFF")
    }

    @Test
    fun `the hand-result ack gate is the one gate still active at OFF`() = runTest {
        // 500's connected suites rely on exactly this split: at OFF nothing waits EXCEPT the next
        // hand's first actor, which still needs the result dialog dismissed.
        val (pacing, _, _) = gates(AnimationSpeed.OFF)
        val view = FakeView(handNumber = 2, isHandStart = true, awaitingHandResultAck = true)
        val job = awaiting(pacing, view)
        advanceTimeBy(1_000_000)
        runCurrent()
        assertFalse(job.isCompleted, "hand 2 must hold until hand 2's result ack")

        pacing.acknowledgeHandResult(2)
        runCurrent()
        assertTrue(job.isCompleted, "acknowledging the result releases the next hand")
    }

    @Test
    fun `the deal gate arms only at hand start`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.NORMAL)
        // Mid-hand view (not hand start, no completed tricks): no gate applies at all.
        val midHand = FakeView(handNumber = 1, isHandStart = false)
        val job = awaiting(pacing, midHand)
        runCurrent()
        assertTrue(job.isCompleted, "a non-hand-start view must not wait on the deal gate")
    }

    @Test
    fun `a hand start waits for the deal-done signal then the bot beat`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.NORMAL)
        val view = FakeView(handNumber = 1, isHandStart = true)
        val job = awaiting(pacing, view)
        // Well short of the backstop (~13.4s at NORMAL): still waiting on the signal.
        advanceTimeBy(3_000)
        runCurrent()
        assertFalse(job.isCompleted, "the first actor must not be released before the deal finishes")

        pacing.dealAnimationFinished(1)
        runCurrent()
        assertFalse(job.isCompleted, "the cosmetic bot beat still applies after the signal")
        advanceTimeBy(AnimationSpeed.NORMAL.botDelayMillis)
        runCurrent()
        assertTrue(job.isCompleted, "signal + bot beat releases the first actor")
    }

    @Test
    fun `the backstop releases a hand start even if the deal signal never arrives`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.NORMAL)
        val view = FakeView(handNumber = 1, isHandStart = true)
        val job = awaiting(pacing, view)
        advanceTimeBy(3_000)
        runCurrent()
        assertFalse(job.isCompleted)
        // Never signal dealAnimationFinished; the 3x-dealPause backstop must release the game.
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(job.isCompleted, "a lost deal signal must not wedge the game")
    }

    @Test
    fun `a held trick releases on acknowledge`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.NORMAL, holdTricks = true)
        val betweenTricks = FakeView(handNumber = 2, trickNumber = 4)
        val job = awaiting(pacing, betweenTricks)
        advanceTimeBy(1_000_000)
        runCurrent()
        assertFalse(job.isCompleted, "with hold on, the next leader waits for the tap")

        pacing.acknowledgeTrick(handNumber = 2, trickNumber = 4)
        runCurrent()
        assertTrue(job.isCompleted, "acknowledging the trick releases the next leader")
    }

    @Test
    fun `a held trick also releases when hold is toggled off live`() = runTest {
        val (pacing, _, holdFlow) = gates(AnimationSpeed.NORMAL, holdTricks = true)
        val betweenTricks = FakeView(handNumber = 1, trickNumber = 2)
        val job = awaiting(pacing, betweenTricks)
        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(job.isCompleted)

        holdFlow.value = false
        runCurrent()
        assertTrue(job.isCompleted, "clearing holdTricks must let play continue past the held trick")
    }

    @Test
    fun `an acknowledgement for a different trick does not release the hold`() = runTest {
        // The gate key is per (hand, trick): a stale ack (previous hand's trick 4) must not leak.
        val (pacing, _, _) = gates(AnimationSpeed.NORMAL, holdTricks = true)
        pacing.acknowledgeTrick(handNumber = 1, trickNumber = 9)
        val betweenTricks = FakeView(handNumber = 2, trickNumber = 4)
        val job = awaiting(pacing, betweenTricks)
        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(job.isCompleted, "hand 1's acks must not unlock hand 2's tricks")
        pacing.acknowledgeTrick(handNumber = 2, trickNumber = 4)
        runCurrent()
        assertTrue(job.isCompleted)
    }

    @Test
    fun `without hold a fresh trick pauses for the timed inter-trick beat`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.NORMAL, holdTricks = false)
        val betweenTricks = FakeView(handNumber = 1, trickNumber = 1)
        val job = awaiting(pacing, betweenTricks)
        advanceTimeBy(999)
        runCurrent()
        assertFalse(job.isCompleted, "the NORMAL inter-trick pause is 1000ms")
        advanceTimeBy(1)
        runCurrent()
        assertTrue(job.isCompleted)
    }

    @Test
    fun `no trick gate applies while a trick is open or before the first trick`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.SLOW, holdTricks = true)
        val openTrick = FakeView(handNumber = 1, trickNumber = 3, trickCardCount = 2)
        val beforeFirst = FakeView(handNumber = 1, trickNumber = 0)
        val job1 = awaiting(pacing, openTrick)
        val job2 = awaiting(pacing, beforeFirst)
        runCurrent()
        assertTrue(job1.isCompleted, "cards on the table means no inter-trick gate")
        assertTrue(job2.isCompleted, "trick 0 (hand not yet under way) never gates")
    }

    @Test
    fun `preAcknowledge marks a snapshot's hand, deal and trick as already seen`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.NORMAL, holdTricks = true)
        // An online reconnection snapshot mid-hand-3: nothing about it was animated into being.
        pacing.preAcknowledge(FakeView(handNumber = 3, trickNumber = 6))

        // The very same positions must now pass every gate without further signals (bar bot beat).
        val handStart = FakeView(handNumber = 3, isHandStart = true, awaitingHandResultAck = true)
        val betweenTricks = FakeView(handNumber = 3, trickNumber = 6)
        val job1 = awaiting(pacing, handStart)
        val job2 = awaiting(pacing, betweenTricks)
        advanceTimeBy(AnimationSpeed.NORMAL.botDelayMillis)
        runCurrent()
        assertTrue(job1.isCompleted, "preAcknowledge covers the result ack and the deal signal")
        assertTrue(job2.isCompleted, "preAcknowledge covers the trick ack")
    }

    @Test
    fun `reset clears stale acknowledgements for a fresh game`() = runTest {
        val (pacing, _, _) = gates(AnimationSpeed.OFF)
        pacing.acknowledgeHandResult(99)
        pacing.reset()
        val view = FakeView(handNumber = 2, isHandStart = true, awaitingHandResultAck = true)
        val job = awaiting(pacing, view)
        advanceUntilIdle()
        assertFalse(job.isCompleted, "a stale ack from the previous game must not leak through reset")
        pacing.acknowledgeHandResult(2)
        runCurrent()
        assertTrue(job.isCompleted)
    }

    private companion object {
        const val HAND_SIZE = 10
        const val PAUSE_SLACK = 250L
    }
}
