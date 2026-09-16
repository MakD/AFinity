package com.makd.afinity.data.repository.audiobookshelf

import com.makd.afinity.data.database.dao.AudiobookshelfDao
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

sealed class AudiobookshelfAddressResult {
    data class Success(val address: String) : AudiobookshelfAddressResult()

    data class AllFailed(val attemptedAddresses: List<String>) : AudiobookshelfAddressResult()

    data class NoRoute(val attemptedAddresses: List<String>) : AudiobookshelfAddressResult()
}

@Singleton
class AudiobookshelfAddressResolver
@Inject
constructor(
    private val audiobookshelfDao: AudiobookshelfDao,
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
    ): AudiobookshelfAddressResult {
        val alternateAddresses =
            audiobookshelfDao
                .getAddresses(serverId, userId)
                .map { it.address }
                .filter { it != primaryUrl }

        val addressesToTry = listOf(primaryUrl) + alternateAddresses

        return when (
            val probe =
                probeAddresses(
                    addresses = addressesToTry,
                    preferLocal = networkConnectivityMonitor.isOnLocalNetwork(),
                    logTag = "Audiobookshelf",
                    networkLocality = networkLocality,
                    validator = { address -> pingService(address) },
                )
        ) {
            is ProbeResult.Success -> AudiobookshelfAddressResult.Success(probe.address)
            ProbeResult.NoRoute -> AudiobookshelfAddressResult.NoRoute(addressesToTry)
            ProbeResult.AllFailed -> AudiobookshelfAddressResult.AllFailed(addressesToTry)
        }
    }

    private suspend fun pingService(address: String, timeoutMs: Long = 2000L): Boolean {
        val normalizedUrl = address.trimEnd('/') + "/ping"
        return try {
            withTimeoutOrNull(timeoutMs) { pingClient.pingUrl(normalizedUrl) } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d("Audiobookshelf ping failed for $address: ${e.message}")
            false
        }
    }
}
