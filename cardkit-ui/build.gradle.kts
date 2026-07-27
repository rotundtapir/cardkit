// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// Game-agnostic Compose Multiplatform building blocks (card rendering, hand fan, theming, the
// card-table sound engine) shared by every game's UI, on Android and in the browser (wasmJs).
// No proprietary dependencies.
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":cardkit-core"))
            // StateFlow appears in public signatures (PacingGates), so consumers need it on their
            // compile classpath: api, not implementation.
            api(libs.kotlinx.coroutines.core)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            // Backs DataStoreKeyValueStore only; never in a public signature, so implementation.
            implementation(libs.androidx.datastore.preferences)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        // Local JVM unit tests against the android target (no emulator): the pacing gates, sound
        // triggers, speech-text expansion and the settings-storage seam use no Android framework
        // APIs (DataStore runs on a plain JVM against a temp file).
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.datastore.preferences)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.rotundtapir.cardkit.ui.generated.resources"
}

android {
    namespace = "io.github.rotundtapir.cardkit.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
