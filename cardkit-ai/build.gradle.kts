// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kover)
}

// Game-agnostic bot toolkit: determinized Monte-Carlo search scaffolding (racing elimination,
// constrained hand sampling, trick memory) shared by the bots of cardkit games. Pure Kotlin like
// cardkit-core. Dependency rule: game *engine* modules must never depend on this module — only
// game :ai modules do (:ai -> cardkit-ai -> cardkit-core).
kotlin {
    jvmToolchain(21)
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":cardkit-core"))
            api(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Coverage ratchet, measured from the jvm test run (same bar as the consuming games' :ai modules).
kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
