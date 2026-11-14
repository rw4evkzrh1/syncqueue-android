package dev.ravikapoor.syncqueue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface SyncQueueDao {

    @Query("SELECT * FROM sync_operation WHERE state = 'pending' AND nextAttemptAt <= :now ORDER BY id LIMIT 1")
    suspend fun nextDue(now: Long): SyncOperation?

    @Query("SELECT COUNT(*) FROM sync_operation WHERE state = :state")
    suspend fun countByState(state: String): Int

    @Query("UPDATE sync_operation SET state = 'in_flight', attempts = attempts + 1 WHERE id = :id")
    suspend fun claim(id: Long)

    @Query("UPDATE sync_operation SET state = 'pending', nextAttemptAt = :at WHERE id = :id")
    suspend fun reschedule(id: Long, at: Long)

    @Query("UPDATE sync_operation SET state = 'failed' WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query("DELETE FROM sync_operation WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sync_operation WHERE state = 'failed'")
    suspend fun clearFailed(): Int

    @Query(
        "DELETE FROM sync_operation WHERE entityType = :entityType " +
            "AND entityId = :entityId AND state = 'pending'"
    )
    suspend fun deletePendingFor(entityType: String, entityId: String)

    @Insert
    suspend fun insert(operation: SyncOperation): Long

    /**
     * Latest write wins: a newer payload for the same entity supersedes any queued one.
     * In-flight rows are left alone so a running handler still owns them.
     */
    @Transaction
    suspend fun replacePending(operation: SyncOperation): Long {
        deletePendingFor(operation.entityType, operation.entityId)
        return insert(operation)
    }
}
