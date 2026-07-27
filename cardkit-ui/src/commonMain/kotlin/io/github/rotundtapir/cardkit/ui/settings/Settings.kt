// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.settings

/**
 * How quickly bot turns play out (the delay before each bot decision).
 *
 * Entry names are FROZEN: they are persisted verbatim as settings values and parsed from test
 * hooks (intent extras, URL parameters) by the consuming apps — renaming one orphans every
 * user's saved speed and breaks the test-override parsing.
 */
enum class AnimationSpeed(val label: String, val botDelayMillis: Long) {
    SLOW("Slow", 1600),
    NORMAL("Normal", 800),
    FAST("Fast", 250),
    OFF("Off", 0);

    /** The next speed in the cycle Slow → Normal → Fast → Off → Slow. */
    fun next(): AnimationSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        /**
         * Lenient parse for persisted values and test overrides (intent extras, URL parameters):
         * the matching entry, or null when [name] is unset or unrecognised.
         */
        fun fromName(name: String?): AnimationSpeed? = entries.find { it.name == name }
    }
}

/**
 * Which AI drives the bot opponents in local games. [STANDARD] is the fast heuristic;
 * [ADVANCED] is the Monte-Carlo search bot (stronger, thinks for up to a few seconds per move).
 * An enum rather than a Boolean so a future level slots in without a storage-key migration.
 *
 * Entry names are FROZEN for the same reason as [AnimationSpeed]'s.
 */
enum class BotSkill(val label: String) {
    STANDARD("Standard"),
    ADVANCED("Advanced");

    companion object {
        /**
         * Lenient parse for persisted values and test overrides (intent extras, URL parameters):
         * the matching entry, or null when [name] is unset or unrecognised.
         */
        fun fromName(name: String?): BotSkill? = entries.find { it.name == name }
    }
}
