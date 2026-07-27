// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.pacing

import io.github.rotundtapir.cardkit.ui.SoundEffect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [soundEffectsFor], the pure trigger logic behind [rememberTableSoundEffects] — the
 * same assertions 500's SoundsTest made against PlayerView transitions, expressed directly on the
 * [TableTransitions] fields the triggers read.
 */
class TableSoundsTest {

    private data class FakeView(
        override val handNumber: Int = 1,
        override val trickNumber: Int = 0,
        override val trickCardCount: Int = 0,
        override val handResultCount: Int = 0,
        override val isHandStart: Boolean = false,
        override val awaitingHandResultAck: Boolean = false,
    ) : TableTransitions

    @Test
    fun `a null on either side of the transition triggers nothing`() {
        val some = FakeView(trickCardCount = 2)
        assertEquals(emptyList(), soundEffectsFor(null, some))
        assertEquals(emptyList(), soundEffectsFor(some, null))
        assertEquals(emptyList(), soundEffectsFor(null, null))
    }

    @Test
    fun `a card landing in the trick fires CARD_PLACE`() {
        val prev = FakeView(trickCardCount = 1)
        val next = prev.copy(trickCardCount = 2)
        assertEquals(listOf(SoundEffect.CARD_PLACE), soundEffectsFor(prev, next))
    }

    @Test
    fun `the trick emptying does not fire CARD_PLACE`() {
        // A completed trick swept away: count drops to 0 — only the trick-number delta sounds.
        val prev = FakeView(trickNumber = 2, trickCardCount = 4)
        val next = FakeView(trickNumber = 3, trickCardCount = 0)
        assertEquals(listOf(SoundEffect.TRICK_TAKEN), soundEffectsFor(prev, next))
    }

    @Test
    fun `an unchanged view triggers nothing`() {
        val view = FakeView(trickNumber = 3, trickCardCount = 1, handResultCount = 1)
        assertEquals(emptyList(), soundEffectsFor(view, view.copy()))
    }

    @Test
    fun `a newly scored hand fires SCORE by count not value`() {
        // The count-based trigger is the contract: two structurally identical consecutive hand
        // results still differ in COUNT, so the second hand's score sound must fire (a value
        // comparison of the results list was the old 500 bug this guards against).
        val prev = FakeView(handResultCount = 1)
        val next = prev.copy(handResultCount = 2)
        assertEquals(listOf(SoundEffect.SCORE), soundEffectsFor(prev, next))
    }

    @Test
    fun `simultaneous deltas fire every matching effect in trigger order`() {
        // The last card of the last trick of a hand can close the trick AND score the hand in one
        // transition; each delta contributes its effect.
        val prev = FakeView(trickNumber = 9, trickCardCount = 3, handResultCount = 0)
        val next = FakeView(trickNumber = 10, trickCardCount = 4, handResultCount = 1)
        assertEquals(
            listOf(SoundEffect.CARD_PLACE, SoundEffect.TRICK_TAKEN, SoundEffect.SCORE),
            soundEffectsFor(prev, next),
        )
    }
}
