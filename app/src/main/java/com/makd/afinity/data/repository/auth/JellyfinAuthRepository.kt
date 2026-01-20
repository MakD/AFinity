package com.makd.afinity.data.repository.auth

import com.makd.afinity.core.AppConstants
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.auth.QuickConnectState
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.operations.QuickConnectApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class JellyfinAuthRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val sessionManagerProvider: Provider<SessionManager>,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val databaseRepository: DatabaseRepository
) : AuthRepository {

    private val sessionManager: SessionManager
        get() = sessionManagerProvider.get()

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        Timber.d("AuthRepository initialized")
    }

    override suspend fun restoreAuthenticationState(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!securePreferencesRepository.hasValidAuthData()) {
                    Timber.d("No valid encrypted auth data found, user needs to login")
                    return@withContext false
                }

                val accessToken = securePreferencesRepository.getAccessToken()
                val userId = securePreferencesRepository.getSavedUserId()
                val serverId = securePreferencesRepository.getSavedServerId()
                val serverUrl = securePreferencesRepository.getSavedServerUrl()
                val username = securePreferencesRepository.getSavedUsername()

                if (accessToken.isNullOrBlank() || userId.isNullOrBlank() ||
                    serverUrl.isNullOrBlank() || username.isNullOrBlank()
                ) {
                    Timber.w("Incomplete encrypted auth data found, clearing and requiring fresh login")
                    clearAllAuthData()
                    return@withContext false
                }

                val userUuid = try {
                    UUID.fromString(userId)
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Invalid UUID format in saved data")
                    clearAllAuthData()
                    return@withContext false
                }

                val user = User(
                    id = userUuid,
                    name = username,
                    serverId = serverId ?: "",
                    accessToken = accessToken,
                    primaryImageTag = null
                )

                apiClient.update(baseUrl = serverUrl, accessToken = accessToken)
                _currentUser.value = user
                _isAuthenticated.value = true

                sessionManager.startSession(
                    serverUrl = serverUrl,
                    serverId = serverId ?: "",
                    userId = userUuid,
                    accessToken = accessToken
                ).onFailure { e ->
                    Timber.w(e, "SessionManager start failed during restore (non-fatal)")
                }

                Timber.d("Optimistically restored session for user: $username")

                val isConnected = networkConnectivityMonitor.isCurrentlyConnected()
                if (!isConnected) {
                    Timber.d("Device offline - keeping optimistic session")
                    return@withContext true
                }

                try {
                    val response = withTimeoutOrNull(5000L) {
                        val userApi = UserApi(apiClient)
                        userApi.getCurrentUser()
                    }

                    if (response?.content != null) {
                        val userDto = response.content!!
                        val updatedUser = user.copy(primaryImageTag = userDto.primaryImageTag)
                        _currentUser.value = updatedUser
                        sessionManager.updateCurrentUser(updatedUser)
                        try {
                            databaseRepository.insertUser(updatedUser)
                        } catch (e: Exception) {
                            Timber.w("Failed to update user in DB during restore")
                        }

                        Timber.d("Session validated with server successfully (ImageTag: ${updatedUser.primaryImageTag})")
                        return@withContext true
                    } else {
                        Timber.w("Server validation timed out - keeping optimistic session (assuming server reachable but slow)")
                        return@withContext true
                    }
                } catch (e: InvalidStatusException) {
                    if (e.status == 401) {
                        Timber.e("Token rejected by server (401) - Logging out")
                        clearAllAuthData()
                        return@withContext false
                    }

                    Timber.w("Server returned error ${e.status} - keeping optimistic session")
                    return@withContext true
                } catch (e: ApiClientException) {
                    Timber.w(e, "ApiClient error during validation - keeping optimistic session")
                    return@withContext true
                } catch (e: Exception) {
                    Timber.e(e, "Unexpected error during validation - keeping optimistic session")
                    return@withContext true
                }

            } catch (e: Exception) {
                Timber.e(e, "Critical error during auth restoration")
                return@withContext false
            }
        }
    }

    override suspend fun hasValidSavedAuth(): Boolean {
        return securePreferencesRepository.hasValidAuthData()
    }

    override suspend fun saveAuthenticationData(
        authResult: AuthenticationResult,
        serverUrl: String,
        username: String
    ) {
        try {
            val accessToken = authResult.accessToken ?: return
            val userId = authResult.user?.id ?: return
            val serverId = authResult.serverId ?: ""

            securePreferencesRepository.saveAuthenticationData(
                accessToken = accessToken,
                userId = userId,
                serverId = serverId,
                serverUrl = serverUrl,
                username = username
            )

            Timber.d("Saved authentication data to encrypted storage for user: $username")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save authentication data to encrypted storage")
        }
    }

    override suspend fun clearAllAuthData() {
        try {
            securePreferencesRepository.clearAuthenticationData()
            clearAuthState()
            Timber.d("Cleared all encrypted authentication data")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear encrypted authentication data")
        }
    }

    override suspend fun authenticateByName(
        username: String,
        password: String
    ): AuthRepository.AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val userApi = UserApi(apiClient)
                val authRequest = org.jellyfin.sdk.model.api.AuthenticateUserByName(
                    username = username,
                    pw = password
                )
                val response = userApi.authenticateUserByName(authRequest)

                val authResult = response.content
                if (authResult != null) {
                    val serverUrl = apiClient.baseUrl ?: ""

                    handleSuccessfulAuth(authResult, username)
                    saveAuthenticationData(authResult, serverUrl, username)

                    AuthRepository.AuthResult.Success(authResult)
                } else {
                    AuthRepository.AuthResult.Error("No authentication result returned")
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Authentication failed")
                AuthRepository.AuthResult.Error("Authentication failed: ${e.message ?: "Unknown error"}")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during authentication")
                AuthRepository.AuthResult.Error("Authentication failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    override suspend fun authenticateWithQuickConnect(secret: String): AuthRepository.AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val userApi = UserApi(apiClient)
                val quickConnectRequest = org.jellyfin.sdk.model.api.QuickConnectDto(
                    secret = secret
                )
                val response = userApi.authenticateWithQuickConnect(quickConnectRequest)

                val authResult = response.content
                if (authResult != null) {
                    val serverUrl = apiClient.baseUrl ?: ""
                    val username = authResult.user?.name ?: "QuickConnect User"

                    handleSuccessfulAuth(authResult, username)
                    saveAuthenticationData(authResult, serverUrl, username)

                    AuthRepository.AuthResult.Success(authResult)
                } else {
                    AuthRepository.AuthResult.Error("QuickConnect failed: No result returned")
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "QuickConnect authentication failed")
                AuthRepository.AuthResult.Error("QuickConnect failed: ${e.message ?: "Unknown error"}")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during QuickConnect authentication")
                AuthRepository.AuthResult.Error("QuickConnect failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    override suspend fun initiateQuickConnect(): QuickConnectState? {
        return withContext(Dispatchers.IO) {
            try {
                val quickConnectApi = QuickConnectApi(apiClient)
                val response = quickConnectApi.initiateQuickConnect()

                response.content?.let { result ->
                    QuickConnectState(
                        code = result.code ?: "",
                        secret = result.secret ?: "",
                        authenticated = result.authenticated ?: false
                    )
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to initiate QuickConnect")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error initiating QuickConnect")
                null
            }
        }
    }

    override suspend fun getQuickConnectState(secret: String): QuickConnectState? {
        return withContext(Dispatchers.IO) {
            try {
                val quickConnectApi = QuickConnectApi(apiClient)
                val response = quickConnectApi.getQuickConnectState(secret = secret)

                response.content?.let { result ->
                    QuickConnectState(
                        code = result.code ?: "",
                        secret = result.secret ?: "",
                        authenticated = result.authenticated ?: false
                    )
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get QuickConnect state")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting QuickConnect state")
                null
            }
        }
    }

    override suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                sessionManager.logout()

                clearAllAuthData()
                Timber.d("Successfully logged out and cleared encrypted data (token kept for re-login)")
            } catch (e: Exception) {
                Timber.e(e, "Error during logout, clearing encrypted state anyway")
                sessionManager.logout()
                clearAllAuthData()
            }
        }
    }

    override suspend fun getCurrentUser(): User? {
        return withContext(Dispatchers.IO) {
            try {
                if (!_isAuthenticated.value) return@withContext null

                val userApi = UserApi(apiClient)
                val response = userApi.getCurrentUser()
                response.content?.let { userDto ->
                    val user = User(
                        id = userDto.id,
                        name = userDto.name ?: "",
                        serverId = "",
                        accessToken = apiClient.accessToken,
                        primaryImageTag = userDto.primaryImageTag
                    )
                    _currentUser.value = user
                    user
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get current user")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting current user")
                null
            }
        }
    }

    override suspend fun getPublicUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            try {
                val userApi = UserApi(apiClient)
                val response = userApi.getPublicUsers()
                response.content?.map { userDto ->
                    User(
                        id = userDto.id,
                        name = userDto.name ?: "",
                        serverId = "",
                        accessToken = null,
                        primaryImageTag = userDto.primaryImageTag
                    )
                } ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get public users")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting public users")
                emptyList()
            }
        }
    }

    override fun hasValidToken(): Boolean {
        return !apiClient.accessToken.isNullOrBlank() && _isAuthenticated.value
    }

    override fun getAccessToken(): String? = apiClient.accessToken

    fun setAuthenticatedUser(user: User, accessToken: String, serverUrl: String) {

        apiClient.update(baseUrl = serverUrl, accessToken = accessToken)

        Timber.d("ApiClient AFTER update - baseUrl: ${apiClient.baseUrl}, hasToken: ${!apiClient.accessToken.isNullOrBlank()}")
        _currentUser.value = user
        _isAuthenticated.value = true
    }

    fun setSessionActive(user: User) {
        _currentUser.value = user
        _isAuthenticated.value = true
    }

    private suspend fun handleSuccessfulAuth(authResult: AuthenticationResult, username: String) {
        authResult.accessToken?.let { token ->
            apiClient.update(accessToken = token)
            _isAuthenticated.value = true

            authResult.user?.let { userDto ->
                val user = User(
                    id = userDto.id,
                    name = userDto.name ?: username,
                    serverId = authResult.serverId ?: "",
                    accessToken = token,
                    primaryImageTag = userDto.primaryImageTag
                )
                _currentUser.value = user

                try {
                    databaseRepository.insertUser(user)
                    Timber.d("Saved user to database: ${user.name}")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to save user to database, continuing anyway")
                }

                val serverUrl = apiClient.baseUrl ?: ""
                val serverId = authResult.serverId ?: ""
                val userId = userDto.id

                sessionManager.startSession(
                    serverUrl = serverUrl,
                    serverId = serverId,
                    userId = userId,
                    accessToken = token
                ).onFailure { e ->
                    Timber.e(e, "Failed to start session via SessionManager")
                }
            }

            Timber.d("Successfully authenticated user: $username")
            CoroutineScope(Dispatchers.IO).launch {
                registerClientCapabilities()
            }
        } ?: run {
            Timber.w("Authentication succeeded but no access token received")
        }
    }

    private suspend fun registerClientCapabilities() {
        try {
            val sessionApi = SessionApi(apiClient)
            val capabilities = ClientCapabilitiesDto(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands = listOf(
                    GeneralCommandType.VOLUME_UP,
                    GeneralCommandType.VOLUME_DOWN,
                    GeneralCommandType.TOGGLE_MUTE,
                    GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                    GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                    GeneralCommandType.MUTE,
                    GeneralCommandType.UNMUTE,
                    GeneralCommandType.SET_VOLUME,
                    GeneralCommandType.DISPLAY_MESSAGE,
                    GeneralCommandType.PLAY,
                    GeneralCommandType.PLAY_STATE,
                    GeneralCommandType.PLAY_NEXT,
                    GeneralCommandType.PLAY_MEDIA_SOURCE,
                ),
                supportsMediaControl = true,
                supportsPersistentIdentifier = true,
                deviceProfile = null,
                appStoreUrl = null,
                iconUrl = AppConstants.CLIENT_ICON_URL
            )

            sessionApi.postFullCapabilities(data = capabilities)
            Timber.d("Successfully registered client capabilities with icon URL")
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to register client capabilities")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error registering client capabilities")
        }
    }

    private fun clearAuthState() {
        apiClient.update(accessToken = null)
        _isAuthenticated.value = false
        _currentUser.value = null
    }
}