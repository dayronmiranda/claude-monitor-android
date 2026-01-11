package com.claudemonitor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val path: String,
    @SerialName("real_path")
    val realPath: String,
    @SerialName("session_count")
    val sessionCount: Int,
    @SerialName("last_modified")
    val lastModified: String
)
