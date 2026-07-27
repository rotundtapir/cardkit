// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ai

import io.github.rotundtapir.cardkit.core.GameRules
import kotlin.random.Random

/**
 * Plays [start] forward under [rules] with [policy] deciding for every seat, until [stop] holds or
 * the game has no current actor. Games use this to roll a sampled world out to a natural boundary
 * (500 and Euchre: the end of the current hand) and score the outcome.
 */
fun <S, A, V> rollout(
    rules: GameRules<S, A, V>,
    start: S,
    policy: (V, Random) -> A,
    random: Random,
    stop: (S) -> Boolean,
): S {
    var s = start
    while (!stop(s)) {
        val actor = rules.currentActor(s) ?: break
        s = rules.apply(s, actor, policy(rules.view(s, actor), random))
    }
    return s
}
