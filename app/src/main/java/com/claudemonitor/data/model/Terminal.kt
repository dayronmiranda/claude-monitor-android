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
    val clients: Int = 0
)

@Serializable
data class TerminalConfig(
    @SerialName("work_dir")
    val workDir: String,
    val type: String = "claude",
    val name: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    val resume: Boolean = false
)
