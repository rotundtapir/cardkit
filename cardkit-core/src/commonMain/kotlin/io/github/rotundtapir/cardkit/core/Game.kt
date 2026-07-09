// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

/**
 * The rules of a game, expressed as a pure state machine over an immutable [State].
 *
 * Keeping the rules pure (no I/O, no framework types) is what makes the engine unit-testable,
 * replayable from a seed, and runnable on a server for future online multiplayer. Concrete games
 * (e.g. 500) implement this; `cardkit-core` only provides the shape and the driver.
 *
 * @param State  the full, authoritative game state
 * @param Action a move a player can make
 * @param View   the redacted, per-seat projection of the state handed to a [Player]
 */
interface GameRules<State, Action, View> {
    /** The seat that must act next, or `null` if the game is over / no one is to move. */
    fun currentActor(state: State): Seat?

    /** Whether [state] is terminal (the game/round has ended). */
    fun isTerminal(state: State): Boolean

    /**
     * The information visible to [seat]: their own cards plus public information, and typically the
     * legal actions available to them. Hidden information (other seats' hands, the undealt stock)
     * must be omitted so neither AI nor a remote client can cheat.
     */
    fun view(state: State, seat: Seat): View

    /** The legal actions for [seat] in [state]. May be empty when it is not that seat's turn. */
    fun legalActions(state: State, seat: Seat): List<Action>

    /**
     * Applies [action], taken by [seat], returning the next state.
     *
     * @throws IllegalStateException / IllegalArgumentException if the action is not legal.
     */
    fun apply(state: State, seat: Seat, action: Action): State
}

/**
 * Drives a game to completion by repeatedly asking the [Player] at the current actor's seat to
 * [Player.decide], then applying the chosen action.
 *
 * The driver is deliberately transport-agnostic: swapping a local AI for a networked opponent is
 * purely a matter of the [players] map, with no change here or in the [rules].
 */
class GameDriver<State, Action, View>(
    private val rules: GameRules<State, Action, View>,
    private val players: Map<Seat, Player<View, Action>>,
) {
    /**
     * Plays from [initial] until [GameRules.isTerminal]. [onState] is invoked with the initial state
     * and after every applied action, so a UI can observe progress. Returns the terminal state.
     *
     * @throws IllegalStateException if the rules report no actor for a non-terminal state — that is
     *   a rules bug, and stopping quietly would hand back a non-terminal state as if the game ended.
     */
    suspend fun play(initial: State, onState: (State) -> Unit = {}): State {
        var state = initial
        onState(state)
        while (!rules.isTerminal(state)) {
            val seat = rules.currentActor(state)
                ?: error("Rules reported no actor for a non-terminal state: $state")
            val player = players[seat]
                ?: error("No player registered for $seat")
            val action = player.decide(rules.view(state, seat))
            state = rules.apply(state, seat, action)
            onState(state)
        }
        return state
    }
}
