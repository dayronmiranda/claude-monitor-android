package com.claudemonitor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val name: String? = null,
    @SerialName("project_path")
    val projectPath: String = "",
    @SerialName("real_path")
    val realPath: String? = null,
    @SerialName("first_message")
    val firstMessage: String = "",
    @SerialName("message_count")
    val messageCount: Int = 0,
    @SerialName("size_bytes")
    val sizeBytes: Long = 0,
    @SerialName("modified_at")
    val modifiedAt: String = ""
) {
    val displayName: String
        get() = name ?: firstMessage.ifEmpty { id.take(8) }
}

@Serializable
data class RenameRequest(
    val name: String
)
