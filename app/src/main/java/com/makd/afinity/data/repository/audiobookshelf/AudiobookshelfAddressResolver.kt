package com.makd.afinity.data.repository.audiobookshelf

import com.makd.afinity.data.database.dao.AudiobookshelfDao
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.isLocalAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

sealed class AudiobookshelfAddressResult {
    data class Success(val address: String) : AudiobookshelfAddressResult()
    data class AllFailed(val attemptedAddresses: List<String>) : AudiobookshelfAddressResult()
}

@Singleton
class AudiobookshelfAddressResolver
@Inject
constructor(
    private val audiobookshelfDao: AudiobookshelfDao,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
) {

    private val pingClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun resolveAddress(
        serverId: String,
        userId: String,
        primaryUrl: String,
    ): AudiobookshelfAddressResult {
        val alternateAddresses = audiobookshelfDao.getAddresses(serverId, userId)
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
            "Audiobookshelf: Resolving address, onWifi=$onWifi, " +
                "addresses=${orderedAddresses.map { "${it}[${if (isLocalAddress(it)) "local" else "ext"}]" }}"
        )

        val startTime = System.currentTimeMillis()

        return coroutineScope {
            val winningAddress = CompletableDeferred<String?>()
            val failureCount = AtomicInteger(0)
            val totalAddresses = orderedAddresses.size

            val jobs = orderedAddresses.map { address ->
                val tag = if (isLocalAddress(address)) "local" else "ext"
                launch {
                    val pingStart = System.currentTimeMillis()
                    val success = pingService(address)
                    val elapsed = System.currentTimeMillis() - pingStart
                    Timber.d("Audiobookshelf: Ping $address [$tag] → ${if (success) "OK" else "FAIL"} (${elapsed}ms)")
                    if (success) {
                        winningAddress.complete(address)
                    } else {
                        if (failureCount.incrementAndGet() == totalAddresses) {
                            winningAddress.complete(null)
                        }
                    }
                }
            }

            val bestAddress = winningAddress.await()
            val totalElapsed = System.currentTimeMillis() - startTime
            jobs.forEach { it.cancel() }

            if (bestAddress != null) {
                val tag = if (isLocalAddress(bestAddress)) "local" else "ext"
                Timber.d("Audiobookshelf: Resolved → $bestAddress [$tag] (${totalElapsed}ms)")
                AudiobookshelfAddressResult.Success(bestAddress)
            } else {
                Timber.w("Audiobookshelf: All $totalAddresses addresses failed (${totalElapsed}ms)")
                AudiobookshelfAddressResult.AllFailed(orderedAddresses)
            }
        }
    }

    private suspend fun pingService(address: String, timeoutMs: Long = 2000L): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(timeoutMs) {
                    val normalizedUrl = address.trimEnd('/') + "/ping"
                    val request = Request.Builder().url(normalizedUrl).get().build()
                    val response = pingClient.newCall(request).execute()
                    response.close()
                    response.isSuccessful
                }
                result == true
            } catch (e: Exception) {
                Timber.d("Audiobookshelf ping failed for $address: ${e.message}")
                false
            }
        }
    }
}