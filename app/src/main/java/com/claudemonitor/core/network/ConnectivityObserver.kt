package com.claudemonitor.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes network connectivity changes in real-time.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Current connectivity status.
     */
    sealed class Status {
        object Available : Status()
        object Unavailable : Status()
        object Losing : Status()
        object Lost : Status()

        val isConnected: Boolean get() = this == Available
    }

    /**
     * Detailed network info.
     */
    data class NetworkInfo(
        val status: Status,
        val type: NetworkType,
        val isMetered: Boolean,
        val downloadSpeedKbps: Int?,
        val uploadSpeedKbps: Int?
    )

    enum class NetworkType {
        Wifi,
        Cellular,
        Ethernet,
        Unknown,
        None
    }

    /**
     * Flow of connectivity status changes.
     */
    val status: Flow<Status> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Status.Available)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(Status.Losing)
            }

            override fun onLost(network: Network) {
                trySend(Status.Lost)
            }

            override fun onUnavailable() {
                trySend(Status.Unavailable)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(getCurrentStatus())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Flow of detailed network info.
     */
    val networkInfo: Flow<NetworkInfo> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(getNetworkInfo(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(NetworkInfo(
                    status = Status.Lost,
                    type = NetworkType.None,
                    isMetered = false,
                    downloadSpeedKbps = null,
                    uploadSpeedKbps = null
                ))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(getCurrentNetworkInfo())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Simple boolean flow for connectivity.
     */
    val isConnected: Flow<Boolean> = status.map { it.isConnected }

    /**
     * Gets current connectivity status synchronously.
     */
    fun getCurrentStatus(): Status {
        val network = connectivityManager.activeNetwork ?: return Status.Unavailable
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return Status.Unavailable

        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            Status.Available
        } else {
            Status.Unavailable
        }
    }

    /**
     * Checks if currently connected.
     */
    fun isCurrentlyConnected(): Boolean = getCurrentStatus().isConnected

    /**
     * Gets current network info synchronously.
     */
    fun getCurrentNetworkInfo(): NetworkInfo {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        return if (capabilities != null) {
            getNetworkInfo(capabilities)
        } else {
            NetworkInfo(
                status = Status.Unavailable,
                type = NetworkType.None,
                isMetered = false,
                downloadSpeedKbps = null,
                uploadSpeedKbps = null
            )
        }
    }

    private fun getNetworkInfo(capabilities: NetworkCapabilities): NetworkInfo {
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.Ethernet
            else -> NetworkType.Unknown
        }

        return NetworkInfo(
            status = Status.Available,
            type = type,
            isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downloadSpeedKbps = capabilities.linkDownstreamBandwidthKbps,
            uploadSpeedKbps = capabilities.linkUpstreamBandwidthKbps
        )
    }
}

/**
 * Extension to wait for network connectivity.
 */
suspend fun ConnectivityObserver.awaitConnection(): ConnectivityObserver.Status {
    return status.first { it == ConnectivityObserver.Status.Available }
}

/**
 * Extension to run block when connected, or wait for connection.
 */
suspend fun <T> ConnectivityObserver.whenConnected(block: suspend () -> T): T {
    if (!isCurrentlyConnected()) {
        awaitConnection()
    }
    return block()
}
