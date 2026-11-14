package dev.ravikapoor.syncqueue

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One durable write that still has to reach the server.
 *
 * Rows are inserted in [STATE_PENDING], claimed one at a time in id order, and only removed
 * once the caller's handler succeeds or the retry budget is spent.
 */
@Entity(tableName = "sync_operation")
internal data class SyncOperation(
    val entityType: String,
    val entityId: String,
    val payload: String,
    val nextAttemptAt: Long,
    val attempts: Int = 0,
    val state: String = STATE_PENDING,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
) {
    internal companion object {
        const val STATE_PENDING = "pending"
        const val STATE_IN_FLIGHT = "in_flight"
        const val STATE_FAILED = "failed"
    }
}
