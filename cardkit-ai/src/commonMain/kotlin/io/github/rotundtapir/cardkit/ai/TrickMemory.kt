// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.TrickPlay

/**
 * The game-agnostic memory of one bot seat across a hand: every card observed hitting the felt and
 * the effective suits each seat has been proven void in (failed to follow). Games wrap this with
 * their own observation plumbing (what a view exposes, when a hand starts) and extra knowledge
 * (own discards, exchanged cards).
 *
 * If observation ever misses a play the sampled worlds are merely less informed, never
 * inconsistent — missed cards stay in the unknown pool.
 */
class TrickMemory {

    /** Every card observed hitting the felt this hand. */
    val seenPlays = mutableSetOf<Card>()

    /** Effective suits each seat has been proven void in (failed to follow). */
    val voids = mutableMapOf<Seat, MutableSet<Suit>>()

    /** Records the [plays] of one (possibly incomplete) trick under [eval]'s following rules. */
    fun record(plays: List<TrickPlay>, eval: TrickEvaluator) {
        if (plays.isEmpty()) return
        plays.forEach { seenPlays += it.card }
        val led = eval.ledSuitOf(plays.first()) ?: return
        plays.drop(1)
            // A joker that is always playable (sole trump) proves nothing about a void; as the
            // highest trump an off-suit joker is a ruff and proves the void like any trump.
            .filterNot { it.card is Joker && eval.jokerRole != JokerRole.HIGHEST_TRUMP }
            .filter { eval.effectiveSuit(it.card) != led }
            .forEach { voids.getOrPut(it.seat) { mutableSetOf() } += led }
    }

    /** Forgets everything; call at each hand boundary. */
    fun reset() {
        seenPlays.clear()
        voids.clear()
    }
}
