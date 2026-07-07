// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.monetization.foss

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rotundtapir.cardkit.monetization.Monetization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The free/libre monetization implementation used by F-Droid and GitHub builds: there are never any
 * ads, and the "remove ads / support" action opens a donation page in the browser.
 *
 * Contains no proprietary dependencies, so a build that uses only this module is pure GPLv3.
 *
 * @param context any context; only its application context is retained.
 * @param donationUrl the page opened by [launchRemoveAdsOrDonate] (e.g. a Liberapay / GitHub Sponsors URL).
 */
class FossMonetization(context: Context, private val donationUrl: String) : Monetization {

    private val appContext = context.applicationContext

    // Ads are always "removed" in FOSS builds — nothing to show.
    override val adsRemoved: StateFlow<Boolean> = MutableStateFlow(true)

    override val offersRemoveAds: Boolean = false

    @Composable
    override fun BannerSlot(modifier: Modifier) {
        // No ads in FOSS builds.
    }

    override fun maybeShowInterstitial(onDismissed: () -> Unit) {
        // No ads in FOSS builds — the caller's continuation runs at once.
        onDismissed()
    }

    override fun launchRemoveAdsOrDonate() {
        // FLAG_ACTIVITY_NEW_TASK lets this launch from the retained application context.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(donationUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    override fun dispose() {
        // Nothing to release.
    }
}
