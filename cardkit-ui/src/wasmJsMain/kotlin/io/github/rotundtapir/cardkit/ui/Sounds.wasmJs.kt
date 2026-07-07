// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import io.github.rotundtapir.cardkit.ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.w3c.dom.Audio

/**
 * Browser sound engine: each play spins up a short-lived `<audio>` element for the OGG one-shot
 * (they may overlap, matching SoundPool's multi-stream behaviour). At volume 0 nothing is created,
 * mirroring the Android actual's silent-context guarantee.
 */
@OptIn(ExperimentalResourceApi::class)
actual class SoundManager {
    actual var volume: Float = 0f

    private val variants: Map<SoundEffect, List<String>> = mapOf(
        SoundEffect.CARD_PLACE to listOf("card_place_1", "card_place_2", "card_place_3"),
        SoundEffect.CARD_SLIDE to listOf("card_slide_1", "card_slide_2"),
        SoundEffect.SHUFFLE to listOf("card_shuffle"),
        SoundEffect.TRICK_TAKEN to listOf("trick_take_1", "trick_take_2"),
        SoundEffect.SCORE to listOf("score_chips"),
    )

    actual fun play(effect: SoundEffect) {
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return
        val audio = Audio(Res.getUri("files/${variants.getValue(effect).random()}.ogg"))
        audio.volume = v.toDouble()
        // Browsers reject playback until the page has seen a user gesture; a tap on "New Game"
        // satisfies that, so just swallow the (expected) early rejections.
        audio.play().catch { null }
    }

    actual fun release() {
        // Nothing held between plays.
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
