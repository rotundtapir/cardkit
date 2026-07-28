// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/**
 * Clickable only while [enabled] — a factory rather than a conditional `.then(if …)` chain, which
 * crashes AGP lint's `SuspiciousModifierThenDetector` (see the consuming repos' CLAUDE.md).
 *
 * Public because every consumer needs the same workaround: both 500 and euchre carried verbatim
 * private clones (`tappableWhen`) before this was exported.
 */
fun Modifier.clickableWhen(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.clickable(onClick = onClick) else this
