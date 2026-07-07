// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DealingTest {
    @Test
    fun `deal splits into hands and leftover`() {
        val deck = standardDeck() // 52
        val result = deal(deck, seats = 4, handSize = 10)
        assertEquals(4, result.hands.size)
        result.hands.forEach { assertEquals(10, it.size) }
        assertEquals(12, result.leftover.size)
        // Every card accounted for exactly once.
        val all = result.hands.flatten() + result.leftover
        assertEquals(deck.toSet(), all.toSet())
        assertEquals(deck.size, all.size)
    }

    @Test
    fun `deal is one-at-a-time in seat order`() {
        // With an unshuffled deck, seat 0 gets cards 0,4,8..., seat 1 gets 1,5,9..., etc.
        val deck = standardDeck()
        val result = deal(deck, seats = 4, handSize = 3)
        assertEquals(listOf(deck[0], deck[4], deck[8]), result.hands[0])
        assertEquals(listOf(deck[1], deck[5], deck[9]), result.hands[1])
    }

    @Test
    fun `shuffle is deterministic for a given seed`() {
        val deck = standardDeck()
        val a = deck.shuffleWith(Random(42))
        val b = deck.shuffleWith(Random(42))
        val c = deck.shuffleWith(Random(43))
        assertEquals(a, b)
        assertEquals(deck.toSet(), a.toSet()) // permutation, no loss
        assert(a != c) { "different seeds should (almost surely) differ" }
    }

    @Test
    fun `shuffleAndDeal reproduces the same deal for the same seed`() {
        val deck = standardDeck(jokers = 1)
        val d1 = shuffleAndDeal(deck, seats = 4, handSize = 10, random = Random(7))
        val d2 = shuffleAndDeal(deck, seats = 4, handSize = 10, random = Random(7))
        assertEquals(d1.hands, d2.hands)
        assertEquals(d1.leftover, d2.leftover)
    }

    @Test
    fun `deal rejects an over-large request`() {
        assertFailsWith<IllegalArgumentException> {
            deal(standardDeck(), seats = 4, handSize = 20) // needs 80 > 52
        }
    }
}
