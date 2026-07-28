// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The typed setting helpers against an in-memory [KeyValueStore]: defaults when unset, the enum
 * name round-trip, and the lenient-parse fallback — the accessor patterns 500's repositories use.
 */
class KeyValueSettingsTest {

    @Test
    fun `defaults hold when nothing is stored`() = runTest {
        val store = InMemoryKeyValueStore()
        assertEquals("dflt", store.stringSetting("name", "dflt").first())
        assertEquals(true, store.booleanSetting("misere_enabled", true).first())
        assertEquals(0.7f, store.floatSetting("sound_volume", 0.7f).first())
        assertEquals(
            AnimationSpeed.NORMAL,
            store.enumSetting("animation_speed", AnimationSpeed.NORMAL, AnimationSpeed::fromName).first(),
        )
    }

    @Test
    fun `every helper round-trips through its typed channel`() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString("name", "Alice")
        store.putBoolean("hold_tricks", true)
        store.putFloat("sound_volume", 0.3f)
        store.putEnum("bot_skill", BotSkill.ADVANCED)

        assertEquals("Alice", store.stringSetting("name", "").first())
        assertEquals(true, store.booleanSetting("hold_tricks", false).first())
        assertEquals(0.3f, store.floatSetting("sound_volume", 0.7f).first())
        assertEquals(
            BotSkill.ADVANCED,
            store.enumSetting("bot_skill", BotSkill.STANDARD, BotSkill::fromName).first(),
        )
    }

    @Test
    fun `enums persist by exact entry name`() = runTest {
        // The names are the wire format shared with existing installs — never localized labels.
        val store = InMemoryKeyValueStore()
        store.putEnum("animation_speed", AnimationSpeed.FAST)
        assertEquals("FAST", store.string("animation_speed").first())
    }

    @Test
    fun `an unrecognised stored enum value falls back to the default`() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString("animation_speed", "TURBO")
        assertEquals(
            AnimationSpeed.NORMAL,
            store.enumSetting("animation_speed", AnimationSpeed.NORMAL, AnimationSpeed::fromName).first(),
        )
    }

    @Test
    fun `setting flows emit on every change`() = runTest {
        val store = InMemoryKeyValueStore()
        val volumes = store.floatSetting("sound_volume", 0.7f)
        assertEquals(0.7f, volumes.first())
        store.putFloat("sound_volume", 0.1f)
        assertEquals(0.1f, volumes.first())
        // Game-side mapping (e.g. 500's 0..1 coercion) composes on top without a new seam.
        store.putFloat("sound_volume", 5f)
        assertEquals(1f, volumes.map { it.coerceIn(0f, 1f) }.first())
    }
}
