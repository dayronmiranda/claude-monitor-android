package com.claudemonitor.ui.base

import com.claudemonitor.core.error.AppError

/**
 * Base interface for all UI states.
 * Provides common properties for loading, error, and empty states.
 */
interface BaseUiState {
    val isLoading: Boolean
    val error: AppError?
    val isEmpty: Boolean get() = false
}

/**
 * Generic UI state for list screens.
 */
data class ListUiState<T>(
    val items: List<T> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null,
    val isRefreshing: Boolean = false
) : BaseUiState {
    override val isEmpty: Boolean get() = items.isEmpty() && !isLoading
}

/**
 * Generic UI state for detail screens.
 */
data class DetailUiState<T>(
    val data: T? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : BaseUiState {
    override val isEmpty: Boolean get() = data == null && !isLoading
}

/**
 * State for forms with validation.
 */
data class FormUiState(
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    override val error: AppError? = null
) : BaseUiState {
    override val isLoading: Boolean get() = isSubmitting

    fun hasFieldError(field: String): Boolean = fieldErrors.containsKey(field)
    fun getFieldError(field: String): String? = fieldErrors[field]
}

/**
 * Represents the current content state.
 */
sealed class ContentState<out T> {
    object Loading : ContentState<Nothing>()
    data class Success<T>(val data: T) : ContentState<T>()
    data class Error(val error: AppError) : ContentState<Nothing>()
    object Empty : ContentState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isEmpty: Boolean get() = this is Empty

    fun getOrNull(): T? = (this as? Success)?.data

    fun <R> map(transform: (T) -> R): ContentState<R> = when (this) {
        is Loading -> Loading
        is Success -> Success(transform(data))
        is Error -> Error(error)
        is Empty -> Empty
    }

    companion object {
        fun <T> fromList(list: List<T>, isLoading: Boolean = false): ContentState<List<T>> {
            return when {
                isLoading -> Loading
                list.isEmpty() -> Empty
                else -> Success(list)
            }
        }

        fun <T> fromNullable(data: T?, isLoading: Boolean = false): ContentState<T> {
            return when {
                isLoading -> Loading
                data == null -> Empty
                else -> Success(data)
            }
        }
    }
}

/**
 * Extension to convert Resource to ContentState.
 */
fun <T> com.claudemonitor.core.error.Resource<T>.toContentState(): ContentState<T> = when (this) {
    is com.claudemonitor.core.error.Resource.Loading -> ContentState.Loading
    is com.claudemonitor.core.error.Resource.Success -> ContentState.Success(data)
    is com.claudemonitor.core.error.Resource.Error -> ContentState.Error(error)
}

/**
 * Extension to convert Resource<List<T>> to ContentState with empty handling.
 */
fun <T> com.claudemonitor.core.error.Resource<List<T>>.toListContentState(): ContentState<List<T>> = when (this) {
    is com.claudemonitor.core.error.Resource.Loading -> ContentState.Loading
    is com.claudemonitor.core.error.Resource.Success -> {
        if (data.isEmpty()) ContentState.Empty else ContentState.Success(data)
    }
    is com.claudemonitor.core.error.Resource.Error -> ContentState.Error(error)
}
