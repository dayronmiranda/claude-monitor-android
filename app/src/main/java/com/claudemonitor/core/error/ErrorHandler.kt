package com.claudemonitor.core.error

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized error handler for the application.
 * Collects, processes, and distributes errors to observers.
 */
@Singleton
class ErrorHandler @Inject constructor() {

    companion object {
        private const val TAG = "ErrorHandler"
    }

    // Global error stream for app-wide error observation
    private val _errors = MutableSharedFlow<ErrorEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val errors: SharedFlow<ErrorEvent> = _errors.asSharedFlow()

    // Fatal errors that require immediate attention
    private val _fatalErrors = MutableSharedFlow<AppError>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val fatalErrors: SharedFlow<AppError> = _fatalErrors.asSharedFlow()

    /**
     * Handles an exception and converts it to AppError.
     * Emits the error to the global error stream.
     *
     * @param throwable The exception to handle
     * @param context Optional context about where the error occurred
     * @param silent If true, don't emit to global stream (for local handling)
     * @return The converted AppError
     */
    suspend fun handle(
        throwable: Throwable,
        context: String? = null,
        silent: Boolean = false
    ): AppError {
        val error = convertToAppError(throwable)

        // Log the error
        logError(error, context, throwable)

        // Emit to global stream unless silent
        if (!silent) {
            val event = ErrorEvent(
                error = error,
                context = context,
                timestamp = System.currentTimeMillis()
            )
            _errors.emit(event)
        }

        // Check for fatal errors
        if (isFatalError(error)) {
            _fatalErrors.emit(error)
        }

        return error
    }

    /**
     * Handles an error synchronously (non-suspend version).
     * Use this in callbacks or places where suspend isn't available.
     */
    fun handleSync(
        throwable: Throwable,
        context: String? = null
    ): AppError {
        val error = convertToAppError(throwable)
        logError(error, context, throwable)
        _errors.tryEmit(ErrorEvent(error, context, System.currentTimeMillis()))
        return error
    }

    /**
     * Creates a CoroutineExceptionHandler that uses this ErrorHandler.
     */
    fun asCoroutineExceptionHandler(context: String? = null): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            handleSync(throwable, context)
        }
    }

    /**
     * Converts a Throwable to the appropriate AppError type.
     */
    private fun convertToAppError(throwable: Throwable): AppError {
        return when (throwable) {
            // Already an AppError
            is AppError -> throwable

            // HTTP errors from Retrofit
            is HttpException -> handleHttpException(throwable)

            // Network errors
            is UnknownHostException -> AppError.Network(
                message = "Unable to reach server",
                cause = throwable,
                isNoConnection = true
            )
            is SocketTimeoutException -> AppError.Network(
                message = "Connection timed out",
                cause = throwable,
                isTimeout = true
            )
            is IOException -> AppError.Network(
                message = throwable.message ?: "Network error",
                cause = throwable
            )

            // Unknown errors
            else -> AppError.Unknown(
                message = throwable.message ?: "An unexpected error occurred",
                cause = throwable
            )
        }
    }

    /**
     * Handles HTTP exceptions from Retrofit.
     */
    private fun handleHttpException(exception: HttpException): AppError {
        val code = exception.code()
        val errorBody = try {
            exception.response()?.errorBody()?.string()
        } catch (e: Exception) {
            null
        }

        return when (code) {
            // Authentication errors
            401 -> AppError.Auth(
                message = "Invalid credentials",
                cause = exception,
                code = code
            )
            403 -> AppError.Auth(
                message = "Access denied",
                cause = exception,
                code = code
            )

            // Not found
            404 -> AppError.NotFound(
                message = errorBody ?: "Resource not found"
            )

            // Validation errors
            400, 422 -> AppError.Validation(
                message = errorBody ?: "Invalid request"
            )

            // Server errors
            in 500..599 -> AppError.Server(
                message = errorBody ?: "Server error",
                cause = exception,
                code = code
            )

            // Other errors
            else -> AppError.Api(
                message = errorBody ?: "Request failed with code $code",
                cause = exception,
                code = code.toString()
            )
        }
    }

    /**
     * Determines if an error is fatal (requires app-level handling).
     */
    private fun isFatalError(error: AppError): Boolean {
        return when (error) {
            is AppError.Auth -> error.code == 401 // Session expired
            is AppError.Database -> true // Database corruption
            else -> false
        }
    }

    /**
     * Logs the error with appropriate level.
     */
    private fun logError(error: AppError, context: String?, throwable: Throwable) {
        val contextStr = context?.let { "[$it] " } ?: ""
        val message = "$contextStr${error.toUserMessage()}"

        when (error) {
            is AppError.Network -> Log.w(TAG, message, throwable)
            is AppError.Auth -> Log.w(TAG, message)
            is AppError.Server -> Log.e(TAG, message, throwable)
            is AppError.Unknown -> Log.e(TAG, message, throwable)
            else -> Log.d(TAG, message)
        }

        // Here you could add Crashlytics/Analytics reporting
        // Firebase.crashlytics.recordException(throwable)
    }
}

/**
 * Represents an error event with metadata.
 */
data class ErrorEvent(
    val error: AppError,
    val context: String?,
    val timestamp: Long
) {
    fun isRecent(withinMs: Long = 5000): Boolean {
        return System.currentTimeMillis() - timestamp < withinMs
    }
}

/**
 * Extension to safely execute a block and handle errors.
 * Returns a Resource instead of Result to properly type errors.
 */
suspend inline fun <T> ErrorHandler.runCatching(
    context: String? = null,
    silent: Boolean = false,
    block: () -> T
): Resource<T> {
    return try {
        Resource.success(block())
    } catch (e: Exception) {
        val error = handle(e, context, silent)
        Resource.error(error)
    }
}
