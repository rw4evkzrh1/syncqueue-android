# Observing the queue

The queue keeps its own state, so health questions should be answered from that state rather
than from handler-side logging, which cannot see rows that were never claimed.

```kotlin
val metrics = SyncMetrics(queue)

metrics.snapshot().let { snapshot ->
    Log.d("SyncWorker", snapshot.format())
    if (snapshot.isDrained) stopWorker()
}
```

## Reading the counters

- `pending` growing while a worker runs means handlers fail fast and the backoff ceiling is
  being reached; compare against `RetryPolicy.maxAttempts`.
- `inFlight` stuck above zero after a process restart identifies rows left claimed by a dead
  process. Flip them back to `pending` in the start-up recovery pass.
- `failed` is the dead-letter count. Alert when it moves, then call `clearFailed()` once the
  offending payloads are understood.

## Sampling

Snapshot on the same cadence as the worker heartbeat, not per operation: each call is three
counts against a small indexed table and stays cheap, but the interesting signal is the
trend. Keep `format()` as the wire format so dashboards and logcat agree.
