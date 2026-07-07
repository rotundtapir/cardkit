// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// The monetization contract plus a FOSS no-op implementation (a donation link, no ads).
// This module MUST remain free of proprietary dependencies — the real ads/billing code lives
// in cardkit-monetization-play. That separation is what keeps F-Droid builds non-proprietary.
android {
    namespace = "io.github.rotundtapir.cardkit.monetization"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    api(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)
}
