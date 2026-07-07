// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
}

// Coordinates used by consuming apps via includeBuild(...) composite builds.
// Gradle substitutes "io.github.rotundtapir.cardkit:<module>" with the local
// project of the same name.
allprojects {
    group = "io.github.rotundtapir.cardkit"
    version = "0.1.0-SNAPSHOT"
}
