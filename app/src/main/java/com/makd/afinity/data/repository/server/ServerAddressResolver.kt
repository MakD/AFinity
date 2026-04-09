package com.makd.afinity.data.repository.server

import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.isLocalAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

sealed class AddressResolutionResult {
    data class Success(val address: String, val serverId: String) : AddressResolutionResult()

    data class AllFailed(val serverId: String, val attemptedAddresses: List<String>) :
        AddressResolutionResult()
}

@Singleton
class ServerAddressResolver
@Inject
constructor(
    private val databaseRepository: DatabaseRepository,
    private val serverRepository: ServerRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
) {

    suspend fun resolveAddress(serverId: String): AddressResolutionResult =
        resolveAddress(serverId) { address ->
            serverRepository.pingServer(address, timeoutMs = 2000L)
        }

    suspend fun resolveAddress(
        serverId: String,
        validator: suspend (String) -> Boolean,
    ): AddressResolutionResult {
        val server =
            databaseRepository.getServer(serverId)
                ?: return AddressResolutionResult.AllFailed(serverId, emptyList())

        val primaryAddress = server.address
        val alternateAddresses =
            databaseRepository
                .getServerAddresses(serverId)
                .map { it.address }
                .filter { it != primaryAddress }
        val addressesToTry = listOf(primaryAddress) + alternateAddresses
        val onWifi = networkConnectivityMonitor.isOnWifi()
        val (localAddresses, externalAddresses) = addressesToTry.partition { isLocalAddress(it) }
        val orderedAddresses =
            if (onWifi) {
                localAddresses + externalAddresses
            } else {
                externalAddresses
            }

        Timber.d(
            "Jellyfin: Resolving address, onWifi=$onWifi, " +
                "addresses=${orderedAddresses.map { "${it}[${if (isLocalAddress(it)) "local" else "ext"}]" }}"
        )

        if (orderedAddresses.isEmpty()) {
            Timber.w(
                "Jellyfin: No reachable addresses (onWifi=$onWifi, skipped ${localAddresses.size} local-only)"
            )
            return AddressResolutionResult.AllFailed(serverId, addressesToTry)
        }

        val startTime = System.currentTimeMillis()

        return coroutineScope {
            val winningAddress = CompletableDeferred<String?>()
            val failureCount = AtomicInteger(0)
            val totalAddresses = orderedAddresses.size

            val jobs = orderedAddresses.map { address ->
                val tag = if (isLocalAddress(address)) "local" else "ext"
                launch {
                    val pingStart = System.currentTimeMillis()
                    val success = validator(address)
                    val elapsed = System.currentTimeMillis() - pingStart
                    Timber.d(
                        "Jellyfin: Probe $address [$tag] → ${if (success) "OK" else "FAIL"} (${elapsed}ms)"
                    )
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
                Timber.d("Jellyfin: Resolved → $bestAddress [$tag] (${totalElapsed}ms)")
                AddressResolutionResult.Success(bestAddress, serverId)
            } else {
                Timber.w("Jellyfin: All $totalAddresses addresses failed (${totalElapsed}ms)")
                AddressResolutionResult.AllFailed(serverId, orderedAddresses)
            }
        }
    }
}
