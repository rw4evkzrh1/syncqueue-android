package dev.ravikapoor.syncqueue.metrics

import dev.ravikapoor.syncqueue.SyncQueue

/**
 * Read-only counters over a [SyncQueue], so a screen or a worker can tell "nothing to send"
 * apart from "sending keeps failing" without opening the database.
 *
 * State names mirror the row values stored by the queue.
 */
class SyncMetrics(private val queue: SyncQueue) {

    suspend fun snapshot(): Snapshot = Snapshot(
        pending = queue.count(STATE_PENDING),
        inFlight = queue.count(STATE_IN_FLIGHT),
        failed = queue.count(STATE_FAILED),
    )

    data class Snapshot(val pending: Int, val inFlight: Int, val failed: Int) {
        val outstanding: Int get() = pending + inFlight
        val isDrained: Boolean get() = outstanding == 0

        /** Single line suitable for logcat or a heartbeat upload. */
        fun format(): String = "queue pending=$pending in_flight=$inFlight failed=$failed"
    }

    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_IN_FLIGHT = "in_flight"
        const val STATE_FAILED = "failed"
    }
}
