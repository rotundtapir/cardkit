// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tiny deterministic game used only to exercise [GameRules] + [GameDriver] + [Player]:
 * seats take turns adding 1..3 to a shared counter until it reaches [CountUp.State.target].
 */
private object CountUp : GameRules<CountUp.State, Int, CountUp.View> {
    data class State(val counter: Int, val target: Int, val seats: Int, val turn: Seat, val moves: Int)
    data class View(val counter: Int, val target: Int, val mySeat: Seat, val legalActions: List<Int>)

    fun initial(seats: Int, target: Int) = State(0, target, seats, Seat(0), 0)

    override fun currentActor(state: State): Seat? = if (isTerminal(state)) null else state.turn
    override fun isTerminal(state: State): Boolean = state.counter >= state.target
    override fun legalActions(state: State, seat: Seat): List<Int> =
        if (seat == state.turn && !isTerminal(state)) listOf(1, 2, 3) else emptyList()

    override fun view(state: State, seat: Seat): View =
        View(state.counter, state.target, seat, legalActions(state, seat))

    override fun apply(state: State, seat: Seat, action: Int): State {
        check(seat == state.turn) { "Not $seat's turn" }
        require(action in 1..3) { "Illegal increment $action" }
        return state.copy(
            counter = state.counter + action,
            turn = Seat((state.turn.index + 1) % state.seats),
            moves = state.moves + 1,
        )
    }
}

class GameDriverTest {
    @Test
    fun `driver runs strategy players to a terminal state`() = runTest {
        val greedy = StrategyPlayer<CountUp.View, Int>(
            strategy = { view, _ -> view.legalActions.max() }, // always +3
            random = Random(0),
        )
        val driver = GameDriver(CountUp, mapOf(Seat(0) to greedy, Seat(1) to greedy))

        val states = mutableListOf<CountUp.State>()
        val terminal = driver.play(CountUp.initial(seats = 2, target = 10)) { states += it }

        assertTrue(CountUp.isTerminal(terminal))
        assertEquals(12, terminal.counter)   // 3,6,9,12
        assertEquals(4, terminal.moves)
        assertEquals(5, states.size)         // initial + one per move
        assertEquals(0, states.first().counter)
    }

    @Test
    fun `strategy sees only the redacted per-seat view`() = runTest {
        // The view a strategy receives carries the legal actions and never the full State.
        var seenLegal: List<Int>? = null
        val spy = StrategyPlayer<CountUp.View, Int>(
            strategy = { view, _ -> seenLegal = view.legalActions; view.legalActions.first() },
            random = Random(0),
        )
        GameDriver(CountUp, mapOf(Seat(0) to spy))
            .play(CountUp.initial(seats = 1, target = 2))
        assertEquals(listOf(1, 2, 3), seenLegal)
    }

    @Test
    fun `ChannelPlayer prompts then resumes on submit`() = runTest {
        val human = ChannelPlayer<CountUp.View, Int>()
        val driver = GameDriver(CountUp, mapOf(Seat(0) to human))

        val result = async { driver.play(CountUp.initial(seats = 1, target = 5)) }

        // The engine prompts the human; the UI would render this view.
        val firstPrompt = human.prompts.first()
        assertEquals(0, firstPrompt.counter)
        assertEquals(listOf(1, 2, 3), firstPrompt.legalActions)

        // Feed moves until the game ends: +3, +3 -> 6 >= 5.
        launch {
            human.submit(3)
            human.submit(3)
        }

        val terminal = result.await()
        assertTrue(terminal.counter >= 5)
        assertEquals(2, terminal.moves)
    }
}
