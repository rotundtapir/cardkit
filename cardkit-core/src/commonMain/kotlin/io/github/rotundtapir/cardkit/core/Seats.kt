// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

/** The next seat clockwise at a table of [playerCount]. */
fun nextSeat(seat: Seat, playerCount: Int): Seat = Seat((seat.index + 1) % playerCount)

/**
 * Team index for [seat]: seat modulo [teamCount]. With 2 teams this yields the standard
 * partnerships at every even table size — 4 players: seats 0&2 vs 1&3; 6 players: 0&2&4 vs 1&3&5.
 * With [teamCount] equal to the player count every seat is its own team, and with 3 teams at a
 * 6-seat table partners sit opposite: 0&3, 1&4, 2&5.
 */
fun teamOf(seat: Seat, teamCount: Int): Int = seat.index % teamCount

/** The other seats on [seat]'s team, in seat order (empty when every seat is its own team). */
fun teammatesOf(seat: Seat, playerCount: Int, teamCount: Int): List<Seat> =
    (0 until playerCount).map(::Seat).filter { it != seat && teamOf(it, teamCount) == teamOf(seat, teamCount) }

/**
 * The [active] seats in the order they play a trick led by [leader]: clockwise (ascending seat
 * index, wrapping) starting at the leader. [leader] must be one of the active seats.
 */
fun playOrder(leader: Seat, active: List<Seat>): List<Seat> {
    val ordered = active.sortedBy { it.index }
    val start = ordered.indexOf(leader)
    require(start >= 0) { "Leader $leader is not an active seat: $active" }
    return List(ordered.size) { ordered[(start + it) % ordered.size] }
}
