// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** [AnimationSpeed] cycle and lenient parsing (ported from 500's SettingsTest). */
class AnimationSpeedTest {

    @Test
    fun `next cycles Slow to Normal to Fast to Off and back`() {
        assertEquals(AnimationSpeed.NORMAL, AnimationSpeed.SLOW.next())
        assertEquals(AnimationSpeed.FAST, AnimationSpeed.NORMAL.next())
        assertEquals(AnimationSpeed.OFF, AnimationSpeed.FAST.next())
        assertEquals(AnimationSpeed.SLOW, AnimationSpeed.OFF.next())
    }

    @Test
    fun `fromName parses known names and rejects the rest`() {
        assertEquals(AnimationSpeed.OFF, AnimationSpeed.fromName("OFF"))
        assertEquals(AnimationSpeed.SLOW, AnimationSpeed.fromName("SLOW"))
        assertEquals(null, AnimationSpeed.fromName("off")) // exact name match only
        assertEquals(null, AnimationSpeed.fromName("bogus"))
        assertEquals(null, AnimationSpeed.fromName(null))
    }
}

/** [BotSkill] lenient parsing (mirrors [AnimationSpeed]'s contract for persisted values). */
class BotSkillTest {

    @Test
    fun `fromName parses known names and rejects the rest`() {
        assertEquals(BotSkill.STANDARD, BotSkill.fromName("STANDARD"))
        assertEquals(BotSkill.ADVANCED, BotSkill.fromName("ADVANCED"))
        assertEquals(null, BotSkill.fromName("advanced")) // exact name match only
        assertEquals(null, BotSkill.fromName("ULTRA"))
        assertEquals(null, BotSkill.fromName(null))
    }
}
