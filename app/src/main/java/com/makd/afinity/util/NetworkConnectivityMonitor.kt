package com.makd.afinity.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkConnectivityMonitor
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkSwitchEvents =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val networkSwitchEvents: SharedFlow<Unit> = _networkSwitchEvents.asSharedFlow()

    private val _networkDropEvents =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val networkDropEvents: SharedFlow<Unit> = _networkDropEvents.asSharedFlow()

    private val _isNetworkAvailable = MutableStateFlow(isCurrentlyConnected())
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _isOnWifi = MutableStateFlow(isOnWifi())
    val isOnWifiFlow: StateFlow<Boolean> = _isOnWifi.asStateFlow()

    private val networks = mutableSetOf<Network>()

    init {
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()

        connectivityManager.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val total = synchronized(networks) { networks.add(network); networks.size }
                    _isNetworkAvailable.value = true
                    if (!_isOnWifi.value) {
                        _isOnWifi.value = isOnWifi()
                    }
                    _networkSwitchEvents.tryEmit(Unit)
                    Timber.d("Network available: $network, Total networks: $total")
                }

                override fun onLost(network: Network) {
                    val remaining =
                        synchronized(networks) { networks.remove(network); networks.size }
                    _isNetworkAvailable.value = remaining > 0
                    _networkDropEvents.tryEmit(Unit)
                    Timber.d("Network lost: $network, Remaining networks: $remaining")
                }
            },
        )

        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    _isOnWifi.value = networkCapabilities.isWifiLike()
                }

                override fun onLost(network: Network) {
                    _isOnWifi.value = false
                }
            }
        )
    }

    private fun NetworkCapabilities.isWifiLike(): Boolean =
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            (hasTransport(NetworkCapabilities.TRANSPORT_VPN) && anyTrackedNetworkIsWifi())

    private fun anyTrackedNetworkIsWifi(): Boolean {
        val tracked = synchronized(networks) { networks.toList() }
        return tracked.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        }
    }

    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isOnWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.isWifiLike()
    }

    fun isOnLocalNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}