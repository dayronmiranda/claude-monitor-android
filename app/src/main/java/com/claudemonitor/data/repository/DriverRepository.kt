package com.claudemonitor.data.repository

import com.claudemonitor.core.error.AppError
import com.claudemonitor.core.error.ErrorHandler
import com.claudemonitor.core.error.Resource
import com.claudemonitor.core.error.toAppError
import com.claudemonitor.data.api.ApiService
import com.claudemonitor.data.api.AuthInterceptor
import com.claudemonitor.data.local.DriverDao
import com.claudemonitor.data.local.DriverEntity
import com.claudemonitor.data.model.Driver
import com.claudemonitor.data.model.DriverStatus
import com.claudemonitor.data.model.HostInfo
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class DriverRepository @Inject constructor(
    private val driverDao: DriverDao,
    private val errorHandler: ErrorHandler
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val apiClients = mutableMapOf<String, ApiService>()

    val drivers: Flow<List<Driver>> = driverDao.getAllDrivers()
        .map { entities -> entities.map { it.toDriver() } }

    suspend fun addDriver(name: String, url: String, username: String, password: String): Driver {
        val trimmedUrl = url.trimEnd('/')

        // Validate URL format
        val validationError = validateDriverUrl(trimmedUrl)
        if (validationError != null) {
            Log.e("DriverRepository", "Invalid driver URL: $validationError")
            throw IllegalArgumentException("Invalid server URL: $validationError")
        }

        val driver = Driver(
            id = UUID.randomUUID().toString(),
            name = name,
            url = trimmedUrl,
            username = username,
            password = password
        )
        driverDao.insertDriver(DriverEntity.fromDriver(driver))
        Log.d("DriverRepository", "Driver added: $name -> $trimmedUrl")
        return driver
    }

    suspend fun updateDriver(driver: Driver) {
        driverDao.updateDriver(DriverEntity.fromDriver(driver))
        apiClients.remove(driver.id) // Clear cached client
    }

    suspend fun deleteDriver(driverId: String) {
        driverDao.deleteDriverById(driverId)
        apiClients.remove(driverId)
    }

    suspend fun getDriver(driverId: String): Driver? {
        return driverDao.getDriverById(driverId)?.toDriver()
    }

    fun getApiService(driver: Driver): ApiService {
        return apiClients.getOrPut(driver.id) {
            createApiService(driver)
        }
    }

    private fun createApiService(driver: Driver): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(driver.username, driver.password))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(driver.url + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(ApiService::class.java)
    }

    suspend fun checkConnection(driver: Driver): DriverStatus {
        return try {
            val api = getApiService(driver)
            val response = api.getHealth()
            if (response.status == "ok") {
                DriverStatus.ONLINE
            } else {
                DriverStatus.ERROR
            }
        } catch (e: Exception) {
            // Log error but don't emit to global stream (silent)
            errorHandler.handle(e, "checkConnection:${driver.name}", silent = true)
            DriverStatus.OFFLINE
        }
    }

    suspend fun getHostInfo(driver: Driver): Resource<HostInfo> {
        return try {
            val api = getApiService(driver)
            val response = api.getHost()
            if (response.data != null) {
                Resource.success(response.data)
            } else {
                Resource.error(AppError.Api(
                    message = response.error?.message ?: "Failed to get host info"
                ))
            }
        } catch (e: Exception) {
            val error = errorHandler.handle(e, "getHostInfo:${driver.name}")
            Resource.error(error)
        }
    }

    suspend fun addDriverWithValidation(
        name: String,
        url: String,
        username: String,
        password: String
    ): Resource<Driver> {
        // Validate inputs
        if (name.isBlank()) {
            return Resource.error(AppError.Validation(
                message = "Name is required",
                field = "name"
            ))
        }
        if (url.isBlank()) {
            return Resource.error(AppError.Validation(
                message = "URL is required",
                field = "url"
            ))
        }
        if (password.isBlank()) {
            return Resource.error(AppError.Validation(
                message = "Password is required",
                field = "password"
            ))
        }

        return try {
            val driver = addDriver(name, url, username, password)

            // Test connection immediately
            val status = checkConnection(driver)
            if (status == DriverStatus.OFFLINE) {
                // Still save the driver but warn
                Resource.Success(driver)
            } else {
                Resource.success(driver)
            }
        } catch (e: Exception) {
            val error = errorHandler.handle(e, "addDriver")
            Resource.error(error)
        }
    }

    /**
     * Validates driver URL format and basic connectivity requirements
     * Returns null if valid, or an error message if invalid
     */
    private fun validateDriverUrl(url: String): String? {
        // Check if URL is blank
        if (url.isBlank()) {
            return "URL cannot be empty"
        }

        // Check if URL has proper scheme
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "URL must start with http:// or https://"
        }

        // Try to parse URL
        return try {
            val parsedUrl = URL(url)

            // Check host
            if (parsedUrl.host.isNullOrBlank()) {
                return "URL must have a valid host"
            }

            // Check port if specified
            if (parsedUrl.port != -1) {
                if (parsedUrl.port < 1 || parsedUrl.port > 65535) {
                    return "Port must be between 1 and 65535, got ${parsedUrl.port}"
                }
            }

            // Log successful validation
            Log.d("DriverRepository", "URL validation passed: host=${parsedUrl.host}, port=${parsedUrl.port}")
            null // Valid URL
        } catch (e: Exception) {
            Log.e("DriverRepository", "URL validation failed: ${e.message}", e)
            "Invalid URL format: ${e.message}"
        }
    }
}
