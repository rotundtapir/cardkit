// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private const val CARD_ART_URL = "https://code.google.com/archive/p/vector-playing-cards/"

/**
 * Credits for the artwork and audio bundled with cardkit-ui: Byron Knoll's public-domain card
 * faces, the CC0 extra faces/back drawn in the same style, and Kenney's CC0 casino sounds.
 * [extraItems] appends per-game credit lines (fonts, game-specific art, voices) after them.
 */
@Composable
fun AcknowledgmentsDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    extraItems: List<String> = emptyList(),
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Acknowledgments") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The playing card faces are “Vector Playing Cards” by Byron Knoll, " +
                        "released into the public domain.",
                )
                Text(
                    "The 11, 12 and 13 card faces and the card back were created for this app " +
                        "in the same style and are dedicated to the public domain (CC0).",
                )
                Text(
                    "Sound effects from Kenney's Casino Audio pack (kenney.nl), " +
                        "public domain (CC0).",
                )
                extraItems.forEach { Text(it) }
                TextButton(onClick = { uriHandler.openUri(CARD_ART_URL) }) {
                    Text("View Byron Knoll's card set")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("acknowledgmentsClose")) {
                Text("Close")
            }
        },
    )
}
