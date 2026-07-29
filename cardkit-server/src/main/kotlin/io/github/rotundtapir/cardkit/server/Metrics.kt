// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.server

import io.github.rotundtapir.cardkit.net.ErrorCode
import java.util.concurrent.atomic.AtomicLong

/**
 * Hand-rolled counters rendered as Prometheus text exposition. Deliberately dependency-free (no
 * Micrometer) — at this scale a monitoring stack would consume more of the 1 GB box than it is
 * worth. Read ad-hoc over SSH via `docker compose exec`; `/metrics` is blocked at the proxy.
 *
 * [prefix] namespaces every series to one game (`euchre_connections_total`), so two game servers on
 * one box never collide.
 */
class Metrics(private val prefix: String) {
    private val connectionsTotal = AtomicLong()
    private val connectionsActive = AtomicLong()
    private val gamesStarted = AtomicLong()
    private val gamesCompleted = AtomicLong()
    private val messagesTotal = AtomicLong()
    private val rejections = ErrorCode.entries.associateWith { AtomicLong() }

    fun connectionOpened() {
        connectionsTotal.incrementAndGet()
        connectionsActive.incrementAndGet()
    }

    fun connectionClosed() {
        connectionsActive.decrementAndGet()
    }

    fun gameStarted() {
        gamesStarted.incrementAndGet()
    }

    fun gameCompleted() {
        gamesCompleted.incrementAndGet()
    }

    fun messageReceived() {
        messagesTotal.incrementAndGet()
    }

    fun rejected(code: ErrorCode) {
        rejections.getValue(code).incrementAndGet()
    }

    /** Render the current values in Prometheus text format. [roomsActive]/[draining] are pulled live. */
    fun render(roomsActive: Int, draining: Boolean): String = buildString {
        fun counter(name: String, help: String, value: Long) {
            append("# HELP ").append(name).append(' ').append(help).append('\n')
            append("# TYPE ").append(name).append(" counter\n")
            append(name).append(' ').append(value).append('\n')
        }
        fun gauge(name: String, help: String, value: Long) {
            append("# HELP ").append(name).append(' ').append(help).append('\n')
            append("# TYPE ").append(name).append(" gauge\n")
            append(name).append(' ').append(value).append('\n')
        }
        counter("${prefix}_connections_total", "WebSocket connections opened", connectionsTotal.get())
        gauge("${prefix}_connections_active", "Currently open connections", connectionsActive.get())
        gauge("${prefix}_rooms_active", "Currently live rooms", roomsActive.toLong())
        counter("${prefix}_games_started_total", "Games started", gamesStarted.get())
        counter("${prefix}_games_completed_total", "Games completed", gamesCompleted.get())
        counter("${prefix}_messages_total", "Client messages received", messagesTotal.get())
        gauge("${prefix}_draining", "1 when the server is draining for restart", if (draining) 1 else 0)
        append("# HELP ${prefix}_rejections_total Rejected requests by reason\n")
        append("# TYPE ${prefix}_rejections_total counter\n")
        for ((code, count) in rejections) {
            append("${prefix}_rejections_total{reason=\"")
                .append(code.name.lowercase()).append("\"} ").append(count.get()).append('\n')
        }
    }
}
