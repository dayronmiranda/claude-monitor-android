package com.claudemonitor.ui.screens.drivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudemonitor.core.error.AppError
import com.claudemonitor.core.error.ErrorHandler
import com.claudemonitor.core.error.Resource
import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.DriverStatus
import com.claudemonitor.data.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverWithStatus(
    val driver: Driver,
    val status: DriverStatus = DriverStatus.OFFLINE
)

data class DriversUiState(
    val drivers: List<DriverWithStatus> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val showAddDialog: Boolean = false,
    val isAddingDriver: Boolean = false
)

@HiltViewModel
class DriversViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriversUiState())
    val uiState: StateFlow<DriversUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            driverRepository.drivers.collect { drivers ->
                _uiState.update { state ->
                    state.copy(
                        drivers = drivers.map { DriverWithStatus(it) }
                    )
                }
                // Check connection status for each driver
                drivers.forEach { driver ->
                    checkDriverStatus(driver)
                }
            }
        }
    }

    private fun checkDriverStatus(driver: Driver) {
        viewModelScope.launch {
            val status = driverRepository.checkConnection(driver)
            _uiState.update { state ->
                state.copy(
                    drivers = state.drivers.map {
                        if (it.driver.id == driver.id) {
                            it.copy(status = status)
                        } else it
                    }
                )
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun addDriver(name: String, url: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingDriver = true, error = null) }

            when (val result = driverRepository.addDriverWithValidation(name, url, username, password)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            showAddDialog = false,
                            isAddingDriver = false
                        )
                    }
                    // Check connection status for the new driver
                    checkDriverStatus(result.data)
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            error = result.error,
                            isAddingDriver = false
                        )
                    }
                }
                is Resource.Loading -> {
                    // Already handled above
                }
            }
        }
    }

    fun deleteDriver(driverId: String) {
        viewModelScope.launch {
            try {
                driverRepository.deleteDriver(driverId)
            } catch (e: Exception) {
                val error = errorHandler.handle(e, "deleteDriver")
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _uiState.value.drivers.forEach { dws ->
                checkDriverStatus(dws.driver)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retryLastAction() {
        // Clear error and let user try again
        clearError()
    }
}
