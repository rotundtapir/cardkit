// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [KeyValueStore] backed by the browser's `localStorage`. Each setting is one `<prefix><key>`
 * entry (500 uses the prefix `"settings."`); every value is a string — booleans as
 * "true"/"false", floats via `Float.toString()`/`toFloatOrNull()` — byte-identical to the
 * repositories this seam was extracted from.
 *
 * Values live in a per-key [MutableStateFlow] seeded from storage on first access, so reads are
 * synchronous and the Flow surface behaves like DataStore's.
 */
class LocalStorageKeyValueStore(private val prefix: String) : KeyValueStore {

    private val flows = mutableMapOf<String, MutableStateFlow<String?>>()

    private fun flowFor(key: String): MutableStateFlow<String?> =
        flows.getOrPut(key) { MutableStateFlow(localStorage.getItem(prefix + key)) }

    override fun string(key: String): Flow<String?> = flowFor(key)

    override suspend fun putString(key: String, value: String) {
        localStorage.setItem(prefix + key, value)
        flowFor(key).value = value
    }

    override fun boolean(key: String): Flow<Boolean?> = string(key).map { it?.toBoolean() }

    override suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    override fun float(key: String): Flow<Float?> = string(key).map { it?.toFloatOrNull() }

    override suspend fun putFloat(key: String, value: Float) = putString(key, value.toString())
}
