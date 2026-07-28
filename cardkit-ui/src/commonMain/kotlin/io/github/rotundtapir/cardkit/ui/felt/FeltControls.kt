// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.felt

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Colors for content drawn directly on the table-green felt, or on the fixed light "card" surfaces
 * floated above it. The app's Material scheme serves the light dialog/card surfaces; most of the
 * chrome, though, sits straight on the dark felt in BOTH theme modes, where the scheme's defaults
 * fail two ways (2026-07-11 contrast audit):
 *
 * - On the felt, light-scheme roles (`primary`, on-surface tones, disabled onSurface at 38%) are
 *   dark-on-dark. Use [onBackground]-derived colors — [OnBackgroundOutlinedButton],
 *   [onBackgroundFieldColors], [feltSwitchColors], and `disabledContentColor = onBackground @ 38%`.
 * - On a FIXED light surface ([CardSurfaceWhite]) the content must be fixed too: theme `primary`
 *   flips to a pale green in dark mode (~2:1 on white). Use [InkOnCardSurface].
 */
val CardSurfaceWhite = Color(0xFFFAFAFA)

/** Fixed dark-green ink for content on [CardSurfaceWhite] — never the theme's mode-dependent primary. */
val InkOnCardSurface = Color(0xFF2E7D32)

/**
 * Fixed near-black body ink for content on [CardSurfaceWhite] — the neutral counterpart to the
 * green [InkOnCardSurface] accent. Used by [OnBackgroundOutlinedButton]'s emphasized form so
 * card-suit glyphs inside keep their true card colors (black ♠♣ / red ♥♦) against the white pill.
 */
val NeutralInkOnCardSurface = Color(0xFF1B1B1B)

/**
 * An outlined button legible on the felt: M3's default content color (`primary`) all but vanishes
 * against the green, so pin the content to `onBackground`.
 *
 * [emphasized] fills the button as a solid [CardSurfaceWhite] pill with [NeutralInkOnCardSurface]
 * content — the one-obvious-primary-choice treatment (a bid the tutorial wants tapped, a lobby's
 * ready-up). Disabled buttons drop to a ghost outline with [disabledContentColor] regardless of
 * emphasis, so the enabled/disabled split stays unmistakable on the felt.
 *
 * The 1dp border sits at `onBackground` alpha 0.6 — the convention both consuming games had
 * already converged on (cardkit's earlier 0.5 was the odd one out).
 */
@Composable
fun OnBackgroundOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    disabledContentColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
    content: @Composable () -> Unit,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (emphasized) CardSurfaceWhite else Color.Transparent,
            contentColor = if (emphasized) NeutralInkOnCardSurface else onBackground,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = disabledContentColor,
        ),
        border = BorderStroke(1.dp, onBackground.copy(alpha = 0.6f)),
        modifier = modifier,
    ) { content() }
}

/**
 * An icon button whose icon is tinted `onBackground` so it reads on the felt (M3's default
 * `IconButton` content color is an on-surface tone that vanishes against the green). Both games
 * duplicated this wrapper around their settings cog.
 */
@Composable
fun OnBackgroundIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * Colors for a solid card-white CTA `Button` (the "Play"-style primary action both games
 * hand-rolled): [CardSurfaceWhite] container with a fixed ink. [contentColor] defaults to the
 * green [InkOnCardSurface] accent (500's home CTA); pass [NeutralInkOnCardSurface] where the
 * label carries card-suit glyphs that must keep their true colors. Disabled state stays
 * felt-legible (faint `onBackground` fill, dimmed-light content) instead of M3's dark-on-dark.
 */
@Composable
fun cardSurfaceButtonColors(contentColor: Color = InkOnCardSurface): ButtonColors {
    val onBackground = MaterialTheme.colorScheme.onBackground
    return ButtonDefaults.buttonColors(
        containerColor = CardSurfaceWhite,
        contentColor = contentColor,
        disabledContainerColor = onBackground.copy(alpha = 0.12f),
        disabledContentColor = onBackground.copy(alpha = 0.38f),
    )
}

/**
 * Tonal button colors on the felt — a faint `onBackground` fill with full-strength content, for
 * toggles/selected chips (both games' hand-sort toggle). Sizing/padding stay at the call site;
 * the colors were the duplicated part.
 */
@Composable
fun feltTonalButtonColors(): ButtonColors {
    val onBackground = MaterialTheme.colorScheme.onBackground
    return ButtonDefaults.outlinedButtonColors(
        containerColor = onBackground.copy(alpha = 0.12f),
        contentColor = onBackground,
    )
}

/**
 * Text-field colors legible on the felt: the M3 defaults draw the label and outline in dark
 * on-surface tones that all but vanish against it.
 */
@Composable
fun onBackgroundFieldColors(): TextFieldColors {
    val onBackground = MaterialTheme.colorScheme.onBackground
    // The scheme's `error` is a dark red (the theme is a light scheme with a dark background), so
    // error states get the M3 dark-scheme error red, which reads clearly on the green.
    val errorRed = Color(0xFFFFB4AB)
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = onBackground,
        unfocusedTextColor = onBackground,
        focusedLabelColor = onBackground,
        unfocusedLabelColor = onBackground.copy(alpha = 0.7f),
        focusedBorderColor = onBackground,
        unfocusedBorderColor = onBackground.copy(alpha = 0.5f),
        cursorColor = onBackground,
        focusedSupportingTextColor = onBackground.copy(alpha = 0.7f),
        unfocusedSupportingTextColor = onBackground.copy(alpha = 0.7f),
        errorTextColor = onBackground,
        errorLabelColor = errorRed,
        errorBorderColor = errorRed,
        errorSupportingTextColor = errorRed,
        errorCursorColor = errorRed,
    )
}

/**
 * Switch colors legible on the felt: the default checked track is theme `primary` — green on
 * green — and the unchecked track a light surface tone. Light thumb, readable track both ways.
 */
@Composable
fun feltSwitchColors(): SwitchColors {
    val onBackground = MaterialTheme.colorScheme.onBackground
    return SwitchDefaults.colors(
        checkedThumbColor = CardSurfaceWhite,
        checkedTrackColor = Color(0xFF66BB6A),
        uncheckedThumbColor = CardSurfaceWhite,
        uncheckedTrackColor = Color.Transparent,
        uncheckedBorderColor = onBackground.copy(alpha = 0.6f),
    )
}
