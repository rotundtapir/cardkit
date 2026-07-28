// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.testing

import io.github.rotundtapir.cardkit.core.Seat

/**
 * The first seed in [seeds] for which [predicate] holds — for pinning a test to a deal with a
 * particular shape ("a hand where seat 2 is void in trumps"). Fails with the searched range when
 * nothing matches, so the test reports "no such deal in 0..10000" instead of a bare NPE.
 *
 * Both games grew this helper verbatim in engine and ai test support; the search itself is
 * game-agnostic.
 */
fun findSeed(seeds: LongRange = 0L..10_000L, predicate: (Long) -> Boolean): Long =
    seeds.firstOrNull(predicate)
        ?: error("No seed in $seeds satisfies the predicate — widen the range or relax the shape")

/** All seats at a table of [playerCount], in seat order — the fixture every rules test opens with. */
fun seats(playerCount: Int): List<Seat> = (0 until playerCount).map(::Seat)
