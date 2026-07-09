// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.monetization.browser

import io.github.rotundtapir.cardkit.monetization.NoAdsMonetization
import kotlinx.browser.window

/**
 * Monetization for browser (wasmJs) builds: the same no-ads behaviour as the Android FOSS
 * implementation — the "support" action opens a donation page in a new tab.
 *
 * @param donationUrl the page opened by [launchRemoveAdsOrDonate] (e.g. a Liberapay / GitHub Sponsors URL).
 */
class BrowserMonetization(private val donationUrl: String) : NoAdsMonetization() {

    override fun launchRemoveAdsOrDonate() {
        // noopener: window.open (unlike anchors) hands the opened page a window.opener back-
        // reference by default, letting it navigate this tab (reverse tabnabbing).
        window.open(donationUrl, "_blank", "noopener,noreferrer")
    }
}
