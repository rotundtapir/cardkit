// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.testing

import io.github.rotundtapir.cardkit.core.GameRules
import io.github.rotundtapir.cardkit.core.Seat
import kotlin.random.Random

/**
 * Drives [rules] from [initial] until [GameRules.isTerminal], choosing every action through
 * [policy], and returns the terminal state.
 *
 * Unlike `GameDriver` (production: suspend, one [io.github.rotundtapir.cardkit.core.Player] per
 * seat), this is a synchronous test loop with the assertions a rules test wants for free:
 *
 * - every non-terminal state must name an actor, and that actor must have at least one legal
 *   action (a silent stall is a rules bug, not a finished game);
 * - every action [policy] picks must come from that state's own [GameRules.legalActions] — so any
 *   test driving a match doubles as a legality sweep;
 * - the game must end within [maxSteps] (a generous default; a loop that never terminates fails
 *   with the step count rather than hanging the suite).
 *
 * [policy] receives the acting seat's redacted view and its legal actions — decide from the view
 * exactly as a real player would (peeking at hidden state is what this seam prevents).
 * [onState] observes the initial state and every state after an applied action, for tests that
 * assert invariants across the whole trajectory.
 */
fun <State, Action, View> drive(
    rules: GameRules<State, Action, View>,
    initial: State,
    maxSteps: Int = 100_000,
    onState: (State) -> Unit = {},
    policy: (view: View, legal: List<Action>) -> Action,
): State {
    var state = initial
    onState(state)
    var steps = 0
    while (!rules.isTerminal(state)) {
        check(steps < maxSteps) {
            "Game did not terminate within $maxSteps steps — non-terminating rules or policy"
        }
        val actor: Seat = checkNotNull(rules.currentActor(state)) {
            "Non-terminal state has no actor (rules bug) after $steps steps"
        }
        val legal = rules.legalActions(state, actor)
        check(legal.isNotEmpty()) {
            "Actor $actor has no legal actions in a non-terminal state (rules bug) after $steps steps"
        }
        val action = policy(rules.view(state, actor), legal)
        check(action in legal) {
            "Policy chose $action for $actor, which is not among that seat's ${legal.size} legal actions"
        }
        state = rules.apply(state, actor, action)
        onState(state)
        steps++
    }
    return state
}

/**
 * Drives a full match with uniformly random legal moves from [rng] — the standard smoke/legality
 * sweep. Deterministic for a seeded [rng]; returns the terminal state.
 */
fun <State, Action, View> driveRandomly(
    rules: GameRules<State, Action, View>,
    initial: State,
    rng: Random,
    maxSteps: Int = 100_000,
    onState: (State) -> Unit = {},
): State = drive(rules, initial, maxSteps, onState) { _, legal -> legal.random(rng) }
