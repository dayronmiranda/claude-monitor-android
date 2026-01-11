package com.claudemonitor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class WsInputMessage(
    val type: String = "input",
    val data: String
)

@Serializable
data class WsResizeMessage(
    val type: String = "resize",
    val cols: Int,
    val rows: Int
)

sealed class WsOutputMessage {
    data class Output(val data: String) : WsOutputMessage()
    data class Closed(val reason: String? = null) : WsOutputMessage()
    data class Error(val message: String) : WsOutputMessage()
    data object Unknown : WsOutputMessage()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): WsOutputMessage {
            return try {
                val element = json.parseToJsonElement(text)
                val obj = element.jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "output" -> Output(obj["data"]?.jsonPrimitive?.content ?: "")
                    "closed" -> Closed(obj["reason"]?.jsonPrimitive?.content)
                    "error" -> Error(obj["message"]?.jsonPrimitive?.content ?: "Unknown error")
                    else -> Unknown
                }
            } catch (e: Exception) {
                // Plain text output (not JSON)
                Output(text)
            }
        }
    }
}
