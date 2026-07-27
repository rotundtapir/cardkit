// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import kotlin.math.sqrt

/** Welford running mean/variance for one search arm's rewards. */
class RunningStat {
    var n = 0
        private set
    var mean = 0.0
        private set
    private var m2 = 0.0

    fun add(x: Double) {
        n++
        val d = x - mean
        mean += d / n
        m2 += d * (x - mean)
    }

    /** Standard error of the mean; infinite below two samples so nothing is eliminated early. */
    val standardError: Double
        get() = if (n < 2) Double.POSITIVE_INFINITY else sqrt(m2 / (n - 1) / n)
}
