// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlinx.serialization.Serializable

/** Colour of a card, used by games where the "same colour" relationship matters (e.g. bowers in 500). */
enum class CardColor { BLACK, RED }

/**
 * The four French suits. The declared order is arbitrary; individual games impose their own suit
 * ranking (for bidding, trumps, etc.) rather than relying on this enum's order.
 */
enum class Suit(val symbol: String, val letter: Char, val color: CardColor) {
    CLUBS("♣", 'C', CardColor.BLACK),
    DIAMONDS("♦", 'D', CardColor.RED),
    HEARTS("♥", 'H', CardColor.RED),
    SPADES("♠", 'S', CardColor.BLACK);

    companion object {
        fun fromLetter(letter: Char): Suit =
            entries.firstOrNull { it.letter == letter.uppercaseChar() }
                ?: throw IllegalArgumentException("Unknown suit letter: $letter")
    }
}

/**
 * Standard card ranks. [ordinal] gives the natural low-to-high order with the Ace high, which is the
 * usual ranking within a suit. Games that rank cards differently (trumps, bowers) do so explicitly.
 */
enum class Rank(val label: String, val code: Char) {
    TWO("2", '2'),
    THREE("3", '3'),
    FOUR("4", '4'),
    FIVE("5", '5'),
    SIX("6", '6'),
    SEVEN("7", '7'),
    EIGHT("8", '8'),
    NINE("9", '9'),
    TEN("10", 'T'),
    ELEVEN("11", 'E'),
    TWELVE("12", 'W'),
    THIRTEEN("13", 'R'),
    JACK("J", 'J'),
    QUEEN("Q", 'Q'),
    KING("K", 'K'),
    ACE("A", 'A');

    companion object {
        fun fromCode(code: Char): Rank =
            entries.firstOrNull { it.code == code.uppercaseChar() }
                ?: throw IllegalArgumentException("Unknown rank code: $code")
    }
}

/**
 * A single playing card. Either a [SuitedCard] or the [Joker].
 *
 * Cards are intentionally **not** [Comparable]: their relative strength depends entirely on the game
 * being played (trump suit, bowers, no-trump, etc.), so ordering is provided by game-specific logic.
 */
@Serializable
sealed interface Card {
    /** A compact, stable, human-readable code such as `"AS"`, `"TH"`, or `"JK"`. */
    val code: String

    /** A display label such as `"A♠"`, `"10♥"`, or `"Joker"`. */
    val label: String

    companion object {
        /** Parses a [code] produced by [Card.code]. */
        fun parse(code: String): Card {
            val trimmed = code.trim()
            if (trimmed.equals("JK", ignoreCase = true)) return Joker
            require(trimmed.length == 2) { "Invalid card code: $code" }
            return SuitedCard(Rank.fromCode(trimmed[0]), Suit.fromLetter(trimmed[1]))
        }
    }
}

/** A ranked card in one of the four suits. */
@Serializable
data class SuitedCard(val rank: Rank, val suit: Suit) : Card {
    override val code: String get() = "${rank.code}${suit.letter}"
    override val label: String get() = "${rank.label}${suit.symbol}"
    val color: CardColor get() = suit.color
    override fun toString(): String = code
}

/** The single Joker. Games that use it (e.g. 500) give it game-specific ranking. */
@Serializable
data object Joker : Card {
    override val code: String get() = "JK"
    override val label: String get() = "Joker"
    override fun toString(): String = code
}

/** Convenience: build a [SuitedCard]. */
infix fun Rank.of(suit: Suit): SuitedCard = SuitedCard(this, suit)
