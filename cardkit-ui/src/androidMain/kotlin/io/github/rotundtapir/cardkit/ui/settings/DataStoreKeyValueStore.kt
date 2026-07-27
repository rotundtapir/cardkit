// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [KeyValueStore] backed by Jetpack Preferences DataStore — the Android implementation. Values are
 * stored under TYPED preference keys (string/boolean/float), byte-compatible with a repository
 * that used `preferencesDataStore(name)` directly, so migrating a game onto this seam preserves
 * every user's saved settings.
 *
 * The internal constructor takes the [DataStore] directly so unit tests can supply one backed by a
 * temp file; production code uses the ([Context], name) constructor, which binds the app's store
 * for that preferences file. DataStore requires a single instance per file per process, so
 * instances are cached per name — matching the process-wide singleton the
 * `by preferencesDataStore` delegate provides.
 */
class DataStoreKeyValueStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : KeyValueStore {

    constructor(context: Context, name: String) : this(storeFor(context, name))

    override fun string(key: String): Flow<String?> =
        dataStore.data.map { preferences -> preferences[stringPreferencesKey(key)] }

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { preferences -> preferences[stringPreferencesKey(key)] = value }
    }

    override fun boolean(key: String): Flow<Boolean?> =
        dataStore.data.map { preferences -> preferences[booleanPreferencesKey(key)] }

    override suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { preferences -> preferences[booleanPreferencesKey(key)] = value }
    }

    override fun float(key: String): Flow<Float?> =
        dataStore.data.map { preferences -> preferences[floatPreferencesKey(key)] }

    override suspend fun putFloat(key: String, value: Float) {
        dataStore.edit { preferences -> preferences[floatPreferencesKey(key)] = value }
    }

    private companion object {
        private val stores = mutableMapOf<String, DataStore<Preferences>>()

        private fun storeFor(context: Context, name: String): DataStore<Preferences> {
            val appContext = context.applicationContext
            return synchronized(stores) {
                stores.getOrPut(name) {
                    PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile(name) }
                }
            }
        }
    }
}
