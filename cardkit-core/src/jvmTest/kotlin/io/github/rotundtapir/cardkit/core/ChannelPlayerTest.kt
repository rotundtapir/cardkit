// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.core

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [ChannelPlayer]'s two load-bearing UI contracts: `trySubmit` drops an action unless the
 * engine is actually waiting (double-tap protection), and `prompts` replays the pending view to a
 * late subscriber (the activity-recreation / remount soft-lock guard).
 */
class ChannelPlayerTest {

    @Test
    fun `trySubmit while the engine is waiting delivers the action to decide`() = runTest {
        val player = ChannelPlayer<String, Int>()
        val decided = async { player.decide("prompt") }
        yield() // let decide() suspend on receive
        assertTrue(player.trySubmit(7))
        assertEquals(7, decided.await())
    }

    @Test
    fun `trySubmit with nothing waiting is dropped, not queued for the next prompt`() = runTest {
        val player = ChannelPlayer<String, Int>()
        // No decide() in flight: the tap must be dropped (RENDEZVOUS has no receiver).
        assertFalse(player.trySubmit(1))

        // A later prompt must NOT consume the dropped action — it waits for a fresh submit.
        val decided = async { player.decide("prompt") }
        yield()
        assertTrue(player.trySubmit(2))
        assertEquals(2, decided.await(), "the fresh action is used, not the earlier dropped one")
    }

    @Test
    fun `a racing double trySubmit keeps the first and drops the second`() = runTest {
        val player = ChannelPlayer<String, Int>()
        val decided = async { player.decide("prompt") }
        yield()
        assertTrue(player.trySubmit(1), "first submit satisfies the waiting receiver")
        assertFalse(player.trySubmit(2), "second submit has no receiver left — dropped")
        assertEquals(1, decided.await())
    }

    @Test
    fun `a late subscriber to prompts still sees the pending view (replay)`() = runTest {
        val player = ChannelPlayer<String, Int>()
        launch { player.decide("the-prompt") }
        yield() // decide() emits the prompt before anyone is subscribed

        // Subscribing only now, the replay=1 buffer still delivers the pending view — without it a
        // UI that (re)subscribes after the engine prompted would never render the choice.
        assertEquals("the-prompt", player.prompts.first())
        player.trySubmit(0) // release the suspended decide()
    }
}
