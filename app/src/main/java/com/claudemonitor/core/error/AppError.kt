package com.claudemonitor.core.error

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Sealed class representing all possible application errors.
 * Provides type-safe error handling across the app.
 */
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null
) {
    /**
     * Network-related errors (no connection, timeout, etc.)
     */
    data class Network(
        override val message: String = "Network error",
        override val cause: Throwable? = null,
        val isTimeout: Boolean = false,
        val isNoConnection: Boolean = false
    ) : AppError(message, cause)

    /**
     * Authentication errors (401, 403, invalid credentials)
     */
    data class Auth(
        override val message: String = "Authentication failed",
        override val cause: Throwable? = null,
        val code: Int? = null
    ) : AppError(message, cause)

    /**
     * Server-side errors (5xx responses)
     */
    data class Server(
        override val message: String = "Server error",
        override val cause: Throwable? = null,
        val code: Int? = null
    ) : AppError(message, cause)

    /**
     * API errors with specific error response from backend
     */
    data class Api(
        override val message: String,
        override val cause: Throwable? = null,
        val code: String? = null,
        val details: Map<String, Any>? = null
    ) : AppError(message, cause)

    /**
     * WebSocket connection errors
     */
    data class WebSocket(
        override val message: String = "WebSocket error",
        override val cause: Throwable? = null,
        val code: Int? = null,
        val canReconnect: Boolean = true
    ) : AppError(message, cause)

    /**
     * Local database errors
     */
    data class Database(
        override val message: String = "Database error",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    /**
     * Resource not found errors
     */
    data class NotFound(
        override val message: String = "Resource not found",
        val resourceType: String? = null,
        val resourceId: String? = null
    ) : AppError(message)

    /**
     * Validation errors (invalid input, etc.)
     */
    data class Validation(
        override val message: String,
        val field: String? = null,
        val constraints: List<String>? = null
    ) : AppError(message)

    /**
     * Unknown/unexpected errors
     */
    data class Unknown(
        override val message: String = "An unexpected error occurred",
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    /**
     * Returns a user-friendly message for display in UI
     */
    fun toUserMessage(): String = when (this) {
        is Network -> when {
            isNoConnection -> "No internet connection"
            isTimeout -> "Connection timed out"
            else -> "Network error. Please check your connection"
        }
        is Auth -> when (code) {
            401 -> "Invalid credentials"
            403 -> "Access denied"
            else -> "Authentication failed"
        }
        is Server -> "Server error. Please try again later"
        is Api -> message
        is WebSocket -> if (canReconnect) "Connection lost. Reconnecting..." else "Connection failed"
        is Database -> "Failed to access local data"
        is NotFound -> resourceType?.let { "$it not found" } ?: "Resource not found"
        is Validation -> field?.let { "Invalid $it: $message" } ?: message
        is Unknown -> "Something went wrong"
    }

    /**
     * Determines if the error is recoverable (can retry)
     */
    fun isRecoverable(): Boolean = when (this) {
        is Network -> true
        is Auth -> false // Need to re-authenticate
        is Server -> true
        is Api -> false
        is WebSocket -> canReconnect
        is Database -> false
        is NotFound -> false
        is Validation -> false
        is Unknown -> false
    }

    /**
     * Returns suggested action for the error
     */
    fun getSuggestedAction(): ErrorAction = when (this) {
        is Network -> ErrorAction.Retry
        is Auth -> ErrorAction.ReAuthenticate
        is Server -> ErrorAction.Retry
        is Api -> ErrorAction.Dismiss
        is WebSocket -> if (canReconnect) ErrorAction.Reconnect else ErrorAction.Dismiss
        is Database -> ErrorAction.Dismiss
        is NotFound -> ErrorAction.GoBack
        is Validation -> ErrorAction.FixInput
        is Unknown -> ErrorAction.Dismiss
    }

    companion object {
        /**
         * Creates an AppError from a Throwable
         */
        fun from(throwable: Throwable): AppError = when (throwable) {
            is AppError -> throwable
            is UnknownHostException -> Network(
                message = "No internet connection",
                cause = throwable,
                isNoConnection = true
            )
            is SocketTimeoutException -> Network(
                message = "Connection timed out",
                cause = throwable,
                isTimeout = true
            )
            is IOException -> Network(
                message = throwable.message ?: "Network error",
                cause = throwable
            )
            else -> Unknown(
                message = throwable.message ?: "Unknown error",
                cause = throwable
            )
        }
    }
}

/**
 * Suggested actions for error recovery
 */
enum class ErrorAction {
    Retry,
    Reconnect,
    ReAuthenticate,
    GoBack,
    FixInput,
    Dismiss
}

/**
 * Extension to convert any Throwable to AppError
 */
fun Throwable.toAppError(): AppError = AppError.from(this)

/**
 * Exception wrapper for AppError to allow throwing
 */
class AppErrorException(val appError: AppError) : Exception(appError.message, appError.cause)
