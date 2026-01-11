package com.claudemonitor.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Driver(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val createdAt: Long = System.currentTimeMillis(),
    val apiToken: String? = null
)

enum class DriverStatus {
    ONLINE,
    OFFLINE,
    CONNECTING,
    ERROR
}
