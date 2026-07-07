// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.monetization.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rotundtapir.cardkit.monetization.Monetization
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Monetization for browser (wasmJs) builds: the same no-ads behaviour as the Android FOSS
 * implementation — the "support" action opens a donation page in a new tab.
 *
 * @param donationUrl the page opened by [launchRemoveAdsOrDonate] (e.g. a Liberapay / GitHub Sponsors URL).
 */
class BrowserMonetization(private val donationUrl: String) : Monetization {

    // Ads are always "removed" in browser builds — nothing to show.
    override val adsRemoved: StateFlow<Boolean> = MutableStateFlow(true)

    override val offersRemoveAds: Boolean = false

    @Composable
    override fun BannerSlot(modifier: Modifier) {
        // No ads in browser builds.
    }

    override fun maybeShowInterstitial(onDismissed: () -> Unit) {
        // No ads in browser builds — the caller's continuation runs at once.
        onDismissed()
    }

    override fun launchRemoveAdsOrDonate() {
        window.open(donationUrl, "_blank")
    }

    override fun dispose() {
        // Nothing to release.
    }
}
