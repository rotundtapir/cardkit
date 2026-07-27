// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.pacing

import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The shape of a game view the pacing gates (and the table sound triggers) read: a game-agnostic
 * projection of "where in the hand/trick lifecycle is this state?". A trick-taking game adapts its
 * player view to this once and every gate below applies unchanged.
 *
 * The members' CONTRACTS are part of the gates' behaviour — implement them exactly:
 */
interface TableTransitions {
    /** 1-based number of the hand this view belongs to; grows monotonically through a game. */
    val handNumber: Int

    /**
     * Number of COMPLETED tricks this hand (0 while the first trick is open). After a trick
     * closes — including while it is still displayed awaiting acknowledgement — this is that
     * trick's number.
     */
    val trickNumber: Int

    /** Cards face up in the CURRENT (open) trick; 0 between tricks. */
    val trickCardCount: Int

    /** Total hands scored so far in the game (e.g. `handResults.size`); grows by one per scored hand. */
    val handResultCount: Int

    /**
     * True only at the very start of a hand, before anyone has acted. For a bidding game this is
     * exactly "in the auction with an empty bidding history" (500: `phase == BIDDING &&
     * biddingHistory.isEmpty()`). Views past that point must report false, or the deal gate would
     * re-arm mid-hand.
     */
    val isHandStart: Boolean

    /**
     * True while a just-scored hand's result still needs the player's acknowledgement — a result
     * exists for the previous hand AND the game is not over (500: `lastHandResult != null &&
     * winner == null`; a finished game shows its final dialog instead and must not gate).
     */
    val awaitingHandResultAck: Boolean
}

/**
 * The signal-driven pacing that keeps bot turns (and, online, incoming server states) from racing
 * ahead of the on-screen animation. The gates are predicates on a view's shape ([TableTransitions]),
 * not on who is deciding, so they pace a bot's turn locally and an incoming server view online
 * identically.
 *
 * [dealPauseMillis] is the game's estimate of its full deal animation (shuffle + flights + flip +
 * slack) at a given speed — it only scales the deadlock backstop below, so a lost deal-done signal
 * can't wedge the game. Every mechanism here is inert at [AnimationSpeed.OFF] — connected/UI test
 * suites depend on it.
 */
class PacingGates(
    private val animationSpeed: StateFlow<AnimationSpeed>,
    private val holdTricks: StateFlow<Boolean>,
    private val dealPauseMillis: (AnimationSpeed) -> Long,
) {
    /** Highest hand number whose end-of-hand result dialog the player has dismissed. */
    private val handResultAcked = MutableStateFlow(0)

    /** Highest hand number whose shuffle/deal animation has finished on screen. */
    private val dealAnimationDone = MutableStateFlow(0)

    /** Key of the last completed trick the player has acknowledged (tapped past). */
    private val trickAcked = MutableStateFlow(0)

    /** Called by the UI when the hand-result dialog is dismissed; unblocks the next hand. */
    fun acknowledgeHandResult(handNumber: Int) {
        handResultAcked.value = maxOf(handResultAcked.value, handNumber)
    }

    /** Called by the UI when a hand's deal animation completes; releases the first bidder. */
    fun dealAnimationFinished(handNumber: Int) {
        dealAnimationDone.value = maxOf(dealAnimationDone.value, handNumber)
    }

    /** Called by the UI when the player taps the completed trick away; releases the next leader. */
    fun acknowledgeTrick(handNumber: Int, trickNumber: Int) {
        trickAcked.value = maxOf(trickAcked.value, trickKey(handNumber, trickNumber))
    }

    /** Reset all signals for a fresh game. */
    fun reset() {
        handResultAcked.value = 0
        dealAnimationDone.value = 0
        trickAcked.value = 0
    }

    /**
     * Mark everything about [view] as already acknowledged. Used when a state arrives that was *not*
     * animated into being — an online (re)connection snapshot — so the subsequent live views don't
     * block waiting for a deal-animation signal that will never come.
     */
    fun preAcknowledge(view: TableTransitions) {
        acknowledgeHandResult(view.handNumber)
        dealAnimationFinished(view.handNumber)
        trickAcked.value = maxOf(trickAcked.value, trickKey(view.handNumber, view.trickNumber))
    }

    /** The cosmetic "thinking" beat applied before a bot's decision (or a bot-driven view online). */
    val botBeatMillis: Long get() = animationSpeed.value.botDelayMillis

    /**
     * Suspend until the UI is ready to reveal [view]: the hand's first bidder waits for the previous
     * result dialog to be dismissed and then for the shuffle/deal animation to finish; a fresh trick
     * waits (with "Hold tricks" on) until the player taps it away, or a short timed pause otherwise.
     */
    suspend fun awaitGates(view: TableTransitions) {
        awaitDealGate(view)
        awaitTrickGate(view)
    }

    private suspend fun awaitDealGate(view: TableTransitions) {
        if (!view.isHandStart) return
        if (view.awaitingHandResultAck) {
            handResultAcked.first { it >= view.handNumber }
        }
        val speed = animationSpeed.value
        if (speed != AnimationSpeed.OFF) {
            // Signal, not timer, so slow devices can't start the auction mid-deal. The timeout is a
            // deadlock backstop (e.g. activity recreated / online snapshot with no deal animation).
            withTimeoutOrNull(dealPauseMillis(speed) * DEAL_BACKSTOP_FACTOR) {
                dealAnimationDone.first { it >= view.handNumber }
            }
            delay(speed.botDelayMillis)
        }
    }

    /**
     * Online only: hold a view that is PAST the start of its hand until the hand has actually been
     * revealed on screen — the previous hand's result dialog dismissed, then the deal animation
     * finished. The local game never needs this (bots are gated BEFORE they act, so post-start
     * states cannot exist early), but an online server plays on without waiting, and without this
     * gate a fresh hand's auction visibly advances behind the result dialog. Inert at OFF; the
     * deal wait carries the same recreation backstop as [awaitGates]; the ack wait is unbounded
     * like the local game's (the dialog that fires it is on screen, rendered from the hand-start
     * view that is never held).
     */
    suspend fun awaitHandRevealed(view: TableTransitions) {
        val speed = animationSpeed.value
        if (speed == AnimationSpeed.OFF) return
        if (view.awaitingHandResultAck) {
            handResultAcked.first { it >= view.handNumber }
        }
        withTimeoutOrNull(dealPauseMillis(speed) * DEAL_BACKSTOP_FACTOR) {
            dealAnimationDone.first { it >= view.handNumber }
        }
    }

    private suspend fun awaitTrickGate(view: TableTransitions) {
        val speed = animationSpeed.value
        if (view.trickCardCount > 0 || view.trickNumber <= 0 || speed == AnimationSpeed.OFF) return
        if (holdTricks.value) {
            val key = trickKey(view.handNumber, view.trickNumber)
            combine(trickAcked, holdTricks) { acked, hold -> !hold || acked >= key }.first { it }
        } else {
            delay(interTrickPauseMillis(speed))
        }
    }

    private fun interTrickPauseMillis(speed: AnimationSpeed): Long = when (speed) {
        AnimationSpeed.SLOW -> 1800L
        AnimationSpeed.NORMAL -> 1000L
        AnimationSpeed.FAST -> 400L
        AnimationSpeed.OFF -> 0L
    }

    private fun trickKey(handNumber: Int, trickNumber: Int) = handNumber * TRICK_KEY_STRIDE + trickNumber

    private companion object {
        const val DEAL_BACKSTOP_FACTOR = 3
        const val TRICK_KEY_STRIDE = 1000
    }
}
