package com.makd.afinity.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

object AfinityServiceTypes {
    const val JELLYFIN = "_jellyfin._tcp"
    const val JELLYSEERR = "_jellyseerr._tcp"
    const val AUDIOBOOKSHELF = "_audiobookshelf._tcp"
}

data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int,
    val scheme: String,
    val path: String,
) {
    val url: String = "$scheme://$host:$port$path".trimEnd('/')
}

@Singleton
class LocalServiceDiscovery
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    private val nsdManager: NsdManager?
        get() = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val wifiManager: WifiManager?
        get() = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val callbackExecutor = Executor { it.run() }

    fun discover(
        serviceType: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Flow<List<DiscoveredService>> {
        val manager =
            nsdManager
                ?: run {
                    Timber.w("mDNS: NsdManager unavailable, skipping $serviceType")
                    return flowOf(emptyList())
                }

        return callbackFlow {
            val found = ConcurrentHashMap<String, DiscoveredService>()
            val infoCallbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

            val multicastLock =
                runCatching {
                        wifiManager?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply {
                            setReferenceCounted(true)
                            acquire()
                        }
                    }
                    .getOrNull()

            fun publish() {
                trySend(found.values.sortedBy { it.name.lowercase() })
            }

            val discoveryListener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(type: String) {
                        Timber.d("mDNS: discovery started for $type")
                    }

                    override fun onDiscoveryStopped(type: String) {
                        Timber.d("mDNS: discovery stopped for $type")
                    }

                    override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                        Timber.w("mDNS: start discovery failed for $type (error $errorCode)")
                        close()
                    }

                    override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                        Timber.w("mDNS: stop discovery failed for $type (error $errorCode)")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        val key = serviceInfo.serviceName ?: return
                        if (infoCallbacks.containsKey(key)) return

                        val callback =
                            object : NsdManager.ServiceInfoCallback {
                                override fun onServiceInfoCallbackRegistrationFailed(
                                    errorCode: Int
                                ) {
                                    Timber.w("mDNS: resolve failed for $key (error $errorCode)")
                                }

                                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                    val service =
                                        serviceInfo.toDiscoveredService(key) ?: return
                                    found[key] = service
                                    publish()
                                }

                                override fun onServiceLost() {
                                    found.remove(key)
                                    publish()
                                }

                                override fun onServiceInfoCallbackUnregistered() = Unit
                            }

                        infoCallbacks[key] = callback
                        runCatching {
                                manager.registerServiceInfoCallback(
                                    serviceInfo,
                                    callbackExecutor,
                                    callback,
                                )
                            }
                            .onFailure {
                                infoCallbacks.remove(key)
                                Timber.w(it, "mDNS: could not resolve $key")
                            }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        val key = serviceInfo.serviceName ?: return
                        found.remove(key)
                        publish()
                    }
                }

            runCatching {
                    manager.discoverServices(
                        serviceType,
                        NsdManager.PROTOCOL_DNS_SD,
                        discoveryListener,
                    )
                }
                .onFailure {
                    Timber.w(it, "mDNS: could not start discovery for $serviceType")
                    close()
                }

            launch {
                delay(timeoutMs)
                close()
            }

            awaitClose {
                infoCallbacks.values.forEach { callback ->
                    runCatching { manager.unregisterServiceInfoCallback(callback) }
                }
                infoCallbacks.clear()
                runCatching { manager.stopServiceDiscovery(discoveryListener) }
                runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
            }
        }
    }

    private fun NsdServiceInfo.toDiscoveredService(fallbackName: String): DiscoveredService? {
        if (port <= 0) return null

        val address =
            (hostAddresses.firstOrNull { it is Inet4Address } ?: hostAddresses.firstOrNull())
                ?.hostAddress
                ?.substringBefore('%')
                ?.takeIf { it.isNotBlank() } ?: return null

        val attrs = attributes.orEmpty()

        fun attribute(key: String): String = attrs[key]?.toString(Charsets.UTF_8)?.trim().orEmpty()

        val scheme = attribute("scheme").lowercase().takeIf { it == "https" } ?: "http"
        val rawPath = attribute("path")
        val path =
            when {
                rawPath.isBlank() || rawPath == "/" -> ""
                rawPath.startsWith("/") -> rawPath
                else -> "/$rawPath"
            }

        return DiscoveredService(
            name = serviceName?.takeIf { it.isNotBlank() } ?: fallbackName,
            host = address,
            port = port,
            scheme = scheme,
            path = path,
        )
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 4000L
        const val MULTICAST_LOCK_TAG = "afinity-mdns"
    }
}