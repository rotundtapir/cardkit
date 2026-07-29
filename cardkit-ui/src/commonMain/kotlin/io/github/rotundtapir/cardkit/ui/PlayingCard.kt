// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
// WildcardImport: the compose-resources `Res` facade is generated and accessed via a wildcard.
@file:Suppress("WildcardImport")

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

/** Default aspect-correct playing-card width. Height is derived as [CardAspectRatio]. */
val DefaultCardWidth: Dp = 64.dp

/** Height:width ratio of a playing card; use it wherever card geometry is mirrored. */
const val CardAspectRatio = 1.4f

/**
 * The rounded-corner shape of a card face at [width]. Shared so overlays that must clip to the
 * face (e.g. [CardHand]'s unplayable scrim) stay in step if the radius ever changes.
 */
fun cardFaceShape(width: Dp): RoundedCornerShape = RoundedCornerShape(width * 0.08f)

/**
 * Card-face artwork: Byron Knoll's public-domain "Vector Playing Cards"
 * (https://code.google.com/archive/p/vector-playing-cards/), bundled as downscaled PNGs.
 */
// An exhaustive card → bundled-artwork lookup: flagged as "complex" only because every rank of
// every suit is one arm. It is a flat data mapping, not branching logic.
@Suppress("CyclomaticComplexMethod", "LongMethod")
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
 * Test-tag prefix of every card face [PlayingCard] draws: `ck:card:`.
 *
 * The `ck:` namespace is deliberate. Games tag their own card wrappers too, and an un-namespaced
 * `card:` prefix put both in one flat namespace: a single prefix query then matched the game's
 * wrapper, the [PlayingCard] nested inside it, *and* every card [CardArtWarmup] pre-renders — in
 * euchre, 76 nodes where 5 were wanted. **A game tagging its own card containers must not reuse
 * this prefix**; pick a distinct one (euchre uses `hand:` for the human's fan).
 *
 * Even namespaced, a bare prefix query is global: cardkit puts this tag on *every* card it draws —
 * hands, tricks, up-cards, dialog illustrations, the warmup deck. Scope card queries to the
 * container you mean (`hasAnyAncestor(hasTestTag(myHandTag))`) rather than counting raw matches.
 */
const val CardTestTagPrefix = "ck:card:"

/**
 * The test tag [PlayingCard] puts on [card]: [CardTestTagPrefix] plus the card's [Card.label], e.g.
 * `ck:card:J♠`. Keyed by **label, not [Card.code]** — `ck:card:J♠`, never `ck:card:JS`. The two look
 * interchangeable in a failing test and are not.
 */
fun cardTestTag(card: Card): String = "$CardTestTagPrefix${card.label}"

/** Applies [tag] as a test tag, or nothing at all when it is `null`. */
private fun Modifier.testTagOrNone(tag: String?): Modifier = if (tag == null) this else testTag(tag)

/**
 * Renders a single face-up [card] using the bundled public-domain card artwork, on a white rounded
 * face so the transparent sprite corners don't show the table through.
 *
 * Carries the test tag [cardTestTag] (`ck:card:<label>`) — read [CardTestTagPrefix] before writing a
 * query against it. Pass [testTag] to substitute your own tag, or `null` to leave the card untagged
 * (what [CardArtWarmup] does for its off-screen deck).
 */
@Composable
fun PlayingCard(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp = DefaultCardWidth,
    testTag: String? = cardTestTag(card),
) {
    val height = width * CardAspectRatio
    Box(
        modifier = modifier
            .size(width, height)
            .clip(cardFaceShape(width))
            .background(Color(0xFFFAFAFA))
            .border(1.dp, Color(0x33000000), cardFaceShape(width))
            .testTagOrNone(testTag),
    ) {
        Image(
            painter = painterResource(card.faceRes()),
            contentDescription = card.label,
            // FillBounds, not Fit: the sprites are 256x372 (aspect 1.4531) on a 1.4 face, and Fit
            // height-fits them, leaving ~2% white gutters down BOTH SIDES. Each sprite carries its
            // own baked-in gray edge line, so a gutter puts two dark lines with a white sliver
            // between them on every side edge - in an overlapped hand fan that reads as a stack of
            // cards behind each card. FillBounds squashes the art 3.7% vertically (imperceptible
            // at table sizes) and is what CardBack already does, which is why backs never showed
            // the artifact. The aspect-correct fix (CardAspectRatio = 1.453125) is a cross-game
            // geometry change; take it deliberately if the art ever changes.
            contentScale = ContentScale.FillBounds,
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
    val height = width * CardAspectRatio
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
