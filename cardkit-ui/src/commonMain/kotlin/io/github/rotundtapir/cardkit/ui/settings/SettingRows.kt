// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// The repeated row shapes of a settings dialog, extracted from 500's SettingsDialog so every
// game's settings read identically: a label on the left, its control on the right. The control
// (not the row) carries the testTag, matching how the games' UI tests find them.

/** A section heading between setting groups ("House rules (apply to new games)", "Online"…). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = modifier)
}

/** A labelled on/off setting: label left, [Switch] right. [switchModifier] carries the testTag. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchModifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelColor: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = labelColor)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = switchModifier,
        )
    }
}

/**
 * A labelled setting whose button shows the current [value] and cycles it on click (500's
 * "Animations" speed row). [buttonModifier] carries the testTag.
 */
@Composable
fun CycleButtonRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        OutlinedButton(onClick = onClick, modifier = buttonModifier) { Text(value) }
    }
}

/** A labelled slider setting (500's "Sound volume" row). [sliderModifier] carries the testTag. */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    sliderModifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f).then(sliderModifier),
        )
    }
}

/**
 * The settings dialog's monetization-and-meta footer, exactly as 500 renders it: the
 * remove-ads/support button (its label derived from the monetization state), the Google
 * UMP-mandated privacy-options button when [privacyOptionsRequired], then [extraContent] (500
 * slots its "Help — rules of 500" button here), "Submit feedback" and "Acknowledgments".
 *
 * Takes the monetization VALUES rather than the Monetization interface so cardkit-ui stays
 * independent of cardkit-monetization — collect the flows at the call site.
 */
@Composable
fun SupportSection(
    offersRemoveAds: Boolean,
    adsRemoved: Boolean,
    onRemoveAdsOrDonate: () -> Unit,
    privacyOptionsRequired: Boolean,
    onShowPrivacyOptions: () -> Unit,
    onFeedback: () -> Unit,
    onAcknowledgments: () -> Unit,
    modifier: Modifier = Modifier,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        OutlinedButton(
            onClick = onRemoveAdsOrDonate,
            modifier = Modifier.fillMaxWidth().testTag("supportButton"),
        ) {
            Text(
                when {
                    !offersRemoveAds -> "Support development"
                    adsRemoved -> "Ads removed — thank you!"
                    else -> "Remove ads"
                },
            )
        }
        // EEA/UK users may revisit their ad-consent choices (a Google UMP requirement).
        // Never shown in FOSS builds, where privacyOptionsRequired is always false.
        if (privacyOptionsRequired) {
            OutlinedButton(
                onClick = onShowPrivacyOptions,
                modifier = Modifier.fillMaxWidth().testTag("privacyOptions"),
            ) { Text("Privacy options") }
        }
        extraContent()
        OutlinedButton(
            onClick = onFeedback,
            modifier = Modifier.fillMaxWidth().testTag("feedbackButton"),
        ) { Text("Submit feedback") }
        OutlinedButton(
            onClick = onAcknowledgments,
            modifier = Modifier.fillMaxWidth().testTag("acknowledgments"),
        ) { Text("Acknowledgments") }
    }
}
