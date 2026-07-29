// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.net

import io.github.rotundtapir.cardkit.core.Seat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The game-agnostic wire protocol for a cardkit online game. One WebSocket per client; every frame
 * is a single JSON object whose `"type"` discriminator selects a [ClientMessage] or [ServerMessage].
 *
 * Compatibility strategy is two-layered:
 *  - **Additive JSON evolution is the default.** New optional fields (with defaults) and new enum
 *    values never require a version bump — the [wireJson] configuration ignores unknown keys and
 *    coerces unknown enum values to each enum's `UNKNOWN` member.
 *  - **A game's `PROTOCOL_VERSION` bumps only on a breaking change** (a field removal/retype, or a
 *    semantic change). The version is per game, declared in that game's own `:net` module, because
 *    each game's wire contract evolves on its own schedule; the server reads it from
 *    `GameDescriptor.protocolVersion`.
 *
 * NOTE: adding a value to an enum embedded in a game's player view (its phase/suit/rank enums, which
 * have no `UNKNOWN` sink) IS a breaking change for old clients — they would fail to decode the whole
 * [ViewUpdate] and silently stall. Such an addition must bump that game's protocol version. The wire
 * enums in this file each carry an `UNKNOWN` member precisely so *they* can grow additively.
 *
 * ## How a game plugs its own types in
 *
 * [ClientMessage] and [ServerMessage] are deliberately **not** sealed: a sealed hierarchy is closed
 * at its declaring module, which would make it impossible for a game module to contribute its own
 * payload types. Instead they are open polymorphic bases, and every concrete message is registered
 * in a [kotlinx.serialization.modules.SerializersModule] — "registration is data". The three
 * payload-carrying messages ([ViewUpdate], [SubmitAction], [LobbyState]) are generic over the game's
 * view/action/config types and are registered with concrete serializers by [wireJson]; a game also
 * contributes its own [CreateLobbyRequest] implementation carrying its house rules.
 *
 * Open polymorphism emits **byte-identical JSON** to a sealed hierarchy (same `"type"` discriminator,
 * same property order, same omission of defaults), which is what allows an already-released game to
 * adopt this module without a protocol change.
 */

/** Which client build connected — reported in [Hello], used for cross-play diagnostics only. */
enum class Platform {
    @SerialName("android") ANDROID,
    @SerialName("web") WEB,
    @SerialName("unknown") UNKNOWN,
}

/**
 * Which distribution the connecting client was built as — reported in [Hello] for server-side
 * diagnostics only (never affects gameplay). [Platform] captures android-vs-web; this splits the
 * Android platform into its two flavours (F-Droid vs Play) and names the web build explicitly.
 */
enum class Distribution {
    @SerialName("web") WEB,
    @SerialName("play") PLAY,
    @SerialName("foss") FOSS,
    @SerialName("unknown") UNKNOWN,
}

/** The fixed set of canned in-game messages. No free text ever crosses the wire (moderation-free). */
enum class Emote {
    @SerialName("wellPlayed") WELL_PLAYED,
    @SerialName("oops") OOPS,
    @SerialName("thinking") THINKING,
    @SerialName("niceHand") NICE_HAND,
    @SerialName("goodGame") GOOD_GAME,
    @SerialName("hurryUp") HURRY_UP,

    /** Forward-compatibility sink: an emote a newer peer sent that this build doesn't know. */
    @SerialName("unknown") UNKNOWN,
}

/** Machine-readable reason accompanying an [ErrorMessage]; the human string is advisory. */
enum class ErrorCode {
    @SerialName("badName") BAD_NAME,
    @SerialName("nameTaken") NAME_TAKEN,
    @SerialName("lobbyFull") LOBBY_FULL,
    @SerialName("noSuchLobby") NO_SUCH_LOBBY,
    @SerialName("seatTaken") SEAT_TAKEN,
    @SerialName("notCreator") NOT_CREATOR,
    @SerialName("notInLobby") NOT_IN_LOBBY,
    @SerialName("badConfig") BAD_CONFIG,
    @SerialName("wrongPhase") WRONG_PHASE,
    @SerialName("staleAction") STALE_ACTION,
    @SerialName("illegalAction") ILLEGAL_ACTION,
    @SerialName("rateLimited") RATE_LIMITED,
    @SerialName("malformed") MALFORMED,
    @SerialName("versionUnsupported") VERSION_UNSUPPORTED,
    @SerialName("serverDraining") SERVER_DRAINING,
    @SerialName("serverFull") SERVER_FULL,
    @SerialName("unknown") UNKNOWN,
}

/** Why a lobby ended for everyone. */
enum class DisbandReason {
    @SerialName("creatorDisbanded") CREATOR_DISBANDED,
    @SerialName("idleTimeout") IDLE_TIMEOUT,
    @SerialName("serverShutdown") SERVER_SHUTDOWN,
    @SerialName("unknown") UNKNOWN,
}

/** Who is currently playing a seat during a game. */
enum class OccupancyStatus {
    /** A connected human. */
    @SerialName("human") HUMAN,

    /** A human who dropped/idled; a bot is playing their cards until they reclaim the seat. */
    @SerialName("botSubstitute") BOT_SUBSTITUTE,

    /** A seat that was empty at start and is a bot for the whole game. */
    @SerialName("bot") BOT,

    @SerialName("unknown") UNKNOWN,
}

/** The room's lifecycle phase, mirrored to clients in [LobbyState]. */
enum class RoomPhase {
    @SerialName("lobby") LOBBY,
    @SerialName("playing") PLAYING,
    @SerialName("finished") FINISHED,
}

/** One seat's occupancy in a lobby snapshot. [name] already carries the "(bot)" suffix for bots. */
@Serializable
data class SeatInfo(
    val seat: Seat,
    val name: String,
    val isBot: Boolean,
    val ready: Boolean,
    val connected: Boolean,
)

/** Sent inside [Welcome] when a session token was honoured, so the client knows where it landed. */
@Serializable
data class ResumedState(val joinCode: String, val phase: RoomPhase)

/**
 * Sane defaults, shared by client (lobby-creation UI) and server (validation floor/ceiling).
 * Deliberately generous: games are friendly, not competitive, and a bot snatching your turn is more
 * discouraging than a slow opponent. Tighten only if competitive matchmaking lands.
 */
const val DEFAULT_TURN_TIMEOUT_SECONDS: Int = 1800
const val DEFAULT_IDLE_DISBAND_MINUTES: Int = 120
const val MIN_TURN_TIMEOUT_SECONDS: Int = 10
const val MAX_TURN_TIMEOUT_SECONDS: Int = 3600
const val MIN_IDLE_DISBAND_MINUTES: Int = 1
const val MAX_IDLE_DISBAND_MINUTES: Int = 240

// ---------------------------------------------------------------------------------------------
// Client -> Server
// ---------------------------------------------------------------------------------------------

/**
 * A frame sent by a client. Open polymorphic (see the file KDoc): concrete types are registered in a
 * [kotlinx.serialization.modules.SerializersModule], so a game can contribute its own.
 */
interface ClientMessage

/** Must be the first frame. A non-null [sessionToken] requests resuming a dropped session. */
@Serializable
@SerialName("hello")
data class Hello(
    val protocolVersion: Int,
    val appVersion: String,
    val platform: Platform = Platform.UNKNOWN,
    val sessionToken: String? = null,
    /** Which distribution this build is (web/play/foss) — diagnostics only. */
    val buildFlavor: Distribution = Distribution.UNKNOWN,
    /** The short git commit the client was built from (empty when unavailable) — diagnostics only. */
    val commit: String = "",
) : ClientMessage

/**
 * Create a new lobby and become its creator, taking a seat.
 *
 * The concrete type is **per game**, because its body is the game's house rules (and, for games with
 * variable table sizes, the seat/team counts). Games declare a `@Serializable @SerialName`
 * `"lobby.create"` class implementing this interface and register it in their wire module; the
 * server routes on the interface and converts it to that game's lobby config through
 * `GameDescriptor.configFrom`.
 */
interface CreateLobbyRequest : ClientMessage {
    val displayName: String
    val turnTimeoutSeconds: Int
    val idleDisbandMinutes: Int

    /** Honoured only in dev mode, so tests and local runs can pin a deal. */
    val seed: Long?
}

/** Join an existing lobby by its 4-character code (case-insensitive). */
@Serializable
@SerialName("lobby.join")
data class JoinLobby(val code: String, val displayName: String) : ClientMessage

/** Change display name while in the lobby. */
@Serializable
@SerialName("lobby.setName")
data class SetName(val displayName: String) : ClientMessage

/** Claim/move to a free seat. Seat index determines partnership in partnership games. */
@Serializable
@SerialName("lobby.pickSeat")
data class PickSeat(val seat: Seat) : ClientMessage

/** Toggle this player's ready flag. */
@Serializable
@SerialName("lobby.ready")
data class SetReady(val ready: Boolean) : ClientMessage

/** Creator-only: adjust timeouts before the game starts. */
@Serializable
@SerialName("lobby.configure")
data class ConfigureLobby(
    val turnTimeoutSeconds: Int? = null,
    val idleDisbandMinutes: Int? = null,
) : ClientMessage

/** Creator-only: start the game; every empty seat is filled by a bot. */
@Serializable
@SerialName("lobby.start")
data object StartGame : ClientMessage

/** Leave the lobby/game. In a live game a bot takes over; the seat can be reclaimed by reconnecting. */
@Serializable
@SerialName("lobby.leave")
data object LeaveLobby : ClientMessage

/** Creator-only: end the lobby for everyone. */
@Serializable
@SerialName("lobby.disband")
data object DisbandLobby : ClientMessage

/** Creator-only, after a game: return the room to the lobby for another game. */
@Serializable
@SerialName("lobby.rematch")
data object RequestRematch : ClientMessage

/**
 * Submit a game action. [stateVersion] echoes the prompting [ViewUpdate]; a mismatch means the
 * action is stale (a double-tap or a race) and is rejected without disturbing the game — the network
 * analogue of [io.github.rotundtapir.cardkit.core.ChannelPlayer.trySubmit].
 *
 * Generic over the game's action type; registered with a concrete serializer by [wireJson].
 */
@Serializable
@SerialName("game.action")
data class SubmitAction<A>(val stateVersion: Int, val action: A) : ClientMessage

/** Send a canned emote to the table. */
@Serializable
@SerialName("emote")
data class SendEmote(val emote: Emote) : ClientMessage

// ---------------------------------------------------------------------------------------------
// Server -> Client
// ---------------------------------------------------------------------------------------------

/**
 * A frame sent by the server. Open polymorphic (see the file KDoc): concrete types are registered in
 * a [kotlinx.serialization.modules.SerializersModule], so a game can contribute its own.
 */
interface ServerMessage

/** Reply to [Hello] on success. [sessionToken] is stored by the client to enable reconnects. */
@Serializable
@SerialName("welcome")
data class Welcome(
    val sessionToken: String,
    val serverVersion: String,
    val resumed: ResumedState? = null,
) : ServerMessage

/** Reply to [Hello] when the client is too old; the server closes the socket after sending this. */
@Serializable
@SerialName("updateRequired")
data class UpdateRequired(val minAppVersion: String, val message: String) : ServerMessage

/**
 * Full lobby snapshot, re-broadcast on every change (no deltas — the client never merges state).
 *
 * Generic over the game's lobby-config type; registered with a concrete serializer by [wireJson].
 */
@Serializable
@SerialName("lobby.state")
data class LobbyState<C>(
    val joinCode: String,
    val gameId: String,
    val config: C,
    val seats: List<SeatInfo>,
    val creatorSeat: Seat,
    val yourSeat: Seat? = null,
    val phase: RoomPhase,
) : ServerMessage

/**
 * A redacted per-seat view after every applied action (and on connect/reconnect). The view *is* the
 * turn prompt: the game's view type carries whose turn it is plus the legal actions, which tell the
 * client what to offer. [turnRemainingMillis] (never an absolute timestamp — client clocks drift)
 * drives the countdown.
 *
 * Generic over the game's view type; registered with a concrete serializer by [wireJson].
 */
@Serializable
@SerialName("game.view")
data class ViewUpdate<V>(
    val stateVersion: Int,
    val view: V,
    val turnRemainingMillis: Long? = null,
) : ServerMessage

/** Whose cards a seat is currently played by, so the UI can show "Alice (bot)" mid-game. */
@Serializable
@SerialName("game.seatStatus")
data class SeatStatus(val seat: Seat, val status: OccupancyStatus) : ServerMessage

/** Explicit room-phase transition to FINISHED, carrying the final scoreline. */
@Serializable
@SerialName("game.over")
data class GameOver(val winnerTeam: Int, val scores: Map<Int, Int>) : ServerMessage

/** A peer sent an emote. */
@Serializable
@SerialName("emote")
data class EmoteReceived(val seat: Seat, val emote: Emote = Emote.UNKNOWN) : ServerMessage

/** The lobby ended for everyone. */
@Serializable
@SerialName("lobby.disbanded")
data class LobbyDisbanded(val reason: DisbandReason = DisbandReason.UNKNOWN) : ServerMessage

/** A rejected request. [fatal] messages precede the server closing the socket. */
@Serializable
@SerialName("error")
data class ErrorMessage(
    val code: ErrorCode = ErrorCode.UNKNOWN,
    val message: String = "",
    val fatal: Boolean = false,
) : ServerMessage
