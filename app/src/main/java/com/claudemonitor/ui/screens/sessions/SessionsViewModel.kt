package com.claudemonitor.ui.screens.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.Session
import com.claudemonitor.data.model.Terminal
import com.claudemonitor.data.repository.DriverRepository
import com.claudemonitor.data.repository.ProjectRepository
import com.claudemonitor.data.repository.TerminalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class SessionsUiState(
    val driver: Driver? = null,
    val projectPath: String = "",
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val editingSessionId: String? = null,
    val creatingTerminal: Boolean = false
)

@HiltViewModel
class SessionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val driverRepository: DriverRepository,
    private val projectRepository: ProjectRepository,
    private val terminalRepository: TerminalRepository
) : ViewModel() {

    private val driverId: String = savedStateHandle["driverId"] ?: ""
    private val projectPath: String = URLDecoder.decode(
        savedStateHandle["projectPath"] ?: "",
        "UTF-8"
    )

    private val _uiState = MutableStateFlow(SessionsUiState(isLoading = true, projectPath = projectPath))
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

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

                projectRepository.getSessions(driver, projectPath)
                    .onSuccess { sessions ->
                        _uiState.update { it.copy(sessions = sessions, isLoading = false) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun startEditing(sessionId: String) {
        _uiState.update { it.copy(editingSessionId = sessionId) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingSessionId = null) }
    }

    fun renameSession(sessionId: String, newName: String) {
        val driver = _uiState.value.driver ?: return

        viewModelScope.launch {
            projectRepository.renameSession(driver, projectPath, sessionId, newName)
                .onSuccess { updatedSession ->
                    _uiState.update { state ->
                        state.copy(
                            sessions = state.sessions.map {
                                if (it.id == sessionId) updatedSession else it
                            },
                            editingSessionId = null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, editingSessionId = null) }
                }
        }
    }

    fun deleteSession(sessionId: String) {
        val driver = _uiState.value.driver ?: return

        viewModelScope.launch {
            projectRepository.deleteSession(driver, projectPath, sessionId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(sessions = state.sessions.filter { it.id != sessionId })
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun resumeSession(session: Session) {
        val driver = _uiState.value.driver ?: return
        val workDir = session.realPath ?: projectPath

        viewModelScope.launch {
            _uiState.update { it.copy(creatingTerminal = true) }

            terminalRepository.createTerminal(
                driver = driver,
                workDir = workDir,
                type = "claude",
                sessionId = session.id,
                resume = true
            ).onSuccess { terminal ->
                _uiState.update { it.copy(creatingTerminal = false) }
                _terminalCreated.emit(terminal.id)
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, creatingTerminal = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
