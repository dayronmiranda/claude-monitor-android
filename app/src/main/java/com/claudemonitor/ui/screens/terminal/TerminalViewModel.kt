package com.claudemonitor.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudemonitor.core.error.AppError
import com.claudemonitor.data.api.ConnectionState
import com.claudemonitor.data.api.WebSocketService
import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.Terminal
import com.claudemonitor.data.model.WsOutputMessage
import com.claudemonitor.data.repository.DriverRepository
import com.claudemonitor.data.repository.TerminalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalUiState(
    val driver: Driver? = null,
    val terminal: Terminal? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val showResumeOption: Boolean = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val driverRepository: DriverRepository,
    private val terminalRepository: TerminalRepository,
    private val webSocketService: WebSocketService
) : ViewModel() {

    private val driverId: String = savedStateHandle["driverId"] ?: ""
    private val terminalId: String = savedStateHandle["terminalId"] ?: ""

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    val buffer: StateFlow<String> = webSocketService.buffer
    val connectionState: StateFlow<ConnectionState> = webSocketService.connectionState

    init {
        loadAndConnect()

        // Observe connection state
        viewModelScope.launch {
            webSocketService.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }

        // Observe messages for errors
        viewModelScope.launch {
            webSocketService.messages.collect { message ->
                when (message) {
                    is WsOutputMessage.Error -> {
                        val appError = AppError.WebSocket(
                            message = message.message,
                            canReconnect = !message.message.contains("Terminal not found") &&
                                           !message.message.contains("Authentication failed") &&
                                           !message.message.contains("Access denied")
                        )
                        _uiState.update { it.copy(error = appError) }

                        // Clear resume option on connection errors (not on terminal not found)
                        if (!message.message.contains("Terminal not found")) {
                            _uiState.update { it.copy(showResumeOption = false) }
                        }
                    }
                    is WsOutputMessage.Closed -> {
                        val appError = AppError.WebSocket(
                            message = "Terminal session closed",
                            canReconnect = true
                        )
                        _uiState.update { it.copy(error = appError, showResumeOption = false) }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadAndConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, showResumeOption = false) }

            try {
                val driver = driverRepository.getDriver(driverId)
                if (driver == null) {
                    _uiState.update { it.copy(error = AppError.NotFound(message = "Driver not found"), isLoading = false) }
                    return@launch
                }

                _uiState.update { it.copy(driver = driver) }

                terminalRepository.getTerminal(driver, terminalId)
                    .onSuccess { terminal ->
                        _uiState.update { it.copy(terminal = terminal, isLoading = false) }

                        // Check terminal status before connecting
                        when (terminal.status) {
                            "running" -> {
                                connect(driver)
                            }
                            "stopped" -> {
                                if (terminal.canResume) {
                                    _uiState.update {
                                        it.copy(
                                            error = AppError.Api(message = "Terminal is stopped. You can resume it to continue."),
                                            showResumeOption = true
                                        )
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(error = AppError.Api(message = "Terminal has stopped and cannot be resumed."))
                                    }
                                }
                            }
                            else -> {
                                _uiState.update {
                                    it.copy(error = AppError.Api(message = "Unknown terminal status: ${terminal.status}"))
                                }
                            }
                        }
                    }
                    .onFailure { e ->
                        val appError = if (e is AppError) e else AppError.Api(message = e.message ?: "Failed to load terminal", cause = e)
                        _uiState.update { it.copy(error = appError, isLoading = false) }
                    }
            } catch (e: Exception) {
                val appError = AppError.Unknown(message = e.message ?: "Unknown error", cause = e)
                _uiState.update { it.copy(error = appError, isLoading = false) }
            }
        }
    }

    private fun connect(driver: Driver) {
        webSocketService.connect(
            baseUrl = driver.url,
            terminalId = terminalId,
            username = driver.username,
            password = driver.password,
            apiToken = driver.apiToken
        )
    }

    fun resumeTerminal() {
        val driver = _uiState.value.driver ?: return
        val terminal = _uiState.value.terminal ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, showResumeOption = false) }

            terminalRepository.resumeTerminal(driver, terminal.id)
                .onSuccess { newTerminal ->
                    _uiState.update { it.copy(terminal = newTerminal, isLoading = false) }
                    connect(driver)
                }
                .onFailure { e ->
                    val appError = if (e is AppError) e else AppError.Api(message = e.message ?: "Failed to resume terminal", cause = e)
                    _uiState.update { it.copy(error = appError, isLoading = false) }
                }
        }
    }

    fun send(data: String) {
        webSocketService.send(data)
    }

    fun resize(cols: Int, rows: Int) {
        webSocketService.resize(cols, rows)
    }

    fun reconnect() {
        val driver = _uiState.value.driver ?: return
        webSocketService.disconnect()
        webSocketService.clearBuffer()
        connect(driver)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, showResumeOption = false) }
    }

    fun clearBuffer() {
        webSocketService.clearBuffer()
    }

    fun killTerminal() {
        val driver = _uiState.value.driver ?: return
        val terminal = _uiState.value.terminal ?: return

        viewModelScope.launch {
            terminalRepository.killTerminal(driver, terminal.id)
                .onSuccess {
                    _uiState.update { it.copy(error = AppError.Api(message = "Terminal stopped")) }
                    webSocketService.disconnect()
                }
                .onFailure { e ->
                    val appError = if (e is AppError) e else AppError.Api(message = e.message ?: "Failed to stop terminal", cause = e)
                    _uiState.update { it.copy(error = appError) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketService.disconnect()
    }
}
