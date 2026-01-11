package com.claudemonitor.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED
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
                        _uiState.update { it.copy(error = message.message) }
                    }
                    is WsOutputMessage.Closed -> {
                        _uiState.update { it.copy(error = "Session closed") }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadAndConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val driver = driverRepository.getDriver(driverId)
                if (driver == null) {
                    _uiState.update { it.copy(error = "Driver not found", isLoading = false) }
                    return@launch
                }

                _uiState.update { it.copy(driver = driver) }

                terminalRepository.getTerminal(driver, terminalId)
                    .onSuccess { terminal ->
                        _uiState.update { it.copy(terminal = terminal, isLoading = false) }
                        connect(driver)
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
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
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketService.disconnect()
    }
}
