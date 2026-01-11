package com.claudemonitor.domain.usecase.base

import com.claudemonitor.core.error.AppError
import com.claudemonitor.core.error.ErrorHandler
import com.claudemonitor.core.error.Resource
import com.claudemonitor.core.error.toAppError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Base class for use cases that return a single result.
 *
 * @param P Parameters type
 * @param R Result type
 */
abstract class UseCase<in P, out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Execute the use case logic.
     */
    protected abstract suspend fun execute(params: P): R

    /**
     * Invoke the use case and return Resource.
     */
    suspend operator fun invoke(params: P): Resource<R> {
        return try {
            withContext(dispatcher) {
                Resource.success(execute(params))
            }
        } catch (e: Exception) {
            Resource.error(e.toAppError())
        }
    }

    /**
     * Invoke with error handler for centralized error handling.
     */
    suspend fun invoke(
        params: P,
        errorHandler: ErrorHandler,
        context: String? = null
    ): Resource<R> {
        return try {
            withContext(dispatcher) {
                Resource.success(execute(params))
            }
        } catch (e: Exception) {
            val error = errorHandler.handle(e, context ?: this::class.simpleName)
            Resource.error(error)
        }
    }
}

/**
 * Use case without parameters.
 */
abstract class NoParamsUseCase<out R>(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : UseCase<Unit, R>(dispatcher) {

    suspend operator fun invoke(): Resource<R> = invoke(Unit)

    suspend fun invoke(
        errorHandler: ErrorHandler,
        context: String? = null
    ): Resource<R> = invoke(Unit, errorHandler, context)
}

/**
 * Base class for use cases that return a Flow.
 */
abstract class FlowUseCase<in P, out R>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Execute the use case logic as a Flow.
     */
    protected abstract fun execute(params: P): Flow<R>

    /**
     * Invoke the use case and return Flow<Resource<R>>.
     */
    operator fun invoke(params: P): Flow<Resource<R>> {
        return execute(params)
            .map<R, Resource<R>> { Resource.success(it) }
            .onStart { emit(Resource.loading()) }
            .catch { emit(Resource.error(it.toAppError())) }
            .flowOn(dispatcher)
    }

    /**
     * Invoke with error handler.
     */
    fun invoke(
        params: P,
        errorHandler: ErrorHandler,
        context: String? = null
    ): Flow<Resource<R>> {
        return execute(params)
            .map<R, Resource<R>> { Resource.success(it) }
            .onStart { emit(Resource.loading()) }
            .catch { e ->
                val error = errorHandler.handleSync(e, context ?: this::class.simpleName)
                emit(Resource.error(error))
            }
            .flowOn(dispatcher)
    }
}

/**
 * Flow use case without parameters.
 */
abstract class NoParamsFlowUseCase<out R>(
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FlowUseCase<Unit, R>(dispatcher) {

    operator fun invoke(): Flow<Resource<R>> = invoke(Unit)

    fun invoke(
        errorHandler: ErrorHandler,
        context: String? = null
    ): Flow<Resource<R>> = invoke(Unit, errorHandler, context)
}

/**
 * Combines multiple use cases into one result.
 */
suspend fun <R1, R2, R> combineUseCases(
    useCase1: suspend () -> Resource<R1>,
    useCase2: suspend () -> Resource<R2>,
    combine: (R1, R2) -> R
): Resource<R> {
    val result1 = useCase1()
    if (result1 is Resource.Error) return Resource.error(result1.error)

    val result2 = useCase2()
    if (result2 is Resource.Error) return Resource.error(result2.error)

    val data1 = result1.getOrNull()!!
    val data2 = result2.getOrNull()!!

    return Resource.success(combine(data1, data2))
}
