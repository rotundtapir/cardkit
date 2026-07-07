// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.random.Random

/**
 * A participant that, when it is their turn, decides an [Action] given the [View] the game exposes to
 * them.
 *
 * This is the central seam that lets the same engine drive very different participants
 * interchangeably:
 *  - [StrategyPlayer] wraps a synchronous AI [Strategy];
 *  - [ChannelPlayer] suspends until a human supplies an action through the UI;
 *  - a future `RemotePlayer` would suspend until an action arrives over the network.
 *
 * [decide] is a `suspend` function precisely so that network and human latency are naturally
 * expressible without changing the engine.
 */
fun interface Player<View, Action> {
    suspend fun decide(view: View): Action
}

/**
 * A synchronous decision function, typically an AI. The [random] argument keeps behaviour
 * deterministic under a seeded RNG.
 *
 * The [view] is expected to carry everything the strategy needs, including the set of legal actions
 * for the seat to move — the engine redacts hidden information (other players' hands) before building
 * it, so a strategy cannot cheat, and the very same view can be sent to a remote client.
 */
fun interface Strategy<View, Action> {
    fun decide(view: View, random: Random): Action
}

/** Adapts a synchronous [Strategy] into a [Player]. */
class StrategyPlayer<View, Action>(
    private val strategy: Strategy<View, Action>,
    private val random: Random,
) : Player<View, Action> {
    override suspend fun decide(view: View): Action = strategy.decide(view, random)
}

/**
 * A [Player] driven externally — the natural fit for a human on a touch UI.
 *
 * When it is this player's turn the engine calls [decide], which emits the current [View] on [prompts]
 * (so the UI can render the choice) and then suspends until [submit] is called with the chosen action.
 */
class ChannelPlayer<View, Action> : Player<View, Action> {
    private val _prompts = MutableSharedFlow<View>(replay = 1, extraBufferCapacity = 1)
    private val responses = Channel<Action>(Channel.RENDEZVOUS)

    /** Emits the view whenever the engine is waiting on this player. The UI collects this. */
    val prompts: SharedFlow<View> = _prompts

    override suspend fun decide(view: View): Action {
        _prompts.emit(view)
        return responses.receive()
    }

    /** Called by the UI to supply the player's chosen action. Suspends until the engine consumes it. */
    suspend fun submit(action: Action) {
        responses.send(action)
    }

    /**
     * Non-suspending submit that only succeeds while the engine is actually waiting in [decide].
     * Use this from UIs: a double-tap (or a tap racing a state change) is dropped instead of being
     * queued and later consumed as the answer to a *different* prompt.
     */
    fun trySubmit(action: Action): Boolean = responses.trySend(action).isSuccess
}
