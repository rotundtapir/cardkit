// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.monetization.foss

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.rotundtapir.cardkit.monetization.NoAdsMonetization

/**
 * The free/libre monetization implementation used by F-Droid and GitHub builds: there are never any
 * ads, and the "remove ads / support" action opens a donation page in the browser.
 *
 * Contains no proprietary dependencies, so a build that uses only this module is pure GPLv3.
 *
 * @param context any context; only its application context is retained.
 * @param donationUrl the page opened by [launchRemoveAdsOrDonate] (e.g. a Liberapay / GitHub Sponsors URL).
 */
class FossMonetization(context: Context, private val donationUrl: String) : NoAdsMonetization() {

    private val appContext = context.applicationContext

    override fun launchRemoveAdsOrDonate() {
        // FLAG_ACTIVITY_NEW_TASK lets this launch from the retained application context.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(donationUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No browser installed (plausible on the de-Googled devices FOSS builds attract) —
            // failing to open a donation page must never crash the game.
        }
    }
}
