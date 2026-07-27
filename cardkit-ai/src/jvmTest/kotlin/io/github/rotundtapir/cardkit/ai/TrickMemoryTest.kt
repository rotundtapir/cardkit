// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.cardkit.core.of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrickMemoryTest {
    private val spades = TrickEvaluator(Suit.SPADES, JokerRole.HIGHEST_TRUMP)
    private val noTrump = TrickEvaluator(null, JokerRole.SOLE_TRUMP)

    @Test
    fun `records every play as seen`() {
        val memory = TrickMemory()
        memory.record(
            listOf(
                TrickPlay(Seat(0), Rank.ACE of Suit.HEARTS),
                TrickPlay(Seat(1), Rank.KING of Suit.HEARTS),
            ),
            spades,
        )
        assertEquals<Set<Card>>(setOf(Rank.ACE of Suit.HEARTS, Rank.KING of Suit.HEARTS), memory.seenPlays)
    }

    @Test
    fun `failing to follow proves a void in the led suit`() {
        val memory = TrickMemory()
        memory.record(
            listOf(
                TrickPlay(Seat(0), Rank.ACE of Suit.HEARTS),
                TrickPlay(Seat(1), Rank.NINE of Suit.DIAMONDS), // discards off-suit
                TrickPlay(Seat(2), Rank.KING of Suit.HEARTS), // follows
            ),
            spades,
        )
        assertEquals<Set<Suit>?>(setOf(Suit.HEARTS), memory.voids[Seat(1)])
        assertTrue(Seat(2) !in memory.voids)
    }

    @Test
    fun `the left bower follows a trump lead without proving a void`() {
        val memory = TrickMemory()
        memory.record(
            listOf(
                TrickPlay(Seat(0), Rank.ACE of Suit.SPADES),
                TrickPlay(Seat(1), Rank.JACK of Suit.CLUBS), // left bower: effectively a spade
            ),
            spades,
        )
        assertTrue(memory.voids.isEmpty())
    }

    @Test
    fun `a sole-trump joker proves nothing about a void`() {
        val memory = TrickMemory()
        memory.record(
            listOf(
                TrickPlay(Seat(0), Rank.ACE of Suit.HEARTS),
                TrickPlay(Seat(1), Joker), // always playable at no-trump
            ),
            noTrump,
        )
        assertTrue(memory.voids.isEmpty())
        assertTrue(Joker in memory.seenPlays)
    }

    @Test
    fun `a highest-trump joker ruff proves the void like any trump`() {
        val memory = TrickMemory()
        memory.record(
            listOf(
                TrickPlay(Seat(0), Rank.ACE of Suit.HEARTS),
                TrickPlay(Seat(1), Joker), // ruffs in a suit contract: proves no hearts
            ),
            spades,
        )
        assertEquals<Set<Suit>?>(setOf(Suit.HEARTS), memory.voids[Seat(1)])
    }

    @Test
    fun `an unconstrained lead records plays but proves no voids`() {
        val memory = TrickMemory()
        memory.record(
            listOf(
                TrickPlay(Seat(0), Joker, nominated = null), // sole-trump joker, no nomination
                TrickPlay(Seat(1), Rank.NINE of Suit.DIAMONDS),
            ),
            noTrump,
        )
        assertTrue(memory.voids.isEmpty())
        assertEquals(2, memory.seenPlays.size)
    }

    @Test
    fun `reset forgets everything`() {
        val memory = TrickMemory()
        memory.record(
            listOf(
                TrickPlay(Seat(0), Rank.ACE of Suit.HEARTS),
                TrickPlay(Seat(1), Rank.NINE of Suit.DIAMONDS),
            ),
            spades,
        )
        memory.reset()
        assertTrue(memory.seenPlays.isEmpty())
        assertTrue(memory.voids.isEmpty())
    }
}
