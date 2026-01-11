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

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<WsOutputMessage>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<WsOutputMessage> = _messages.asSharedFlow()

    private val _buffer = MutableStateFlow("")
    val buffer: StateFlow<String> = _buffer.asStateFlow()

    fun connect(baseUrl: String, terminalId: String, username: String, password: String) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        // Build WebSocket URL
        val wsUrl = baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/api/terminals/$terminalId/ws"

        // Add auth header
        val credentials = "$username:$password"
        val basic = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Basic $basic")
            .build()

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
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
                Log.e(TAG, "WebSocket failure", t)
                _connectionState.value = ConnectionState.ERROR
                _messages.tryEmit(WsOutputMessage.Error(t.message ?: "Connection failed"))
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
        _connectionState.value = ConnectionState.DISCONNECTED
        _buffer.value = ""
    }

    fun clearBuffer() {
        _buffer.value = ""
    }
}
