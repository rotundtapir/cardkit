// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.net

import io.github.rotundtapir.cardkit.core.Seat
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the game-agnostic half of the wire format, exercised through a toy game's registrations.
 *
 * The golden strings for the shared messages are **byte-identical to the ones the 500 app shipped**
 * while these types were sealed and lived in its own `:net` module. That equality is the whole
 * argument that open polymorphism plus [gameWireModule] registration is a safe home for an
 * already-released protocol: same discriminator, same property order, same omission of defaults.
 *
 * The generic-payload assertions pin the other half of the claim — that `ViewUpdate`/`SubmitAction`/
 * `LobbyState` being generic introduces no extra nesting or type wrapper around the game's payload.
 */
class WireShapeTest {

    // ---- a minimal toy game, standing in for a real one -----------------------------------------

    @Serializable
    private data class ToyView(val seat: Seat, val hand: List<String>, val handSizes: Map<Seat, Int>)

    @Serializable
    private sealed interface ToyAction {
        @Serializable
        @SerialName("move")
        data class Move(val squares: Int) : ToyAction
    }

    @Serializable
    private data class ToyConfig(
        val hardMode: Boolean = false,
        val turnTimeoutSeconds: Int = DEFAULT_TURN_TIMEOUT_SECONDS,
        val idleDisbandMinutes: Int = DEFAULT_IDLE_DISBAND_MINUTES,
    )

    @Serializable
    @SerialName("lobby.create")
    private data class ToyCreateLobby(
        override val displayName: String,
        val hardMode: Boolean = false,
        override val turnTimeoutSeconds: Int = DEFAULT_TURN_TIMEOUT_SECONDS,
        override val idleDisbandMinutes: Int = DEFAULT_IDLE_DISBAND_MINUTES,
        override val seed: Long? = null,
    ) : CreateLobbyRequest

    private val json = wireJson(
        gameWireModule(
            actionSerializer = ToyAction.serializer(),
            viewSerializer = ToyView.serializer(),
            configSerializer = ToyConfig.serializer(),
            createLobbyClass = ToyCreateLobby::class,
            createLobbySerializer = ToyCreateLobby.serializer(),
        ),
    )

    private fun view() = ToyView(
        seat = Seat(0),
        hand = listOf("AS", "KH"),
        handSizes = mapOf(Seat(0) to 10, Seat(1) to 10),
    )

    private inline fun <reified T : ClientMessage> roundTripClient(msg: T) {
        val encoded = json.encodeToString<ClientMessage>(msg)
        assertEquals(msg, json.decodeFromString<ClientMessage>(encoded), "client round-trip: $encoded")
    }

    private inline fun <reified T : ServerMessage> roundTripServer(msg: T) {
        val encoded = json.encodeToString<ServerMessage>(msg)
        assertEquals(msg, json.decodeFromString<ServerMessage>(encoded), "server round-trip: $encoded")
    }

    // ---- shared shapes: byte-identical to what 500 shipped when these were sealed ---------------

    @Test
    fun `shared client messages keep the shapes 500 shipped`() {
        assertEquals(
            """{"type":"hello","protocolVersion":1,"appVersion":"0.3.0","platform":"android"}""",
            json.encodeToString<ClientMessage>(Hello(1, "0.3.0", Platform.ANDROID)),
        )
        assertEquals(
            """{"type":"hello","protocolVersion":1,"appVersion":"0.3.0","platform":"android",""" +
                """"buildFlavor":"foss","commit":"6f7e099"}""",
            json.encodeToString<ClientMessage>(
                Hello(1, "0.3.0", Platform.ANDROID, buildFlavor = Distribution.FOSS, commit = "6f7e099"),
            ),
        )
        assertEquals(
            """{"type":"lobby.join","code":"AB12","displayName":"Alice"}""",
            json.encodeToString<ClientMessage>(JoinLobby("AB12", "Alice")),
        )
        assertEquals(
            """{"type":"lobby.pickSeat","seat":2}""",
            json.encodeToString<ClientMessage>(PickSeat(Seat(2))),
        )
        assertEquals(
            """{"type":"lobby.start"}""",
            json.encodeToString<ClientMessage>(StartGame),
        )
        assertEquals(
            """{"type":"emote","emote":"wellPlayed"}""",
            json.encodeToString<ClientMessage>(SendEmote(Emote.WELL_PLAYED)),
        )
    }

    @Test
    fun `shared server messages keep the shapes 500 shipped`() {
        assertEquals(
            """{"type":"error","code":"badName","message":"try again"}""",
            json.encodeToString<ServerMessage>(ErrorMessage(ErrorCode.BAD_NAME, "try again")),
        )
        assertEquals(
            """{"type":"game.seatStatus","seat":1,"status":"botSubstitute"}""",
            json.encodeToString<ServerMessage>(SeatStatus(Seat(1), OccupancyStatus.BOT_SUBSTITUTE)),
        )
        assertEquals(
            """{"type":"game.over","winnerTeam":0,"scores":{"0":520,"1":260}}""",
            json.encodeToString<ServerMessage>(GameOver(0, mapOf(0 to 520, 1 to 260))),
        )
        assertEquals(
            """{"type":"welcome","sessionToken":"tok","serverVersion":"0.3.0"}""",
            json.encodeToString<ServerMessage>(Welcome("tok", "0.3.0")),
        )
        assertEquals(
            """{"type":"lobby.disbanded","reason":"idleTimeout"}""",
            json.encodeToString<ServerMessage>(LobbyDisbanded(DisbandReason.IDLE_TIMEOUT)),
        )
    }

    // ---- generic payloads: the game's own type, inlined with no wrapper ------------------------

    @Test
    fun `a generic payload is inlined with no extra nesting`() {
        assertEquals(
            """{"type":"game.action","stateVersion":5,"action":{"type":"move","squares":3}}""",
            json.encodeToString<ClientMessage>(SubmitAction(5, ToyAction.Move(3))),
        )
        assertEquals(
            """{"type":"game.view","stateVersion":7,"view":{"seat":0,"hand":["AS","KH"],""" +
                """"handSizes":{"0":10,"1":10}},"turnRemainingMillis":30000}""",
            json.encodeToString<ServerMessage>(ViewUpdate(7, view(), turnRemainingMillis = 30_000)),
        )
        assertEquals(
            """{"type":"lobby.state","joinCode":"AB12","gameId":"ab12cdef-0000","config":{},""" +
                """"seats":[{"seat":0,"name":"Alice","isBot":false,"ready":true,"connected":true}],""" +
                """"creatorSeat":0,"yourSeat":0,"phase":"lobby"}""",
            json.encodeToString<ServerMessage>(
                LobbyState(
                    joinCode = "AB12",
                    gameId = "ab12cdef-0000",
                    config = ToyConfig(),
                    seats = listOf(SeatInfo(Seat(0), "Alice", isBot = false, ready = true, connected = true)),
                    creatorSeat = Seat(0),
                    yourSeat = Seat(0),
                    phase = RoomPhase.LOBBY,
                ),
            ),
        )
    }

    @Test
    fun `a game's own create-lobby request rides the shared discriminator`() {
        assertEquals(
            """{"type":"lobby.create","displayName":"Bob"}""",
            json.encodeToString<ClientMessage>(ToyCreateLobby("Bob")),
        )
        // Routing is on the shared interface, so the server never names the game's concrete type.
        val decoded = json.decodeFromString<ClientMessage>(
            """{"type":"lobby.create","displayName":"Bob","hardMode":true,"seed":42}""",
        )
        assertTrue(decoded is CreateLobbyRequest, "should decode as the shared interface: $decoded")
        assertEquals("Bob", decoded.displayName)
        assertEquals(42L, decoded.seed)
    }

    @Test
    fun `all message types round-trip`() {
        roundTripClient(Hello(1, "0.3.0", Platform.WEB, sessionToken = "tok"))
        roundTripClient(ToyCreateLobby("Bob", hardMode = true, seed = 42L))
        roundTripClient(JoinLobby("cd34", "Carol"))
        roundTripClient(SetName("Dave"))
        roundTripClient(PickSeat(Seat(3)))
        roundTripClient(SetReady(true))
        roundTripClient(ConfigureLobby(turnTimeoutSeconds = 60))
        roundTripClient(StartGame)
        roundTripClient(LeaveLobby)
        roundTripClient(DisbandLobby)
        roundTripClient(RequestRematch)
        roundTripClient(SubmitAction(1, ToyAction.Move(2)))
        roundTripClient(SendEmote(Emote.GOOD_GAME))

        roundTripServer(Welcome("tok", "0.3.0", ResumedState("AB12", RoomPhase.PLAYING)))
        roundTripServer(UpdateRequired("0.3.0", "please update"))
        roundTripServer(
            LobbyState(
                joinCode = "AB12",
                gameId = "ab12cdef-0000",
                config = ToyConfig(hardMode = true),
                seats = listOf(SeatInfo(Seat(0), "Alice", isBot = false, ready = true, connected = true)),
                creatorSeat = Seat(0),
                yourSeat = Seat(0),
                phase = RoomPhase.LOBBY,
            ),
        )
        roundTripServer(ViewUpdate(7, view(), turnRemainingMillis = 30_000))
        roundTripServer(ViewUpdate(8, view()))
        roundTripServer(SeatStatus(Seat(2), OccupancyStatus.HUMAN))
        roundTripServer(GameOver(1, mapOf(0 to 100, 1 to 500)))
        roundTripServer(EmoteReceived(Seat(1), Emote.OOPS))
        roundTripServer(LobbyDisbanded(DisbandReason.IDLE_TIMEOUT))
        roundTripServer(ErrorMessage(ErrorCode.RATE_LIMITED, "slow down", fatal = false))
    }

    // ---- forward/backward compatibility behaviours ---------------------------------------------

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val encoded = """{"type":"lobby.join","code":"AB12","displayName":"Alice","futureField":123}"""
        assertEquals(JoinLobby("AB12", "Alice"), json.decodeFromString<ClientMessage>(encoded))
    }

    @Test
    fun `unknown enum values coerce to UNKNOWN so new emotes never break old clients`() {
        val encoded = """{"type":"emote","seat":0,"emote":"cartwheel"}"""
        assertEquals(EmoteReceived(Seat(0), Emote.UNKNOWN), json.decodeFromString<ServerMessage>(encoded))
    }

    @Test
    fun `an unregistered message type fails to decode rather than decoding as null`() {
        // The server treats a decode failure as MALFORMED; open polymorphism must not soften that
        // into a silently-dropped-but-accepted frame.
        assertFailsWith<SerializationException> {
            json.decodeFromString<ClientMessage>("""{"type":"lobby.telepathy","wish":"win"}""")
        }
    }

    @Test
    fun `Seat serializes as a bare int and works as a JSON map key`() {
        val encoded = json.encodeToString<ServerMessage>(ViewUpdate(1, view()))
        assertTrue(encoded.contains(""""seat":0"""), "Seat should be a bare int: $encoded")
        assertTrue(
            encoded.contains(""""handSizes":{"0":10,"1":10}"""),
            "Seat map key should be a string int: $encoded",
        )
    }
}
