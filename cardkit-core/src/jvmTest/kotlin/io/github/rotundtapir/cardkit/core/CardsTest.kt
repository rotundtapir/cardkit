// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardsTest {
    @Test
    fun `suited card code and label`() {
        val aceSpades = Rank.ACE of Suit.SPADES
        assertEquals("AS", aceSpades.code)
        assertEquals("A♠", aceSpades.label)

        val tenHearts = Rank.TEN of Suit.HEARTS
        assertEquals("TH", tenHearts.code)
        assertEquals("10♥", tenHearts.label)
    }

    @Test
    fun `joker code and label`() {
        assertEquals("JK", Joker.code)
        assertEquals("Joker", Joker.label)
    }

    @Test
    fun `parse round-trips every card in a standard deck plus joker`() {
        for (card in standardDeck(jokers = 1)) {
            assertEquals(card, Card.parse(card.code), "round-trip failed for ${card.code}")
        }
    }

    @Test
    fun `parse is case-insensitive and trims`() {
        assertEquals(Rank.KING of Suit.DIAMONDS, Card.parse("  kd "))
        assertEquals(Joker, Card.parse("jk"))
    }

    @Test
    fun `parse rejects garbage`() {
        assertFailsWith<IllegalArgumentException> { Card.parse("ZZ") }
        assertFailsWith<IllegalArgumentException> { Card.parse("A") }
    }

    @Test
    fun `colour reflects suit`() {
        assertEquals(CardColor.BLACK, (Rank.TWO of Suit.CLUBS).color)
        assertEquals(CardColor.RED, (Rank.TWO of Suit.HEARTS).color)
        assertTrue(Suit.SPADES.color == CardColor.BLACK && Suit.DIAMONDS.color == CardColor.RED)
    }
}
