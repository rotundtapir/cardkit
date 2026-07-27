// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SeatsTest {

    @Test
    fun `nextSeat wraps clockwise`() {
        assertEquals(Seat(1), nextSeat(Seat(0), 4))
        assertEquals(Seat(0), nextSeat(Seat(3), 4))
        assertEquals(Seat(0), nextSeat(Seat(5), 6))
    }

    @Test
    fun `two teams yield alternating partnerships`() {
        assertEquals(0, teamOf(Seat(0), 2))
        assertEquals(1, teamOf(Seat(1), 2))
        assertEquals(0, teamOf(Seat(2), 2))
        assertEquals(1, teamOf(Seat(3), 2))
    }

    @Test
    fun `teammates at a four-seat table with two teams is the opposite seat`() {
        assertEquals(listOf(Seat(2)), teammatesOf(Seat(0), 4, 2))
        assertEquals(listOf(Seat(1)), teammatesOf(Seat(3), 4, 2))
    }

    @Test
    fun `teammates at six seats with two teams are the same-parity seats`() {
        assertEquals(listOf(Seat(2), Seat(4)), teammatesOf(Seat(0), 6, 2))
    }

    @Test
    fun `teammates with three teams sit opposite`() {
        assertEquals(listOf(Seat(4)), teammatesOf(Seat(1), 6, 3))
    }

    @Test
    fun `every seat its own team has no teammates`() {
        assertEquals(emptyList(), teammatesOf(Seat(1), 2, 2))
    }

    @Test
    fun `play order rotates the active seats from the leader`() {
        val all = listOf(Seat(0), Seat(1), Seat(2), Seat(3))
        assertEquals(listOf(Seat(2), Seat(3), Seat(0), Seat(1)), playOrder(Seat(2), all))
    }

    @Test
    fun `play order skips sitting-out seats`() {
        // Euchre lone hand: seat 2 sits out, leader 3 -> 3, 0, 1.
        val active = listOf(Seat(0), Seat(1), Seat(3))
        assertEquals(listOf(Seat(3), Seat(0), Seat(1)), playOrder(Seat(3), active))
    }

    @Test
    fun `play order requires the leader to be active`() {
        assertFailsWith<IllegalArgumentException> { playOrder(Seat(2), listOf(Seat(0), Seat(1))) }
    }
}
