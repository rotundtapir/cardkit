// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.CardColor
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.SuitedCard

/** Default aspect-correct playing-card width. Height is derived as 1.4×. */
val DefaultCardWidth: Dp = 64.dp

private val CardFace = Color(0xFFFAFAFA)
private val BlackInk = Color(0xFF1A1A1A)
private val RedInk = Color(0xFFC62828)

private fun Card.ink(): Color = when (this) {
    is SuitedCard -> if (color == CardColor.RED) RedInk else BlackInk
    Joker -> RedInk
}

/** Stable human-readable label, e.g. "10♥" or "Joker" — also used as the card's UI-test tag. */
val Card.displayLabel: String
    get() = when (this) {
        is SuitedCard -> rank.label + suit.symbol
        Joker -> "Joker"
    }

/**
 * Renders a single face-up [card] as a rounded white card with rank/suit pips in opposite corners and
 * a large central symbol. The [Joker] is drawn with a "JOKER" label.
 */
@Composable
fun PlayingCard(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp = DefaultCardWidth,
) {
    val height = width * 1.4f
    val ink = card.ink()
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(width * 0.12f))
            .background(CardFace)
            .border(1.dp, Color(0x33000000), RoundedCornerShape(width * 0.12f))
            .testTag("card:${card.displayLabel}"),
    ) {
        when (card) {
            is SuitedCard -> {
                CornerLabel(card.rank.label, card.suit.symbol, ink, width, Alignment.TopStart)
                Text(
                    text = card.suit.symbol,
                    color = ink,
                    fontSize = (width.value * 0.44f).sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                CornerLabel(card.rank.label, card.suit.symbol, ink, width, Alignment.BottomEnd)
            }
            Joker -> {
                Text(
                    text = "JOKER",
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = (width.value * 0.16f).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(2.dp),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CornerLabel(
    rank: String,
    suit: String,
    ink: Color,
    width: Dp,
    alignment: Alignment,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.align(alignment).padding(width * 0.06f),
    ) {
        Text(rank, color = ink, fontWeight = FontWeight.Bold, fontSize = (width.value * 0.2f).sp)
        Text(suit, color = ink, fontSize = (width.value * 0.2f).sp)
    }
}

/** The back of a card, for opponents' hidden hands and the stock/kitty. */
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
            .background(Color(0xFF303F9F))
            .border(1.dp, Color(0x55000000), RoundedCornerShape(width * 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(width * 0.1f)
                .clip(RoundedCornerShape(width * 0.08f))
                .background(Color(0x33FFFFFF)),
        )
    }
}
