// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.server

import io.github.rotundtapir.cardkit.core.GameRules
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Strategy
import io.github.rotundtapir.cardkit.net.CreateLobbyRequest
import io.github.rotundtapir.cardkit.net.DEFAULT_IDLE_DISBAND_MINUTES
import io.github.rotundtapir.cardkit.net.DEFAULT_TURN_TIMEOUT_SECONDS
import io.github.rotundtapir.cardkit.net.GameOver
import io.github.rotundtapir.cardkit.net.gameWireModule
import io.github.rotundtapir.cardkit.net.wireJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A deliberately trivial two-seat game, so this module's tests exercise the room actor, seat hosting,
 * snapshots and the wire plumbing without depending on any real game.
 *
 * Two players alternate moving 1 or 2 squares; the game ends after [ToyConfig.target] moves. Each
 * seat also holds a secret number that [ToyRules.view] redacts, so a leak would be visible in tests.
 */
@Serializable
data class ToyState(
    val moves: List<Int> = emptyList(),
    val toAct: Int = 0,
    val rngSeed: Long = 0,
    /** Per-seat hidden information; only the viewing seat's own entry survives redaction. */
    val secrets: Map<Seat, Int> = mapOf(Seat(0) to 11, Seat(1) to 22),
)

@Serializable
sealed interface ToyAction {
    @Serializable
    @SerialName("move")
    data class Move(val squares: Int) : ToyAction
}

@Serializable
data class ToyView(
    val seat: Seat,
    val moves: List<Int>,
    val toAct: Seat?,
    val legalActions: List<ToyAction>,
    val yourSecret: Int,
)

@Serializable
data class ToyConfig(
    val target: Int = TOY_TARGET,
    val turnTimeoutSeconds: Int = DEFAULT_TURN_TIMEOUT_SECONDS,
    val idleDisbandMinutes: Int = DEFAULT_IDLE_DISBAND_MINUTES,
)

@Serializable
@SerialName("lobby.create")
data class ToyCreateLobby(
    override val displayName: String,
    val target: Int = TOY_TARGET,
    override val turnTimeoutSeconds: Int = DEFAULT_TURN_TIMEOUT_SECONDS,
    override val idleDisbandMinutes: Int = DEFAULT_IDLE_DISBAND_MINUTES,
    override val seed: Long? = null,
) : CreateLobbyRequest

const val TOY_SEATS: Int = 2
const val TOY_TARGET: Int = 6

class ToyRules(private val target: Int = TOY_TARGET) : GameRules<ToyState, ToyAction, ToyView> {

    override fun currentActor(state: ToyState): Seat? = if (isTerminal(state)) null else Seat(state.toAct)

    override fun isTerminal(state: ToyState): Boolean = state.moves.size >= target

    override fun view(state: ToyState, seat: Seat): ToyView = ToyView(
        seat = seat,
        moves = state.moves,
        toAct = currentActor(state),
        legalActions = legalActions(state, seat),
        yourSecret = state.secrets[seat] ?: 0,
    )

    override fun legalActions(state: ToyState, seat: Seat): List<ToyAction> =
        if (currentActor(state) != seat) emptyList() else listOf(ToyAction.Move(1), ToyAction.Move(2))

    override fun apply(state: ToyState, seat: Seat, action: ToyAction): ToyState {
        check(currentActor(state) == seat) { "Not $seat's turn" }
        require(action is ToyAction.Move && action.squares in 1..2) { "Illegal move: $action" }
        return state.copy(
            moves = state.moves + action.squares,
            toAct = (seat.index + 1) % TOY_SEATS,
        )
    }
}

object ToyDescriptor : GameDescriptor<ToyState, ToyAction, ToyView, ToyConfig> {
    override val gameName: String = "Toy"
    override val metricsPrefix: String = "toy"
    override val protocolVersion: Int = 1

    override val wireJson = wireJson(
        gameWireModule(
            actionSerializer = ToyAction.serializer(),
            viewSerializer = ToyView.serializer(),
            configSerializer = ToyConfig.serializer(),
            createLobbyClass = ToyCreateLobby::class,
            createLobbySerializer = ToyCreateLobby.serializer(),
        ),
    )

    override val stateSerializer = ToyState.serializer()
    override val configSerializer = ToyConfig.serializer()

    override fun bot(config: ToyConfig): Strategy<ToyView, ToyAction> =
        Strategy { view, random -> view.legalActions.ifEmpty { listOf(ToyAction.Move(1)) }.random(random) }

    override fun rulesFor(config: ToyConfig): GameRules<ToyState, ToyAction, ToyView> = ToyRules(config.target)

    override fun newGame(config: ToyConfig, seed: Long): ToyState = ToyState(rngSeed = seed)

    override fun gameOver(state: ToyState): GameOver {
        val scores = state.moves.withIndex()
            .groupBy({ it.index % TOY_SEATS }, { it.value })
            .mapValues { (_, moves) -> moves.sum() }
        val winner = scores.maxByOrNull { it.value }?.key ?: -1
        return GameOver(winner, scores)
    }

    override fun botRestoreSeed(state: ToyState): Long = state.rngSeed

    /** A target below 1 is the "unsupported configuration" case, so tests can hit BAD_CONFIG. */
    override fun configFrom(request: CreateLobbyRequest): ToyConfig? {
        val toy = request as? ToyCreateLobby ?: return null
        if (toy.target < 1) return null
        return ToyConfig(toy.target, toy.turnTimeoutSeconds, toy.idleDisbandMinutes)
    }

    override fun playerCount(config: ToyConfig): Int = TOY_SEATS
    override fun turnTimeoutSeconds(config: ToyConfig): Int = config.turnTimeoutSeconds
    override fun idleDisbandMinutes(config: ToyConfig): Int = config.idleDisbandMinutes

    override fun withTimeouts(config: ToyConfig, turnTimeoutSeconds: Int?, idleDisbandMinutes: Int?): ToyConfig =
        config.copy(
            turnTimeoutSeconds = turnTimeoutSeconds ?: config.turnTimeoutSeconds,
            idleDisbandMinutes = idleDisbandMinutes ?: config.idleDisbandMinutes,
        )
}
