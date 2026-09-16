package com.makd.afinity.data.repository.jellyseerr

import com.makd.afinity.data.database.dao.JellyseerrDao
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.NetworkLocality
import com.makd.afinity.util.ProbeResult
import com.makd.afinity.util.pingUrl
import com.makd.afinity.util.probeAddresses
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import timber.log.Timber

sealed class JellyseerrAddressResult {
    data class Success(val address: String) : JellyseerrAddressResult()

    data class AllFailed(val attemptedAddresses: List<String>) : JellyseerrAddressResult()

    data class NoRoute(val attemptedAddresses: List<String>) : JellyseerrAddressResult()
}

@Singleton
class JellyseerrAddressResolver
@Inject
constructor(
    private val jellyseerrDao: JellyseerrDao,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val networkLocality: NetworkLocality,
) {

    private val pingClient =
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

    suspend fun resolveAddress(
        serverId: String,
        userId: String,
        primaryUrl: String,
    ): JellyseerrAddressResult {
        val alternateAddresses =
            jellyseerrDao
                .getAddresses(serverId, userId)
                .map { it.address }
                .filter { it != primaryUrl }

        val addressesToTry = listOf(primaryUrl) + alternateAddresses

        return when (
            val probe =
                probeAddresses(
                    addresses = addressesToTry,
                    preferLocal = networkConnectivityMonitor.isOnLocalNetwork(),
                    logTag = "Jellyseerr",
                    networkLocality = networkLocality,
                    validator = { address -> pingService(address) },
                )
        ) {
            is ProbeResult.Success -> JellyseerrAddressResult.Success(probe.address)
            ProbeResult.NoRoute -> JellyseerrAddressResult.NoRoute(addressesToTry)
            ProbeResult.AllFailed -> JellyseerrAddressResult.AllFailed(addressesToTry)
        }
    }

    private suspend fun pingService(address: String, timeoutMs: Long = 2000L): Boolean {
        val normalizedUrl = address.trimEnd('/') + "/api/v1/status"
        return try {
            withTimeoutOrNull(timeoutMs) { pingClient.pingUrl(normalizedUrl) } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d("Jellyseerr ping failed for $address: ${e.message}")
            false
        }
    }
}
