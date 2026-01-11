package com.claudemonitor.core.network

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Configurable retry policy with exponential backoff.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 10000L,
    val backoffMultiplier: Double = 2.0,
    val retryOn: Set<RetryCondition> = setOf(
        RetryCondition.NetworkError,
        RetryCondition.ServerError,
        RetryCondition.Timeout
    )
) {
    companion object {
        val Default = RetryPolicy()
        val Aggressive = RetryPolicy(maxAttempts = 5, initialDelayMs = 500L)
        val Conservative = RetryPolicy(maxAttempts = 2, initialDelayMs = 2000L)
        val NoRetry = RetryPolicy(maxAttempts = 1)
    }

    /**
     * Calculates delay for the given attempt (0-indexed).
     */
    fun getDelayForAttempt(attempt: Int): Long {
        if (attempt <= 0) return 0
        val delay = initialDelayMs * backoffMultiplier.pow(attempt - 1)
        return min(delay.toLong(), maxDelayMs)
    }

    /**
     * Checks if retry should be attempted for the given condition.
     */
    fun shouldRetry(condition: RetryCondition, attempt: Int): Boolean {
        return attempt < maxAttempts && condition in retryOn
    }
}

/**
 * Conditions that can trigger a retry.
 */
enum class RetryCondition {
    NetworkError,      // IOException, no connection
    ServerError,       // 5xx responses
    Timeout,           // Socket timeout
    RateLimited,       // 429 responses
    Unauthorized       // 401 (for token refresh)
}

/**
 * Executes a block with retry policy.
 */
suspend fun <T> withRetry(
    policy: RetryPolicy = RetryPolicy.Default,
    conditionMapper: (Throwable) -> RetryCondition? = { defaultConditionMapper(it) },
    block: suspend (attempt: Int) -> T
): T {
    var lastException: Throwable? = null

    repeat(policy.maxAttempts) { attempt ->
        try {
            return block(attempt)
        } catch (e: Throwable) {
            lastException = e
            val condition = conditionMapper(e)

            if (condition != null && policy.shouldRetry(condition, attempt + 1)) {
                val delayMs = policy.getDelayForAttempt(attempt + 1)
                delay(delayMs)
            } else {
                throw e
            }
        }
    }

    throw lastException ?: IllegalStateException("Retry failed without exception")
}

/**
 * Default condition mapper for common exceptions.
 */
private fun defaultConditionMapper(throwable: Throwable): RetryCondition? {
    return when {
        throwable is java.net.SocketTimeoutException -> RetryCondition.Timeout
        throwable is java.net.UnknownHostException -> RetryCondition.NetworkError
        throwable is java.io.IOException -> RetryCondition.NetworkError
        throwable.message?.contains("500") == true -> RetryCondition.ServerError
        throwable.message?.contains("502") == true -> RetryCondition.ServerError
        throwable.message?.contains("503") == true -> RetryCondition.ServerError
        throwable.message?.contains("429") == true -> RetryCondition.RateLimited
        else -> null
    }
}

/**
 * Result of a retry operation with metadata.
 */
data class RetryResult<T>(
    val value: T,
    val attempts: Int,
    val totalDelayMs: Long
)

/**
 * Executes with retry and returns metadata.
 */
suspend fun <T> withRetryResult(
    policy: RetryPolicy = RetryPolicy.Default,
    conditionMapper: (Throwable) -> RetryCondition? = { defaultConditionMapper(it) },
    block: suspend (attempt: Int) -> T
): RetryResult<T> {
    var totalDelay = 0L
    var attempts = 0

    val result = withRetry(policy, conditionMapper) { attempt ->
        attempts = attempt + 1
        if (attempt > 0) {
            totalDelay += policy.getDelayForAttempt(attempt)
        }
        block(attempt)
    }

    return RetryResult(result, attempts, totalDelay)
}
