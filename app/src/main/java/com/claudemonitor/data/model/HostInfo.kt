package com.claudemonitor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val id: String,
    val name: String,
    val version: String,
    val platform: String,
    val uptime: String,
    val stats: HostStats
)

@Serializable
data class HostStats(
    @SerialName("total_sessions")
    val totalSessions: Int = 0,
    @SerialName("active_terminals")
    val activeTerminals: Int = 0,
    @SerialName("total_projects")
    val totalProjects: Int = 0
)
