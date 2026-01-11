package com.claudemonitor.core.error

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A wrapper class that represents a resource state:
 * Loading, Success, or Error.
 *
 * This provides a consistent way to handle async operations
 * across the app with proper error handling.
 */
sealed class Resource<out T> {

    /**
     * Loading state with optional progress indicator
     */
    data class Loading<T>(
        val progress: Float? = null,
        val message: String? = null
    ) : Resource<T>()

    /**
     * Success state with data
     */
    data class Success<T>(val data: T) : Resource<T>()

    /**
     * Error state with AppError
     */
    data class Error<T>(
        val error: AppError,
        val cachedData: T? = null // Optional cached data to show while error
    ) : Resource<T>()

    /**
     * Returns true if this is a Loading state
     */
    val isLoading: Boolean get() = this is Loading

    /**
     * Returns true if this is a Success state
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns true if this is an Error state
     */
    val isError: Boolean get() = this is Error

    /**
     * Returns the data if Success, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> cachedData
        is Loading -> null
    }

    /**
     * Returns the data if Success, throws if Error
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw AppErrorException(error)
        is Loading -> throw IllegalStateException("Resource is still loading")
    }

    /**
     * Returns the data if Success, default value otherwise
     */
    fun getOrDefault(default: @UnsafeVariance T): T = getOrNull() ?: default

    /**
     * Returns the error if Error state, null otherwise
     */
    fun errorOrNull(): AppError? = (this as? Error)?.error

    /**
     * Maps the success data to a new type
     */
    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Loading -> Loading(progress, message)
        is Success -> Success(transform(data))
        is Error -> Error(error, cachedData?.let(transform))
    }

    /**
     * Flat maps the success data
     */
    suspend fun <R> flatMap(transform: suspend (T) -> Resource<R>): Resource<R> = when (this) {
        is Loading -> Loading(progress, message)
        is Success -> transform(data)
        is Error -> Error(error)
    }

    /**
     * Executes the given block if Success
     */
    inline fun onSuccess(action: (T) -> Unit): Resource<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Executes the given block if Error
     */
    inline fun onError(action: (AppError) -> Unit): Resource<T> {
        if (this is Error) action(error)
        return this
    }

    /**
     * Executes the given block if Loading
     */
    inline fun onLoading(action: (Float?) -> Unit): Resource<T> {
        if (this is Loading) action(progress)
        return this
    }

    /**
     * Folds the resource into a single value
     */
    inline fun <R> fold(
        onLoading: (Float?) -> R,
        onSuccess: (T) -> R,
        onError: (AppError) -> R
    ): R = when (this) {
        is Loading -> onLoading(progress)
        is Success -> onSuccess(data)
        is Error -> onError(error)
    }

    companion object {
        /**
         * Creates a Loading resource
         */
        fun <T> loading(progress: Float? = null, message: String? = null): Resource<T> =
            Loading(progress, message)

        /**
         * Creates a Success resource
         */
        fun <T> success(data: T): Resource<T> = Success(data)

        /**
         * Creates an Error resource
         */
        fun <T> error(error: AppError, cachedData: T? = null): Resource<T> =
            Error(error, cachedData)

        /**
         * Creates an Error resource from a Throwable
         */
        fun <T> error(throwable: Throwable, cachedData: T? = null): Resource<T> =
            Error(throwable.toAppError(), cachedData)
    }
}

/**
 * Converts a Flow<T> to Flow<Resource<T>> with loading and error states
 */
fun <T> Flow<T>.asResource(): Flow<Resource<T>> = this
    .map<T, Resource<T>> { Resource.success(it) }
    .onStart { emit(Resource.loading()) }
    .catch { emit(Resource.error(it)) }

/**
 * Converts a Flow<T> to Flow<Resource<T>> with custom error handler
 */
fun <T> Flow<T>.asResource(
    errorHandler: ErrorHandler,
    context: String? = null
): Flow<Resource<T>> = this
    .map<T, Resource<T>> { Resource.success(it) }
    .onStart { emit(Resource.loading()) }
    .catch { e ->
        val error = errorHandler.handle(e, context, silent = true)
        emit(Resource.error(error))
    }

/**
 * Executes a suspend block and wraps result in Resource
 */
suspend fun <T> resourceOf(block: suspend () -> T): Resource<T> = try {
    Resource.success(block())
} catch (e: Exception) {
    Resource.error(e)
}

/**
 * Executes a suspend block with error handler
 */
suspend fun <T> resourceOf(
    errorHandler: ErrorHandler,
    context: String? = null,
    block: suspend () -> T
): Resource<T> = try {
    Resource.success(block())
} catch (e: Exception) {
    val error = errorHandler.handle(e, context, silent = true)
    Resource.error(error)
}

/**
 * Combines two Resources into one
 */
fun <T1, T2, R> combineResources(
    r1: Resource<T1>,
    r2: Resource<T2>,
    combine: (T1, T2) -> R
): Resource<R> {
    // If any is loading, result is loading
    if (r1 is Resource.Loading || r2 is Resource.Loading) {
        return Resource.loading()
    }

    // If any is error, return first error
    val error1 = r1.errorOrNull()
    if (error1 != null) return Resource.error(error1)

    val error2 = r2.errorOrNull()
    if (error2 != null) return Resource.error(error2)

    // Both are success
    val data1 = r1.getOrNull()!!
    val data2 = r2.getOrNull()!!
    return Resource.success(combine(data1, data2))
}
