// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.felt

import androidx.compose.ui.graphics.Color

/** Amber used to make your own team's names pop against the felt (readable on the dark green). */
val PartnerHighlight = Color(0xFFFFD54F)

/**
 * Distinct, felt-readable colours for the OPPOSING teams, assigned in team-index order (your own
 * team is always the amber [PartnerHighlight]). Drawn from the Okabe–Ito palette so the amber /
 * blue / purple triad stays distinguishable under all common colour-vision deficiencies (the
 * red↔green axis is avoided). Telling teams apart by colour matters most in games with several
 * opposing teams (e.g. 500's 6-player, three-teams-of-two table), where many names crowd the felt.
 */
val OpponentTeamColors = listOf(
    Color(0xFF56B4E9), // sky blue      (Okabe–Ito)
    Color(0xFFCC79A7), // reddish purple (Okabe–Ito)
)
