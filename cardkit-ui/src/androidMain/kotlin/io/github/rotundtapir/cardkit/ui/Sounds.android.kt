// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** [SoundPool]-backed Android actual. OGG one-shots live in this module's `res/raw`. */
actual class SoundManager(context: Context) {
    private val appContext = context.applicationContext

    actual var volume: Float = 0f
        set(value) {
            field = value
            // Warm the pool the moment audio is enabled: SoundPool.load() is asynchronous, and a
            // play() against a still-loading sample is silently dropped ("sample not READY"), so
            // creating the pool lazily on the first PLAY would swallow that first effect. At 0
            // nothing is created — the -no-audio instrumentation invariant below.
            if (value > 0f) ensurePool()
        }

    // The SoundPool is created lazily on the FIRST audible play: at volume 0 (e.g. instrumentation
    // runs on a -no-audio emulator, where native playback is flaky enough to kill the process) no
    // native audio resources are ever touched.
    private var soundPool: SoundPool? = null
    private var soundIds: Map<SoundEffect, List<Int>> = emptyMap()

    private fun ensurePool(): SoundPool = soundPool ?: SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .also { pool ->
            soundPool = pool
            // Variant lists come from the shared SoundEffect.variantNames table; only the
            // name -> res/raw id translation is platform-local.
            soundIds = SoundEffect.entries.associateWith { effect ->
                effect.variantNames.map { pool.load(appContext, rawRes(it), 1) }
            }
        }

    actual fun play(effect: SoundEffect) {
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return
        val pool = ensurePool()
        pool.play(soundIds.getValue(effect).random(), v, v, 1, 0, 1f)
    }

    private fun rawRes(name: String): Int = when (name) {
        "card_place_1" -> R.raw.card_place_1
        "card_place_2" -> R.raw.card_place_2
        "card_place_3" -> R.raw.card_place_3
        "card_slide_1" -> R.raw.card_slide_1
        "card_slide_2" -> R.raw.card_slide_2
        "card_shuffle" -> R.raw.card_shuffle
        "trick_take_1" -> R.raw.trick_take_1
        "trick_take_2" -> R.raw.trick_take_2
        "score_chips" -> R.raw.score_chips
        else -> error("No res/raw resource mapped for sound variant '$name'")
    }

    actual fun release() {
        soundPool?.release()
        soundPool = null
    }
}

// One SoundPool for the whole process: activity recreation (and each instrumentation test's
// fresh activity) must NOT churn native audio resources — rapid create/release cycles wedge the
// emulator's audio stack and crash the instrumented process. Android reclaims it at process death.
private var sharedSoundManager: SoundManager? = null

@Composable
actual fun rememberSoundManager(volume: Float): SoundManager {
    val context = LocalContext.current
    val manager = remember {
        sharedSoundManager ?: SoundManager(context.applicationContext).also { sharedSoundManager = it }
    }
    SideEffect { manager.volume = volume }
    return manager
}
