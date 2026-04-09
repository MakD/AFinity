package com.makd.afinity.data.repository.auth

import com.makd.afinity.core.AppConstants
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.auth.QuickConnectState
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
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
import javax.inject.Singleton

@Singleton
class JellyfinAuthRepository
@Inject
constructor(
    private val jellyfin: Jellyfin,
    private val sessionManager: SessionManager,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val databaseRepository: DatabaseRepository,
) : AuthRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val currentUser: StateFlow<User?> by lazy {
        sessionManager.currentSession.map { it?.user }.stateIn(scope, SharingStarted.Eagerly, null)
    }

    override val isAuthenticated: StateFlow<Boolean> by lazy {
        sessionManager.currentSession
            .map { it != null }
            .stateIn(scope, SharingStarted.Eagerly, false)
    }

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

                if (
                    accessToken.isNullOrBlank() ||
                        userId.isNullOrBlank() ||
                        serverUrl.isNullOrBlank() ||
                        username.isNullOrBlank()
                ) {
                    Timber.w(
                        "Incomplete encrypted auth data found, clearing and requiring fresh login"
                    )
                    clearAllAuthData()
                    return@withContext false
                }

                val userUuid =
                    try {
                        UUID.fromString(userId)
                    } catch (e: IllegalArgumentException) {
                        Timber.e(e, "Invalid UUID format in saved data")
                        clearAllAuthData()
                        return@withContext false
                    }

                val startResult = sessionManager.startSession(
                    serverUrl = serverUrl,
                    serverId = serverId ?: "",
                    userId = userUuid,
                    accessToken = accessToken,
                )

                val startFailure = startResult.exceptionOrNull()
                if (startFailure != null) {
                    val is401 = startFailure is InvalidStatusException && startFailure.status == 401
                        || startFailure.message?.contains("401") == true
                    if (is401) {
                        Timber.e("Token rejected by server (401) - Logging out")
                        clearAllAuthData()
                        return@withContext false
                    }
                    Timber.w(startFailure, "SessionManager start failed during restore (non-fatal)")
                }

                Timber.d("Session restored for user: $username (url: $serverUrl)")
                return@withContext true
            } catch (e: Exception) {
                Timber.e(e, "Critical error during auth restoration")
                return@withContext false
            }
        }
    }

    override suspend fun hasValidSavedAuth(): Boolean {
        return securePreferencesRepository.hasValidAuthData()
    }

    override suspend fun clearAllAuthData() {
        try {
            securePreferencesRepository.clearAuthenticationData()
            Timber.d("Cleared all encrypted authentication data")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear encrypted authentication data")
        }
    }

    override suspend fun authenticateByName(
        serverUrl: String,
        username: String,
        password: String,
    ): AuthRepository.AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val client = jellyfin.createApi(baseUrl = serverUrl)
                val userApi = UserApi(client)
                val authRequest =
                    org.jellyfin.sdk.model.api.AuthenticateUserByName(
                        username = username,
                        pw = password,
                    )
                val response = userApi.authenticateUserByName(authRequest)

                val authResult = response.content
                handleSuccessfulAuth(authResult, username, client)
                AuthRepository.AuthResult.Success(authResult)
            } catch (e: ApiClientException) {
                Timber.e(e, "Authentication failed")
                AuthRepository.AuthResult.Error(
                    "Authentication failed: ${e.message ?: "Unknown error"}"
                )
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during authentication")
                AuthRepository.AuthResult.Error(
                    "Authentication failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    override suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String,
    ): AuthRepository.AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val client = jellyfin.createApi(baseUrl = serverUrl)
                val userApi = UserApi(client)
                val quickConnectRequest =
                    org.jellyfin.sdk.model.api.QuickConnectDto(secret = secret)
                val response = userApi.authenticateWithQuickConnect(quickConnectRequest)

                val authResult = response.content
                val username = authResult.user?.name ?: "QuickConnect User"
                handleSuccessfulAuth(authResult, username, client)
                AuthRepository.AuthResult.Success(authResult)
            } catch (e: ApiClientException) {
                Timber.e(e, "QuickConnect authentication failed")
                AuthRepository.AuthResult.Error(
                    "QuickConnect failed: ${e.message ?: "Unknown error"}"
                )
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during QuickConnect authentication")
                AuthRepository.AuthResult.Error(
                    "QuickConnect failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    override suspend fun initiateQuickConnect(serverUrl: String): QuickConnectState? {
        return withContext(Dispatchers.IO) {
            try {
                val client = jellyfin.createApi(baseUrl = serverUrl)
                val quickConnectApi = QuickConnectApi(client)
                val result = quickConnectApi.initiateQuickConnect().content
                QuickConnectState(
                    code = result.code,
                    secret = result.secret,
                    authenticated = result.authenticated,
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to initiate QuickConnect")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error initiating QuickConnect")
                null
            }
        }
    }

    override suspend fun getQuickConnectState(
        serverUrl: String,
        secret: String,
    ): QuickConnectState? {
        return withContext(Dispatchers.IO) {
            try {
                val client = jellyfin.createApi(baseUrl = serverUrl)
                val quickConnectApi = QuickConnectApi(client)
                val result = quickConnectApi.getQuickConnectState(secret = secret).content
                QuickConnectState(
                    code = result.code,
                    secret = result.secret,
                    authenticated = result.authenticated,
                )
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
                Timber.d("Successfully logged out")
            } catch (e: Exception) {
                Timber.e(e, "Error during logout")
            }
        }
    }

    override suspend fun getCurrentUser(): User? {
        return withContext(Dispatchers.IO) {
            try {
                val client = sessionManager.getCurrentApiClient() ?: return@withContext null

                val userApi = UserApi(client)
                val userDto = userApi.getCurrentUser().content
                User(
                    id = userDto.id,
                    name = userDto.name ?: "",
                    serverId = "",
                    accessToken = client.accessToken,
                    primaryImageTag = userDto.primaryImageTag,
                )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get current user")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting current user")
                null
            }
        }
    }

    override suspend fun getPublicUsers(serverUrl: String): List<User> {
        return withContext(Dispatchers.IO) {
            try {
                val client = jellyfin.createApi(baseUrl = serverUrl)
                val userApi = UserApi(client)
                userApi.getPublicUsers().content.map { userDto ->
                    User(
                        id = userDto.id,
                        name = userDto.name ?: "",
                        serverId = "",
                        accessToken = null,
                        primaryImageTag = userDto.primaryImageTag,
                    )
                }
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get public users")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting public users")
                emptyList()
            }
        }
    }

    private suspend fun handleSuccessfulAuth(
        authResult: AuthenticationResult,
        username: String,
        client: ApiClient,
    ) {
        authResult.accessToken?.let { token ->
            client.update(accessToken = token)

            authResult.user?.let { userDto ->
                val user =
                    User(
                        id = userDto.id,
                        name = userDto.name ?: username,
                        serverId = authResult.serverId ?: "",
                        accessToken = token,
                        primaryImageTag = userDto.primaryImageTag,
                    )

                try {
                    databaseRepository.insertUser(user)
                    Timber.d("Saved user to database: ${user.name}")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to save user to database, continuing anyway")
                }
            }

            Timber.d("Successfully authenticated user: $username")
            scope.launch { registerClientCapabilities(client) }
        } ?: run { Timber.w("Authentication succeeded but no access token received") }
    }

    private suspend fun registerClientCapabilities(client: ApiClient) {
        try {
            val sessionApi = SessionApi(client)
            val capabilities =
                ClientCapabilitiesDto(
                    playableMediaTypes = listOf(MediaType.VIDEO),
                    supportedCommands =
                        listOf(
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
                    iconUrl = AppConstants.CLIENT_ICON_URL,
                )

            sessionApi.postFullCapabilities(data = capabilities)
            Timber.d("Successfully registered client capabilities with icon URL")
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to register client capabilities")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error registering client capabilities")
        }
    }
}
