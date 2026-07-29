// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// The game-agnostic half of the online wire protocol, plus the client-side WebSocket session.
// Pure Kotlin Multiplatform (jvm + wasmJs) so an Android app, a browser build and a JVM server all
// speak the same envelope. FOSS-only (Ktor is Apache-2.0) — no proprietary dependency ever reaches
// this module, which is what keeps a consuming game's F-Droid build graph clean.
//
// Game payloads never live here: a game supplies its own State/Action/View types by registering
// them in a SerializersModule (see wireJson). This module may depend only on cardkit-core.
kotlin {
    jvmToolchain(21)
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":cardkit-core"))
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
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

// Protocol code is mostly declarations plus a thin client; the wire-shape and name tests cover the
// logic that matters. Matches the ratchet the same code carried in the 500 repo.
kover {
    reports {
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
