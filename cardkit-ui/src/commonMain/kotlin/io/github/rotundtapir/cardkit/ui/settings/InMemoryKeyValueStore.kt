// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [KeyValueStore] held entirely in memory — nothing persists past the instance.
 *
 * This is the store for tests (both games' settings tests re-implemented it privately before it
 * was published) and for previews; production code wants a platform store
 * ([DataStoreKeyValueStore] on Android, `LocalStorageKeyValueStore` on web). Lives in main source
 * rather than a test artifact because cardkit-ui has no jvm target — consumers' androidUnitTest
 * and wasm test source sets both reach it here.
 */
class InMemoryKeyValueStore : KeyValueStore {
    private val strings = mutableMapOf<String, MutableStateFlow<String?>>()
    private val booleans = mutableMapOf<String, MutableStateFlow<Boolean?>>()
    private val floats = mutableMapOf<String, MutableStateFlow<Float?>>()

    override fun string(key: String): Flow<String?> = strings.getOrPut(key) { MutableStateFlow(null) }

    override suspend fun putString(key: String, value: String) {
        strings.getOrPut(key) { MutableStateFlow(null) }.value = value
    }

    override fun boolean(key: String): Flow<Boolean?> = booleans.getOrPut(key) { MutableStateFlow(null) }

    override suspend fun putBoolean(key: String, value: Boolean) {
        booleans.getOrPut(key) { MutableStateFlow(null) }.value = value
    }

    override fun float(key: String): Flow<Float?> = floats.getOrPut(key) { MutableStateFlow(null) }

    override suspend fun putFloat(key: String, value: Float) {
        floats.getOrPut(key) { MutableStateFlow(null) }.value = value
    }
}
