package com.claudemonitor.domain.usecase.driver

import com.claudemonitor.core.error.AppError
import com.claudemonitor.core.error.AppErrorException
import com.claudemonitor.core.error.Resource
import com.claudemonitor.core.network.ConnectivityObserver
import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.DriverStatus
import com.claudemonitor.data.repository.DriverRepository
import com.claudemonitor.domain.usecase.base.FlowUseCase
import com.claudemonitor.domain.usecase.base.NoParamsFlowUseCase
import com.claudemonitor.domain.usecase.base.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Get all drivers as a Flow.
 */
class GetDriversUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) : NoParamsFlowUseCase<List<Driver>>() {

    override fun execute(params: Unit): Flow<List<Driver>> {
        return driverRepository.drivers
    }
}

/**
 * Get a single driver by ID.
 */
class GetDriverUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) : UseCase<String, Driver>() {

    override suspend fun execute(params: String): Driver {
        return driverRepository.getDriver(params)
            ?: throw AppErrorException(AppError.NotFound(
                message = "Driver not found",
                resourceType = "Driver",
                resourceId = params
            ))
    }
}

/**
 * Parameters for adding a driver.
 */
data class AddDriverParams(
    val name: String,
    val url: String,
    val username: String,
    val password: String
)

/**
 * Add a new driver with validation.
 */
class AddDriverUseCase @Inject constructor(
    private val driverRepository: DriverRepository,
    private val connectivityObserver: ConnectivityObserver
) : UseCase<AddDriverParams, Driver>() {

    override suspend fun execute(params: AddDriverParams): Driver {
        // Validate inputs
        validate(params)

        // Check connectivity
        if (!connectivityObserver.isCurrentlyConnected()) {
            throw AppErrorException(AppError.Network(
                message = "No internet connection",
                isNoConnection = true
            ))
        }

        // Add driver
        val driver = driverRepository.addDriver(
            name = params.name,
            url = params.url,
            username = params.username,
            password = params.password
        )

        // Test connection
        val status = driverRepository.checkConnection(driver)
        if (status == DriverStatus.OFFLINE) {
            // Driver added but couldn't connect - still return success with warning
            // The UI can show a warning that connection couldn't be verified
        }

        return driver
    }

    private fun validate(params: AddDriverParams) {
        if (params.name.isBlank()) {
            throw AppErrorException(AppError.Validation(
                message = "Name is required",
                field = "name"
            ))
        }
        if (params.url.isBlank()) {
            throw AppErrorException(AppError.Validation(
                message = "URL is required",
                field = "url"
            ))
        }
        if (!params.url.startsWith("http://") && !params.url.startsWith("https://")) {
            throw AppErrorException(AppError.Validation(
                message = "URL must start with http:// or https://",
                field = "url"
            ))
        }
        if (params.password.isBlank()) {
            throw AppErrorException(AppError.Validation(
                message = "Password is required",
                field = "password"
            ))
        }
    }
}

/**
 * Check connection status for a driver.
 */
class CheckDriverConnectionUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) : UseCase<Driver, DriverStatus>() {

    override suspend fun execute(params: Driver): DriverStatus {
        return driverRepository.checkConnection(params)
    }
}

/**
 * Delete a driver.
 */
class DeleteDriverUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) : UseCase<String, Unit>() {

    override suspend fun execute(params: String) {
        // Check if driver exists
        driverRepository.getDriver(params)
            ?: throw AppErrorException(AppError.NotFound(
                message = "Driver not found",
                resourceType = "Driver",
                resourceId = params
            ))

        driverRepository.deleteDriver(params)
    }
}

/**
 * Check all drivers' connection status.
 */
data class DriverWithStatus(
    val driver: Driver,
    val status: DriverStatus
)

class CheckAllDriversConnectionUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) : NoParamsFlowUseCase<List<DriverWithStatus>>() {

    override fun execute(params: Unit): Flow<List<DriverWithStatus>> {
        return kotlinx.coroutines.flow.flow {
            val drivers = driverRepository.drivers.first()
            val results = drivers.map { driver ->
                val status = driverRepository.checkConnection(driver)
                DriverWithStatus(driver, status)
            }
            emit(results)
        }
    }
}
