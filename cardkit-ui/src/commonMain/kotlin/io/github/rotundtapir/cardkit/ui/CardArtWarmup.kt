// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.SuitedCard

/**
 * Invisible composable that touches every bundled card bitmap so `painterResource` caches them
 * all up front. On web, image resources load asynchronously on first use — without this, the
 * first deal shows blank card backs and faces until each PNG arrives. Compose it once, for the
 * app's lifetime, stacked behind (or beside) the real UI; it draws nothing and handles no input.
 */
@Composable
fun CardArtWarmup(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(1.dp).alpha(0f)) {
        CardBack(width = 1.dp)
        PlayingCard(Joker, width = 1.dp)
        Suit.entries.forEach { suit ->
            Rank.entries.forEach { rank ->
                PlayingCard(SuitedCard(rank = rank, suit = suit), width = 1.dp)
            }
        }
    }
}
