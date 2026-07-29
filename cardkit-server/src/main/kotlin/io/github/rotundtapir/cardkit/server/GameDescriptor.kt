// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.server

import io.github.rotundtapir.cardkit.core.GameRules
import io.github.rotundtapir.cardkit.core.Strategy
import io.github.rotundtapir.cardkit.net.CreateLobbyRequest
import io.github.rotundtapir.cardkit.net.GameOver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Everything the generic server needs to know about one game — the single seam between this module
 * and a game's engine, bot and wire types. A game's `:server` module implements this once and its
 * `main()` is then a dozen lines of Ktor wiring.
 *
 * The type parameters are the game's own: [S]tate, [A]ction, [V]iew and lobby [C]onfig. Everything
 * in this module is generic over exactly these four, so no game type is ever named here.
 *
 * Nothing on this interface may block or suspend: it is called from the room actor, whose
 * single-writer discipline is what makes the room lock-free.
 */
interface GameDescriptor<S : Any, A : Any, V : Any, C : Any> {

    /** Human-readable game name, used in startup logs. */
    val gameName: String

    /**
     * Prefix for the Prometheus metric names, e.g. `"euchre"` ⇒ `euchre_connections_total`. Distinct
     * per game so two servers can be scraped without their series colliding.
     */
    val metricsPrefix: String

    /**
     * The game's wire-protocol version, checked against every client's `Hello`. Per game because
     * each game's wire contract evolves on its own schedule.
     */
    val protocolVersion: Int

    /** The game's [Json], from `cardkit.net.wireJson` with its payload types registered. */
    val wireJson: Json

    /** Serializes the engine state into a room snapshot, so games survive a restart. */
    val stateSerializer: KSerializer<S>

    /** Serializes the lobby config into a room snapshot. */
    val configSerializer: KSerializer<C>

    /**
     * The pool of bot display names for empty seats. A name colliding with a seated human's is
     * skipped automatically, so the pool only needs to be longer than the table.
     */
    val botNames: List<String> get() = DEFAULT_BOT_NAMES

    /**
     * A fresh bot strategy for this room's [config]. Called once per room and shared by every
     * bot-played seat, each with its own seeded [kotlin.random.Random], so the strategy must be
     * stateless.
     *
     * Takes the config because a bot is a function of the rules it plays under: a house rule that
     * adds a card or a bid changes what a correct move is, and a bot built for the wrong ruleset
     * misplays rather than failing loudly.
     *
     * Prefer a cheap heuristic bot: it runs on the server's single vCPU for every abandoned seat.
     */
    fun bot(config: C): Strategy<V, A>

    /** The rules for a lobby's negotiated [config] — the game's house-rule toggles applied. */
    fun rulesFor(config: C): GameRules<S, A, V>

    /**
     * A fresh initial state. Separate from [rulesFor] because cardkit's [GameRules] deliberately has
     * no `newGame`: dealing is a game's own concern, and its signature varies (seeds, first dealer).
     */
    fun newGame(config: C, seed: Long): S

    /** The final scoreline for a terminal [state], broadcast when the driver finishes. */
    fun gameOver(state: S): GameOver

    /**
     * A seed for the bots of a game restored from a snapshot, normally the state's own evolving RNG
     * seed. Bot-RNG continuity across a restart is deliberately not guaranteed — only that a restored
     * game is itself deterministic.
     */
    fun botRestoreSeed(state: S): Long

    /**
     * Translate a client's create-lobby request into this game's config, or return null if the
     * requested setup is unsupported (the client gets `BAD_CONFIG`). This is where a game validates
     * seat/team counts and clamps its own house rules — the server never second-guesses it.
     */
    fun configFrom(request: CreateLobbyRequest): C?

    /** How many seats this config seats. Fixed for most games; 500 varies it per lobby. */
    fun playerCount(config: C): Int

    /** The per-turn timeout, in seconds, before a bot covers the turn. */
    fun turnTimeoutSeconds(config: C): Int

    /** How long an empty room lingers before it is disbanded, in minutes. */
    fun idleDisbandMinutes(config: C): Int

    /**
     * Apply a creator's timeout change, leaving any null unchanged. Values arrive already clamped to
     * the protocol's documented bounds.
     */
    fun withTimeouts(config: C, turnTimeoutSeconds: Int?, idleDisbandMinutes: Int?): C

    companion object {
        /**
         * Names for bot-played seats. Ordinary first names on purpose: a table of humans and bots
         * should read as a table of people, and [io.github.rotundtapir.cardkit.net.Names.botLabel]
         * appends the "(bot)" marker that keeps them honest.
         */
        val DEFAULT_BOT_NAMES: List<String> = listOf(
            "Alice", "Bruce", "Cleo", "Dev", "Esther", "Frank", "Greta", "Hugo",
            "Ivy", "Jack", "Kira", "Leo", "Mona", "Nate", "Opal", "Wally",
        )
    }
}
