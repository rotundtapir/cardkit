// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import io.github.rotundtapir.cardkit.ui.generated.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.khronos.webgl.Int8Array
import org.khronos.webgl.set

/**
 * Web Audio-backed browser actual. The OGG one-shots (wasm-only compose resources) are fetched
 * and decoded into [AudioBuffer]s once, up front — per-play `<audio>` elements re-decode on every
 * effect and lag noticeably. Each [play] is then a cheap buffer trigger; overlapping plays get
 * their own source node, matching SoundPool's multi-stream behaviour.
 *
 * Browsers keep an [AudioContext] suspended until the page sees a user gesture; [play] calls
 * `resume()` each time, so the first tap ("New Game") unlocks audio.
 */
@OptIn(ExperimentalResourceApi::class)
actual class SoundManager {
    actual var volume: Float = 0f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = AudioContext()
    private val buffers = mutableMapOf<String, AudioBuffer>()

    init {
        // Decode everything at construction (decoding is allowed while the context is suspended).
        // ~9 small OGGs; done before the first deal finishes even on slow connections. Variant
        // lists come from the shared SoundEffect.variantNames table.
        SoundEffect.entries.flatMap { it.variantNames }.forEach { name ->
            scope.launch {
                // A failed fetch/decode leaves just this variant silent (play() already tolerates
                // missing buffers); sound must never break the app.
                runCatching {
                    val bytes = Res.readBytes("files/$name.ogg")
                    val jsBytes = Int8Array(bytes.size)
                    for (i in bytes.indices) jsBytes[i] = bytes[i]
                    buffers[name] = context.decodeAudioData(jsBytes.buffer).await()
                }
            }
        }
    }

    actual fun play(effect: SoundEffect) {
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return
        if (context.state == "suspended") context.resume()
        // Not decoded yet (first seconds of a cold page): drop the effect rather than queue stale audio.
        val buffer = effect.variantNames.mapNotNull { buffers[it] }.randomOrNull() ?: return
        val source = context.createBufferSource()
        source.buffer = buffer
        val gain = context.createGain()
        gain.gain.value = v.toDouble()
        source.connect(gain)
        gain.connect(context.destination)
        source.start()
    }

    actual fun release() {
        // The context and buffers live for the page; nothing held per play.
    }
}

// One shared instance per page, matching the Android actual's process-wide singleton.
private var sharedSoundManager: SoundManager? = null

@Composable
actual fun rememberSoundManager(volume: Float): SoundManager {
    val manager = remember {
        sharedSoundManager ?: SoundManager().also { sharedSoundManager = it }
    }
    SideEffect { manager.volume = volume }
    return manager
}
