# syncqueue-android

A durable, ordered write queue for offline-first Android apps that already use Room.

Screens editing offline produce writes that must survive process death, airplane mode, and a
flaky mobile network. Firing those writes directly from the ViewModel loses them. Spinning a
custom queue per feature duplicates retry logic and ordering rules. This library puts one
queue in the app database: writes are persisted in the same transaction as the user's edit,
delivered oldest-first, retried with jittered backoff, and dead-lettered instead of silently
dropped.

## Install

```kotlin
// settings.gradle.kts
includeBuild("syncqueue-android") // or a published coordinate

// app/build.gradle.kts
dependencies {
    implementation(project(":library"))
}
```

## Usage

```kotlin
val queue = SyncQueues.create(context)

// offline screen: the edit and the queued write land in one transaction
queue.enqueue(entityType = "note", entityId = note.id, payload = note.toJson())

// worker: whatever has network
queue.drain { operation ->
    api.put(operation.entityType, operation.entityId, operation.payload) // throws on failure
}
```

`drain` returns `DrainResult(synced, failed)`. Failed rows stay in the `failed` state until
`clearFailed()` is called, so a poison payload is inspectable rather than retried forever.

Re-enqueuing the same `entityType`/`entityId` replaces the pending write: latest edit wins.
An in-flight row is never replaced.

## Layout

```
library/src/main/kotlin/dev/ravikapoor/syncqueue/
  SyncOperation.kt   Room entity and row states
  SyncQueueDao.kt    queries, claim/reschedule, latest-write-wins replace
  RetryPolicy.kt     exponential backoff with equal jitter, capped attempts
  SyncQueue.kt       enqueue/drain surface, database and factory
```

## Screenshots

The sample app is a two-pane notes list with an airplane-mode toggle; captures land in
`docs/screenshots/` once the sample module is wired.

## minSdk notes

`minSdk` is 23, matching Room's own floor for KSP codegen on this compiler version and
keeping `java.time` available without desugaring. AGP 8.7 / Kotlin 2.1 / Room 2.6.1.

## Limits

- Rows claimed by a process that dies mid-handler stay `in_flight`; run a recovery pass at
  start-up to flip stale claims back to `pending`.
- One queue per database file; pass a distinct `name` if a module needs isolation.
