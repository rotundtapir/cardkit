// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Card-table green, used as a sensible default surface behind cards. */
val TableGreen = Color(0xFF1B5E20)
val TableGreenDark = Color(0xFF0B3D12)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFFB71C1C),
    background = TableGreen,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66BB6A),
    secondary = Color(0xFFEF5350),
    background = TableGreenDark,
)

/**
 * Shared Material 3 theme for every card-game app in the suite. Individual apps may wrap or override
 * it, but this gives a consistent green-table baseline out of the box.
 */
@Composable
fun CardkitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
