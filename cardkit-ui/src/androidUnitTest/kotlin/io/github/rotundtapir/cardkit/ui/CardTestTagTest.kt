// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.of
import io.github.rotundtapir.cardkit.core.standardDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [cardTestTag] is a published contract: consumers' instrumented suites locate cards by this exact
 * string, so drift here silently breaks them downstream. Pin the format and the namespace.
 */
class CardTestTagTest {

    @Test
    fun `tag is the ck-namespaced prefix plus the card label`() {
        assertEquals("ck:card:", CardTestTagPrefix)
        assertEquals("ck:card:J♠", cardTestTag(Rank.JACK of Suit.SPADES))
        assertEquals("ck:card:10♥", cardTestTag(Rank.TEN of Suit.HEARTS))
        assertEquals("ck:card:Joker", cardTestTag(Joker))
    }

    @Test
    fun `tag is keyed by label, never by code`() {
        // The label/code confusion is the other half of the footgun — `ck:card:JS` is not a tag
        // this library ever emits.
        val jackSpades = Rank.JACK of Suit.SPADES
        assertEquals("$CardTestTagPrefix${jackSpades.label}", cardTestTag(jackSpades))
        assertTrue(cardTestTag(jackSpades) != "$CardTestTagPrefix${jackSpades.code}")
    }

    @Test
    fun `every card in a standard deck plus joker gets a distinct tag`() {
        val deck = standardDeck(jokers = 1)
        val tags = deck.map { cardTestTag(it) }
        assertEquals(deck.size, tags.toSet().size)
        assertTrue(tags.all { it.startsWith(CardTestTagPrefix) })
    }

    @Test
    fun `the un-namespaced prefix is no longer emitted`() {
        // Guards the whole point of the rename: a consumer's own `card:`-prefixed tags must not be
        // matched by a query for cardkit's cards, nor the reverse.
        assertTrue(standardDeck(jokers = 1).none { cardTestTag(it).startsWith("card:") })
    }
}
