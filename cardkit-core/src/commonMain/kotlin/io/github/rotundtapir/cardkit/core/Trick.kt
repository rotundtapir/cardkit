// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlinx.serialization.Serializable

/**
 * One card played to a trick, by a seat, optionally nominating a suit (a game may let the Joker be
 * led with a named suit to follow — 500 does at no-trump).
 */
@Serializable
data class TrickPlay(val seat: Seat, val card: Card, val nominated: Suit? = null)

/** How the Joker ranks in tricks, for games that include it in the deck. */
enum class JokerRole {
    /** Not in the deck (standard Euchre). A stray Joker is never trump and cannot win a trick. */
    ABSENT,

    /** The highest trump, above the right bower (500 suit contracts, Euchre's "Benny"). */
    HIGHEST_TRUMP,

    /**
     * The only trump: there is no trump suit, and the Joker beats everything (500 no-trump, where
     * leading it may nominate the suit to follow via [TrickPlay.nominated]).
     */
    SOLE_TRUMP,
}

/** The suit of the Jack that acts as the left bower for [suit] (the other suit of the same colour). */
fun leftBowerSuit(suit: Suit): Suit = when (suit) {
    Suit.SPADES -> Suit.CLUBS
    Suit.CLUBS -> Suit.SPADES
    Suit.HEARTS -> Suit.DIAMONDS
    Suit.DIAMONDS -> Suit.HEARTS
}

/**
 * Encapsulates trump-and-bower trick-taking rules for one hand: which cards are trumps, what suit
 * each card effectively belongs to for following, and which card wins a trick.
 *
 * With a [trumpSuit] and [bowersEnabled] the trump order (high→low) is: Joker (when
 * [JokerRole.HIGHEST_TRUMP]), right bower (Jack of the trump suit), left bower (Jack of the
 * same-colour suit), then A K Q … of the trump suit. The left bower counts as a member of the trump
 * suit, not its printed suit. With [bowersEnabled] = false the Jacks stay in their printed suits
 * (plain whist-style trumps). With [trumpSuit] = null nothing is trump except the Joker when it is
 * the [JokerRole.SOLE_TRUMP]; otherwise the highest card of the led suit wins.
 *
 * Instantiation per game: 500 suit contract = `(suit, HIGHEST_TRUMP)`; 500 no-trump =
 * `(null, SOLE_TRUMP)`; Euchre = `(suit, ABSENT)`; Euchre with the Benny house rule =
 * `(suit, HIGHEST_TRUMP)`.
 */
class TrickEvaluator(
    val trumpSuit: Suit?,
    val jokerRole: JokerRole,
    val bowersEnabled: Boolean = true,
) {

    fun isRightBower(card: Card): Boolean =
        bowersEnabled && trumpSuit != null && card is SuitedCard &&
            card.rank == Rank.JACK && card.suit == trumpSuit

    fun isLeftBower(card: Card): Boolean =
        bowersEnabled && trumpSuit != null && card is SuitedCard &&
            card.rank == Rank.JACK && card.suit == leftBowerSuit(trumpSuit)

    /** Whether [card] is a trump: any trump-suit card (incl. both bowers), or a Joker that is in play. */
    fun isTrump(card: Card): Boolean = when {
        card is Joker -> jokerRole != JokerRole.ABSENT
        trumpSuit == null -> false
        isLeftBower(card) -> true
        card is SuitedCard -> card.suit == trumpSuit
        else -> false
    }

    /**
     * The effective suit of [card]: the suit it "belongs to" for following. The left bower belongs
     * to the trump suit; a in-play Joker belongs to the trump suit (`null` when it is the sole
     * trump); an [JokerRole.ABSENT] Joker belongs to no suit.
     */
    fun effectiveSuit(card: Card): Suit? = when {
        card is Joker -> if (jokerRole == JokerRole.ABSENT) null else trumpSuit
        isLeftBower(card) -> trumpSuit
        card is SuitedCard -> card.suit
        else -> null
    }

    /**
     * A comparable strength for [card] within a trick whose led suit is [ledSuit]. Higher wins; a
     * card that cannot win (off-suit and non-trump) scores below every eligible card.
     */
    fun strength(card: Card, ledSuit: Suit?): Int {
        if (trumpSuit != null) {
            if (isTrump(card)) {
                return when {
                    card is Joker -> 1000
                    isRightBower(card) -> 900
                    isLeftBower(card) -> 800
                    card is SuitedCard -> 100 + card.rank.ordinal
                    else -> 100
                }
            }
            // Non-trump card: only wins if it is of the led suit.
            return if (card is SuitedCard && card.suit == ledSuit) card.rank.ordinal else -1
        }
        // No trump suit: only a sole-trump Joker beats the led suit.
        return when {
            card is Joker && jokerRole == JokerRole.SOLE_TRUMP -> 1000
            card is SuitedCard && card.suit == ledSuit -> card.rank.ordinal
            else -> -1
        }
    }

    /**
     * The led suit established by the first play of a trick. Normally the effective suit of the led
     * card; if the sole-trump Joker is led it is the [TrickPlay.nominated] suit (or `null` if the
     * leader named none, leaving following unconstrained).
     */
    fun ledSuitOf(firstPlay: TrickPlay): Suit? = when {
        firstPlay.card is Joker && trumpSuit == null && jokerRole == JokerRole.SOLE_TRUMP -> firstPlay.nominated
        else -> effectiveSuit(firstPlay.card)
    }

    /** The seat that wins a completed [trick] (played in order, at least one play). */
    fun winner(trick: List<TrickPlay>): Seat {
        require(trick.isNotEmpty()) { "Empty trick has no winner" }
        val ledSuit = ledSuitOf(trick.first())
        return trick.maxBy { strength(it.card, ledSuit) }.seat
    }

    /**
     * The legal cards from [hand] when [ledSuit] has been led (pass `null` only for the
     * sole-trump-Joker-led-without-nomination case, which leaves play unconstrained). Players must
     * follow the led suit if able; when void they may play anything. A sole-trump Joker may always
     * be played.
     */
    fun legalFollows(hand: List<Card>, ledSuit: Suit?): List<Card> {
        if (ledSuit == null) return hand
        val following = hand.filter { effectiveSuit(it) == ledSuit }
        if (following.isEmpty()) return hand
        return if (trumpSuit == null && jokerRole == JokerRole.SOLE_TRUMP) {
            following + hand.filter { it is Joker } // the sole-trump Joker is always playable
        } else {
            following
        }
    }
}
