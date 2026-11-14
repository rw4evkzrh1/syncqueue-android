package dev.ravikapoor.syncqueue

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlin.coroutines.cancellation.CancellationException

/**
 * Crash-safe write queue backed by Room.
 *
 * Enqueue from the UI thread of an offline-first screen, then drain from whatever worker owns
 * the network. Ordering is FIFO by row id, so causally related writes keep their order.
 */
class SyncQueue internal constructor(
    private val dao: SyncQueueDao,
    private val retryPolicy: RetryPolicy,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Queues [payload] and drops any older pending write for the same entity. */
    suspend fun enqueue(entityType: String, entityId: String, payload: String): Long =
        dao.replacePending(
            SyncOperation(
                entityType = entityType,
                entityId = entityId,
                payload = payload,
                nextAttemptAt = clock(),
            )
        )

    /** Runs [handler] for every due operation until the queue is empty or nothing is due. */
    suspend fun drain(handler: suspend (SyncOperation) -> Unit): DrainResult {
        var synced = 0
        var exhausted = 0
        while (true) {
            val operation = dao.nextDue(clock()) ?: break
            dao.claim(operation.id)
            val attempt = operation.attempts + 1
            try {
                handler(operation)
                dao.delete(operation.id)
                synced++
            } catch (error: CancellationException) {
                dao.reschedule(operation.id, clock())
                throw error
            } catch (error: Exception) {
                if (retryPolicy.isExhausted(attempt)) {
                    dao.markFailed(operation.id)
                    exhausted++
                } else {
                    dao.reschedule(operation.id, clock() + retryPolicy.delayFor(attempt))
                }
            }
        }
        return DrainResult(synced = synced, failed = exhausted)
    }

    suspend fun count(state: String): Int = dao.countByState(state)

    /** Drops dead-lettered rows, e.g. after the user signs in again. */
    suspend fun clearFailed(): Int = dao.clearFailed()

    data class DrainResult(val synced: Int, val failed: Int)
}

@Database(entities = [SyncOperation::class], version = 1, exportSchema = true)
internal abstract class SyncQueueDatabase : RoomDatabase() {
    internal abstract fun dao(): SyncQueueDao
}

object SyncQueues {

    fun create(
        context: Context,
        name: String = "syncqueue.db",
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): SyncQueue {
        val database = Room.databaseBuilder(context, SyncQueueDatabase::class.java, name).build()
        return SyncQueue(database.dao(), retryPolicy)
    }
}
