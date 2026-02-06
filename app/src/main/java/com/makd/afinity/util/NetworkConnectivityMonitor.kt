package com.makd.afinity.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

@Singleton
class NetworkConnectivityMonitor
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isNetworkAvailable: Flow<Boolean> =
        callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        private val networks = mutableSetOf<Network>()

                        override fun onAvailable(network: Network) {
                            networks.add(network)
                            trySend(true)
                            Timber.d(
                                "Network available: $network, Total networks: ${networks.size}"
                            )
                        }

                        override fun onLost(network: Network) {
                            networks.remove(network)
                            trySend(networks.isNotEmpty())
                            Timber.d("Network lost: $network, Remaining networks: ${networks.size}")
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            val hasInternet =
                                networkCapabilities.hasCapability(
                                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                                )
                            val isValidated =
                                networkCapabilities.hasCapability(
                                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                                )
                            Timber.d(
                                "Network capabilities changed: hasInternet=$hasInternet, isValidated=$isValidated"
                            )
                        }
                    }

                val networkRequest =
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        .build()

                connectivityManager.registerNetworkCallback(networkRequest, callback)

                trySend(isCurrentlyConnected())

                awaitClose {
                    Timber.d("Unregistering network callback")
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }
            .distinctUntilChanged()

    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
