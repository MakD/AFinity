package com.makd.afinity.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.InetAddresses
import android.net.Network
import android.net.NetworkCapabilities
import com.makd.afinity.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class Locality {
    ON_LINK,
    TAILSCALE,
    TUNNELLED,
    PUBLIC,
    UNKNOWN,
}

private const val RESOLVE_TIMEOUT_MS = 3_000L
private const val TAILSCALE_MAGIC_DNS = "100.100.100.100"
private const val TAILSCALE_ULA_PREFIX = "fd7a:115c:a1e0"
private const val TAILSCALE_DOMAIN_SUFFIX = "ts.net"

@Singleton
class NetworkLocality
@Inject
constructor(
    @param:ApplicationContext context: Context,
    networkConnectivityMonitor: NetworkConnectivityMonitor,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val cache = ConcurrentHashMap<String, Locality>()

    init {
        scope.launch { networkConnectivityMonitor.networkSwitchEvents.collect { cache.clear() } }
        scope.launch { networkConnectivityMonitor.networkDropEvents.collect { cache.clear() } }
    }

    suspend fun resolve(url: String): Locality {
        val host = extractHost(url)
        if (host.isBlank()) return Locality.UNKNOWN

        val activeNetwork = connectivityManager.activeNetwork ?: return Locality.UNKNOWN
        val key = "${activeNetwork.networkHandle}|$host"
        cache[key]?.let { return it }

        val addresses = resolveOn(activeNetwork, host)
        if (addresses.isNullOrEmpty()) return Locality.UNKNOWN

        val locality = classify(addresses)
        if (locality != Locality.UNKNOWN) {
            cache[key] = locality
        }
        return locality
    }

    private suspend fun resolveOn(network: Network, host: String): List<InetAddress>? {
        if (isNumericHost(host)) {
            return runCatching { listOf(InetAddresses.parseNumericAddress(host)) }.getOrNull()
        }
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                runCatching { network.getAllByName(host).toList() }
                    .onFailure { Timber.d("Locality: could not resolve $host (${it.message})") }
                    .getOrNull()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun classify(addresses: List<InetAddress>): Locality {
        val networks = connectivityManager.allNetworks

        for (network in networks) {
            if (isVpn(network)) continue
            if (!isWifiLike(network)) continue
            if (isOnLink(addresses, network)) return Locality.ON_LINK
        }

        for (network in networks) {
            if (!isVpn(network)) continue
            if (isOnLink(addresses, network)) return tunnelKind(network)
        }

        for (network in networks) {
            if (!isVpn(network)) continue
            if (carriesDefaultRoute(network)) return tunnelKind(network)
        }

        return Locality.PUBLIC
    }

    private fun carriesDefaultRoute(network: Network): Boolean =
        connectivityManager.getLinkProperties(network)?.routes?.any { it.isDefaultRoute } == true

    private fun tunnelKind(network: Network): Locality =
        if (isTailscale(network)) Locality.TAILSCALE else Locality.TUNNELLED

    private fun isTailscale(network: Network): Boolean {
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return false
        if (linkProperties.domains?.endsWith(TAILSCALE_DOMAIN_SUFFIX) == true) return true
        if (linkProperties.dnsServers.any { it.hostAddress == TAILSCALE_MAGIC_DNS }) return true
        return linkProperties.routes.any {
            it.destination.address.hostAddress?.startsWith(TAILSCALE_ULA_PREFIX) == true
        }
    }

    private fun isVpn(network: Network): Boolean =
        connectivityManager
            .getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    private fun isWifiLike(network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun isOnLink(addresses: List<InetAddress>, network: Network): Boolean {
        val routes = connectivityManager.getLinkProperties(network)?.routes ?: return false
        return routes.any { route ->
            !route.isDefaultRoute &&
                !route.hasGateway() &&
                addresses.any { route.destination.contains(it) }
        }
    }

    private fun isNumericHost(host: String): Boolean =
        runCatching { InetAddresses.isNumericAddress(host) }.getOrDefault(false)
}