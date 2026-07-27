// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DataStoreKeyValueStore] against a real temp-file [DataStore] via its internal
 * DataStore-injecting constructor — no Android runtime, no Robolectric (ported from 500's
 * DataStoreSettingsRepositoryTest). The typed-key assertions are the migration contract: values a
 * shipped 500 build wrote under `stringPreferencesKey`/`booleanPreferencesKey`/
 * `floatPreferencesKey` must read back through this seam unchanged, and vice versa.
 */
class DataStoreKeyValueStoreTest {

    private val tempFiles = mutableListOf<File>()

    private fun newStore(): DataStore<Preferences> {
        val file = File.createTempFile("settings-${UUID.randomUUID()}", ".preferences_pb").also { tempFiles += it }
        file.delete() // DataStore wants to own creation
        return PreferenceDataStoreFactory.create { file }
    }

    @AfterTest
    fun cleanUp() {
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `unset keys read as null`() = runTest {
        val store = DataStoreKeyValueStore(newStore())
        assertEquals(null, store.string("animation_speed").first())
        assertEquals(null, store.boolean("hold_tricks").first())
        assertEquals(null, store.float("sound_volume").first())
    }

    @Test
    fun `every type round-trips through storage`() = runTest {
        val dataStore = newStore()
        val store = DataStoreKeyValueStore(dataStore)
        store.putString("player_name", "Alice")
        store.putBoolean("hold_tricks", true)
        store.putFloat("sound_volume", 0.3f)

        // A fresh wrapper over the same store reads back the persisted values.
        val reopened = DataStoreKeyValueStore(dataStore)
        assertEquals("Alice", reopened.string("player_name").first())
        assertEquals(true, reopened.boolean("hold_tricks").first())
        assertEquals(0.3f, reopened.float("sound_volume").first())
    }

    @Test
    fun `values written under the typed preference keys read back through the seam`() = runTest {
        // What an existing install's DataStore file contains (written by 500's old repository).
        val dataStore = newStore()
        dataStore.edit {
            it[stringPreferencesKey("animation_speed")] = "FAST"
            it[booleanPreferencesKey("misere_enabled")] = false
            it[floatPreferencesKey("sound_volume")] = 0.25f
        }
        val store = DataStoreKeyValueStore(dataStore)
        assertEquals("FAST", store.string("animation_speed").first())
        assertEquals(false, store.boolean("misere_enabled").first())
        assertEquals(0.25f, store.float("sound_volume").first())
    }

    @Test
    fun `values written through the seam land under the typed preference keys`() = runTest {
        val dataStore = newStore()
        val store = DataStoreKeyValueStore(dataStore)
        store.putString("bot_skill", "ADVANCED")
        store.putBoolean("sort_hand_by_default", true)
        store.putFloat("sound_volume", 0.9f)

        val preferences = dataStore.data.first()
        assertEquals("ADVANCED", preferences[stringPreferencesKey("bot_skill")])
        assertEquals(true, preferences[booleanPreferencesKey("sort_hand_by_default")])
        assertEquals(0.9f, preferences[floatPreferencesKey("sound_volume")])
    }

    @Test
    fun `the typed helpers compose 500's accessor patterns over the seam`() = runTest {
        val dataStore = newStore()
        dataStore.edit { it[stringPreferencesKey("animation_speed")] = "TURBO" } // unrecognised
        val store = DataStoreKeyValueStore(dataStore)
        assertEquals(
            AnimationSpeed.NORMAL,
            store.enumSetting("animation_speed", AnimationSpeed.NORMAL, AnimationSpeed::fromName).first(),
        )
        assertEquals(true, store.booleanSetting("misere_enabled", true).first())
        store.putEnum("animation_speed", AnimationSpeed.OFF)
        assertEquals(
            AnimationSpeed.OFF,
            store.enumSetting("animation_speed", AnimationSpeed.NORMAL, AnimationSpeed::fromName).first(),
        )
    }
}
