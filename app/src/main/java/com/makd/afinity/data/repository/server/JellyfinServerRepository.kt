package com.makd.afinity.data.repository.server

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.repository.DatabaseRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.SystemApi
import timber.log.Timber

@Singleton
class JellyfinServerRepository
@Inject
constructor(
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val sessionManagerProvider: Provider<SessionManager>,
    private val databaseRepository: DatabaseRepository,
) : ServerRepository {

    private val sessionManager: SessionManager
        get() = sessionManagerProvider.get()

    private val _currentBaseUrl = MutableStateFlow("")
    override val currentBaseUrl: StateFlow<String> = _currentBaseUrl.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentServer = MutableStateFlow<Server?>(null)
    override val currentServer: StateFlow<Server?> = _currentServer.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            sessionManager.currentSession.collect { session ->
                if (session != null) {
                    try {
                        val server = databaseRepository.getServer(session.serverId)
                        if (server != null) {
                            _currentServer.value = server
                            _currentBaseUrl.value = server.address
                            _isConnected.value = true
                            Timber.d(
                                "JellyfinServerRepository: Updated current server to ${server.name} (${server.id})"
                            )
                        } else {
                            Timber.w(
                                "JellyfinServerRepository: Session changed but server ${session.serverId} not found in database"
                            )
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "JellyfinServerRepository: Failed to load server for session")
                    }
                } else {
                    _currentServer.value = null
                    _currentBaseUrl.value = ""
                    _isConnected.value = false
                    Timber.d("JellyfinServerRepository: Session cleared, current server reset")
                }
            }
        }
    }

    override fun getBaseUrl(): String {
        return apiClient.baseUrl ?: ""
    }

    override suspend fun setBaseUrl(baseUrl: String) {
        try {
            apiClient.update(baseUrl = baseUrl)

            _currentBaseUrl.value = baseUrl
            _isConnected.value = false

            Timber.d("Updated base URL to: $baseUrl")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set base URL: $baseUrl")
            throw e
        }
    }

    override suspend fun discoverServers(): List<Server> {
        return try {
            val discoveredServers = mutableListOf<Server>()

            withTimeoutOrNull(5000) {
                jellyfin.discovery.discoverLocalServers(timeout = 3000, maxServers = 10).collect {
                    serverInfo ->
                    Timber.d("Discovered server: ${serverInfo.name} at ${serverInfo.address}")

                    val server =
                        Server(
                            id = serverInfo.id ?: UUID.randomUUID().toString(),
                            name = serverInfo.name ?: "Jellyfin Server",
                            version = null,
                            address = serverInfo.address ?: "",
                        )
                    discoveredServers.add(server)
                }
            } ?: run { Timber.w("Server discovery timed out after 5 seconds") }

            Timber.d("Discovered ${discoveredServers.size} local servers")
            discoveredServers
        } catch (e: Exception) {
            Timber.e(e, "Failed to discover servers")
            emptyList()
        }
    }

    override fun discoverServersFlow(): Flow<List<Server>> = flow {
        try {
            val discoveredServers = mutableListOf<Server>()
            emit(emptyList())

            jellyfin.discovery.discoverLocalServers(timeout = 5000, maxServers = 10).collect {
                serverInfo ->
                Timber.d("Discovered server: ${serverInfo.name} at ${serverInfo.address}")

                val server =
                    Server(
                        id = serverInfo.id ?: UUID.randomUUID().toString(),
                        name = serverInfo.name ?: "Jellyfin Server",
                        version = null,
                        address = serverInfo.address ?: "",
                    )
                discoveredServers.add(server)
                emit(discoveredServers.toList())
            }

            Timber.d("Discovery complete: ${discoveredServers.size} servers found")
        } catch (e: Exception) {
            Timber.e(e, "Failed to discover servers")
            emit(emptyList())
        }
    }

    override suspend fun testServerConnection(serverAddress: String): ServerConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                val originalUrl = _currentBaseUrl.value
                val originalConnected = _isConnected.value
                val originalServer = _currentServer.value

                try {
                    apiClient.update(baseUrl = serverAddress)
                    _currentBaseUrl.value = serverAddress

                    val systemApi = SystemApi(apiClient)
                    val response = systemApi.getPublicSystemInfo()
                    val systemInfo = response.content

                    if (systemInfo != null) {
                        val server =
                            Server(
                                id = systemInfo.id ?: UUID.randomUUID().toString(),
                                name = systemInfo.serverName ?: "Jellyfin Server",
                                version = systemInfo.version,
                                address = serverAddress,
                            )

                        apiClient.update(baseUrl = originalUrl)
                        _currentBaseUrl.value = originalUrl
                        _isConnected.value = originalConnected
                        _currentServer.value = originalServer

                        ServerConnectionResult.Success(
                            server = server,
                            serverAddress = serverAddress,
                            version = systemInfo.version ?: "Unknown",
                            isQuickConnectEnabled = systemInfo.startupWizardCompleted == true,
                        )
                    } else {
                        apiClient.update(baseUrl = originalUrl)
                        _currentBaseUrl.value = originalUrl
                        _isConnected.value = originalConnected
                        _currentServer.value = originalServer

                        ServerConnectionResult.Error("No system information received from server")
                    }
                } catch (e: Exception) {
                    apiClient.update(baseUrl = originalUrl)
                    _currentBaseUrl.value = originalUrl
                    _isConnected.value = originalConnected
                    _currentServer.value = originalServer
                    throw e
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "API error testing server connection")
                ServerConnectionResult.Error("Server error: ${e.message ?: "Unknown API error"}")
            } catch (e: Exception) {
                Timber.e(e, "Network error testing server connection")
                ServerConnectionResult.Error(
                    "Failed to connect: ${e.message ?: "Check server address and network connection"}"
                )
            }
        }
    }

    override suspend fun getServerInfo(): Server? {
        return withContext(Dispatchers.IO) {
            try {
                val systemApi = SystemApi(apiClient)
                val response = systemApi.getPublicSystemInfo()
                val systemInfo = response.content

                systemInfo?.let {
                    Server(
                        id = it.id ?: UUID.randomUUID().toString(),
                        name = it.serverName ?: "Jellyfin Server",
                        version = it.version,
                        address = _currentBaseUrl.value,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get server info")
                null
            }
        }
    }

    override suspend fun refreshServerInfo() {
        withContext(Dispatchers.IO) {
            try {
                val systemApi = SystemApi(apiClient)
                val response = systemApi.getPublicSystemInfo()
                val systemInfo = response.content

                if (systemInfo != null) {
                    val server =
                        Server(
                            id = systemInfo.id ?: UUID.randomUUID().toString(),
                            name = systemInfo.serverName ?: "Jellyfin Server",
                            version = systemInfo.version,
                            address = _currentBaseUrl.value,
                        )
                    _currentServer.value = server
                    _isConnected.value = true
                    Timber.d("Server info refreshed: ${server.name}")
                } else {
                    Timber.e("Failed to refresh server info - no system info returned")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh server info")
            }
        }
    }

    override fun isConnectedToServer(): Boolean = _isConnected.value

    override fun getCurrentServer(): Server? = _currentServer.value

    override fun disconnect() {
        _isConnected.value = false
        _currentServer.value = null
        _currentBaseUrl.value = ""
    }

    override fun buildImageUrl(
        itemId: String,
        imageType: String,
        imageIndex: Int,
        tag: String?,
        maxWidth: Int?,
        maxHeight: Int?,
        quality: Int?,
    ): String {
        val baseUrl = _currentBaseUrl.value
        if (baseUrl.isBlank()) return ""

        val params = mutableListOf<String>()

        if (maxWidth != null) params.add("maxWidth=$maxWidth")
        if (maxHeight != null) params.add("maxHeight=$maxHeight")
        if (quality != null) params.add("quality=$quality")
        if (tag != null) params.add("tag=$tag")

        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""

        return "$baseUrl/Items/$itemId/Images/$imageType/$imageIndex$queryString"
    }

    override fun buildStreamUrl(
        itemId: String,
        mediaSourceId: String,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        videoStreamIndex: Int?,
        accessToken: String?,
    ): String {
        val baseUrl = _currentBaseUrl.value
        if (baseUrl.isBlank()) return ""

        val params = mutableListOf<String>()

        params.add("MediaSourceId=$mediaSourceId")
        params.add("Static=true")

        if (maxBitrate != null) params.add("maxStreamingBitrate=$maxBitrate")
        if (accessToken != null) params.add("api_key=$accessToken")

        val queryString = params.joinToString("&")

        return "$baseUrl/Videos/$itemId/stream?$queryString"
    }

    sealed class ServerConnectionResult {
        data class Success(
            val server: Server,
            val serverAddress: String,
            val version: String,
            val isQuickConnectEnabled: Boolean,
        ) : ServerConnectionResult()

        data class Error(val message: String) : ServerConnectionResult()
    }
}
