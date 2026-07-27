// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Which client platform a build runs on. */
enum class AppPlatform { ANDROID, WEB }

/**
 * Which distribution a build was made as. [AppPlatform] captures android-vs-web; this splits the
 * Android platform into its two flavours (F-Droid vs Play) and names the web build explicitly.
 */
enum class AppDistribution { FOSS, PLAY, WEB, UNKNOWN }

/**
 * Build-specific values the shared UI needs, supplied by each entry point (they come from AGP's
 * BuildConfig in the Android app / build constants on web, which multiplatform code cannot see).
 */
data class AppConfig(
    /** Where "Submit feedback" goes: the game's issue tracker (FOSS/web) or a mailto (Play). */
    val feedbackUri: String,
    /** This build's user-facing version (e.g. "0.3.0"), reported to an online server on connect. */
    val version: String,
    /** Which client build this is, reported to an online server for cross-play diagnostics. */
    val platform: AppPlatform,
    /** Which distribution this build is (web/play/foss), reported to an online server. */
    val flavor: AppDistribution = AppDistribution.UNKNOWN,
    /** The short git commit this build was made from, reported to an online server. */
    val commit: String = "",
)

/** Provided by each app's root composable; read where the UI needs a build-specific value. */
val LocalAppConfig = staticCompositionLocalOf<AppConfig> {
    error("LocalAppConfig not provided")
}
