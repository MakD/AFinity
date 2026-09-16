package com.makd.afinity.data.repository.server

import com.makd.afinity.data.database.dao.ServerAddressMemoryDao
import com.makd.afinity.data.models.server.ServerAddressMemory
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.util.LocalNetworkPermission
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.NetworkLocality
import com.makd.afinity.util.ProbeResult
import com.makd.afinity.util.probeAddresses
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

sealed class AddressResolutionResult {
    data class Success(val address: String, val serverId: String) : AddressResolutionResult()

    data class AllFailed(val serverId: String, val attemptedAddresses: List<String>) :
        AddressResolutionResult()

    data class PermissionRequired(val serverId: String, val attemptedAddresses: List<String>) :
        AddressResolutionResult()

    data class NoRoute(val serverId: String, val attemptedAddresses: List<String>) :
        AddressResolutionResult()
}

@Singleton
class ServerAddressResolver
@Inject
constructor(
    private val databaseRepository: DatabaseRepository,
    private val serverRepository: ServerRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val networkLocality: NetworkLocality,
    private val localNetworkPermission: LocalNetworkPermission,
    private val addressMemoryDao: ServerAddressMemoryDao,
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

        val onLocalNetwork = networkConnectivityMonitor.isOnLocalNetwork()
        val networkKey = networkConnectivityMonitor.currentNetworkKey()
        val remembered = networkKey?.let { key ->
            runCatching { addressMemoryDao.get(serverId, key)?.address }
                .onFailure { Timber.w(it, "Could not read remembered address") }
                .getOrNull()
        }

        val probe =
            probeAddresses(
                addresses = addressesToTry,
                preferLocal = onLocalNetwork,
                logTag = "Jellyfin",
                networkLocality = networkLocality,
                rememberedAddress = remembered,
                validator = validator,
            )

        if (probe is ProbeResult.Success && networkKey != null) {
            runCatching {
                addressMemoryDao.upsert(
                    ServerAddressMemory(
                        serverId = serverId,
                        networkKey = networkKey,
                        address = probe.address,
                        lastSucceededAt = System.currentTimeMillis(),
                    )
                )
            }
                .onFailure { Timber.w(it, "Could not remember working address") }
        }

        return when {
            probe is ProbeResult.Success -> AddressResolutionResult.Success(probe.address, serverId)
            localNetworkPermission.mayExplainFailure(addressesToTry, onLocalNetwork) ->
                AddressResolutionResult.PermissionRequired(serverId, addressesToTry)
            probe is ProbeResult.NoRoute ->
                AddressResolutionResult.NoRoute(serverId, addressesToTry)
            else -> AddressResolutionResult.AllFailed(serverId, addressesToTry)
        }
    }
}
