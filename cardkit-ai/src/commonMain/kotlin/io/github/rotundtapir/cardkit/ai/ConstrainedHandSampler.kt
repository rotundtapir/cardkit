// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import kotlin.random.Random

/**
 * Samples hidden hands consistent with what a bot seat knows, for Monte-Carlo determinization:
 * unknown cards are dealt randomly to the other seats, then a best-effort repair pass swaps cards
 * out of hands whose holder is proven void in their suit. Games copy the public state around the
 * sampled hands themselves (and decide what to do with the undealt [Result.pool] — face-down
 * kitties, dead cards).
 *
 * One pass; irreparable placements are accepted: a sampled world may be slightly misinformed but
 * is always structurally legal to replay.
 */
class ConstrainedHandSampler(
    /** The full deck for this table — also the universe for "which cards are still unseen". */
    val deck: List<Card>,
) {

    /** One sampled world's hands plus the cards left undealt. */
    class Result(val hands: MutableMap<Seat, MutableList<Card>>, val pool: ArrayDeque<Card>)

    /**
     * Samples hands for every seat in [handSizes]. [fixedHands] pins seats whose cards are known
     * exactly (the bot's own hand, an exposed hand) — they are copied verbatim and never repaired
     * into or out of. [knownGone] cards (seen plays, own buried discards) are excluded from the
     * pool. [voids] are repaired when [eval] is non-null (games pass null before trump exists,
     * when no void can have been proven).
     */
    fun sample(
        fixedHands: Map<Seat, List<Card>>,
        handSizes: Map<Seat, Int>,
        knownGone: Set<Card>,
        voids: Map<Seat, Set<Suit>>,
        eval: TrickEvaluator?,
        random: Random,
    ): Result {
        val known = buildSet {
            addAll(knownGone)
            fixedHands.values.forEach(::addAll)
        }
        val pool = ArrayDeque(deck.filterNot { it in known }.shuffled(random))
        val needed = handSizes.filterKeys { it !in fixedHands }.values.sum()
        require(needed <= pool.size) {
            "Cannot deal $needed hidden cards: deck has ${deck.size}, ${known.size} known/fixed, " +
                "leaving ${pool.size} — the caller's view/tracker bookkeeping is inconsistent"
        }
        val hands = mutableMapOf<Seat, MutableList<Card>>()
        fixedHands.forEach { (seat, cards) -> hands[seat] = cards.toMutableList() }
        handSizes.keys.sortedBy { it.index }
            .filterNot { it in hands }
            .forEach { seat -> hands[seat] = MutableList(handSizes.getValue(seat)) { pool.removeFirst() } }
        if (eval != null) repairVoids(hands, voids, eval, pool, fixedHands.keys)
        return Result(hands, pool)
    }

    private fun repairVoids(
        hands: MutableMap<Seat, MutableList<Card>>,
        voids: Map<Seat, Set<Suit>>,
        eval: TrickEvaluator,
        leftover: ArrayDeque<Card>,
        fixed: Set<Seat>,
    ) {
        voids.keys.sortedBy { it.index }
            .filterNot { it in fixed }
            .filter { it in hands }
            .forEach { seat -> repairSeat(seat, hands, voids, eval, leftover, fixed) }
    }

    private fun repairSeat(
        seat: Seat,
        hands: MutableMap<Seat, MutableList<Card>>,
        voids: Map<Seat, Set<Suit>>,
        eval: TrickEvaluator,
        leftover: ArrayDeque<Card>,
        fixed: Set<Seat>,
    ) {
        val voidSuits = voids.getValue(seat)
        val hand = hands.getValue(seat)
        hand.indices
            .filter { eval.effectiveSuit(hand[it]) in voidSuits }
            .forEach { i ->
                if (!swapWithLeftover(hand, i, voidSuits, eval, leftover)) {
                    swapWithOtherHand(seat, hand, i, hands, voids, eval, fixed)
                }
            }
    }

    private fun swapWithLeftover(
        hand: MutableList<Card>,
        i: Int,
        voidSuits: Set<Suit>,
        eval: TrickEvaluator,
        leftover: ArrayDeque<Card>,
    ): Boolean {
        val li = leftover.indexOfFirst { eval.effectiveSuit(it) !in voidSuits }
        if (li < 0) return false
        val incoming = leftover[li]
        leftover[li] = hand[i]
        hand[i] = incoming
        return true
    }

    private fun swapWithOtherHand(
        seat: Seat,
        hand: MutableList<Card>,
        i: Int,
        hands: MutableMap<Seat, MutableList<Card>>,
        voids: Map<Seat, Set<Suit>>,
        eval: TrickEvaluator,
        fixed: Set<Seat>,
    ) {
        val outgoing = hand[i]
        val candidates = hands.keys.sortedBy { it.index }
            .filterNot { it == seat || it in fixed }
            // The displaced card must be legal for the other seat to hold.
            .filterNot { eval.effectiveSuit(outgoing) in voids[it].orEmpty() }
        for (other in candidates) {
            val otherHand = hands.getValue(other)
            val oi = otherHand.indexOfFirst { eval.effectiveSuit(it) !in voids.getValue(seat) }
            if (oi >= 0) {
                hand[i] = otherHand[oi]
                otherHand[oi] = outgoing
                return
            }
        }
    }
}
