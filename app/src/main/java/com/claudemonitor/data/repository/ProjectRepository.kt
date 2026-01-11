package com.claudemonitor.data.repository

import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.Project
import com.claudemonitor.data.model.Session
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend fun getProjects(driver: Driver): Result<List<Project>> {
        return try {
            val api = driverRepository.getApiService(driver)
            val response = api.getProjects()
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to get projects"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProject(driver: Driver, projectPath: String): Result<Project> {
        return try {
            val api = driverRepository.getApiService(driver)
            val encodedPath = URLEncoder.encode(projectPath, "UTF-8")
            val response = api.getProject(encodedPath)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to get project"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessions(driver: Driver, projectPath: String): Result<List<Session>> {
        return try {
            val api = driverRepository.getApiService(driver)
            val encodedPath = URLEncoder.encode(projectPath, "UTF-8")
            val response = api.getSessions(encodedPath)
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to get sessions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameSession(
        driver: Driver,
        projectPath: String,
        sessionId: String,
        name: String
    ): Result<Session> {
        return try {
            val api = driverRepository.getApiService(driver)
            val encodedPath = URLEncoder.encode(projectPath, "UTF-8")
            val response = api.renameSession(
                encodedPath,
                sessionId,
                com.claudemonitor.data.model.RenameRequest(name)
            )
            if (response.success && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to rename session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSession(driver: Driver, projectPath: String, sessionId: String): Result<Unit> {
        return try {
            val api = driverRepository.getApiService(driver)
            val encodedPath = URLEncoder.encode(projectPath, "UTF-8")
            val response = api.deleteSession(encodedPath, sessionId)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error?.message ?: "Failed to delete session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
