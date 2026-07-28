// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.SuitedCard

/**
 * Every card face cardkit bundles art for: the Joker plus the full `Suit × Rank` sweep —
 * [CardArtWarmup]'s default. A game whose deck is smaller should pass its own list instead:
 * each warmed face costs its PNG fetch (on web, before the first deal) and a decoded
 * `ImageBitmap` held for the process lifetime.
 */
val AllCardArt: List<Card> =
    listOf(Joker) + Suit.entries.flatMap { suit -> Rank.entries.map { rank -> SuitedCard(rank = rank, suit = suit) } }

/**
 * Invisible composable that touches [cards]' bitmaps (plus the card back) so `painterResource`
 * caches them up front. On web, image resources load asynchronously on first use — without this,
 * the first deal shows blank card backs and faces until each PNG arrives. Compose it once, for the
 * app's lifetime, stacked behind (or beside) the real UI; it draws nothing and handles no input.
 *
 * [cards] defaults to [AllCardArt]; a game should pass the deck it actually deals (euchre's
 * 25-card deck skips ~780 KB of PNG fetches and ~15 MB of process-lifetime bitmap cache that its
 * players can never see).
 *
 * **UI tests:** `clearAndSetSemantics {}` prunes only the **merged** semantics tree. TalkBack and
 * the browser a11y tree therefore see nothing here, but the pre-rendered cards inside are still
 * reachable from `useUnmergedTree = true` finders — that is inherent to `clearAndSetSemantics`, not
 * a bug to re-diagnose. Their test tags are suppressed ([PlayingCard]'s `testTag = null`), so a
 * [CardTestTagPrefix] query no longer trips over them; each card's `contentDescription` (its
 * [io.github.rotundtapir.cardkit.core.Card.label]) still is visible. Scope card queries to the
 * container you mean rather than matching the whole tree.
 */
@Composable
fun CardArtWarmup(modifier: Modifier = Modifier, cards: List<Card> = AllCardArt) {
    // clearAndSetSemantics: alpha hides pixels, not semantics — without it every warmed card
    // shows up in the browser's accessibility tree as a phantom img before the real UI. It prunes
    // the merged tree only, hence testTag = null below: an unmerged-tree card query in a consumer's
    // instrumented suite would otherwise find every warmed card.
    Box(modifier = modifier.size(1.dp).alpha(0f).clearAndSetSemantics {}) {
        CardBack(width = 1.dp)
        cards.forEach { card ->
            PlayingCard(card, width = 1.dp, testTag = null)
        }
    }
}
