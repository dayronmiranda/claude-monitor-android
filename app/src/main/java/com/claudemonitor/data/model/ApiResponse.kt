package com.claudemonitor.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String? = null,
    val uptime: String? = null,
    val checks: JsonElement? = null,
    val stats: JsonElement? = null
)
