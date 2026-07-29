// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.server

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.net.ClientMessage
import io.github.rotundtapir.cardkit.net.ConfigureLobby
import io.github.rotundtapir.cardkit.net.CreateLobbyRequest
import io.github.rotundtapir.cardkit.net.DisbandLobby
import io.github.rotundtapir.cardkit.net.ErrorCode
import io.github.rotundtapir.cardkit.net.ErrorMessage
import io.github.rotundtapir.cardkit.net.Hello
import io.github.rotundtapir.cardkit.net.JoinLobby
import io.github.rotundtapir.cardkit.net.LeaveLobby
import io.github.rotundtapir.cardkit.net.Names
import io.github.rotundtapir.cardkit.net.PickSeat
import io.github.rotundtapir.cardkit.net.RequestRematch
import io.github.rotundtapir.cardkit.net.RoomPhase
import io.github.rotundtapir.cardkit.net.SendEmote
import io.github.rotundtapir.cardkit.net.ServerMessage
import io.github.rotundtapir.cardkit.net.SetName
import io.github.rotundtapir.cardkit.net.SetReady
import io.github.rotundtapir.cardkit.net.StartGame
import io.github.rotundtapir.cardkit.net.SubmitAction
import io.github.rotundtapir.cardkit.net.UpdateRequired
import io.github.rotundtapir.cardkit.net.Welcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Transport-agnostic orchestration: owns the registries and shared limiters, decides the fate of a
 * [Hello], and routes every subsequent [ClientMessage] to the right [Room]. The Ktor layer
 * ([gameServerModule]) only owns socket I/O and hands decoded messages here, which keeps the business
 * logic unit-testable without a real WebSocket.
 */
class GameServer<S : Any, A : Any, V : Any, C : Any>(
    val config: ServerConfig,
    private val scope: CoroutineScope,
    val descriptor: GameDescriptor<S, A, V, C>,
    val metrics: Metrics = Metrics(descriptor.metricsPrefix),
    private val abuseLog: AbuseLog = AbuseLog(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val logger = LoggerFactory.getLogger("connect")

    val sessionRegistry = SessionRegistry(nowMillis = nowMillis)
    val persistence: RoomPersistence<S, C> = config.dataDir?.let {
        FileRoomPersistence(
            dir = java.nio.file.Path.of(it),
            scope = scope,
            snapshotSerializer = RoomSnapshot.serializer(descriptor.stateSerializer, descriptor.configSerializer),
        )
    } ?: RoomPersistence.none()
    val rooms = RoomRegistry(
        config,
        scope,
        descriptor,
        sessionRegistry,
        metrics,
        abuseLog,
        persistence,
        nowMillis,
    )

    private val connectionIds = AtomicLong()
    private val connectionsPerIp = ConcurrentHashMap<String, Int>()
    private val lobbyThrottle =
        SlidingWindowCounter(LOBBY_WINDOW_MILLIS, config.lobbiesPerIpPer10Min, nowMillis)

    fun nextConnectionId(): Long = connectionIds.incrementAndGet()

    /**
     * Restore every room snapshotted by the previous process. Call once at boot, BEFORE the
     * listener starts, so a reconnecting client's session token already resolves to its restored
     * room. Snapshots older than their own room's idle-disband window are dropped — that room
     * would have been reaped had the server kept running.
     */
    fun restoreRooms() {
        if (!persistence.durable) return
        var restored = 0
        for (snapshot in persistence.loadAll()) {
            if (!restorable(snapshot)) {
                persistence.delete(snapshot.gameId)
                continue
            }
            if (rooms.restore(snapshot) != null) restored++
        }
        if (restored > 0) logger.info("restored {} room(s) from snapshots", restored)
    }

    private fun restorable(snapshot: RoomSnapshot<S, C>): Boolean {
        if (snapshot.snapshotVersion != RoomSnapshot.CURRENT_VERSION) {
            // A deliberate cross-version refusal (see RoomSnapshot.snapshotVersion): losing the
            // room beats restoring a state this build might misinterpret.
            logger.warn(
                "dropping snapshot {}: schema version {} (this server writes {})",
                snapshot.gameId,
                snapshot.snapshotVersion,
                RoomSnapshot.CURRENT_VERSION,
            )
            return false
        }
        val idleMillis = config.idleDisbandMillisOverride
            ?: (descriptor.idleDisbandMinutes(snapshot.lobbyConfig) * 60_000L)
        if (nowMillis() - snapshot.savedAtMillis >= idleMillis) return false // would have been reaped anyway
        return snapshot.phase != RoomPhase.FINISHED &&
            !(snapshot.phase == RoomPhase.PLAYING && snapshot.gameState == null)
    }

    /** Start background maintenance (currently: evicting stale session tokens). Call once at boot. */
    fun startMaintenance() {
        scope.launch {
            val interval = (config.sessionTtlMillis / SESSION_SWEEPS_PER_TTL)
                .coerceIn(MIN_SWEEP_INTERVAL_MILLIS, MAX_SWEEP_INTERVAL_MILLIS)
            while (true) {
                delay(interval)
                sessionRegistry.evictStale(config.sessionTtlMillis)
                lobbyThrottle.evictStale()
            }
        }
    }

    /** Enforce the per-IP connection cap. Returns false (and logs) when [ip] is over the limit. */
    fun tryOpenConnection(ip: String): Boolean {
        if (config.devMode) return true
        // merge + compute are both atomic per key and serialise against each other, so a concurrent
        // open and close can't race the count into an inconsistent state (the old AtomicInteger +
        // remove(k, v) pair could drop a just-incremented counter).
        val count = connectionsPerIp.merge(ip, 1, Int::plus)!!
        if (count <= config.maxConnectionsPerIp) return true
        closeConnection(ip) // roll back this attempt's increment
        abuseLog.log(AbuseLog.Event.CONN_CAP, ip, "over ${config.maxConnectionsPerIp}")
        return false
    }

    fun closeConnection(ip: String) {
        connectionsPerIp.compute(ip) { _, current -> if (current == null || current <= 1) null else current - 1 }
    }

    /** Active connection count for [ip] — for tests. */
    fun connectionsFor(ip: String): Int = connectionsPerIp[ip] ?: 0

    /** The verdict on a client's opening [Hello]. */
    sealed interface HelloResult<out S : Any, out A : Any, out V : Any, out C : Any> {
        data class Accepted<S : Any, A : Any, V : Any, C : Any>(
            val token: String,
            val welcome: Welcome,
            val resumeRoom: Room<S, A, V, C>?,
            val resumeSeat: Seat?,
        ) : HelloResult<S, A, V, C>

        data class Rejected(val response: UpdateRequired) :
            HelloResult<Nothing, Nothing, Nothing, Nothing>
    }

    fun processHello(hello: Hello, ip: String): HelloResult<S, A, V, C> {
        if (hello.protocolVersion != descriptor.protocolVersion ||
            !Versions.isAtLeast(hello.appVersion, config.minAppVersion)
        ) {
            abuseLog.log(AbuseLog.Event.VERSION_REJECT, ip, "app=${hello.appVersion} proto=${hello.protocolVersion}")
            metrics.rejected(ErrorCode.VERSION_UNSUPPORTED)
            return HelloResult.Rejected(
                UpdateRequired(config.minAppVersion, "Please update the app to play online."),
            )
        }
        val existing = hello.sessionToken?.let { sessionRegistry.lookup(it) }
        val token = if (existing != null) hello.sessionToken!! else sessionRegistry.newToken()
        val resumeRoom = existing?.gameId?.let { rooms.byGameId(it) }
        val resumeSeat = existing?.seat
        return HelloResult.Accepted(
            token = token,
            welcome = Welcome(token, config.serverVersion, resumeRoom?.resumedState()),
            resumeRoom = resumeRoom,
            resumeSeat = resumeSeat,
        )
    }

    /**
     * Route one already-decoded, already-rate-limited client message.
     *
     * [ClientMessage] is open polymorphic, so unlike a sealed hierarchy this `when` needs an else —
     * a message type this server knows nothing about is simply ignored (the game's own wire module
     * decides what decodes at all).
     */
    fun route(connection: PlayerConnection, message: ClientMessage) {
        when (message) {
            is Hello -> Unit // handled once at connect
            is CreateLobbyRequest -> handleCreate(connection, message)
            is JoinLobby -> handleJoinLobby(connection, message)
            is SetName -> forward(connection) { RoomCommand.SetName(connection, message.displayName) }
            is PickSeat -> forward(connection) { RoomCommand.PickSeat(connection, message.seat) }
            is SetReady -> forward(connection) { RoomCommand.SetReady(connection, message.ready) }
            is ConfigureLobby -> forward(connection) {
                RoomCommand.Configure(connection, message.turnTimeoutSeconds, message.idleDisbandMinutes)
            }
            StartGame -> forward(connection) { RoomCommand.Start(connection) }
            LeaveLobby -> forward(connection) { RoomCommand.Leave(connection) }
            DisbandLobby -> forward(connection) { RoomCommand.Disband(connection) }
            RequestRematch -> forward(connection) { RoomCommand.Rematch(connection) }
            is SubmitAction<*> -> forward(connection) {
                // The game's Json registers exactly one action type, so anything that decoded as a
                // SubmitAction carries an A (see cardkit.net.gameWireModule).
                @Suppress("UNCHECKED_CAST")
                RoomCommand.Submit(connection, message.stateVersion, message.action as A)
            }
            is SendEmote -> forward(connection) { RoomCommand.SendEmote(connection, message.emote) }
            else -> Unit
        }
    }

    /**
     * Log a newly-established connection (after a successful [Hello]). One line per client carries the
     * build telemetry — platform, distribution flavour, version and git commit — plus the resumed
     * lobby code when a session token was honoured. The commit is empty for pre-telemetry clients.
     */
    fun onConnected(connection: PlayerConnection, resumeRoom: Room<S, A, V, C>?) {
        logger.info(
            "connect id={} ip={} platform={} flavor={} version={} commit={} resume={}",
            connection.id,
            connection.remoteIp,
            connection.platform.name.lowercase(),
            connection.buildFlavor.name.lowercase(),
            connection.appVersion,
            connection.commit.ifBlank { "-" },
            resumeRoom?.joinCode ?: "-",
        )
    }

    /** Notify the current room that a socket dropped, so it can bot-substitute or free the seat. */
    fun onDisconnected(connection: PlayerConnection) {
        connection.connected = false
        roomOf(connection)?.submit(RoomCommand.Disconnected(connection))
    }

    /** A frame was rejected by the socket rate limiter — record it (throttled) for fail2ban. */
    fun onRateLimited(connection: PlayerConnection) {
        metrics.rejected(ErrorCode.RATE_LIMITED)
        if (connection.throttleAbuseLog(nowMillis())) abuseLog.log(AbuseLog.Event.RATE_LIMIT, connection.remoteIp)
    }

    /** A frame failed to decode — record it (throttled) for fail2ban. */
    fun onMalformed(connection: PlayerConnection) {
        metrics.rejected(ErrorCode.MALFORMED)
        if (connection.throttleAbuseLog(nowMillis())) abuseLog.log(AbuseLog.Event.MALFORMED, connection.remoteIp)
    }

    private fun handleCreate(connection: PlayerConnection, message: CreateLobbyRequest) {
        if (alreadySeated(connection)) return
        if (!validName(connection, message.displayName)) return
        val lobbyConfig = descriptor.configFrom(message)
            ?: return deliver(connection, ErrorMessage(ErrorCode.BAD_CONFIG, "Unsupported game configuration"))
        if (!config.devMode && !lobbyThrottle.tryRecord(connection.remoteIp)) {
            abuseLog.log(AbuseLog.Event.LOBBY_THROTTLE, connection.remoteIp)
            return deliver(connection, ErrorMessage(ErrorCode.RATE_LIMITED, "Too many lobbies; slow down"))
        }
        when (val result = rooms.create(connection.sessionToken, lobbyConfig, message.seed)) {
            is RoomRegistry.CreateResult.Created ->
                result.room.submit(RoomCommand.Join(connection, message.displayName))
            RoomRegistry.CreateResult.Draining ->
                deliver(connection, ErrorMessage(ErrorCode.SERVER_DRAINING, "Server restarting soon; try shortly"))
            RoomRegistry.CreateResult.ServerFull -> {
                abuseLog.log(AbuseLog.Event.SERVER_FULL, connection.remoteIp)
                deliver(connection, ErrorMessage(ErrorCode.SERVER_FULL, "Server at capacity"))
            }
        }
    }

    private fun handleJoinLobby(connection: PlayerConnection, message: JoinLobby) {
        if (alreadySeated(connection)) return
        if (!validName(connection, message.displayName)) return
        val room = rooms.find(message.code)
        if (room == null) {
            // A stream of misses from one IP is a join-code scan — surface it so fail2ban can act.
            abuseLog.log(AbuseLog.Event.CODE_SCAN, connection.remoteIp, message.code.take(CODE_LOG_LIMIT))
            return deliver(connection, ErrorMessage(ErrorCode.NO_SUCH_LOBBY, "No lobby with that code"))
        }
        room.submit(RoomCommand.Join(connection, message.displayName))
    }

    /** Refuse create/join while already seated elsewhere, so a connection is never in two rooms. */
    private fun alreadySeated(connection: PlayerConnection): Boolean {
        if (connection.roomId == null) return false
        deliver(connection, ErrorMessage(ErrorCode.WRONG_PHASE, "Leave your current lobby first"))
        return true
    }

    private inline fun forward(connection: PlayerConnection, command: () -> RoomCommand<S, A>) {
        val room = roomOf(connection)
            ?: return deliver(connection, ErrorMessage(ErrorCode.NOT_IN_LOBBY, "You are not in a lobby"))
        room.submit(command())
    }

    private fun roomOf(connection: PlayerConnection): Room<S, A, V, C>? =
        connection.roomId?.let { rooms.byGameId(it) }

    private fun validName(connection: PlayerConnection, raw: String): Boolean {
        if (Names.isValid(raw)) return true
        abuseLog.log(AbuseLog.Event.BAD_NAME, connection.remoteIp, raw.take(NAME_LOG_LIMIT))
        deliver(connection, ErrorMessage(ErrorCode.BAD_NAME, "Name not allowed"))
        return false
    }

    private fun deliver(connection: PlayerConnection, message: ServerMessage) {
        if (!connection.enqueue(message)) connection.requestClose()
    }

    private companion object {
        const val LOBBY_WINDOW_MILLIS = 10L * 60 * 1000
        const val NAME_LOG_LIMIT = 24
        const val CODE_LOG_LIMIT = 8
        const val SESSION_SWEEPS_PER_TTL = 4L
        const val MIN_SWEEP_INTERVAL_MILLIS = 30_000L
        const val MAX_SWEEP_INTERVAL_MILLIS = 15 * 60_000L
    }
}
