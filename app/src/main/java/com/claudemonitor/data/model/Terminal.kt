package com.claudemonitor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Terminal(
    val id: String,
    val name: String,
    @SerialName("work_dir")
    val workDir: String,
    val type: String,
    val status: String,
    val active: Boolean,
    val clients: Int = 0,
    @SerialName("session_id")
    val sessionId: String? = null,
    val model: String? = null,
    @SerialName("can_resume")
    val canResume: Boolean = false,
    @SerialName("started_at")
    val startedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("last_access_at")
    val lastAccessAt: String? = null
)

@Serializable
data class TerminalConfig(
    @SerialName("work_dir")
    val workDir: String,
    val type: String = "claude",
    val name: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    val resume: Boolean = false,
    val id: String? = null,
    val command: String? = null,
    val model: String? = null,
    @SerialName("system_prompt")
    val systemPrompt: String? = null,
    @SerialName("allowed_tools")
    val allowedTools: List<String>? = null,
    @SerialName("disallowed_tools")
    val disallowedTools: List<String>? = null,
    @SerialName("permission_mode")
    val permissionMode: String? = null,
    @SerialName("additional_dirs")
    val additionalDirs: List<String>? = null,
    @SerialName("continue")
    val `continue`: Boolean = false
)

@Serializable
data class ResizeRequest(val rows: Int, val cols: Int)

@Serializable
data class DirectoryEntry(
    val name: String,
    val path: String,
    val is_dir: Boolean,
    val size: Long
)

@Serializable
data class DirectoryListing(
    val current_path: String,
    val entries: List<DirectoryEntry>
)
