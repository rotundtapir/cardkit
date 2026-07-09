// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.runtime.Composable

/**
 * The card-table sound vocabulary. All effects are short one-shots from Kenney's CC0 "Casino
 * Audio" pack (kenney.nl), bundled per platform (Android `res/raw`, wasm compose resources).
 */
enum class SoundEffect {
    /** A card landing on the felt (a play into the current trick). */
    CARD_PLACE,

    /** A dealt packet arriving at its destination. */
    CARD_SLIDE,

    /** One riffle of the deck during the shuffle stage. */
    SHUFFLE,

    /** A completed trick being swept up by its winner. */
    TRICK_TAKEN,

    /** A score being settled (chips changing hands). */
    SCORE,
}

/**
 * Each effect's variant file base names — the single table both platform actuals translate into
 * their resource form (`res/raw` ids on Android, wasm compose-resource paths). Exhaustive by
 * construction: adding a [SoundEffect] without variants fails to compile.
 */
internal val SoundEffect.variantNames: List<String>
    get() = when (this) {
        SoundEffect.CARD_PLACE -> listOf("card_place_1", "card_place_2", "card_place_3")
        SoundEffect.CARD_SLIDE -> listOf("card_slide_1", "card_slide_2")
        SoundEffect.SHUFFLE -> listOf("card_shuffle")
        SoundEffect.TRICK_TAKEN -> listOf("trick_take_1", "trick_take_2")
        SoundEffect.SCORE -> listOf("score_chips")
    }

/**
 * A small platform-backed effect player. [play] picks a random variant where an effect has
 * several. [volume] is 0f..1f — at 0 the play is skipped entirely (no audio resource is even
 * touched). Call [release] when done, or share one instance per process via [rememberSoundManager].
 */
expect class SoundManager {
    /** Playback volume, 0f (silent — playback skipped) to 1f. */
    var volume: Float

    fun play(effect: SoundEffect)

    fun release()
}

/** The process-wide [SoundManager], kept at [volume]. Never released — see the platform actuals. */
@Composable
expect fun rememberSoundManager(volume: Float): SoundManager
