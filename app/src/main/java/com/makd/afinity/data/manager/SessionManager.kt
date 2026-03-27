package com.makd.afinity.data.manager

import android.content.Context
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.server.AddressResolutionResult
import com.makd.afinity.data.repository.server.ServerAddressResolver
import com.makd.afinity.data.repository.server.ServerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class Session(
    val serverId: String,
    val userId: UUID,
    val serverUrl: String,
    val user: User? = null,
    val server: Server? = null,
)

@Singleton
class SessionManager
@Inject
constructor(
    private val serverRepository: ServerRepository,
    private val databaseRepository: DatabaseRepository,
    private val sessionPreferences: SessionPreferences,
    private val securePrefsRepository: SecurePreferencesRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val serverAddressResolver: ServerAddressResolver,
    private val okHttpFactory: OkHttpFactory,
    @param:ApplicationContext private val context: Context,
) {
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _isServerReachable = MutableStateFlow(true)
    val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()

    private val apiClients = ConcurrentHashMap<String, ApiClient>()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun startSession(
        serverUrl: String,
        serverId: String,
        userId: UUID,
        accessToken: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val user = databaseRepository.getUser(userId)
                val server = databaseRepository.getServer(serverId)
                val resolvedUrl =
                    try {
                        val result = serverAddressResolver.resolveAddress(serverId)
                        if (result is AddressResolutionResult.Success) {
                            Timber.d(
                                "Resolved server address: ${result.address} (saved: $serverUrl)"
                            )
                            _isServerReachable.value = true
                            result.address
                        } else {
                            Timber.w(
                                "Address resolution failed, starting in offline mode. Saved URL: $serverUrl"
                            )
                            _isServerReachable.value = false
                            serverUrl
                        }
                    } catch (e: Exception) {
                        Timber.w(
                            e,
                            "Address resolution error, starting in offline mode. Saved URL: $serverUrl",
                        )
                        _isServerReachable.value = false
                        serverUrl
                    }

                serverRepository.setBaseUrl(resolvedUrl)

                val apiClient = getOrCreateApiClient(serverId, resolvedUrl)
                apiClient.update(baseUrl = resolvedUrl, accessToken = accessToken)

                securePrefsRepository.saveAuthenticationData(
                    accessToken = accessToken,
                    userId = userId,
                    serverId = serverId,
                    serverUrl = resolvedUrl,
                    username = user?.name ?: "User",
                )

                if (user != null) {
                    securePrefsRepository.saveServerUserToken(
                        serverId = serverId,
                        userId = userId,
                        accessToken = accessToken,
                        username = user.name,
                        serverUrl = resolvedUrl,
                    )
                }

                _currentSession.value =
                    Session(
                        serverId = serverId,
                        userId = userId,
                        serverUrl = resolvedUrl,
                        user = user,
                        server = server,
                    )

                sessionPreferences.saveActiveSession(serverId, userId, resolvedUrl)
                sessionScope.launch {
                    try {
                        jellyseerrRepository.setActiveJellyfinSession(serverId, userId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to link Jellyseerr session")
                    }
                }
                sessionScope.launch {
                    try {
                        audiobookshelfRepository.setActiveJellyfinSession(serverId, userId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to link Audiobookshelf session")
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start session")
                Result.failure(e)
            }
        }

    fun setServerReachable(reachable: Boolean) {
        _isServerReachable.value = reachable
    }

    suspend fun updateSessionUrl(newUrl: String) {
        val current = _currentSession.value ?: return
        if (current.serverUrl == newUrl) return

        _currentSession.value = current.copy(serverUrl = newUrl)
        apiClients[current.serverId]?.update(baseUrl = newUrl)
        sessionPreferences.saveActiveSession(current.serverId, current.userId, newUrl)

        Timber.d("Session URL updated: $newUrl")
    }

    fun updateCurrentUser(user: User) {
        val current = _currentSession.value
        if (current != null && current.userId == user.id) {
            _currentSession.value = current.copy(user = user)
            Timber.d("Updated current user in session: ${user.name} (Tag: ${user.primaryImageTag})")
        }
    }

    suspend fun getOrRestoreApiClient(serverId: String): ApiClient? {
        apiClients[serverId]?.let {
            return it
        }

        databaseRepository.getServer(serverId) ?: return null
        val tokens = securePrefsRepository.getAllServerUserTokens()
        val tokenInfo = tokens.find { it.serverId == serverId } ?: return null

        Timber.d("Restoring ApiClient for background work: Server $serverId")
        val client = getOrCreateApiClient(serverId, tokenInfo.serverUrl)
        client.update(baseUrl = tokenInfo.serverUrl, accessToken = tokenInfo.accessToken)
        return client
    }

    suspend fun switchUser(serverId: String, userId: UUID): Result<Unit> {
        val token =
            securePrefsRepository.getServerUserToken(serverId, userId)
                ?: return Result.failure(
                    IllegalStateException("No token found for user $userId on server $serverId")
                )
        val serverUrl =
            securePrefsRepository
                .getAllServerUserTokens()
                .find { it.serverId == serverId && it.userId == userId }
                ?.serverUrl
                ?: return Result.failure(
                    IllegalStateException("Could not find Server URL for target user")
                )

        return startSession(serverUrl, serverId, userId, token)
    }

    suspend fun logout() {
        if (_currentSession.value == null) {
            Timber.w("No session to logout from")
            return
        }

        sessionPreferences.clearSession()
        jellyseerrRepository.clearActiveSession()
        audiobookshelfRepository.clearActiveSession()
        _currentSession.value = null
        securePrefsRepository.clearAuthenticationData()
        apiClients.clear()

        Timber.d("Logged out successfully (token kept for re-login)")
    }

    private fun getOrCreateApiClient(serverId: String, serverUrl: String): ApiClient {
        val existingClient = apiClients[serverId]
        if (existingClient != null) {
            if (existingClient.baseUrl != serverUrl) {
                existingClient.update(baseUrl = serverUrl)
            }
            return existingClient
        }

        Timber.d("Creating NEW ApiClient for server: $serverId with baseUrl: $serverUrl")
        val jellyfin = createJellyfin {
            this.context = this@SessionManager.context
            this.clientInfo = ClientInfo(name = "AFinity", version = BuildConfig.VERSION_NAME)
            this.apiClientFactory = okHttpFactory
            this.socketConnectionFactory = okHttpFactory
        }
        val newClient = jellyfin.createApi(baseUrl = serverUrl)
        apiClients[serverId] = newClient
        return newClient
    }

    fun getCurrentApiClient(): ApiClient? {
        val session = _currentSession.value ?: return null
        return apiClients[session.serverId]
    }
}
