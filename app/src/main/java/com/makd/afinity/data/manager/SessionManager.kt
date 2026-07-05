package com.makd.afinity.data.manager

import android.content.Context
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.server.AddressResolutionResult
import com.makd.afinity.data.repository.server.ServerAddressResolver
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.api.operations.UserApi
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class Session(
    val serverId: String,
    val userId: UUID,
    val serverUrl: String,
    val user: User? = null,
    val server: Server? = null,
    val isAdmin: Boolean? = null,
)

@Singleton
class SessionManager
@Inject
constructor(
    private val serverRepository: ServerRepository,
    private val databaseRepository: DatabaseRepository,
    private val securePrefsRepository: SecurePreferencesRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val serverAddressResolver: ServerAddressResolver,
    private val okHttpFactory: OkHttpFactory,
    private val jellyfin: Jellyfin,
    @param:ApplicationContext private val context: Context,
    @ApplicationScope private val sessionScope: CoroutineScope,
) {
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _isServerReachable = MutableStateFlow(true)
    val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()

    /**
     * True while switchUser() is running. currentSession is overwritten in place during a switch
     * (never nulled), so isAuthenticated never toggles false->true on its own; UI layers that need
     * to react to an account switch (e.g. showing a loading screen) should observe this instead.
     */
    private val _isSwitchingSession = MutableStateFlow(false)
    val isSwitchingSession: StateFlow<Boolean> = _isSwitchingSession.asStateFlow()
    private val switchingCallCount = AtomicInteger(0)

    private val apiClients = ConcurrentHashMap<String, ApiClient>()
    private val sessionMutex = Mutex()

    suspend fun startSession(
        serverUrl: String,
        serverId: String,
        userId: UUID,
        accessToken: String,
        urlPreValidated: Boolean = false,
    ): Result<Unit> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val user = databaseRepository.getUser(userId)
                val server = databaseRepository.getServer(serverId)

                val sawUnauthorized = AtomicBoolean(false)
                val validator: suspend (String) -> Boolean = { address ->
                    try {
                        val tempClient =
                            jellyfin.createApi(baseUrl = address).also {
                                it.update(accessToken = accessToken)
                            }
                        val response =
                            withTimeoutOrNull(3000L) { UserApi(tempClient).getCurrentUser() }
                        response?.content != null
                    } catch (e: InvalidStatusException) {
                        if (e.status == 401) sawUnauthorized.set(true)
                        false
                    } catch (_: Exception) {
                        false
                    }
                }

                val resolvedUrl =
                    if (urlPreValidated) {
                        _isServerReachable.value = true
                        serverUrl
                    } else try {
                        val result = serverAddressResolver.resolveAddress(serverId, validator)
                        if (result is AddressResolutionResult.Success) {
                            Timber.d(
                                "Resolved server address: ${result.address} (saved: $serverUrl)"
                            )
                            _isServerReachable.value = true
                            result.address
                        } else if (sawUnauthorized.get()) {
                            Timber.e("Token rejected by server during address resolution (401)")
                            return@withContext Result.failure(InvalidStatusException(401, null))
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
                        isAdmin = user?.isAdmin == true,
                    )

                securePrefsRepository.saveActiveSession(serverId, userId, resolvedUrl)

                try {
                    jellyseerrRepository.setActiveJellyfinSession(serverId, userId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to link Jellyseerr session")
                }
                try {
                    audiobookshelfRepository.setActiveJellyfinSession(serverId, userId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to link Audiobookshelf session")
                }

                sessionScope.launch {
                    try {
                        val userDto = UserApi(apiClient).getCurrentUser().content
                        val isAdmin = userDto.policy?.isAdministrator == true
                        val refreshedUser =
                            user?.copy(isAdmin = isAdmin)
                                ?: User(
                                    id = userDto.id,
                                    name = userDto.name ?: "",
                                    serverId = serverId,
                                    accessToken = accessToken,
                                    primaryImageTag = userDto.primaryImageTag,
                                    isAdmin = isAdmin,
                                )
                        databaseRepository.insertUser(refreshedUser)
                        val current = _currentSession.value
                        if (current?.serverId == serverId && current.userId == userId) {
                            _currentSession.value =
                                current.copy(isAdmin = isAdmin, user = refreshedUser)
                        }
                        Timber.d("Admin status refreshed from policy: isAdmin=$isAdmin")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to refresh user policy; using cached isAdmin")
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start session")
                Result.failure(e)
            }
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
        securePrefsRepository.saveActiveSession(current.serverId, current.userId, newUrl)

        Timber.d("Session URL updated: $newUrl")
    }

    suspend fun getOrRestoreApiClient(serverId: String): ApiClient? {
        apiClients[serverId]?.let {
            return it
        }

        databaseRepository.getServer(serverId) ?: return null
        val tokens = securePrefsRepository.getAllServerUserTokens()
        val tokenInfo = tokens.find { it.serverId == serverId } ?: return null

        Timber.d("Restoring ApiClient for background work: Server $serverId")
        val address =
            when (val result = serverAddressResolver.resolveAddress(serverId)) {
                is AddressResolutionResult.Success -> result.address
                is AddressResolutionResult.AllFailed -> tokenInfo.serverUrl
            }
        val client = getOrCreateApiClient(serverId, address)
        client.update(baseUrl = address, accessToken = tokenInfo.accessToken)
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

        switchingCallCount.incrementAndGet()
        _isSwitchingSession.value = true
        val switchStartTime = System.currentTimeMillis()
        return try {
            startSession(serverUrl, serverId, userId, token)
        } finally {
            val remaining = MIN_SWITCH_DISPLAY_MS - (System.currentTimeMillis() - switchStartTime)
            if (remaining > 0) delay(remaining)
            if (switchingCallCount.decrementAndGet() == 0) {
                _isSwitchingSession.value = false
            }
        }
    }

    suspend fun logout() = sessionMutex.withLock {
        if (_currentSession.value == null) {
            Timber.w("No session to logout from")
            return@withLock
        }

        securePrefsRepository.clearActiveSession()
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
        val newClient = jellyfin.createApi(baseUrl = serverUrl)
        apiClients[serverId] = newClient
        return newClient
    }

    fun getCurrentApiClient(): ApiClient? {
        val session = _currentSession.value ?: return null
        return apiClients[session.serverId]
    }

    companion object {
        private const val MIN_SWITCH_DISPLAY_MS = 500L
    }
}
