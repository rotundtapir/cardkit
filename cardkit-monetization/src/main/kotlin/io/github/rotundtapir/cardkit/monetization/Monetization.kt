// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.monetization

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/**
 * The single seam through which app UI interacts with monetization, so shared code never references
 * a proprietary ad or billing symbol directly.
 *
 * Two implementations exist:
 *  - [io.github.rotundtapir.cardkit.monetization.foss.FossMonetization] — no ads, "remove ads" opens
 *    a donation page. Lives in this module (no proprietary dependencies). Used by F-Droid/GitHub builds.
 *  - `PlayMonetization` in `cardkit-monetization-play` — real Google Mobile Ads + Play Billing. Only
 *    an app's `play` build flavor depends on that module.
 */
interface Monetization {
    /**
     * Whether ads should be hidden — `true` when the user has purchased "remove ads" (Play) or always
     * `true` in FOSS builds. UI observes this to decide whether to reserve space for a banner.
     */
    val adsRemoved: StateFlow<Boolean>

    /** Whether a "remove ads" purchase is even offered (false in FOSS builds, which show a donation prompt instead). */
    val offersRemoveAds: Boolean

    /** A banner ad slot. A no-op composable in FOSS builds and when [adsRemoved] is true. */
    @Composable
    fun BannerSlot(modifier: Modifier)

    /** Show an interstitial if one is ready and ads are enabled; otherwise does nothing. */
    fun maybeShowInterstitial(activity: Activity)

    /**
     * Either launch the "remove ads" purchase flow (Play) or open the donation page (FOSS), depending
     * on the implementation.
     */
    fun launchRemoveAdsOrDonate(activity: Activity)

    /** Release any held resources (ad views, billing connections). Called when the app is destroyed. */
    fun dispose()
}
