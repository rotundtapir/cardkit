// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.random.Random

/**
 * A player's position at the table, `0`-based. Turn order, partnerships and so on are defined by the
 * game; [Seat] is just a stable identifier.
 */
@Serializable
@JvmInline
value class Seat(val index: Int) {
    init {
        require(index >= 0) { "Seat index must be non-negative: $index" }
    }

    override fun toString(): String = "Seat($index)"
}

/** The result of dealing: one hand per seat plus any [leftover] (a kitty/widow/stock). */
data class Deal(
    val hands: List<List<Card>>,
    val leftover: List<Card>,
)

/**
 * Shuffles [this] deck deterministically using [random]. Passing a seeded [Random] makes deals
 * reproducible, which is essential for unit tests and for a future authoritative server.
 */
fun List<Card>.shuffleWith(random: Random): List<Card> = this.shuffled(random)

/**
 * Deals from an already-ordered [deck] to [seats] players, [handSize] cards each, dealt one card at a
 * time in seat order (the conventional way). Any remaining cards become [Deal.leftover].
 *
 * @throws IllegalArgumentException if the deck is too small for the requested deal.
 */
fun deal(deck: List<Card>, seats: Int, handSize: Int): Deal {
    require(seats > 0) { "seats must be positive" }
    require(handSize >= 0) { "handSize must be non-negative" }
    require(seats * handSize <= deck.size) {
        "Cannot deal $handSize cards to $seats seats from a ${deck.size}-card deck"
    }
    val hands = List(seats) { mutableListOf<Card>() }
    var i = 0
    repeat(handSize) {
        for (s in 0 until seats) {
            hands[s] += deck[i++]
        }
    }
    return Deal(hands.map { it.toList() }, deck.subList(i, deck.size).toList())
}

/** Shuffles [deck] with [random] and then [deal]s it. */
fun shuffleAndDeal(deck: List<Card>, seats: Int, handSize: Int, random: Random): Deal =
    deal(deck.shuffleWith(random), seats, handSize)
