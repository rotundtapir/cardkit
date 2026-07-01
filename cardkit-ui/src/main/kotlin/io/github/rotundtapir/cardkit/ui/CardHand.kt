// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Card
import kotlin.math.roundToInt

/**
 * A fanned, overlapping row of face-up cards — a player's hand.
 *
 * Cards that are not [playable] are not clickable and (when [dimUnplayable]) dimmed with an opaque
 * scrim — never translucency, which would let overlapped cards bleed through. A card in [selected]
 * is lifted upward; the layout reserves headroom so the lift is not clipped. Overlap is expressed as
 * a fraction of card width (0 = fully stacked, 1 = fully separated).
 */
@Composable
fun CardHand(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    cardWidth: Dp = DefaultCardWidth,
    overlap: Float = 0.5f,
    liftHeight: Dp = 16.dp,
    playable: (Card) -> Boolean = { true },
    dimUnplayable: Boolean = true,
    selected: Set<Card> = emptySet(),
    onCardClick: (Card) -> Unit = {},
) {
    Layout(
        modifier = modifier.wrapContentSize(),
        content = {
            cards.forEach { card ->
                val enabled = playable(card)
                val lift = if (card in selected) -liftHeight.value else 0f
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = lift * density }
                        .then(
                            if (enabled) Modifier.clickable { onCardClick(card) } else Modifier
                        ),
                ) {
                    PlayingCard(card, width = cardWidth)
                    if (!enabled && dimUnplayable) {
                        // Opaque-backed scrim: dims the card without exposing the one beneath.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(cardWidth * 0.08f))
                                .background(Color(0x8FFAFAFA)),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        if (placeables.isEmpty()) return@Layout layout(0, 0) {}

        val cw = placeables.first().width
        val step = (cw * overlap).roundToInt().coerceAtLeast(1)
        val width = cw + step * (placeables.size - 1)
        val liftPx = liftHeight.roundToPx()
        val height = placeables.maxOf { it.height } + liftPx

        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                p.placeRelative(x = step * i, y = liftPx)
            }
        }
    }
}
