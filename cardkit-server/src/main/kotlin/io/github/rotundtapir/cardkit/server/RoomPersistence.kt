// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.server

import io.github.rotundtapir.cardkit.net.RoomPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Everything a restarted server needs to resurrect one room: the identity (game id, join code,
 * creator token), the lobby config, the per-seat roster with its session-token ownership, and — for
 * a game in progress — the authoritative engine state. A cardkit engine is a pure, seed-deterministic
 * state machine, so [gameState] alone fully restores a game; the
 * [io.github.rotundtapir.cardkit.core.GameDriver] resumes from it.
 *
 * This is full hidden information (every hand, any kitty, and the bearer tokens that own the seats).
 * It exists only on the server's disk and must never be sent to a client.
 */
@Serializable
data class RoomSnapshot<S, C>(
    /**
     * Schema version of this snapshot, checked at restore. Deliberately has no default: writers
     * must state it, and a pre-versioning file fails to decode instead of masquerading as v1. Bump
     * [CURRENT_VERSION] on any incompatible change to this class, to a game's state type, or to the
     * engine's interpretation of a state — a mismatched snapshot is dropped at boot (that room
     * degrades to the pre-persistence behaviour: the game is lost, the server is fine).
     */
    val snapshotVersion: Int,
    val gameId: String,
    val joinCode: String,
    val creatorToken: String,
    val lobbyConfig: C,
    val phase: RoomPhase,
    val seats: List<SeatSnapshot>,
    /** Carried across the restart so post-restore versions keep increasing and never repeat. */
    val stateVersion: Int,
    /** The live engine state; non-null exactly while the room is [RoomPhase.PLAYING]. */
    val gameState: S?,
    val savedAtMillis: Long,
) {
    @Serializable
    data class SeatSnapshot(
        val name: String?,
        val isBot: Boolean,
        val ownerToken: String?,
    )

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Where room snapshots live between restarts. [save]/[delete] must never block the room actor —
 * implementations do their I/O elsewhere. [loadAll] runs once at boot, before the server accepts
 * connections.
 */
interface RoomPersistence<S : Any, C : Any> {
    /** True when snapshots survive a process restart — gates the shutdown behaviour of rooms. */
    val durable: Boolean

    fun save(snapshot: RoomSnapshot<S, C>)
    fun delete(gameId: String)
    fun loadAll(): List<RoomSnapshot<S, C>>

    /**
     * Write out everything still queued, on the calling thread. Called once during graceful
     * shutdown so the newest state of every room reaches disk before the process exits; a hard
     * crash can still lose the last write or two, which restores as a state a move or so older.
     */
    fun flushSync() {}

    companion object {
        /** In-memory-only mode (no `DATA_DIR`): every operation is a no-op. */
        fun <S : Any, C : Any> none(): RoomPersistence<S, C> = object : RoomPersistence<S, C> {
            override val durable: Boolean get() = false
            override fun save(snapshot: RoomSnapshot<S, C>) = Unit
            override fun delete(gameId: String) = Unit
            override fun loadAll(): List<RoomSnapshot<S, C>> = emptyList()
        }
    }
}

/**
 * One JSON file per room under [dir], named `<gameId>.json`. Writes are handed to a single writer
 * coroutine on [Dispatchers.IO] and conflated per room — only the newest pending snapshot of a room
 * is ever written, so a bot-speed burst of state changes costs one file write, and the room actor
 * never waits on the disk. Each write goes to a temp file first and is atomically renamed into
 * place, so a crash mid-write can never corrupt an existing snapshot.
 */
class FileRoomPersistence<S : Any, C : Any>(
    private val dir: Path,
    scope: CoroutineScope,
    private val snapshotSerializer: KSerializer<RoomSnapshot<S, C>>,
) : RoomPersistence<S, C> {
    private val logger = LoggerFactory.getLogger("persistence")

    override val durable: Boolean get() = true

    /** Newest pending state per room; a [Tombstone] means "delete the file". */
    private val pending = ConcurrentHashMap<String, Any>()
    private val dirty = Channel<String>(Channel.UNLIMITED)

    /** Makes each write's temp file unique, so concurrent writes of one room cannot collide. */
    private val tmpCounter = AtomicLong()

    private object Tombstone

    init {
        Files.createDirectories(dir)
        scope.launch(Dispatchers.IO) {
            for (gameId in dirty) {
                // Removing the entry claims this room's newest pending work; a duplicate id left in
                // the channel by an older save finds nothing and is skipped.
                when (val work = pending.remove(gameId)) {
                    null -> Unit
                    Tombstone -> runCatching { Files.deleteIfExists(fileFor(gameId)) }
                        .onFailure { logger.warn("failed to delete snapshot $gameId", it) }
                    else -> writeClaimed(work)
                }
            }
        }
    }

    override fun save(snapshot: RoomSnapshot<S, C>) {
        pending[snapshot.gameId] = snapshot
        dirty.trySend(snapshot.gameId)
    }

    override fun delete(gameId: String) {
        pending[gameId] = Tombstone
        dirty.trySend(gameId)
    }

    override fun flushSync() {
        // Drain the queue through the same claim protocol the writer coroutine uses, rather than
        // snapshotting `pending.keys` — which is what this used to do, and which can throw
        // NoSuchElementException straight out of the graceful-shutdown path, abandoning every
        // snapshot still queued.
        //
        // The cause is NOT the ConcurrentHashMap iterator: those are weakly consistent and never
        // throw on concurrent modification. It is `Iterable.toList()`, whose fast path for a
        // single-element collection calls `iterator().next()` *without* `hasNext()`. So with exactly
        // one room pending — the ordinary case on a small server, and the likely one at SIGTERM —
        // the writer removing that entry between the size check and the next() loses the race. A
        // two-thread probe reproduces it within a handful of attempts.
        //
        // Every save() enqueues a token, so anything left in `pending` has a token here to claim.
        // remove() is atomic, so racing the writer is safe: whoever claims an entry writes it, the
        // other finds nothing.
        while (true) {
            val gameId = dirty.tryReceive().getOrNull() ?: break
            when (val work = pending.remove(gameId)) {
                null -> Unit
                Tombstone -> runCatching { Files.deleteIfExists(fileFor(gameId)) }
                else -> writeClaimed(work)
            }
        }
    }

    override fun loadAll(): List<RoomSnapshot<S, C>> = dir.listDirectoryEntries()
        .filter { it.extension == "json" }
        .mapNotNull { file ->
            runCatching { json.decodeFromString(snapshotSerializer, file.readText()) }
                .onFailure {
                    // An unreadable snapshot (truncated disk, format change) loses one room, not the
                    // boot: log it, move it aside so it isn't retried forever, and carry on.
                    logger.warn("skipping unreadable snapshot ${file.fileName}", it)
                    runCatching {
                        Files.move(file, file.resolveSibling("${file.nameWithoutExtension}.corrupt"))
                    }
                }
                .getOrNull()
        }

    /**
     * Write a snapshot claimed from [pending]. The map is untyped (it also holds [Tombstone]), and
     * the generic snapshot type is erased, so the cast is the price of the conflating writer; only
     * [save] ever puts a snapshot in, and it is always this room's own type.
     */
    @Suppress("UNCHECKED_CAST")
    private fun writeClaimed(work: Any) {
        write(work as RoomSnapshot<S, C>)
    }

    private fun write(snapshot: RoomSnapshot<S, C>) {
        // The temp file name must be unique per write, not per room: [flushSync] writes on the
        // calling thread and can run while the writer coroutine is mid-write for the same room (it
        // claims a newer snapshot than the one already in flight). Sharing one "<gameId>.tmp" let
        // those two writes interleave their bytes and then publish the mixture — the very corruption
        // the atomic rename is here to prevent, and it cost that room its game at the next boot.
        // Whichever write lands last wins, so a race can still persist a state a move or two older;
        // that is the documented tolerance, and it is not a lost room.
        val target = fileFor(snapshot.gameId)
        val tmp = target.resolveSibling("${snapshot.gameId}.${tmpCounter.incrementAndGet()}.tmp")
        try {
            tmp.writeText(json.encodeToString(snapshotSerializer, snapshot))
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            // A failed write costs restart durability for one room, never the live game itself.
            logger.warn("failed to write snapshot ${snapshot.gameId}", e)
            runCatching { Files.deleteIfExists(tmp) } // don't leave a partial file behind
        }
    }

    private fun fileFor(gameId: String): Path = dir.resolve("$gameId.json")

    private companion object {
        /** Lenient on read so an older server can still load snapshots written by a newer one. */
        val json = Json { ignoreUnknownKeys = true }
    }
}
