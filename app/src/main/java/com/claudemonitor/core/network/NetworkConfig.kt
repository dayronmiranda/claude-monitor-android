package com.claudemonitor.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global network configuration for the app.
 */
@Singleton
class NetworkConfig @Inject constructor() {

    // Timeouts
    val connectTimeoutMs: Long = 10_000L      // 10 seconds
    val readTimeoutMs: Long = 30_000L         // 30 seconds
    val writeTimeoutMs: Long = 30_000L        // 30 seconds
    val callTimeoutMs: Long = 60_000L         // 60 seconds total

    // WebSocket specific
    val wsPingIntervalMs: Long = 30_000L      // 30 seconds
    val wsReconnectDelayMs: Long = 1_000L     // Initial reconnect delay
    val wsMaxReconnectDelayMs: Long = 30_000L // Max reconnect delay
    val wsMaxReconnectAttempts: Int = 10      // Max reconnect attempts

    // Retry policies for different operations
    val defaultRetryPolicy = RetryPolicy.Default
    val apiRetryPolicy = RetryPolicy(
        maxAttempts = 3,
        initialDelayMs = 1000L,
        maxDelayMs = 5000L
    )
    val connectionCheckRetryPolicy = RetryPolicy(
        maxAttempts = 2,
        initialDelayMs = 500L,
        maxDelayMs = 2000L
    )

    // Cache configuration
    val cacheMaxAgeSeconds: Int = 60          // 1 minute
    val cacheMaxStaleSeconds: Int = 60 * 60   // 1 hour when offline

    // Circuit breaker
    val circuitBreakerThreshold: Int = 5      // Failures before opening
    val circuitBreakerResetMs: Long = 30_000L // Time before retry

    companion object {
        val TIMEOUT_UNIT = TimeUnit.MILLISECONDS
    }
}

/**
 * Circuit breaker to prevent overwhelming failed services.
 */
class CircuitBreaker(
    private val threshold: Int = 5,
    private val resetTimeMs: Long = 30_000L
) {
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var isOpen = false

    @Synchronized
    fun recordSuccess() {
        failureCount = 0
        isOpen = false
    }

    @Synchronized
    fun recordFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        if (failureCount >= threshold) {
            isOpen = true
        }
    }

    @Synchronized
    fun canProceed(): Boolean {
        if (!isOpen) return true

        // Check if reset time has passed
        val elapsed = System.currentTimeMillis() - lastFailureTime
        if (elapsed >= resetTimeMs) {
            // Half-open state: allow one request through
            isOpen = false
            failureCount = threshold - 1 // Will open again on next failure
            return true
        }

        return false
    }

    fun getState(): CircuitState = when {
        !isOpen -> CircuitState.Closed
        canProceed() -> CircuitState.HalfOpen
        else -> CircuitState.Open
    }
}

enum class CircuitState {
    Closed,    // Normal operation
    Open,      // Failing, reject requests
    HalfOpen   // Testing if service recovered
}
