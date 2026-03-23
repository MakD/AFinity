package com.makd.afinity.data.repository.server

import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.isLocalAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
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

    suspend fun resolveAddress(serverId: String): AddressResolutionResult {
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
                externalAddresses + localAddresses
            }

        Timber.d(
            "Resolving address for server $serverId, " +
                "trying ${orderedAddresses.size} address(es), onWifi=$onWifi"
        )

        if (orderedAddresses.size == 1) {
            val address = orderedAddresses.first()
            Timber.d("Pinging $address...")
            if (serverRepository.pingServer(address, timeoutMs = 2000L)) {
                Timber.d("Address resolved: $address")
                return AddressResolutionResult.Success(address, serverId)
            }
            Timber.w("Single address $address failed for server $serverId")
            return AddressResolutionResult.AllFailed(serverId, orderedAddresses)
        }
        return coroutineScope {
            val deferreds =
                orderedAddresses.mapIndexed { index, address ->
                    async {
                        Timber.d("Pinging $address...")
                        val success = serverRepository.pingServer(address, timeoutMs = 2000L)
                        Triple(address, success, index)
                    }
                }

            val results = deferreds.map { it.await() }
            val successful = results.filter { it.second }.sortedBy { it.third }

            if (successful.isNotEmpty()) {
                val best = successful.first().first
                Timber.d("Address resolved: $best (from ${successful.size} reachable)")
                AddressResolutionResult.Success(best, serverId)
            } else {
                Timber.w("All ${orderedAddresses.size} addresses failed for server $serverId")
                AddressResolutionResult.AllFailed(serverId, orderedAddresses)
            }
        }
    }
}
