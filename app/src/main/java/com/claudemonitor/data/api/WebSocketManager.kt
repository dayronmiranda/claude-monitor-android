package com.claudemonitor.data.api

import android.util.Base64
import android.util.Log
import com.claudemonitor.core.error.AppError
import com.claudemonitor.core.logging.AppLogger
import com.claudemonitor.core.network.ConnectivityObserver
import com.claudemonitor.core.network.NetworkConfig
import com.claudemonitor.data.model.WsInputMessage
import com.claudemonitor.data.model.WsOutputMessage
import com.claudemonitor.data.model.WsResizeMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * WebSocket connection state.
 */
sealed class WsConnectionState {
    object Disconnected : WsConnectionState()
    object Connecting : WsConnectionState()
    data class Connected(val terminalId: String) : WsConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int, val nextRetryMs: Long) : WsConnectionState()
    data class Error(val error: AppError, val canRetry: Boolean) : WsConnectionState()

    val isConnected: Boolean get() = this is Connected
    val isConnecting: Boolean get() = this is Connecting || this is Reconnecting
}

/**
 * Configuration for a WebSocket connection.
 */
data class WsConnectionConfig(
    val baseUrl: String,
    val terminalId: String,
    val username: String,
    val password: String
)

/**
 * WebSocket manager with automatic reconnection strategy.
 */
@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val connectivityObserver: ConnectivityObserver,
    private val networkConfig: NetworkConfig,
    private val logger: AppLogger?
) {
    companion object {
        private const val TAG = "WebSocketManager"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Connection state
    private var webSocket: WebSocket? = null
    private var currentConfig: WsConnectionConfig? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    // State flows
    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<WsOutputMessage>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<WsOutputMessage> = _messages.asSharedFlow()

    private val _buffer = MutableStateFlow("")
    val buffer: StateFlow<String> = _buffer.asStateFlow()

    // Reconnection settings
    private val initialReconnectDelay = networkConfig.wsReconnectDelayMs
    private val maxReconnectDelay = networkConfig.wsMaxReconnectDelayMs
    private val maxReconnectAttempts = networkConfig.wsMaxReconnectAttempts

    init {
        // Monitor connectivity changes for auto-reconnect
        scope.launch {
            connectivityObserver.status.collect { status ->
                if (status == ConnectivityObserver.Status.Available) {
                    val state = _connectionState.value
                    if (state is WsConnectionState.Error && state.canRetry) {
                        logger?.i(TAG, "Network available, attempting reconnect")
                        reconnect()
                    }
                }
            }
        }
    }

    /**
     * Connect to a terminal WebSocket.
     */
    fun connect(config: WsConnectionConfig) {
        if (_connectionState.value.isConnected || _connectionState.value.isConnecting) {
            logger?.d(TAG, "Already connected or connecting")
            return
        }

        currentConfig = config
        reconnectAttempt = 0
        doConnect(config)
    }

    private fun doConnect(config: WsConnectionConfig) {
        _connectionState.value = WsConnectionState.Connecting

        val wsUrl = config.baseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/api/terminals/${config.terminalId}/ws"

        val credentials = "${config.username}:${config.password}"
        val basic = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Basic $basic")
            .build()

        logger?.d(TAG, "Connecting to: $wsUrl")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger?.i(TAG, "WebSocket connected")
                reconnectAttempt = 0
                _connectionState.value = WsConnectionState.Connected(config.terminalId)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = WsOutputMessage.parse(text)
                if (message is WsOutputMessage.Output) {
                    appendToBuffer(message.data)
                }
                _messages.tryEmit(message)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                logger?.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logger?.d(TAG, "WebSocket closed: $code $reason")
                handleDisconnect(wasClean = true, reason = reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logger?.e(TAG, "WebSocket failure", t)
                handleDisconnect(wasClean = false, error = t)
            }
        })
    }

    private fun handleDisconnect(wasClean: Boolean, reason: String? = null, error: Throwable? = null) {
        webSocket = null

        if (wasClean) {
            _connectionState.value = WsConnectionState.Disconnected
            _messages.tryEmit(WsOutputMessage.Closed(reason))
        } else {
            val appError = error?.let {
                AppError.WebSocket(
                    message = it.message ?: "Connection failed",
                    cause = it,
                    canReconnect = reconnectAttempt < maxReconnectAttempts
                )
            } ?: AppError.WebSocket(message = "Connection lost", canReconnect = true)

            if (reconnectAttempt < maxReconnectAttempts && currentConfig != null) {
                scheduleReconnect()
            } else {
                _connectionState.value = WsConnectionState.Error(appError, canRetry = false)
                _messages.tryEmit(WsOutputMessage.Error(appError.message))
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()

        val delay = calculateReconnectDelay()
        reconnectAttempt++

        logger?.i(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${delay}ms")

        _connectionState.value = WsConnectionState.Reconnecting(
            attempt = reconnectAttempt,
            maxAttempts = maxReconnectAttempts,
            nextRetryMs = delay
        )

        reconnectJob = scope.launch {
            delay(delay)

            // Check if still should reconnect
            if (currentConfig != null && !_connectionState.value.isConnected) {
                // Check connectivity before attempting
                if (connectivityObserver.isCurrentlyConnected()) {
                    currentConfig?.let { doConnect(it) }
                } else {
                    // Wait for connectivity
                    _connectionState.value = WsConnectionState.Error(
                        error = AppError.Network(
                            message = "Waiting for network",
                            isNoConnection = true
                        ),
                        canRetry = true
                    )
                }
            }
        }
    }

    private fun calculateReconnectDelay(): Long {
        val delay = initialReconnectDelay * (2.0.pow(reconnectAttempt - 1)).toLong()
        return min(delay, maxReconnectDelay)
    }

    /**
     * Manually trigger reconnection.
     */
    fun reconnect() {
        reconnectJob?.cancel()
        reconnectAttempt = 0

        currentConfig?.let {
            disconnect(clearConfig = false)
            doConnect(it)
        }
    }

    /**
     * Disconnect from WebSocket.
     */
    fun disconnect(clearConfig: Boolean = true) {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        webSocket = null

        if (clearConfig) {
            currentConfig = null
        }

        _connectionState.value = WsConnectionState.Disconnected
        _buffer.value = ""
    }

    /**
     * Send input to terminal.
     */
    fun send(data: String): Boolean {
        val ws = webSocket ?: return false

        val message = WsInputMessage(data = data)
        val jsonString = json.encodeToString(message)
        return ws.send(jsonString)
    }

    /**
     * Send resize command.
     */
    fun resize(cols: Int, rows: Int): Boolean {
        val ws = webSocket ?: return false

        val message = WsResizeMessage(cols = cols, rows = rows)
        val jsonString = json.encodeToString(message)
        return ws.send(jsonString)
    }

    /**
     * Clear the output buffer.
     */
    fun clearBuffer() {
        _buffer.value = ""
    }

    private fun appendToBuffer(data: String) {
        val newBuffer = _buffer.value + data
        _buffer.value = if (newBuffer.length > 100000) {
            newBuffer.takeLast(100000)
        } else {
            newBuffer
        }
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
