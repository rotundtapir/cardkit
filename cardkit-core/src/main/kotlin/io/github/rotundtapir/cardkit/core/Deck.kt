// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

/**
 * Builds a deck of [Card]s. Deliberately flexible so that games with non-standard decks can express
 * exactly the composition they need — e.g. 500's 43-card deck, Euchre's 24-card deck, etc.
 *
 * Example (a fragment of the 500 deck — note the classic game subtracts the [extendedRanks]
 * that `rangeTo` includes between TEN and JACK):
 * ```
 * val cards = buildDeck {
 *     suits(Suit.HEARTS, Suit.DIAMONDS) { ranks((Rank.FOUR..Rank.ACE) - extendedRanks) }
 *     suits(Suit.SPADES, Suit.CLUBS)   { ranks((Rank.FIVE..Rank.ACE) - extendedRanks) }
 *     joker()
 * }
 * ```
 */
class DeckBuilder internal constructor() {
    private val cards = mutableListOf<Card>()

    /** Scope used inside [suits] to declare which ranks a suit contains. */
    inner class SuitScope internal constructor(private val targetSuits: List<Suit>) {
        fun ranks(ranks: Iterable<Rank>) {
            for (suit in targetSuits) for (rank in ranks) cards += SuitedCard(rank, suit)
        }

        fun rank(rank: Rank) = ranks(listOf(rank))
    }

    /** Adds cards for the given [suits] using the ranks declared in [block]. */
    fun suits(vararg suits: Suit, block: SuitScope.() -> Unit) {
        SuitScope(suits.toList()).block()
    }

    /** Adds every suit for the given [ranks]. */
    fun allSuits(ranks: Iterable<Rank>) {
        suits(*Suit.entries.toTypedArray()) { ranks(ranks) }
    }

    /** Adds a single explicit card. */
    fun card(rank: Rank, suit: Suit) {
        cards += SuitedCard(rank, suit)
    }

    /** Adds [count] Joker(s). */
    fun joker(count: Int = 1) {
        repeat(count) { cards += Joker }
    }

    internal fun build(): List<Card> = cards.toList()
}

/** Entry point for the [DeckBuilder] DSL. Returns an immutable, ordered list of cards. */
fun buildDeck(block: DeckBuilder.() -> Unit): List<Card> =
    DeckBuilder().apply(block).build()

/** The three extended ranks (11/12/13) used by large-deck games such as six-handed 500. */
val extendedRanks: Set<Rank> = setOf(Rank.ELEVEN, Rank.TWELVE, Rank.THIRTEEN)

/** The 13 classic ranks of a suit, low to high (Ace high). Excludes the [extendedRanks]. */
val ranksTwoToAce: List<Rank> = Rank.entries.filter { it !in extendedRanks }

/**
 * Inclusive rank range in enum (strength) order, e.g. `Rank.FOUR..Rank.ACE`. Note this follows raw
 * enum order, so a range spanning TEN..JACK includes the [extendedRanks] declared between them —
 * subtract [extendedRanks] for a classic-deck range.
 */
operator fun Rank.rangeTo(other: Rank): List<Rank> =
    Rank.entries.subList(this.ordinal, other.ordinal + 1)

/** A standard 52-card deck, optionally with [jokers] added. */
fun standardDeck(jokers: Int = 0): List<Card> = buildDeck {
    allSuits(ranksTwoToAce)
    if (jokers > 0) joker(jokers)
}
