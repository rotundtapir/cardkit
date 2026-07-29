// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.server

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * The set of live rooms, indexed both by 4-character join code and by full game id. Also owns the
 * drain flag: while draining, no new lobbies may be created (so active games can finish before a
 * deploy/reboot restarts the process). Live state is in-memory; with a durable [RoomPersistence]
 * (`DATA_DIR` set) each room is also snapshotted to disk and [restore]d at boot, so a deploy or
 * crash no longer loses in-flight games — seats are reclaimed via the ordinary session-token path.
 */
class RoomRegistry<S : Any, A : Any, V : Any, C : Any>(
    private val config: ServerConfig,
    private val scope: CoroutineScope,
    private val descriptor: GameDescriptor<S, A, V, C>,
    private val sessionRegistry: SessionRegistry,
    private val metrics: Metrics,
    private val abuseLog: AbuseLog,
    private val persistence: RoomPersistence<S, C> = RoomPersistence.none(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val logger = LoggerFactory.getLogger("room")
    private val byCode = ConcurrentHashMap<String, Room<S, A, V, C>>()
    private val byGameId = ConcurrentHashMap<String, Room<S, A, V, C>>()
    // Codes are short lookup keys, not secrets — scan resistance comes from the alphabet size and the
    // CODE_SCAN abuse log, not unpredictability — so a plain (non-blocking) RNG is the right choice.
    private val codeRandom = Random.Default

    @Volatile
    var draining: Boolean = false
        private set

    /** Flip drain mode on/off. In drain mode, [create] refuses new rooms. */
    fun setDraining(value: Boolean) {
        draining = value
    }

    fun roomCount(): Int = byCode.size

    /** Rooms with a game currently in progress — the deploy drain waits for this to reach zero. */
    fun activeGames(): Int = byCode.values.count { it.isPlaying() }

    /** Find a room by its (case-insensitive) join code. */
    fun find(code: String): Room<S, A, V, C>? = byCode[normalizeCode(code)]

    fun byGameId(gameId: String): Room<S, A, V, C>? = byGameId[gameId]

    fun all(): Collection<Room<S, A, V, C>> = byCode.values

    /** Outcome of a create attempt: a [Room], or why it was refused. */
    sealed interface CreateResult<out S : Any, out A : Any, out V : Any, out C : Any> {
        data class Created<S : Any, A : Any, V : Any, C : Any>(val room: Room<S, A, V, C>) :
            CreateResult<S, A, V, C>

        data object Draining : CreateResult<Nothing, Nothing, Nothing, Nothing>
        data object ServerFull : CreateResult<Nothing, Nothing, Nothing, Nothing>
    }

    fun create(creatorToken: String, lobbyConfig: C, requestedSeed: Long?): CreateResult<S, A, V, C> {
        if (draining) return CreateResult.Draining
        if (byCode.size >= config.maxRooms) return CreateResult.ServerFull
        val gameId = UUID.randomUUID().toString()
        val room = newRoom(
            gameId = gameId,
            joinCode = "", // set once we successfully claim a code below
            creatorToken = creatorToken,
            lobbyConfig = lobbyConfig,
            requestedSeed = requestedSeed,
        )
        if (!claimCode(room)) return CreateResult.ServerFull
        byGameId[gameId] = room
        room.start()
        logger.info(
            "lobby created code={} game={} players={}",
            room.joinCode,
            gameId,
            descriptor.playerCount(lobbyConfig),
        )
        return CreateResult.Created(room)
    }

    /**
     * Re-adopt a room persisted by a previous process. Boot-time only, before any client can
     * connect, so the join code and game id are claimed without contention (a duplicate — two
     * snapshots claiming one code — loses that room rather than the boot). Returns the live room,
     * or null if it could not be adopted.
     */
    fun restore(snapshot: RoomSnapshot<S, C>): Room<S, A, V, C>? {
        val room = newRoom(
            gameId = snapshot.gameId,
            joinCode = snapshot.joinCode,
            creatorToken = snapshot.creatorToken,
            lobbyConfig = snapshot.lobbyConfig,
            requestedSeed = null,
        )
        if (byCode.putIfAbsent(snapshot.joinCode, room) != null) {
            logger.warn("snapshot {} collides on code {}; dropping it", snapshot.gameId, snapshot.joinCode)
            return null
        }
        byGameId[snapshot.gameId] = room
        room.restoreFrom(snapshot)
        room.start()
        logger.info(
            "lobby restored code={} game={} phase={} players={}",
            snapshot.joinCode,
            snapshot.gameId,
            snapshot.phase,
            descriptor.playerCount(snapshot.lobbyConfig),
        )
        return room
    }

    private fun newRoom(
        gameId: String,
        joinCode: String,
        creatorToken: String,
        lobbyConfig: C,
        requestedSeed: Long?,
    ) = Room(
        gameId = gameId,
        joinCode = joinCode,
        creatorToken = creatorToken,
        initialConfig = lobbyConfig,
        requestedSeed = requestedSeed,
        scope = scope,
        config = config,
        descriptor = descriptor,
        sessionRegistry = sessionRegistry,
        metrics = metrics,
        abuseLog = abuseLog,
        persistence = persistence,
        nowMillis = nowMillis,
        onClosed = ::remove,
    )

    /**
     * Atomically claim a free code for [room] via [ConcurrentHashMap.putIfAbsent] (so two concurrent
     * creates can't collide on the same code), setting [Room.joinCode]. False if none is free.
     */
    private fun claimCode(room: Room<S, A, V, C>): Boolean {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = randomCode()
            if (byCode.putIfAbsent(code, room) == null) {
                room.joinCode = code
                return true
            }
        }
        return false
    }

    private fun randomCode(): String =
        buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(CODE_ALPHABET[codeRandom.nextInt(CODE_ALPHABET.length)]) } }

    private fun remove(room: Room<S, A, V, C>) {
        byCode.remove(room.joinCode)
        byGameId.remove(room.gameId)
    }

    internal companion object {
        const val CODE_LENGTH = 4
        const val MAX_CODE_ATTEMPTS = 200

        // Uppercase alphanumeric (codes are always uppercase) minus the glyphs that are ambiguous
        // *in uppercase*: 0/O and 1/I. (L is kept — uppercase L is unmistakable; only lowercase "l"
        // collides with 1/I.) 32 symbols ⇒ 32^4 ≈ 1.05M codes, ~16× a 16^4 hex space; combined
        // with CODE_SCAN abuse logging, scanning is impractical.
        const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        /** Normalise a user-typed code: trim, uppercase (the alphabet has no ambiguous glyphs to fold). */
        fun normalizeCode(code: String): String = code.trim().uppercase()
    }
}
