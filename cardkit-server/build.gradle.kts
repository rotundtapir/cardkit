// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// The authoritative online server, generic over any cardkit game: rooms, lobbies, seat hosting with
// bot substitution and reconnect, session tokens, restart-surviving room snapshots, anti-abuse, and
// the Ktor transport. A game supplies its rules, bot, serializers and house-rule config through a
// single GameDescriptor and ships a thin main() of its own.
//
// JVM-only by design (the first such module here) — a server never runs in a browser. It may depend
// only on cardkit-core and cardkit-net: no Compose, no Android, no game types. Deliberately NOT the
// `application` plugin: this is a library, and each game's :server module is the binary. Logging is
// slf4j-api only, so games choose their own backend.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":cardkit-core"))
    api(project(":cardkit-net"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.forwarded.header)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Matches the ratchet this code carried in the 500 repo. The room actor, seat hosting and snapshot
// round-trip are exercised through a toy game; the transport itself is covered by each game's own
// integration suite against a real WebSocket.
kover {
    reports {
        verify {
            rule {
                minBound(60)
            }
        }
    }
}
