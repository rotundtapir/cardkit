// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckTest {
    @Test
    fun `standard deck has 52 distinct cards`() {
        val deck = standardDeck()
        assertEquals(52, deck.size)
        assertEquals(52, deck.toSet().size)
        assertTrue(deck.none { it is Joker })
    }

    @Test
    fun `standard deck with jokers`() {
        assertEquals(54, standardDeck(jokers = 2).size)
        assertEquals(2, standardDeck(jokers = 2).count { it is Joker })
    }

    @Test
    fun `rangeTo yields inclusive rank range in enum order`() {
        // Raw enum order: the extended ranks sit between TEN and JACK by design.
        assertEquals(
            listOf(Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
                Rank.ELEVEN, Rank.TWELVE, Rank.THIRTEEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE),
            Rank.FOUR..Rank.ACE,
        )
        // Subtracting extendedRanks gives the classic range.
        assertEquals(
            listOf(Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
                Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE),
            (Rank.FOUR..Rank.ACE) - extendedRanks,
        )
        assertEquals(listOf(Rank.ACE), Rank.ACE..Rank.ACE)
    }

    @Test
    fun `buildDeck composes a 500-style 43-card deck`() {
        val deck = buildDeck {
            suits(Suit.HEARTS, Suit.DIAMONDS) { ranks((Rank.FOUR..Rank.ACE) - extendedRanks) } // 11 * 2 = 22
            suits(Suit.SPADES, Suit.CLUBS) { ranks((Rank.FIVE..Rank.ACE) - extendedRanks) }      // 10 * 2 = 20
            joker()                                                              // 1
        }
        assertEquals(43, deck.size)
        assertEquals(11, deck.count { it is SuitedCard && it.suit == Suit.HEARTS })
        assertEquals(10, deck.count { it is SuitedCard && it.suit == Suit.SPADES })
        assertEquals(1, deck.count { it is Joker })
        // No black 4s, no 2s or 3s anywhere.
        assertTrue(deck.none { it is SuitedCard && it.rank == Rank.FOUR && it.color == CardColor.BLACK })
        assertTrue(deck.none { it is SuitedCard && (it.rank == Rank.TWO || it.rank == Rank.THREE) })
    }
}
