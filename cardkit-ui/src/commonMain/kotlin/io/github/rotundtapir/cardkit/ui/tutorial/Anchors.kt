// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.tutorial

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Screen-space rects of the tutorial's interaction targets (a bid button, a card, the felt),
 * recorded by [tutorialTarget] as each element is laid out and read back by the tutorial bubble to
 * anchor itself. A stable holder over a snapshot map, passed instead of a raw mutable collection so
 * composable signatures stay stable and side-effect-free to read.
 */
@Stable
class TutorialAnchors {
    private val rects = mutableStateMapOf<String, Rect>()

    /** Record [key]'s current on-screen [rect] (called from layout). */
    fun record(key: String, rect: Rect) {
        rects[key] = rect
    }

    /** The rect last recorded for [key], or null if that target isn't on screen. */
    operator fun get(key: String): Rect? = rects[key]
}

/**
 * Records this composable's window bounds under [key] for the tutorial bubble to anchor to.
 * [widthFraction] narrows the recorded rect to the left fraction of the bounds — for cards in the
 * overlapping fan, where only the left strip of each card is actually visible, so the bubble's tail
 * points at what the player can see rather than at the covered remainder.
 */
fun Modifier.tutorialTarget(
    anchors: TutorialAnchors?,
    key: String,
    widthFraction: Float = 1f,
): Modifier =
    if (anchors == null) this else onGloballyPositioned { coords ->
        val bounds = coords.boundsInRoot()
        // boundsInRoot() is clipped by ancestors: a target fully scrolled out of a scrollable
        // reports an empty rect at the origin, which would anchor the bubble to the screen corner.
        // Skip it and keep the last real bounds (or let the caller's fallback anchor apply).
        if (bounds.width <= 0f || bounds.height <= 0f) return@onGloballyPositioned
        anchors.record(
            key,
            if (widthFraction >= 1f) {
                bounds
            } else {
                Rect(bounds.left, bounds.top, bounds.left + bounds.width * widthFraction, bounds.bottom)
            },
        )
    }
