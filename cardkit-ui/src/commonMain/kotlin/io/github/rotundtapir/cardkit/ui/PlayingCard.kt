// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.SuitedCard
import io.github.rotundtapir.cardkit.ui.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Default aspect-correct playing-card width. Height is derived as 1.4×. */
val DefaultCardWidth: Dp = 64.dp

/** Stable human-readable label, e.g. "10♥" or "Joker" — also used as the card's UI-test tag. */
val Card.displayLabel: String
    get() = when (this) {
        is SuitedCard -> rank.label + suit.symbol
        Joker -> "Joker"
    }

/**
 * Card-face artwork: Byron Knoll's public-domain "Vector Playing Cards"
 * (https://code.google.com/archive/p/vector-playing-cards/), bundled as downscaled PNGs.
 */
private fun Card.faceRes(): DrawableResource = when (this) {
    Joker -> Res.drawable.card_red_joker
    is SuitedCard -> when (suit) {
        Suit.CLUBS -> when (rank) {
            Rank.TWO -> Res.drawable.card_2_of_clubs
            Rank.THREE -> Res.drawable.card_3_of_clubs
            Rank.FOUR -> Res.drawable.card_4_of_clubs
            Rank.FIVE -> Res.drawable.card_5_of_clubs
            Rank.SIX -> Res.drawable.card_6_of_clubs
            Rank.SEVEN -> Res.drawable.card_7_of_clubs
            Rank.EIGHT -> Res.drawable.card_8_of_clubs
            Rank.NINE -> Res.drawable.card_9_of_clubs
            Rank.TEN -> Res.drawable.card_10_of_clubs
            Rank.ELEVEN -> Res.drawable.card_11_of_clubs
            Rank.TWELVE -> Res.drawable.card_12_of_clubs
            Rank.THIRTEEN -> Res.drawable.card_13_of_clubs
            Rank.JACK -> Res.drawable.card_jack_of_clubs
            Rank.QUEEN -> Res.drawable.card_queen_of_clubs
            Rank.KING -> Res.drawable.card_king_of_clubs
            Rank.ACE -> Res.drawable.card_ace_of_clubs
        }
        Suit.DIAMONDS -> when (rank) {
            Rank.TWO -> Res.drawable.card_2_of_diamonds
            Rank.THREE -> Res.drawable.card_3_of_diamonds
            Rank.FOUR -> Res.drawable.card_4_of_diamonds
            Rank.FIVE -> Res.drawable.card_5_of_diamonds
            Rank.SIX -> Res.drawable.card_6_of_diamonds
            Rank.SEVEN -> Res.drawable.card_7_of_diamonds
            Rank.EIGHT -> Res.drawable.card_8_of_diamonds
            Rank.NINE -> Res.drawable.card_9_of_diamonds
            Rank.TEN -> Res.drawable.card_10_of_diamonds
            Rank.ELEVEN -> Res.drawable.card_11_of_diamonds
            Rank.TWELVE -> Res.drawable.card_12_of_diamonds
            Rank.THIRTEEN -> Res.drawable.card_13_of_diamonds
            Rank.JACK -> Res.drawable.card_jack_of_diamonds
            Rank.QUEEN -> Res.drawable.card_queen_of_diamonds
            Rank.KING -> Res.drawable.card_king_of_diamonds
            Rank.ACE -> Res.drawable.card_ace_of_diamonds
        }
        Suit.HEARTS -> when (rank) {
            Rank.TWO -> Res.drawable.card_2_of_hearts
            Rank.THREE -> Res.drawable.card_3_of_hearts
            Rank.FOUR -> Res.drawable.card_4_of_hearts
            Rank.FIVE -> Res.drawable.card_5_of_hearts
            Rank.SIX -> Res.drawable.card_6_of_hearts
            Rank.SEVEN -> Res.drawable.card_7_of_hearts
            Rank.EIGHT -> Res.drawable.card_8_of_hearts
            Rank.NINE -> Res.drawable.card_9_of_hearts
            Rank.TEN -> Res.drawable.card_10_of_hearts
            Rank.ELEVEN -> Res.drawable.card_11_of_hearts
            Rank.TWELVE -> Res.drawable.card_12_of_hearts
            Rank.THIRTEEN -> Res.drawable.card_13_of_hearts
            Rank.JACK -> Res.drawable.card_jack_of_hearts
            Rank.QUEEN -> Res.drawable.card_queen_of_hearts
            Rank.KING -> Res.drawable.card_king_of_hearts
            Rank.ACE -> Res.drawable.card_ace_of_hearts
        }
        Suit.SPADES -> when (rank) {
            Rank.TWO -> Res.drawable.card_2_of_spades
            Rank.THREE -> Res.drawable.card_3_of_spades
            Rank.FOUR -> Res.drawable.card_4_of_spades
            Rank.FIVE -> Res.drawable.card_5_of_spades
            Rank.SIX -> Res.drawable.card_6_of_spades
            Rank.SEVEN -> Res.drawable.card_7_of_spades
            Rank.EIGHT -> Res.drawable.card_8_of_spades
            Rank.NINE -> Res.drawable.card_9_of_spades
            Rank.TEN -> Res.drawable.card_10_of_spades
            Rank.ELEVEN -> Res.drawable.card_11_of_spades
            Rank.TWELVE -> Res.drawable.card_12_of_spades
            Rank.THIRTEEN -> Res.drawable.card_13_of_spades
            Rank.JACK -> Res.drawable.card_jack_of_spades
            Rank.QUEEN -> Res.drawable.card_queen_of_spades
            Rank.KING -> Res.drawable.card_king_of_spades
            Rank.ACE -> Res.drawable.card_ace_of_spades
        }
    }
}

/**
 * Renders a single face-up [card] using the bundled public-domain card artwork, on a white rounded
 * face so the transparent sprite corners don't show the table through.
 */
@Composable
fun PlayingCard(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp = DefaultCardWidth,
) {
    val height = width * 1.4f
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(width * 0.08f))
            .background(Color(0xFFFAFAFA))
            .border(1.dp, Color(0x33000000), RoundedCornerShape(width * 0.08f))
            .testTag("card:${card.displayLabel}"),
    ) {
        Image(
            painter = painterResource(card.faceRes()),
            contentDescription = card.displayLabel,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The back of a card, for opponents' hidden hands and the stock/kitty. Renders the bundled
 * original card-back artwork (an indigo lattice, drawn for this library — public domain).
 */
@Composable
fun CardBack(
    modifier: Modifier = Modifier,
    width: Dp = DefaultCardWidth,
) {
    val height = width * 1.4f
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(width * 0.12f))
            .background(Color.White)
            .border(1.dp, Color(0x55000000), RoundedCornerShape(width * 0.12f)),
    ) {
        Image(
            painter = painterResource(Res.drawable.card_back),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
