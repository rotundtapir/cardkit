// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunningStatTest {

    @Test
    fun `mean tracks the running average`() {
        val stat = RunningStat()
        listOf(1.0, 2.0, 3.0, 4.0).forEach(stat::add)
        assertEquals(4, stat.n)
        assertEquals(2.5, stat.mean)
    }

    @Test
    fun `standard error is infinite below two samples`() {
        val stat = RunningStat()
        assertEquals(Double.POSITIVE_INFINITY, stat.standardError)
        stat.add(5.0)
        assertEquals(Double.POSITIVE_INFINITY, stat.standardError)
        stat.add(5.0)
        assertEquals(0.0, stat.standardError)
    }

    @Test
    fun `standard error matches the direct formula`() {
        val xs = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val stat = RunningStat()
        xs.forEach(stat::add)
        val mean = xs.average()
        val sampleVariance = xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1)
        val expected = kotlin.math.sqrt(sampleVariance / xs.size)
        assertTrue(abs(stat.standardError - expected) < 1e-12)
    }
}
