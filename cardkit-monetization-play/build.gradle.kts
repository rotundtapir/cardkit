// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// PROPRIETARY dependencies live here and NOWHERE else. Only an app's `play` build flavor should
// depend on this module; the FOSS/F-Droid flavor must not, keeping that build free of non-free code.
// This is permitted under the project's GPL linking exception (see LICENSE-EXCEPTION.md).
android {
    namespace = "io.github.rotundtapir.cardkit.monetization.play"
    compileSdk = 35

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
    api(project(":cardkit-monetization"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)

    // Proprietary — quarantined to this module.
    implementation(libs.play.services.ads)
    implementation(libs.play.billing.ktx)
    implementation(libs.user.messaging.platform)
}
