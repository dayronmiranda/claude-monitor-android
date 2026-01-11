package com.claudemonitor.ui.screens.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.HostInfo
import com.claudemonitor.data.model.Project
import com.claudemonitor.data.repository.DriverRepository
import com.claudemonitor.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val driver: Driver? = null,
    val hostInfo: HostInfo? = null,
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val driverRepository: DriverRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val driverId: String = savedStateHandle["driverId"] ?: ""

    private val _uiState = MutableStateFlow(ProjectsUiState(isLoading = true))
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

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

                // Load host info
                val hostInfoResult = driverRepository.getHostInfo(driver)
                hostInfoResult.onSuccess { hostInfo ->
                    _uiState.update { it.copy(hostInfo = hostInfo) }
                }

                // Load projects
                projectRepository.getProjects(driver)
                    .onSuccess { projects ->
                        _uiState.update { it.copy(projects = projects, isLoading = false) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = e.message, isLoading = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
