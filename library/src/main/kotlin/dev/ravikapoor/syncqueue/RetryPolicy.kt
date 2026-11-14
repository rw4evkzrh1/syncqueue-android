package dev.ravikapoor.syncqueue

import kotlin.random.Random

/**
 * Exponential backoff with equal jitter, so a reconnect storm does not retry every device in
 * lockstep. Attempts and delays are capped; callers decide what to do once [isExhausted].
 */
class RetryPolicy(
    val maxAttempts: Int = 5,
    private val baseDelayMillis: Long = 1_000L,
    private val maxDelayMillis: Long = 5 * 60_000L,
    private val random: Random = Random.Default,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(baseDelayMillis > 0) { "baseDelayMillis must be positive" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis must exceed the base delay" }
    }

    fun isExhausted(attempt: Int): Boolean = attempt >= maxAttempts

    /** Jittered delay before the next try; [attempt] is 1-based and already counted. */
    fun delayFor(attempt: Int): Long {
        require(attempt >= 1) { "attempt must be at least 1" }
        val shift = (attempt - 1).coerceIn(0, 16)
        val ceiling = minOf(maxDelayMillis, baseDelayMillis shl shift)
        return random.nextLong(ceiling / 2, ceiling + 1)
    }
}
