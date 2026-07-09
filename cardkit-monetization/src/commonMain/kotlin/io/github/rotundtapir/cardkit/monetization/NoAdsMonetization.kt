// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.monetization

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Base for the ad-free [Monetization] implementations (Android FOSS, browser): ads are permanently
 * "removed", the banner slot renders nothing, interstitial continuations run at once, and there is
 * nothing to release. Subclasses supply only their platform's way of opening the donation page —
 * keeping the no-ads contract in one place so a new interface member cannot be handled on one
 * platform and forgotten on the other.
 */
abstract class NoAdsMonetization : Monetization {

    // Ads are always "removed" — nothing to show.
    override val adsRemoved: StateFlow<Boolean> = MutableStateFlow(true)

    override val offersRemoveAds: Boolean = false

    @Composable
    override fun BannerSlot(modifier: Modifier) {
        // No ads.
    }

    override fun maybeShowInterstitial(onDismissed: () -> Unit) {
        // No ad to show — the caller's continuation runs at once.
        onDismissed()
    }

    override fun dispose() {
        // Nothing to release.
    }
}
