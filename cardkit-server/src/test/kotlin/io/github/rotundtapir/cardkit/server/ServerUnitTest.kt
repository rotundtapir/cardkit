// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.server

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.net.ErrorCode
import io.github.rotundtapir.cardkit.net.RoomPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The small, sharp pieces: limiters, version comparison, metric naming, snapshot shape, config. */
class ServerUnitTest {

    private companion object {
        const val RACE_ROUNDS = 200
        const val AWAIT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }

    // --- rate limiting ----------------------------------------------------------------------------

    @Test
    fun `the token bucket allows a burst then refills at its rate`() {
        var now = 0L
        val limiter = RateLimiter(ratePerSecond = 10, burst = 3) { now }
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire(), "the burst is spent")
        now = 100 // 0.1s at 10/s = one token
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `the sliding window counts per key and forgets keys once they age out`() {
        var now = 0L
        val counter = SlidingWindowCounter(windowMillis = 1000, limit = 2) { now }
        assertTrue(counter.tryRecord("a"))
        assertTrue(counter.tryRecord("a"))
        assertFalse(counter.tryRecord("a"), "over the limit inside the window")
        assertTrue(counter.tryRecord("b"), "a different key has its own budget")
        now = 1001
        assertTrue(counter.tryRecord("a"), "the window has slid past the old hits")
        // A key nobody touches again must not linger forever.
        now = 5000
        counter.evictStale()
        assertEquals(0, counter.keyCount())
    }

    // --- versions ---------------------------------------------------------------------------------

    @Test
    fun `dotted versions compare numerically, not lexically`() {
        assertTrue(Versions.isAtLeast("0.10.0", "0.9.0"), "10 > 9 even though \"10\" < \"9\" as text")
        assertTrue(Versions.isAtLeast("1.0.0", "1.0.0"))
        assertFalse(Versions.isAtLeast("0.2.9", "0.3.0"))
        // Junk and suffixes degrade to 0 rather than throwing.
        assertTrue(Versions.isAtLeast("1.2.3-beta", "1.2.3"))
        assertFalse(Versions.isAtLeast("", "0.0.1"))
    }

    // --- metrics ----------------------------------------------------------------------------------

    @Test
    fun `metric series are namespaced by the game's prefix`() {
        val metrics = Metrics("euchre")
        metrics.connectionOpened()
        metrics.gameStarted()
        metrics.rejected(ErrorCode.STALE_ACTION)
        val rendered = metrics.render(roomsActive = 3, draining = true)
        assertTrue(rendered.contains("euchre_connections_total 1"), rendered)
        assertTrue(rendered.contains("euchre_rooms_active 3"), rendered)
        assertTrue(rendered.contains("euchre_games_started_total 1"), rendered)
        assertTrue(rendered.contains("euchre_draining 1"), rendered)
        assertTrue(rendered.contains("""euchre_rejections_total{reason="stale_action"} 1"""), rendered)
        assertFalse(rendered.contains("fivehundred"), "no other game's prefix should leak in")
    }

    // --- join codes -------------------------------------------------------------------------------

    @Test
    fun `join codes avoid glyphs that are ambiguous in uppercase`() {
        assertEquals(RoomRegistry.CODE_LENGTH, 4)
        assertFalse(RoomRegistry.CODE_ALPHABET.contains('0'), "0 reads as O")
        assertFalse(RoomRegistry.CODE_ALPHABET.contains('O'))
        assertFalse(RoomRegistry.CODE_ALPHABET.contains('1'), "1 reads as I")
        assertFalse(RoomRegistry.CODE_ALPHABET.contains('I'))
        assertTrue(RoomRegistry.CODE_ALPHABET.contains('L'), "uppercase L is unmistakable")
        assertEquals("AB12", RoomRegistry.normalizeCode("  ab12 "))
    }

    // --- bot naming -------------------------------------------------------------------------------

    @Test
    fun `a bot never takes a name a human at the table is already using`() {
        val pool = listOf("Jack", "Ivy", "Leo")
        val picked = Room.pickBotNames(pool, taken = listOf("jack"), count = 3, random = Random(1))
        assertEquals(setOf("Ivy", "Leo"), picked.toSet(), "the pool shrinks to the non-colliding names")
    }

    @Test
    fun `bot names never collide with a seated human, at any seed or count`() {
        // A human "jack" (or "JACK") must mean no bot named Jack, whichever names the shuffle picks —
        // so sweep the seeds rather than trusting one. Moved here from the 500 app when this helper
        // did: it tests the default pool and the helper, neither of which is game-specific.
        val taken = listOf("jack", "ALICE", "Mona")
        for (seed in 0L until 50L) {
            val picked = Room.pickBotNames(
                pool = GameDescriptor.DEFAULT_BOT_NAMES,
                taken = taken,
                count = 3,
                random = Random(seed),
            )
            assertEquals(3, picked.size, "seed=$seed")
            assertEquals(3, picked.distinct().size, "bot names must be unique among themselves (seed=$seed)")
            assertTrue(
                picked.none { it.lowercase() in taken.map(String::lowercase).toSet() },
                "seed=$seed picked=$picked",
            )
        }
    }

    // --- server config ----------------------------------------------------------------------------

    @Test
    fun `env vars override a game's defaults, and anything unset keeps them`() {
        val defaults = ServerConfig(
            allowedOrigins = listOf("https://rotundtapir.github.io"),
            minAppVersion = "0.2.0",
            serverVersion = "0.2.1",
        )
        val env = mapOf(
            "PORT" to "9000",
            "MIN_APP_VERSION" to "0.4.0",
            "ALLOWED_ORIGINS" to "https://a.example, https://b.example",
            "DEV_MODE" to "true",
        )
        val config = ServerConfig.fromEnv(defaults) { env[it] }
        assertEquals(9000, config.port)
        assertEquals("0.4.0", config.minAppVersion)
        assertEquals(listOf("https://a.example", "https://b.example"), config.allowedOrigins)
        assertTrue(config.devMode)
        assertEquals("0.2.1", config.serverVersion, "unset vars keep the game's own default")
        assertNull(config.dataDir)
    }

    @Test
    fun `the origin check allows non-browser clients and a wildcard, and rejects strangers`() {
        val strict = ServerConfig(allowedOrigins = listOf("https://rotundtapir.github.io"))
        assertTrue(strict.originAllowed("https://rotundtapir.github.io"))
        assertTrue(strict.originAllowed(null), "a null Origin is a non-browser client, not an attacker")
        assertFalse(strict.originAllowed("https://evil.example"))
        assertTrue(ServerConfig(allowedOrigins = listOf("*")).originAllowed("https://anything.example"))
    }

    // --- snapshots --------------------------------------------------------------------------------

    @Test
    fun `a room snapshot serializes its game payload inline, with no wrapper`() {
        // Pins the on-disk shape: a game adopting cardkit-server must find its existing snapshots
        // still readable, so the generic type parameters must not add nesting or reorder fields.
        val snapshot = RoomSnapshot(
            snapshotVersion = RoomSnapshot.CURRENT_VERSION,
            gameId = "game-1",
            joinCode = "AB12",
            creatorToken = "tok",
            lobbyConfig = ToyConfig(target = 2, turnTimeoutSeconds = 30, idleDisbandMinutes = 5),
            phase = RoomPhase.PLAYING,
            seats = listOf(
                RoomSnapshot.SeatSnapshot(name = "Alice", isBot = false, ownerToken = "tok"),
                RoomSnapshot.SeatSnapshot(name = "Ivy (bot)", isBot = true, ownerToken = null),
            ),
            stateVersion = 4,
            gameState = ToyState(moves = listOf(1, 2), toAct = 1, rngSeed = 7, secrets = mapOf(Seat(0) to 11)),
            savedAtMillis = 1234,
        )
        val serializer = RoomSnapshot.serializer(ToyDescriptor.stateSerializer, ToyDescriptor.configSerializer)
        val encoded = Json.encodeToString(serializer, snapshot)
        assertEquals(
            """{"snapshotVersion":1,"gameId":"game-1","joinCode":"AB12","creatorToken":"tok",""" +
                """"lobbyConfig":{"target":2,"turnTimeoutSeconds":30,"idleDisbandMinutes":5},""" +
                """"phase":"playing","seats":[{"name":"Alice","isBot":false,"ownerToken":"tok"},""" +
                """{"name":"Ivy (bot)","isBot":true,"ownerToken":null}],"stateVersion":4,""" +
                """"gameState":{"moves":[1,2],"toAct":1,"rngSeed":7,"secrets":{"0":11}},""" +
                """"savedAtMillis":1234}""",
            encoded,
        )
        assertEquals(snapshot, Json.decodeFromString(serializer, encoded))
    }

    @Test
    fun `a flushSync racing the writer coroutine never publishes a mixture of two snapshots`() = runBlocking {
        // Regression test. flushSync writes on the calling thread while the writer coroutine may be
        // mid-write for the same room; when both used one "<gameId>.tmp" their bytes interleaved and
        // the atomic rename published the mixture, so the room was quarantined as corrupt at boot and
        // its game was lost. Hammering save+flushSync must leave a file that always decodes.
        val dir = Files.createTempDirectory("cardkit-snapshot-race")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val serializer = RoomSnapshot.serializer(ToyDescriptor.stateSerializer, ToyDescriptor.configSerializer)
            val persistence = FileRoomPersistence(dir, scope, serializer)
            repeat(RACE_ROUNDS) { round ->
                persistence.save(snapshot(stateVersion = round))
                persistence.save(snapshot(stateVersion = round + 1)) // supersedes it while the first may be in flight
                persistence.flushSync()
            }
            withTimeout(AWAIT_MILLIS) {
                while (persistence.loadAll().isEmpty()) delay(POLL_MILLIS)
            }
            assertEquals(
                emptyList(),
                Files.list(dir).use { paths -> paths.filter { it.toString().endsWith(".corrupt") }.toList() },
                "no snapshot should have been quarantined as corrupt",
            )
            assertEquals(1, persistence.loadAll().size, "exactly one readable snapshot per room")
        } finally {
            scope.cancel()
        }
    }

    private fun snapshot(stateVersion: Int) = RoomSnapshot(
        snapshotVersion = RoomSnapshot.CURRENT_VERSION,
        gameId = "race-1",
        joinCode = "AB12",
        creatorToken = "tok",
        lobbyConfig = ToyConfig(),
        phase = RoomPhase.PLAYING,
        seats = listOf(RoomSnapshot.SeatSnapshot("Alice", isBot = false, ownerToken = "tok")),
        stateVersion = stateVersion,
        // A payload long enough that two interleaved writes would be obvious.
        gameState = ToyState(moves = List(stateVersion + 1) { it % 2 + 1 }, toAct = 1, rngSeed = 7),
        savedAtMillis = 1_000L + stateVersion,
    )

    @Test
    fun `a snapshot written without a schema version fails to decode rather than passing as v1`() {
        val serializer = RoomSnapshot.serializer(ToyDescriptor.stateSerializer, ToyDescriptor.configSerializer)
        val withoutVersion = """{"gameId":"g","joinCode":"AB12","creatorToken":"t","lobbyConfig":{},""" +
            """"phase":"lobby","seats":[],"stateVersion":0,"gameState":null,"savedAtMillis":1}"""
        val decoded = runCatching { Json.decodeFromString(serializer, withoutVersion) }
        assertTrue(decoded.isFailure, "snapshotVersion has no default on purpose")
    }
}
