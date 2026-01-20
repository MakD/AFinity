package com.makd.afinity.data.manager

import android.content.Context
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.auth.JellyfinAuthRepository
import com.makd.afinity.data.repository.server.ServerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class Session(
    val serverId: String,
    val userId: UUID,
    val serverUrl: String,
    val user: User? = null,
    val server: Server? = null
)

sealed class ConnectionState {
    data class Online(val session: Session) : ConnectionState()
    data class Offline(val session: Session, val lastSyncTime: Long) : ConnectionState()
    data object Disconnected : ConnectionState()
}

@Singleton
class SessionManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverRepository: ServerRepository,
    private val databaseRepository: DatabaseRepository,
    private val sessionPreferences: SessionPreferences,
    private val securePrefsRepository: SecurePreferencesRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    @ApplicationContext private val context: Context
) {
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val apiClients = mutableMapOf<String, ApiClient>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            restoreLastSession()
        }
    }

    suspend fun startSession(
        serverUrl: String,
        serverId: String,
        userId: UUID,
        accessToken: String,
        caller: String = "Unknown"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = databaseRepository.getUser(userId)
            val server = databaseRepository.getServer(serverId)

            serverRepository.setBaseUrl(serverUrl)

            val apiClient = getOrCreateApiClient(serverId, serverUrl)
            apiClient.update(baseUrl = serverUrl, accessToken = accessToken)

            securePrefsRepository.saveAuthenticationData(
                accessToken = accessToken,
                userId = userId,
                serverId = serverId,
                serverUrl = serverUrl,
                username = user?.name ?: "User"
            )

            if (user != null) {
                securePrefsRepository.saveServerUserToken(
                    serverId = serverId,
                    userId = userId,
                    accessToken = accessToken,
                    username = user.name,
                    serverUrl = serverUrl
                )
            }

            if (user != null) {
                (authRepository as? JellyfinAuthRepository)?.setSessionActive(user)
                Timber.d("Synced AuthRepository with user: ${user.name}")
            }

            jellyseerrRepository.setActiveJellyfinSession(serverId, userId)
            Timber.d("Linked Jellyseerr session for user: $userId")

            val session = Session(
                serverId = serverId,
                userId = userId,
                serverUrl = serverUrl,
                user = user,
                server = server
            )
            _currentSession.value = session
            _connectionState.value = ConnectionState.Online(session)

            sessionPreferences.saveActiveSession(serverId, userId, serverUrl)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start session")
            _connectionState.value = ConnectionState.Disconnected
            Result.failure(e)
        }
    }

    fun updateCurrentUser(user: User) {
        val current = _currentSession.value
        if (current != null && current.userId == user.id) {
            _currentSession.value = current.copy(user = user)
            Timber.d("Updated current user in session: ${user.name} (Tag: ${user.primaryImageTag})")
        }
    }

    suspend fun getOrRestoreApiClient(serverId: String): ApiClient? {
        apiClients[serverId]?.let { return it }

        val server = databaseRepository.getServer(serverId) ?: return null
        val tokens = securePrefsRepository.getAllServerUserTokens()
        val tokenInfo = tokens.find { it.serverId == serverId } ?: return null

        Timber.d("Restoring ApiClient for background work: Server $serverId")
        val client = getOrCreateApiClient(serverId, tokenInfo.serverUrl)
        client.update(baseUrl = tokenInfo.serverUrl, accessToken = tokenInfo.accessToken)
        return client
    }

    fun getServerIdForUrl(url: String): String? {
        return runBlocking {
            securePrefsRepository.getAllServerUserTokens().find { it.serverUrl == url }?.serverId
        }
    }

    suspend fun switchServer(serverId: String): Result<Unit> {
        val server = databaseRepository.getServer(serverId) ?: return Result.failure(
            IllegalArgumentException("Server $serverId not found in database")
        )

        val lastUserId = securePrefsRepository.getLastUserIdForServer(serverId)
            ?: return Result.failure(
                IllegalStateException("No user found for server $serverId")
            )

        val token = securePrefsRepository.getServerUserToken(serverId, lastUserId)
            ?: return Result.failure(
                IllegalStateException("No authentication token found")
            )

        return startSession(server.address, serverId, lastUserId, token, caller = "switchServer")
    }

    suspend fun switchUser(serverId: String, userId: UUID): Result<Unit> {
        val token = securePrefsRepository.getServerUserToken(serverId, userId)
            ?: return Result.failure(
                IllegalStateException("No token found for user $userId on server $serverId")
            )
        val serverUrl = securePrefsRepository.getAllServerUserTokens()
            .find { it.serverId == serverId && it.userId == userId }?.serverUrl
            ?: return Result.failure(IllegalStateException("Could not find Server URL for target user"))

        return startSession(serverUrl, serverId, userId, token, caller = "switchUser")
    }

    private suspend fun restoreLastSession() {
        Timber.d("Attempting to restore last session...")

        val savedSession = sessionPreferences.getActiveSession() ?: run {
            Timber.d("No saved session found")
            return
        }

        val serverId = savedSession.serverId
        val userId = savedSession.userId
        val serverUrl = savedSession.serverUrl

        val token = securePrefsRepository.getServerUserToken(
            serverId,
            userId
        ) ?: run {
            Timber.w("Saved session exists but no token found - clearing")
            sessionPreferences.clearSession()
            return
        }

        val result = startSession(
            serverUrl,
            serverId,
            userId,
            token,
            caller = "SessionManager.restoreLastSession"
        )

        if (result.isSuccess) {
            Timber.d("Session restored successfully")
        } else {
            Timber.w("Failed to restore session: ${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun enterOfflineMode() {
        val session = _currentSession.value ?: run {
            Timber.w("Cannot enter offline mode - no session")
            return
        }

        _connectionState.value = ConnectionState.Offline(
            session = session,
            lastSyncTime = System.currentTimeMillis()
        )
        Timber.d("Entered offline mode")
    }

    suspend fun returnOnline() {
        val session = _currentSession.value ?: run {
            Timber.w("Cannot return online - no session")
            return
        }

        _connectionState.value = ConnectionState.Online(session)
        Timber.d("Returned to online mode")
    }

    suspend fun logout() {
        val session = _currentSession.value ?: run {
            Timber.w("No session to logout from")
            return
        }

        sessionPreferences.clearSession()
        jellyseerrRepository.clearActiveSession()
        _currentSession.value = null
        _connectionState.value = ConnectionState.Disconnected
        authRepository.clearAllAuthData()

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
            this.clientInfo = ClientInfo(
                name = "AFinity",
                version = BuildConfig.VERSION_NAME
            )
        }
        val newClient = jellyfin.createApi(baseUrl = serverUrl)
        apiClients[serverId] = newClient
        return newClient
    }

    fun getCurrentApiClient(): ApiClient? {
        val session = _currentSession.value ?: return null
        return apiClients[session.serverId]
    }

    fun getApiClientForServer(serverId: String): ApiClient? {
        return apiClients[serverId]
    }

    suspend fun getAllSavedSessions(): List<SavedSession> {
        return withContext(Dispatchers.IO) {
            val tokens = securePrefsRepository.getAllServerUserTokens()
            tokens.map { token ->
                SavedSession(
                    serverId = token.serverId,
                    userId = token.userId,
                    serverUrl = token.serverUrl
                )
            }
        }
    }

    suspend fun hasSavedSessions(): Boolean {
        return getAllSavedSessions().isNotEmpty()
    }

    fun isOnline(): Boolean {
        return _connectionState.value is ConnectionState.Online
    }

    fun hasActiveSession(): Boolean {
        return _currentSession.value != null
    }
}