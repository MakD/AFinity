package com.makd.afinity.data.repository.jellyseerr

import com.makd.afinity.data.database.dao.JellyseerrDao
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.isLocalAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class JellyseerrAddressResult {
    data class Success(val address: String) : JellyseerrAddressResult()
    data class AllFailed(val attemptedAddresses: List<String>) : JellyseerrAddressResult()
}

@Singleton
class JellyseerrAddressResolver
@Inject
constructor(
    private val jellyseerrDao: JellyseerrDao,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
) {

    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun resolveAddress(
        serverId: String,
        userId: String,
        primaryUrl: String,
    ): JellyseerrAddressResult {
        val alternateAddresses = jellyseerrDao.getAddresses(serverId, userId)
            .map { it.address }
            .filter { it != primaryUrl }

        val addressesToTry = listOf(primaryUrl) + alternateAddresses

        val onWifi = networkConnectivityMonitor.isOnWifi()
        val (localAddresses, externalAddresses) = addressesToTry.partition { isLocalAddress(it) }
        val orderedAddresses = if (onWifi) {
            localAddresses + externalAddresses
        } else {
            externalAddresses + localAddresses
        }

        Timber.d(
            "Jellyseerr: Resolving address, trying ${orderedAddresses.size} address(es), onWifi=$onWifi"
        )

        for (address in orderedAddresses) {
            Timber.d("Jellyseerr: Pinging $address...")
            if (pingService(address)) {
                Timber.d("Jellyseerr: Address resolved: $address")
                return JellyseerrAddressResult.Success(address)
            }
        }

        Timber.w("Jellyseerr: All ${orderedAddresses.size} addresses failed")
        return JellyseerrAddressResult.AllFailed(orderedAddresses)
    }

    private suspend fun pingService(address: String, timeoutMs: Long = 3000L): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val normalizedUrl = address.trimEnd('/') + "/api/v1/status"
                    val request = Request.Builder().url(normalizedUrl).get().build()
                    val response = pingClient.newCall(request).execute()
                    response.close()
                    response.isSuccessful
                }
                result == true
            } catch (e: Exception) {
                Timber.d("Jellyseerr ping failed for $address: ${e.message}")
                false
            }
        }
    }
}