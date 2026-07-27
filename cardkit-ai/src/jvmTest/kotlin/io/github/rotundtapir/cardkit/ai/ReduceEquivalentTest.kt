// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReduceEquivalentTest {
    private val spades = TrickEvaluator(Suit.SPADES, JokerRole.ABSENT)

    @Test
    fun `adjacent cards with no live rival between them collapse to the lower`() {
        // Holding K and A of hearts with the queen already gone: they are interchangeable.
        val legal = listOf(Rank.KING of Suit.HEARTS, Rank.ACE of Suit.HEARTS)
        val unseen = listOf(Rank.NINE of Suit.HEARTS, Rank.TEN of Suit.HEARTS)
        val reduced = reduceEquivalent(legal, unseen, spades)
        assertEquals(listOf(Rank.KING of Suit.HEARTS), reduced)
    }

    @Test
    fun `a live rival in between keeps both cards`() {
        // Holding Q and A of hearts: with the king still out there they are distinct choices,
        // without it they collapse to the queen.
        val legal = listOf(Rank.QUEEN of Suit.HEARTS, Rank.ACE of Suit.HEARTS)
        assertEquals(legal, reduceEquivalent(legal, listOf(Rank.KING of Suit.HEARTS), spades))
        assertEquals(listOf(Rank.QUEEN of Suit.HEARTS), reduceEquivalent(legal, emptyList(), spades))
    }

    @Test
    fun `bowers split equivalence classes across printed suits`() {
        // With spades trump, J♠ (right) and A♠ are NOT adjacent: the left bower (J♣) sits between.
        val legal = listOf(Rank.ACE of Suit.SPADES, Rank.JACK of Suit.SPADES)
        val withLeftLive = reduceEquivalent(legal, listOf(Rank.JACK of Suit.CLUBS), spades)
        assertEquals(2, withLeftLive.size)
        // Once the left bower is gone (and K/Q too), ace and right bower are interchangeable.
        val collapsed = reduceEquivalent(legal, listOf(Rank.NINE of Suit.HEARTS), spades)
        assertEquals(listOf(Rank.ACE of Suit.SPADES), collapsed)
    }

    @Test
    fun `cards with no effective suit are kept as-is`() {
        val legal = listOf(Joker, Rank.NINE of Suit.HEARTS)
        val reduced = reduceEquivalent(legal, emptyList(), spades)
        assertTrue(Joker in reduced)
    }
}
