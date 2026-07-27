// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The per-platform storage seam behind a game's settings repository: Jetpack Preferences DataStore
 * on Android (`DataStoreKeyValueStore`), the browser's `localStorage` on wasm
 * (`LocalStorageKeyValueStore`).
 *
 * The interface is typed per primitive, NOT string-only, because the wire encodings differ per
 * platform and must stay byte-compatible with what shipped: DataStore stores booleans and floats
 * under TYPED preference keys (a proto boolean/float, not a string), while localStorage stores
 * everything as strings ("true"/"false", `Float.toString()`). A string-only seam would silently
 * re-encode Android values and orphan every user's saved settings.
 *
 * Key names are chosen by the game and are part of its persisted-data contract — renaming one
 * orphans that setting's saved value.
 */
interface KeyValueStore {
    /** The stored string under [key], null while unset. Emits on every change. */
    fun string(key: String): Flow<String?>

    suspend fun putString(key: String, value: String)

    /** The stored boolean under [key], null while unset. Emits on every change. */
    fun boolean(key: String): Flow<Boolean?>

    suspend fun putBoolean(key: String, value: Boolean)

    /** The stored float under [key], null while unset (or unparseable, on web). Emits on every change. */
    fun float(key: String): Flow<Float?>

    suspend fun putFloat(key: String, value: Float)
}

/** The stored string under [key], or [default] while unset. */
fun KeyValueStore.stringSetting(key: String, default: String): Flow<String> =
    string(key).map { it ?: default }

/** The stored boolean under [key], or [default] while unset. */
fun KeyValueStore.booleanSetting(key: String, default: Boolean): Flow<Boolean> =
    boolean(key).map { it ?: default }

/** The stored float under [key], or [default] while unset. */
fun KeyValueStore.floatSetting(key: String, default: Float): Flow<Float> =
    float(key).map { it ?: default }

/**
 * The stored enum under [key] — persisted by `name`, read back through the enum's lenient [parse]
 * (e.g. `AnimationSpeed::fromName`) — or [default] while unset or unrecognised. The lenient parse
 * is the load-bearing part: a value written by a newer app version must degrade to the default,
 * never crash.
 */
fun <T> KeyValueStore.enumSetting(key: String, default: T, parse: (String?) -> T?): Flow<T> =
    string(key).map { parse(it) ?: default }

/** Persists [value] under [key] by its enum name, pairing with [enumSetting]. */
suspend fun <T : Enum<T>> KeyValueStore.putEnum(key: String, value: T) = putString(key, value.name)
