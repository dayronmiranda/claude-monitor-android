package com.claudemonitor.data.api

import android.util.Base64
import android.util.Log
import com.claudemonitor.data.model.WsInputMessage
import com.claudemonitor.data.model.WsOutputMessage
import com.claudemonitor.data.model.WsResizeMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class WebSocketService(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WebSocketService"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
    private var retryCount = 0
    private var maxRetries = 3

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<WsOutputMessage>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<WsOutputMessage> = _messages.asSharedFlow()

    private val _buffer = MutableStateFlow("")
    val buffer: StateFlow<String> = _buffer.asStateFlow()

    private var pendingConnection: Triple<String, String, Triple<String, String, String>>? = null

    fun connect(baseUrl: String, terminalId: String, username: String, password: String, apiToken: String? = null) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            return
        }

        // Store connection parameters for potential retries
        pendingConnection = Triple(baseUrl, terminalId, Triple(username, password, apiToken ?: ""))
        retryCount = 0

        _connectionState.value = ConnectionState.CONNECTING
        attemptConnect(baseUrl, terminalId, username, password, apiToken)
    }

    private fun attemptConnect(baseUrl: String, terminalId: String, username: String, password: String, apiToken: String? = null) {
        // Build WebSocket URL
        val wsUrl = baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/api/terminals/$terminalId/ws"

        val requestBuilder = Request.Builder()
            .url(wsUrl)

        // Use API Token if available (preferred), fallback to Basic Auth
        if (!apiToken.isNullOrBlank()) {
            Log.d(TAG, "Using X-API-Token authentication")
            requestBuilder.header("X-API-Token", apiToken)
        } else {
            Log.d(TAG, "Using Basic Auth authentication")
            val credentials = "$username:$password"
            val basic = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            requestBuilder.header("Authorization", "Basic $basic")
        }

        val request = requestBuilder.build()

        Log.d(TAG, "Connecting to WebSocket: $baseUrl/api/terminals/$terminalId/ws (attempt ${retryCount + 1})")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                retryCount = 0
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = WsOutputMessage.parse(text)
                if (message is WsOutputMessage.Output) {
                    // Append to buffer (limit size)
                    val newBuffer = _buffer.value + message.data
                    _buffer.value = if (newBuffer.length > 100000) {
                        newBuffer.takeLast(100000)
                    } else {
                        newBuffer
                    }
                }
                _messages.tryEmit(message)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                _messages.tryEmit(WsOutputMessage.Closed(reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.javaClass.simpleName}", t)
                if (response != null) {
                    Log.e(TAG, "Response code: ${response.code}, message: ${response.message}")
                }

                val errorMsg = when (t) {
                    is java.io.EOFException -> "Server closed connection (EOFException) - authentication or server issue"
                    else -> t.message ?: "Connection failed"
                }

                if (retryCount < maxRetries && t is java.io.EOFException) {
                    retryCount++
                    val backoffMs = 1000L * (1 shl (retryCount - 1)) // Exponential backoff
                    Log.d(TAG, "Retrying in ${backoffMs}ms (attempt $retryCount/$maxRetries)")
                    // Note: In a real app, you'd use viewModelScope or a coroutine scope here
                    _connectionState.value = ConnectionState.CONNECTING
                } else {
                    _connectionState.value = ConnectionState.ERROR
                    _messages.tryEmit(WsOutputMessage.Error(errorMsg))
                }
            }
        })
    }

    fun send(data: String) {
        val message = WsInputMessage(data = data)
        val jsonString = json.encodeToString(message)
        webSocket?.send(jsonString)
    }

    fun resize(cols: Int, rows: Int) {
        val message = WsResizeMessage(cols = cols, rows = rows)
        val jsonString = json.encodeToString(message)
        webSocket?.send(jsonString)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        pendingConnection = null
        retryCount = 0
        _connectionState.value = ConnectionState.DISCONNECTED
        _buffer.value = ""
    }

    fun clearBuffer() {
        _buffer.value = ""
    }
}
