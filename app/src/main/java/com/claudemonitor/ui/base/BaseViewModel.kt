package com.claudemonitor.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudemonitor.core.error.AppError
import com.claudemonitor.core.error.ErrorHandler
import com.claudemonitor.core.error.Resource
import com.claudemonitor.core.error.toAppError
import com.claudemonitor.core.logging.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Base ViewModel with common functionality for error handling,
 * loading states, and coroutine management.
 */
abstract class BaseViewModel(
    protected val errorHandler: ErrorHandler,
    protected val logger: AppLogger? = null
) : ViewModel() {

    private val tag = this::class.java.simpleName

    // Generic loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Generic error state
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    // One-time events (navigation, snackbars, etc.)
    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    // Active jobs for cancellation
    private val activeJobs = mutableMapOf<String, Job>()

    /**
     * Exception handler that routes to ErrorHandler.
     */
    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val error = errorHandler.handleSync(throwable, tag)
        _error.value = error
        _isLoading.value = false
    }

    /**
     * Launch a coroutine with loading and error handling.
     */
    protected fun launchWithLoading(
        key: String? = null,
        showLoading: Boolean = true,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        // Cancel previous job with same key
        key?.let { activeJobs[it]?.cancel() }

        val job = viewModelScope.launch(exceptionHandler) {
            try {
                if (showLoading) _isLoading.value = true
                _error.value = null
                block()
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }

        key?.let { activeJobs[it] = job }
        return job
    }

    /**
     * Launch with custom loading state.
     */
    protected fun <T> launchWithState(
        loadingState: MutableStateFlow<Boolean>,
        errorState: MutableStateFlow<AppError?>,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch(exceptionHandler) {
            try {
                loadingState.value = true
                errorState.value = null
                block()
            } catch (e: Exception) {
                val error = errorHandler.handle(e, tag)
                errorState.value = error
            } finally {
                loadingState.value = false
            }
        }
    }

    /**
     * Execute a Resource-returning operation with state updates.
     */
    protected suspend fun <T> executeWithResource(
        resource: Resource<T>,
        onSuccess: (T) -> Unit = {},
        onError: (AppError) -> Unit = { _error.value = it }
    ) {
        when (resource) {
            is Resource.Loading -> _isLoading.value = true
            is Resource.Success -> {
                _isLoading.value = false
                onSuccess(resource.data)
            }
            is Resource.Error -> {
                _isLoading.value = false
                onError(resource.error)
            }
        }
    }

    /**
     * Collect a Flow<Resource<T>> with automatic state management.
     */
    protected fun <T> collectResource(
        flow: Flow<Resource<T>>,
        onSuccess: (T) -> Unit,
        onError: (AppError) -> Unit = { _error.value = it },
        onLoading: () -> Unit = { _isLoading.value = true }
    ): Job {
        return viewModelScope.launch {
            flow.collect { resource ->
                when (resource) {
                    is Resource.Loading -> onLoading()
                    is Resource.Success -> {
                        _isLoading.value = false
                        onSuccess(resource.data)
                    }
                    is Resource.Error -> {
                        _isLoading.value = false
                        onError(resource.error)
                    }
                }
            }
        }
    }

    /**
     * Clear current error.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Send a one-time UI event.
     */
    protected fun sendEvent(event: UiEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    /**
     * Log debug message.
     */
    protected fun logDebug(message: String) {
        logger?.d(tag, message)
    }

    /**
     * Log error.
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        logger?.e(tag, message, throwable)
    }

    override fun onCleared() {
        super.onCleared()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}

/**
 * Base class for one-time UI events.
 */
sealed class UiEvent {
    data class ShowSnackbar(val message: String, val action: String? = null) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    object NavigateBack : UiEvent()
    data class ShowDialog(val dialogType: String, val data: Any? = null) : UiEvent()
}

/**
 * Extension to safely collect events in a Composable.
 */
@androidx.compose.runtime.Composable
fun BaseViewModel.collectEvents(
    onEvent: (UiEvent) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        events.collect { event ->
            onEvent(event)
        }
    }
}
