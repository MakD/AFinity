package com.makd.afinity.data.repository.server

import android.content.Context
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.network.UrlCandidates
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.di.ProberClient
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.SystemApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
class JellyfinServerRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val jellyfin: Jellyfin,
    @param:ProberClient private val proberJellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val sessionManagerProvider: Provider<SessionManager>,
    private val databaseRepository: DatabaseRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val serverAddressResolverProvider: Provider<ServerAddressResolver>,
    @ApplicationScope private val scope: CoroutineScope,
) : ServerRepository {

    private val sessionManager: SessionManager
        get() = sessionManagerProvider.get()

    private val serverAddressResolver: ServerAddressResolver
        get() = serverAddressResolverProvider.get()

    private val _currentBaseUrl = MutableStateFlow("")
    override val currentBaseUrl: StateFlow<String> = _currentBaseUrl.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentServer = MutableStateFlow<Server?>(null)
    override val currentServer: StateFlow<Server?> = _currentServer.asStateFlow()

    private val _unsupportedServerVersion = MutableStateFlow<String?>(null)
    override val unsupportedServerVersion: StateFlow<String?> =
        _unsupportedServerVersion.asStateFlow()

    private val reconnectMutex = Mutex()

    init {
        scope.launch {
            sessionManager.currentSession.collect { session ->
                if (session != null) {
                    try {
                        val server = databaseRepository.getServer(session.serverId)
                        if (server != null) {
                            _currentServer.value = server
                            _currentBaseUrl.value = session.serverUrl
                            _isConnected.value = sessionManager.isServerReachable.value
                            Timber.d(
                                "JellyfinServerRepository: Updated current server to ${server.name} (${server.id}). Connected: ${_isConnected.value}"
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
        scope.launch {
            merge(
                    networkConnectivityMonitor.networkSwitchEvents,
                    networkConnectivityMonitor.networkDropEvents,
                )
                .debounce(500)
                .collectLatest {
                    val session = sessionManager.currentSession.value ?: return@collectLatest
                    if (_currentBaseUrl.value.isBlank()) return@collectLatest
                    if (!networkConnectivityMonitor.isCurrentlyConnected()) return@collectLatest
                    if (!tryResolveAndConnect()) {
                        confirmUnreachable(session.serverId)
                    }
                }
        }
        scope.launch {
            combine(
                    sessionManager.isServerReachable,
                    networkConnectivityMonitor.isNetworkAvailable,
                ) { reachable, networkAvailable ->
                    !reachable && networkAvailable
                }
                .distinctUntilChanged()
                .collectLatest { shouldRecover ->
                    if (!shouldRecover) return@collectLatest
                    if (sessionManager.currentSession.value == null) return@collectLatest
                    var delayMs = RECONNECT_INITIAL_DELAY_MS
                    while (
                        currentCoroutineContext().isActive &&
                            !sessionManager.isServerReachable.value
                    ) {
                        if (
                            networkConnectivityMonitor.isCurrentlyConnected() &&
                                tryResolveAndConnect()
                        ) {
                            break
                        }
                        delay(delayMs)
                        delayMs = (delayMs * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)
                    }
                }
        }
    }

    private suspend fun tryResolveAndConnect(): Boolean = reconnectMutex.withLock {
        val session = sessionManager.currentSession.value ?: return@withLock false
        if (_currentBaseUrl.value.isBlank()) return@withLock false
        val serverId = session.serverId
        try {
            val result = serverAddressResolver.resolveAddress(serverId)
            if (sessionManager.currentSession.value?.serverId != serverId) {
                Timber.d("Session changed during re-resolution, discarding result for $serverId")
                return@withLock false
            }
            when (result) {
                is AddressResolutionResult.Success -> {
                    sessionManager.setServerReachable(true)
                    if (result.address != _currentBaseUrl.value) {
                        Timber.d(
                            "Reconnected: updating URL from ${_currentBaseUrl.value} to ${result.address}"
                        )
                        setBaseUrl(result.address)
                        sessionManager.updateSessionUrl(result.address)
                    }
                    true
                }
                is AddressResolutionResult.AllFailed -> {
                    Timber.w("Re-resolution failed for all addresses")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to re-resolve server address")
            false
        }
    }

    private suspend fun confirmUnreachable(serverId: String) {
        if (!sessionManager.isServerReachable.value) return
        delay(UNREACHABLE_CONFIRM_DELAY_MS)
        if (!networkConnectivityMonitor.isCurrentlyConnected()) return
        if (sessionManager.currentSession.value?.serverId != serverId) return
        if (!sessionManager.isServerReachable.value) return
        if (tryResolveAndConnect()) return
        if (sessionManager.currentSession.value?.serverId != serverId) return
        Timber.w("Sustained re-resolution failure, marking server unreachable")
        sessionManager.setServerReachable(false)
    }

    override suspend fun forceReconnect(): Boolean = tryResolveAndConnect()

    override fun getBaseUrl(): String {
        return apiClient.baseUrl ?: ""
    }

    override suspend fun setBaseUrl(baseUrl: String) {
        try {
            apiClient.update(baseUrl = baseUrl)

            _currentBaseUrl.value = baseUrl
            _isConnected.value = sessionManager.isServerReachable.value
            _unsupportedServerVersion.value = null

            Timber.d("Updated base URL to: $baseUrl")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set base URL: $baseUrl")
            throw e
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
            val urlsToTry = UrlCandidates.jellyfin(serverAddress)

            var lastException: Exception? = null

            for (url in urlsToTry) {
                try {
                    val testClient = jellyfin.createApi(baseUrl = url)
                    val systemApi = SystemApi(testClient)
                    val response = systemApi.getPublicSystemInfo()
                    val systemInfo = response.content

                    if (systemInfo != null) {
                        if (!ServerVersionSupport.isSupported(systemInfo.version)) {
                            Timber.w(
                                "Rejecting server at $url: version ${systemInfo.version} is below ${ServerVersionSupport.minimum}"
                            )
                            return@withContext ServerConnectionResult.Error(
                                ServerVersionSupport.unsupportedMessage(
                                    context,
                                    systemInfo.version,
                                )
                            )
                        }
                        val server =
                            Server(
                                id = systemInfo.id ?: UUID.randomUUID().toString(),
                                name = systemInfo.serverName ?: "Jellyfin Server",
                                version = systemInfo.version,
                                address = url,
                            )
                        return@withContext ServerConnectionResult.Success(
                            server = server,
                            serverAddress = url,
                            version = systemInfo.version ?: "Unknown",
                            isQuickConnectEnabled = systemInfo.startupWizardCompleted == true,
                        )
                    }
                } catch (e: ApiClientException) {
                    lastException = e
                } catch (e: Exception) {
                    lastException = e
                }
            }

            if (lastException is ApiClientException) {
                Timber.e(lastException, "API error testing server connection")
                ServerConnectionResult.Error(
                    "Server error: ${lastException.message ?: "Unknown API error"}"
                )
            } else {
                Timber.e(lastException, "Network error testing server connection")
                ServerConnectionResult.Error(
                    "Failed to connect: ${lastException?.message ?: "Check server address and network connection"}"
                )
            }
        }
    }

    override suspend fun pingServer(address: String, timeoutMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result =
                    withTimeoutOrNull(timeoutMs) {
                        val testClient = proberJellyfin.createApi(baseUrl = address)
                        val systemApi = SystemApi(testClient)
                        systemApi.getPingSystem()
                        true
                    }
                result == true
            } catch (e: Exception) {
                Timber.d("Ping failed for $address: ${e.message}")
                false
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
                    _unsupportedServerVersion.value =
                        if (ServerVersionSupport.isSupported(systemInfo.version)) {
                            null
                        } else {
                            Timber.e(
                                "Connected server is Jellyfin ${systemInfo.version}, below the required ${ServerVersionSupport.minimum}"
                            )
                            systemInfo.version
                        }
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
        _unsupportedServerVersion.value = null
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

    private companion object {
        const val RECONNECT_INITIAL_DELAY_MS = 2_000L
        const val RECONNECT_MAX_DELAY_MS = 30_000L
        const val UNREACHABLE_CONFIRM_DELAY_MS = 4_000L
    }
}
