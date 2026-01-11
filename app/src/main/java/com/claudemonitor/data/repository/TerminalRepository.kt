package com.claudemonitor.data.repository

import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.Terminal
import com.claudemonitor.data.model.TerminalConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TerminalRepository @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend fun getTerminals(driver: Driver): Result<List<Terminal>> {
        return try {
            val api = driverRepository.getApiService(driver)
            val response = api.getTerminals()
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to get terminals"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTerminal(driver: Driver, terminalId: String): Result<Terminal> {
        return try {
            val api = driverRepository.getApiService(driver)
            val response = api.getTerminal(terminalId)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to get terminal"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTerminal(
        driver: Driver,
        workDir: String,
        type: String = "claude",
        name: String? = null,
        sessionId: String? = null,
        resume: Boolean = false
    ): Result<Terminal> {
        return try {
            val api = driverRepository.getApiService(driver)
            val config = TerminalConfig(
                workDir = workDir,
                type = type,
                name = name,
                sessionId = sessionId,
                resume = resume
            )
            val response = api.createTerminal(config)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to create terminal"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun killTerminal(driver: Driver, terminalId: String): Result<Unit> {
        return try {
            val api = driverRepository.getApiService(driver)
            val response = api.killTerminal(terminalId)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to kill terminal"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
