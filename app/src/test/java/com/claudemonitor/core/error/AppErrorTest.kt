package com.claudemonitor.core.error

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorTest {

    @Test
    fun `Network error with no connection`() {
        val error = AppError.Network(
            message = "No connection",
            isNoConnection = true
        )

        assertEquals("No internet connection", error.toUserMessage())
        assertTrue(error.isRecoverable())
        assertEquals(ErrorAction.Retry, error.getSuggestedAction())
    }

    @Test
    fun `Network error with timeout`() {
        val error = AppError.Network(
            message = "Timeout",
            isTimeout = true
        )

        assertEquals("Connection timed out", error.toUserMessage())
        assertTrue(error.isRecoverable())
    }

    @Test
    fun `Auth error 401`() {
        val error = AppError.Auth(
            message = "Unauthorized",
            code = 401
        )

        assertEquals("Invalid credentials", error.toUserMessage())
        assertFalse(error.isRecoverable())
        assertEquals(ErrorAction.ReAuthenticate, error.getSuggestedAction())
    }

    @Test
    fun `Auth error 403`() {
        val error = AppError.Auth(
            message = "Forbidden",
            code = 403
        )

        assertEquals("Access denied", error.toUserMessage())
        assertFalse(error.isRecoverable())
    }

    @Test
    fun `Server error is recoverable`() {
        val error = AppError.Server(
            message = "Internal error",
            code = 500
        )

        assertEquals("Server error. Please try again later", error.toUserMessage())
        assertTrue(error.isRecoverable())
        assertEquals(ErrorAction.Retry, error.getSuggestedAction())
    }

    @Test
    fun `Validation error with field`() {
        val error = AppError.Validation(
            message = "is required",
            field = "name"
        )

        assertEquals("Invalid name: is required", error.toUserMessage())
        assertFalse(error.isRecoverable())
        assertEquals(ErrorAction.FixInput, error.getSuggestedAction())
    }

    @Test
    fun `NotFound error with resource type`() {
        val error = AppError.NotFound(
            message = "Not found",
            resourceType = "Driver",
            resourceId = "123"
        )

        assertEquals("Driver not found", error.toUserMessage())
        assertFalse(error.isRecoverable())
        assertEquals(ErrorAction.GoBack, error.getSuggestedAction())
    }

    @Test
    fun `WebSocket error can reconnect`() {
        val error = AppError.WebSocket(
            message = "Connection lost",
            canReconnect = true
        )

        assertEquals("Connection lost. Reconnecting...", error.toUserMessage())
        assertTrue(error.isRecoverable())
        assertEquals(ErrorAction.Reconnect, error.getSuggestedAction())
    }

    @Test
    fun `WebSocket error cannot reconnect`() {
        val error = AppError.WebSocket(
            message = "Failed",
            canReconnect = false
        )

        assertEquals("Connection failed", error.toUserMessage())
        assertFalse(error.isRecoverable())
        assertEquals(ErrorAction.Dismiss, error.getSuggestedAction())
    }

    @Test
    fun `from UnknownHostException creates Network error`() {
        val exception = UnknownHostException("example.com")
        val error = AppError.from(exception)

        assertTrue(error is AppError.Network)
        val networkError = error as AppError.Network
        assertTrue(networkError.isNoConnection)
    }

    @Test
    fun `from SocketTimeoutException creates Network error with timeout`() {
        val exception = SocketTimeoutException("Read timed out")
        val error = AppError.from(exception)

        assertTrue(error is AppError.Network)
        val networkError = error as AppError.Network
        assertTrue(networkError.isTimeout)
    }

    @Test
    fun `from IOException creates Network error`() {
        val exception = IOException("Connection reset")
        val error = AppError.from(exception)

        assertTrue(error is AppError.Network)
    }

    @Test
    fun `from unknown exception creates Unknown error`() {
        val exception = IllegalStateException("Something bad")
        val error = AppError.from(exception)

        assertTrue(error is AppError.Unknown)
        assertEquals("Something bad", error.message)
    }

    @Test
    fun `toAppError extension works`() {
        val exception = IOException("Test")
        val error = exception.toAppError()

        assertTrue(error is AppError.Network)
    }

    @Test
    fun `AppErrorException wraps AppError`() {
        val appError = AppError.Server(message = "Server error")
        val exception = AppErrorException(appError)

        assertEquals("Server error", exception.message)
        assertEquals(appError, exception.appError)
    }
}
