// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator

/**
 * Collapses cards that are interchangeable this trick — same effective suit with no card another
 * player could still hold ([unseen]) ranking strictly between them — keeping the lowest of each
 * class. Typically halves a search's play-arm count late in a hand; on small decks (Euchre's 24)
 * the collapse is dramatic.
 */
fun reduceEquivalent(legal: List<Card>, unseen: Collection<Card>, eval: TrickEvaluator): List<Card> =
    legal.groupBy { eval.effectiveSuit(it) }.flatMap { (suit, cards) ->
        if (suit == null) cards else collapse(cards, unseen, suit, eval)
    }

private fun collapse(cards: List<Card>, unseen: Collection<Card>, suit: Suit, eval: TrickEvaluator): List<Card> {
    val rivals = unseen.filter { eval.effectiveSuit(it) == suit }.map { eval.strength(it, suit) }
    val sorted = cards.sortedBy { eval.strength(it, suit) }
    val kept = mutableListOf(sorted.first())
    for (i in 1 until sorted.size) {
        val lo = eval.strength(sorted[i - 1], suit)
        val hi = eval.strength(sorted[i], suit)
        if (rivals.any { it in (lo + 1) until hi }) kept += sorted[i]
    }
    return kept
}
