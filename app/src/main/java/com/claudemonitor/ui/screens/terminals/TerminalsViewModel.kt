package com.claudemonitor.ui.screens.terminals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.Terminal
import com.claudemonitor.data.repository.DriverRepository
import com.claudemonitor.data.repository.TerminalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalsUiState(
    val driver: Driver? = null,
    val terminals: List<Terminal> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val creatingTerminal: Boolean = false
)

@HiltViewModel
class TerminalsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val driverRepository: DriverRepository,
    private val terminalRepository: TerminalRepository
) : ViewModel() {

    private val driverId: String = savedStateHandle["driverId"] ?: ""

    private val _uiState = MutableStateFlow(TerminalsUiState(isLoading = true))
    val uiState: StateFlow<TerminalsUiState> = _uiState.asStateFlow()

    private val _terminalCreated = MutableSharedFlow<String>()
    val terminalCreated: SharedFlow<String> = _terminalCreated.asSharedFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val driver = driverRepository.getDriver(driverId)
                if (driver == null) {
                    _uiState.update { it.copy(error = "Driver not found", isLoading = false) }
                    return@launch
                }

                _uiState.update { it.copy(driver = driver) }

                terminalRepository.getTerminals(driver)
                    .onSuccess { terminals ->
                        _uiState.update { it.copy(terminals = terminals, isLoading = false) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun createTerminal(workDir: String, type: String) {
        val driver = _uiState.value.driver ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(creatingTerminal = true) }

            terminalRepository.createTerminal(
                driver = driver,
                workDir = workDir,
                type = type
            ).onSuccess { terminal ->
                _uiState.update { it.copy(
                    creatingTerminal = false,
                    showCreateDialog = false
                )}
                _terminalCreated.emit(terminal.id)
            }.onFailure { e ->
                _uiState.update { it.copy(
                    error = e.message,
                    creatingTerminal = false
                )}
            }
        }
    }

    fun killTerminal(terminalId: String) {
        val driver = _uiState.value.driver ?: return

        viewModelScope.launch {
            terminalRepository.killTerminal(driver, terminalId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(terminals = state.terminals.filter { it.id != terminalId })
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
