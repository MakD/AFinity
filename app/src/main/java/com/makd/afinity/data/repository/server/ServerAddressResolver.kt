package com.makd.afinity.data.repository.server

import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.probeAddresses
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

        val bestAddress =
            probeAddresses(
                addresses = addressesToTry,
                preferLocal = networkConnectivityMonitor.isOnLocalNetwork(),
                logTag = "Jellyfin",
                validator = validator,
            )

        return if (bestAddress != null) {
            AddressResolutionResult.Success(bestAddress, serverId)
        } else {
            AddressResolutionResult.AllFailed(serverId, addressesToTry)
        }
    }
}