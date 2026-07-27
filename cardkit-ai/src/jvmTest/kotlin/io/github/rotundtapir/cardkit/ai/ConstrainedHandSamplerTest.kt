// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.of
import io.github.rotundtapir.cardkit.core.standardDeck
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstrainedHandSamplerTest {
    private val deck = standardDeck()
    private val sampler = ConstrainedHandSampler(deck)
    private val spades = TrickEvaluator(Suit.SPADES, JokerRole.ABSENT)

    private val myHand: List<Card> = listOf(
        Rank.ACE of Suit.SPADES,
        Rank.KING of Suit.SPADES,
        Rank.QUEEN of Suit.SPADES,
        Rank.JACK of Suit.SPADES,
        Rank.TEN of Suit.SPADES,
    )
    private val allSeats = (0..3).map(::Seat)
    private val handSizes = allSeats.associateWith { 5 }

    private fun sample(
        knownGone: Set<Card> = emptySet(),
        voids: Map<Seat, Set<Suit>> = emptyMap(),
        eval: TrickEvaluator? = spades,
        seed: Long = 1,
    ) = sampler.sample(mapOf(Seat(0) to myHand), handSizes, knownGone, voids, eval, Random(seed))

    @Test
    fun `fixed hands are copied verbatim and hand sizes are honored`() {
        val result = sample()
        assertEquals(myHand, result.hands.getValue(Seat(0)))
        allSeats.forEach { assertEquals(5, result.hands.getValue(it).size, "seat $it") }
        assertEquals(deck.size - 4 * 5, result.pool.size)
    }

    @Test
    fun `no card is dealt twice and known-gone cards are never dealt`() {
        val gone = setOf(Rank.ACE of Suit.HEARTS, Rank.KING of Suit.HEARTS)
        val result = sample(knownGone = gone)
        val dealt = result.hands.values.flatten() + result.pool
        assertEquals(dealt.size, dealt.toSet().size, "no duplicates")
        gone.forEach { assertTrue(it !in dealt, "$it should be out of play") }
    }

    @Test
    fun `proven voids are repaired when possible`() {
        // Seat 1 is void in hearts; the pool is plentiful, so repair must always succeed.
        val result = sample(voids = mapOf(Seat(1) to setOf(Suit.HEARTS)))
        val hearts = result.hands.getValue(Seat(1)).filter { spades.effectiveSuit(it) == Suit.HEARTS }
        assertEquals(emptyList(), hearts)
    }

    @Test
    fun `repair respects the left bower's effective suit`() {
        // Void in spades must exclude the Jack of clubs too (it is effectively a spade).
        val result = sample(voids = mapOf(Seat(2) to setOf(Suit.SPADES)))
        val spadesHeld = result.hands.getValue(Seat(2)).filter { spades.effectiveSuit(it) == Suit.SPADES }
        assertEquals(emptyList(), spadesHeld)
    }

    @Test
    fun `fixed seats are never repaired even against their own voids`() {
        // Nonsense input (my own hand violates my "void"), but fixed hands must stay untouched.
        val result = sample(voids = mapOf(Seat(0) to setOf(Suit.SPADES)))
        assertEquals(myHand, result.hands.getValue(Seat(0)))
    }

    @Test
    fun `null evaluator skips repair`() {
        val result = sample(voids = mapOf(Seat(1) to setOf(Suit.HEARTS)), eval = null, seed = 3)
        // Not asserting hearts are present (chance), only that sampling is structurally sound.
        assertEquals(5, result.hands.getValue(Seat(1)).size)
    }

    @Test
    fun `same seed samples the same world`() {
        val a = sample(seed = 42)
        val b = sample(seed = 42)
        assertEquals(a.hands, b.hands)
        assertEquals(a.pool.toList(), b.pool.toList())
    }

    @Test
    fun `demanding more cards than remain is an immediate, attributable error`() {
        val tiny = ConstrainedHandSampler(listOf(Rank.ACE of Suit.HEARTS, Rank.KING of Suit.HEARTS))
        val failure = kotlin.test.assertFailsWith<IllegalArgumentException> {
            tiny.sample(
                fixedHands = emptyMap(),
                handSizes = mapOf(Seat(0) to 2, Seat(1) to 2),
                knownGone = setOf(Rank.ACE of Suit.HEARTS),
                voids = emptyMap(),
                eval = null,
                random = Random(1),
            )
        }
        assertTrue("deck has 2" in failure.message.orEmpty())
    }

    @Test
    fun `irreparable voids still produce size-correct replayable hands`() {
        // Every unknown card is a heart-or-spade world: make seats void in both red suits with a
        // tiny deck so repair cannot fully succeed, and check structure survives.
        val tinyDeck = listOf(
            Rank.ACE of Suit.HEARTS, Rank.KING of Suit.HEARTS, Rank.QUEEN of Suit.HEARTS,
            Rank.ACE of Suit.DIAMONDS, Rank.KING of Suit.DIAMONDS, Rank.QUEEN of Suit.DIAMONDS,
        )
        val tiny = ConstrainedHandSampler(tinyDeck)
        val result = tiny.sample(
            fixedHands = emptyMap(),
            handSizes = mapOf(Seat(0) to 3, Seat(1) to 3),
            knownGone = emptySet(),
            voids = mapOf(Seat(0) to setOf(Suit.HEARTS, Suit.DIAMONDS)),
            eval = spades,
            random = Random(5),
        )
        assertEquals(3, result.hands.getValue(Seat(0)).size)
        assertEquals(3, result.hands.getValue(Seat(1)).size)
        val dealt = result.hands.values.flatten()
        assertEquals(dealt.size, dealt.toSet().size)
    }
}
