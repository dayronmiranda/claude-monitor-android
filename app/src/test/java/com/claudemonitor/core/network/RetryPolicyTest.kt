package com.claudemonitor.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `default policy has correct values`() {
        val policy = RetryPolicy.Default

        assertEquals(3, policy.maxAttempts)
        assertEquals(1000L, policy.initialDelayMs)
        assertEquals(10000L, policy.maxDelayMs)
        assertEquals(2.0, policy.backoffMultiplier, 0.01)
    }

    @Test
    fun `getDelayForAttempt returns correct delays with exponential backoff`() {
        val policy = RetryPolicy(
            initialDelayMs = 1000L,
            maxDelayMs = 10000L,
            backoffMultiplier = 2.0
        )

        assertEquals(0L, policy.getDelayForAttempt(0))    // First attempt, no delay
        assertEquals(1000L, policy.getDelayForAttempt(1)) // 1000 * 2^0
        assertEquals(2000L, policy.getDelayForAttempt(2)) // 1000 * 2^1
        assertEquals(4000L, policy.getDelayForAttempt(3)) // 1000 * 2^2
        assertEquals(8000L, policy.getDelayForAttempt(4)) // 1000 * 2^3
        assertEquals(10000L, policy.getDelayForAttempt(5)) // Capped at maxDelay
    }

    @Test
    fun `shouldRetry returns true for valid conditions`() {
        val policy = RetryPolicy(
            maxAttempts = 3,
            retryOn = setOf(RetryCondition.NetworkError, RetryCondition.Timeout)
        )

        assertTrue(policy.shouldRetry(RetryCondition.NetworkError, 0))
        assertTrue(policy.shouldRetry(RetryCondition.Timeout, 1))
        assertTrue(policy.shouldRetry(RetryCondition.NetworkError, 2))
    }

    @Test
    fun `shouldRetry returns false when max attempts exceeded`() {
        val policy = RetryPolicy(maxAttempts = 3)

        assertFalse(policy.shouldRetry(RetryCondition.NetworkError, 3))
        assertFalse(policy.shouldRetry(RetryCondition.NetworkError, 4))
    }

    @Test
    fun `shouldRetry returns false for excluded conditions`() {
        val policy = RetryPolicy(
            retryOn = setOf(RetryCondition.NetworkError)
        )

        assertFalse(policy.shouldRetry(RetryCondition.ServerError, 0))
        assertFalse(policy.shouldRetry(RetryCondition.RateLimited, 0))
    }

    @Test
    fun `NoRetry policy has single attempt`() {
        val policy = RetryPolicy.NoRetry

        assertEquals(1, policy.maxAttempts)
        assertFalse(policy.shouldRetry(RetryCondition.NetworkError, 1))
    }

    @Test
    fun `Aggressive policy has more attempts and shorter delay`() {
        val policy = RetryPolicy.Aggressive

        assertEquals(5, policy.maxAttempts)
        assertEquals(500L, policy.initialDelayMs)
    }

    @Test
    fun `withRetry succeeds on first attempt`() = runTest {
        var attempts = 0

        val result = withRetry(RetryPolicy.Default) {
            attempts++
            "success"
        }

        assertEquals("success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `withRetry retries on failure`() = runTest {
        var attempts = 0

        val result = withRetry(
            policy = RetryPolicy(maxAttempts = 3, initialDelayMs = 10L),
            conditionMapper = { RetryCondition.NetworkError }
        ) {
            attempts++
            if (attempts < 3) throw Exception("Retry me")
            "success"
        }

        assertEquals("success", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `withRetry throws after max attempts`() = runTest {
        var attempts = 0

        try {
            withRetry(
                policy = RetryPolicy(maxAttempts = 2, initialDelayMs = 10L),
                conditionMapper = { RetryCondition.NetworkError }
            ) {
                attempts++
                throw Exception("Always fail")
            }
            fail("Should have thrown")
        } catch (e: Exception) {
            assertEquals("Always fail", e.message)
            assertEquals(2, attempts)
        }
    }

    @Test
    fun `withRetry does not retry for excluded conditions`() = runTest {
        var attempts = 0

        try {
            withRetry(
                policy = RetryPolicy(
                    maxAttempts = 3,
                    retryOn = setOf(RetryCondition.NetworkError)
                ),
                conditionMapper = { RetryCondition.ServerError }
            ) {
                attempts++
                throw Exception("Server error")
            }
            fail("Should have thrown")
        } catch (e: Exception) {
            assertEquals(1, attempts) // No retry
        }
    }

    @Test
    fun `withRetryResult returns metadata`() = runTest {
        var attempts = 0

        val result = withRetryResult(
            policy = RetryPolicy(maxAttempts = 3, initialDelayMs = 10L),
            conditionMapper = { RetryCondition.NetworkError }
        ) {
            attempts++
            if (attempts < 2) throw Exception("Retry")
            "success"
        }

        assertEquals("success", result.value)
        assertEquals(2, result.attempts)
        assertTrue(result.totalDelayMs >= 10L)
    }
}
