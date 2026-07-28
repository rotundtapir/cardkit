// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// Test fixtures for games built on cardkit-core: a legality-asserting match driver, seed search,
// and simple policies. JVM only — consumers use it from their jvmTest/androidUnitTest source sets,
// which both consume JVM artifacts; no game ships it in production code.
kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":cardkit-core"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
