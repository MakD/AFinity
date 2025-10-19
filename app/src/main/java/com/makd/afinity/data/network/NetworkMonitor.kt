package com.makd.afinity.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network connection types
 */
enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    NONE
}

/**
 * Network state
 */
data class NetworkState(
    val isConnected: Boolean,
    val connectionType: ConnectionType
)

/**
 * Monitors network connectivity changes
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Observe network state changes
     */
    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val state = getCurrentNetworkState()
                Timber.d("Network available: $state")
                trySend(state)
            }

            override fun onLost(network: Network) {
                val state = getCurrentNetworkState()
                Timber.d("Network lost: $state")
                trySend(state)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val state = getCurrentNetworkState()
                Timber.d("Network capabilities changed: $state")
                trySend(state)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        trySend(getCurrentNetworkState())

        awaitClose {
            Timber.d("Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Get current network state
     */
    fun getCurrentNetworkState(): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            val connectionType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.NONE
            }
            NetworkState(isConnected = true, connectionType = connectionType)
        } else {
            NetworkState(isConnected = false, connectionType = ConnectionType.NONE)
        }
    }

    /**
     * Check if currently on WiFi
     */
    fun isOnWifi(): Boolean {
        return getCurrentNetworkState().connectionType == ConnectionType.WIFI
    }

    /**
     * Check if currently on cellular
     */
    fun isOnCellular(): Boolean {
        return getCurrentNetworkState().connectionType == ConnectionType.CELLULAR
    }
}