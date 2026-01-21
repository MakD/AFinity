package com.makd.afinity.ui.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.auth.JellyfinAuthRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val jellyfinRepository: JellyfinRepository,
    private val authRepository: AuthRepository,
    private val databaseRepository: DatabaseRepository,
    private val sessionManager: SessionManager,
    private val securePreferencesRepository: SecurePreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _publicUsers = MutableStateFlow<List<User>>(emptyList())
    val publicUsers: StateFlow<List<User>> = _publicUsers.asStateFlow()

    private val _savedServers = MutableStateFlow<List<Server>>(emptyList())
    val savedServers: StateFlow<List<Server>> = _savedServers.asStateFlow()

    private val _savedUsers = MutableStateFlow<List<User>>(emptyList())
    val savedUsers: StateFlow<List<User>> = _savedUsers.asStateFlow()

    private val _connectedServerUrl = MutableStateFlow("")

    private var quickConnectJob: Job? = null

    val loginState = combine(
        uiState,
        serverUrl,
        publicUsers
    ) { ui, server, users ->
        LoginState(
            uiState = ui,
            serverUrl = server,
            publicUsers = users,
            hasServerUrl = server.isNotBlank(),
            isConnectedToServer = server.isNotBlank() && !ui.isConnecting && ui.isConnectedToServer
        )
    }

    init {
        viewModelScope.launch {
            try {
                val servers = databaseRepository.getAllServers()
                _savedServers.value = servers
                Timber.d("Loaded ${servers.size} saved servers")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load saved servers")
            }
        }

        val passedServerUrl = savedStateHandle.get<String>("serverUrl")

        if (!passedServerUrl.isNullOrBlank()) {
            Timber.d("Login initialized with passed server URL: $passedServerUrl")
            _serverUrl.value = passedServerUrl
            _uiState.value = _uiState.value.copy(showAddServerInput = true)
            connectToServer()
        } else {
            viewModelScope.launch {
                val currentUrl = jellyfinRepository.getBaseUrl()
                if (currentUrl.isNotBlank()) {
                    _serverUrl.value = currentUrl
                    _connectedServerUrl.value = currentUrl
                    jellyfinRepository.refreshServerInfo()
                    _uiState.value = _uiState.value.copy(isConnectedToServer = true)
                    loadPublicUsers()
                }
            }

            viewModelScope.launch {
                authRepository.isAuthenticated.collect { isAuthenticated ->
                    if (isAuthenticated) {
                        _uiState.value = _uiState.value.copy(
                            isLoggedIn = true,
                            isLoggingIn = false
                        )
                    }
                }
            }
        }
    }

    fun setServerUrl(url: String) {
        val trimmedUrl = url.trim()
        val currentConnectedUrl = _connectedServerUrl.value

        _serverUrl.value = trimmedUrl

        if (trimmedUrl.isBlank()) {
            clearServerConnection()
        } else if (currentConnectedUrl.isNotBlank() && trimmedUrl != currentConnectedUrl) {
            clearServerConnection()
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return (url.startsWith("http://") || url.startsWith("https://")) &&
                url.length > 8 &&
                (url.contains(".") ||
                        url.contains(":")) &&
                !url.endsWith("://") &&
                !url.contains(" ")
    }

    fun connectToServer() {
        val url = _serverUrl.value.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                isConnectedToServer = false,
                error = null
            )

            try {
                Timber.d("Validating Jellyfin server at: $url")
                when (val validationResult = jellyfinRepository.validateServer(url)) {
                    is JellyfinServerRepository.ServerConnectionResult.Success -> {
                        Timber.d("Server validation successful for: $url")

                        jellyfinRepository.setBaseUrl(url)
                        jellyfinRepository.refreshServerInfo()
                        _connectedServerUrl.value = url

                        try {
                            databaseRepository.insertServer(validationResult.server)
                            Timber.d("Saved server to database: ${validationResult.server.name} (${validationResult.server.id})")
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to save server to database, continuing anyway")
                        }

                        loadPublicUsers()

                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnectedToServer = true
                        )

                        Timber.d("Successfully connected to Jellyfin server: $url (${validationResult.server.name})")
                    }

                    is JellyfinServerRepository.ServerConnectionResult.Error -> {
                        Timber.w("Server validation failed for: $url - ${validationResult.message}")

                        _uiState.value = _uiState.value.copy(
                            isConnecting = false,
                            isConnectedToServer = false,
                            error = "Not a valid Jellyfin server: ${validationResult.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    isConnectedToServer = false,
                    error = "Failed to connect to server: ${e.message ?: "Unknown error"}"
                )
                Timber.e(e, "Failed to connect to server: $url")
            }
        }
    }

    private fun clearServerConnection() {
        _uiState.value = _uiState.value.copy(
            isConnecting = false,
            isConnectedToServer = false,
            error = null
        )
        _publicUsers.value = emptyList()
        _connectedServerUrl.value = ""
    }

    private suspend fun loadPublicUsers() {
        try {
            val users = jellyfinRepository.getPublicUsers()
            _publicUsers.value = users
            Timber.d("Loaded ${users.size} public users")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load public users")
        }
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            error = null
        )
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            error = null
        )
    }

    fun selectUser(user: User) {
        _uiState.value = _uiState.value.copy(
            selectedUser = user,
            username = user.name,
            error = null
        )
    }

    fun selectServer(server: Server) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    selectedServer = server,
                    showAddServerInput = false,
                    isConnecting = true,
                    error = null
                )

                Timber.d("Selecting server: ${server.name} (${server.address})")

                jellyfinRepository.setBaseUrl(server.address)
                jellyfinRepository.refreshServerInfo()
                _serverUrl.value = server.address
                _connectedServerUrl.value = server.address

                loadSavedUsers(server.id)

                loadPublicUsers()

                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    isConnectedToServer = true
                )

                Timber.d("Successfully connected to saved server: ${server.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to saved server: ${server.name}")
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    isConnectedToServer = false,
                    error = "Failed to connect to ${server.name}: ${e.message}"
                )
            }
        }
    }

    private suspend fun loadSavedUsers(serverId: String) {
        try {
            val allUsers = databaseRepository.getUsersForServer(serverId)
            val usersWithTokens = allUsers.filter { user ->
                val hasToken =
                    securePreferencesRepository.getServerUserToken(serverId, user.id) != null
                if (!hasToken) {
                    Timber.d("Excluding user ${user.name} from saved users (no token)")
                }
                hasToken
            }
            _savedUsers.value = usersWithTokens
            Timber.d("Loaded ${usersWithTokens.size} saved users with tokens for server $serverId (${allUsers.size} total users)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load saved users for server $serverId")
            _savedUsers.value = emptyList()
        }
    }

    fun loginWithSavedUser(user: User) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoggingIn = true,
                error = null
            )

            try {
                Timber.d("Attempting 1-tap login for saved user: ${user.name}")

                val token = securePreferencesRepository.getServerUserToken(user.serverId, user.id)

                if (token != null) {
                    val server = databaseRepository.getServer(user.serverId)
                    if (server == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            error = "Server not found. Please login again."
                        )
                        Timber.w("Server ${user.serverId} not found in database for user ${user.name}")
                        return@launch
                    }

                    val serverUrl = server.address
                    Timber.d("Using server URL from database: $serverUrl")

                    val result = sessionManager.startSession(
                        serverUrl = serverUrl,
                        serverId = user.serverId,
                        userId = user.id,
                        accessToken = token
                    )

                    if (result.isSuccess) {
                        securePreferencesRepository.saveAuthenticationData(
                            accessToken = token,
                            userId = user.id,
                            serverId = user.serverId,
                            serverUrl = serverUrl,
                            username = user.name
                        )
                        Timber.d("Updated active session persistence for: ${user.name}")

                        (authRepository as? JellyfinAuthRepository)?.setSessionActive(user)

                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            isLoggedIn = true
                        )
                        Timber.d("Successfully logged in with saved user: ${user.name}")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            error = "Failed to start session: ${result.exceptionOrNull()?.message}"
                        )
                        Timber.e(
                            result.exceptionOrNull(),
                            "Failed to start session for user: ${user.name}"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoggingIn = false,
                        error = "Saved session expired. Please login again."
                    )
                    Timber.w("No saved token found for user: ${user.name}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoggingIn = false,
                    error = "Login failed: ${e.message ?: "Unknown error"}"
                )
                Timber.e(e, "1-tap login failed for user: ${user.name}")
            }
        }
    }

    fun showAddNewServer() {
        _uiState.value = _uiState.value.copy(
            showAddServerInput = true,
            selectedServer = null
        )
        clearServerConnection()
    }

    fun login() {
        val currentState = _uiState.value

        if (currentState.username.isBlank()) {
            _uiState.value = currentState.copy(error = "Username is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isLoggingIn = true,
                error = null
            )

            try {
                val result = authRepository.authenticateByName(
                    username = currentState.username,
                    password = currentState.password
                )

                when (result) {
                    is AuthRepository.AuthResult.Success -> {
                        val sdkAuthResult = result.authResult

                        val token = sdkAuthResult.accessToken
                        val userId = sdkAuthResult.user?.id
                        val serverId = sdkAuthResult.serverId
                            ?: _savedServers.value.find { it.address == _serverUrl.value }?.id ?: ""

                        if (token != null && userId != null) {
                            val sessionResult = sessionManager.startSession(
                                serverUrl = _serverUrl.value,
                                serverId = serverId,
                                userId = userId,
                                accessToken = token
                            )

                            if (sessionResult.isSuccess) {
                                _uiState.value = _uiState.value.copy(
                                    isLoggingIn = false,
                                    isLoggedIn = true
                                )
                                Timber.d("Successfully logged in user: ${currentState.username}")
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isLoggingIn = false,
                                    error = "Session start failed: ${sessionResult.exceptionOrNull()?.message}"
                                )
                            }
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoggingIn = false,
                                error = "Login succeeded but user ID or token was missing."
                            )
                        }
                    }

                    is AuthRepository.AuthResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            error = result.message
                        )
                        Timber.e("Login failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoggingIn = false,
                    error = "Login failed: ${e.message ?: "Unknown error"}"
                )
                Timber.e(e, "Login failed for user: ${currentState.username}")
            }
        }
    }

    fun startQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoggingIn = true,
                quickConnectCode = null,
                error = null
            )

            try {
                val quickConnectState = authRepository.initiateQuickConnect()

                if (quickConnectState != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoggingIn = false,
                        quickConnectCode = quickConnectState.code,
                        quickConnectSecret = quickConnectState.secret
                    )

                    pollQuickConnectStatus(quickConnectState.secret)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoggingIn = false,
                        error = "Failed to start Quick Connect. Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoggingIn = false,
                    error = "Quick Connect error: ${e.message ?: "Unknown error"}"
                )
                Timber.e(e, "Failed to start QuickConnect")
            }
        }
    }

    private suspend fun pollQuickConnectStatus(secret: String) {
        try {
            repeat(120) {
                delay(1000)

                val state = authRepository.getQuickConnectState(secret)
                if (state?.authenticated == true) {
                    when (val result = authRepository.authenticateWithQuickConnect(secret)) {
                        is AuthRepository.AuthResult.Success -> {
                            val sdkAuthResult = result.authResult

                            val token = sdkAuthResult.accessToken
                            val userId = sdkAuthResult.user?.id
                            val serverId = sdkAuthResult.serverId
                                ?: _savedServers.value.find { it.address == _serverUrl.value }?.id
                                ?: ""

                            if (token != null && userId != null) {
                                val sessionResult = sessionManager.startSession(
                                    serverUrl = _serverUrl.value,
                                    serverId = serverId,
                                    userId = userId,
                                    accessToken = token
                                )

                                if (sessionResult.isSuccess) {
                                    _uiState.value = _uiState.value.copy(
                                        isLoggingIn = false,
                                        isLoggedIn = true,
                                        quickConnectCode = null,
                                        quickConnectSecret = null
                                    )
                                    Timber.d("Successfully authenticated with QuickConnect")
                                    return
                                } else {
                                    _uiState.value = _uiState.value.copy(
                                        isLoggingIn = false,
                                        error = "Session start failed: ${sessionResult.exceptionOrNull()?.message}"
                                    )
                                    return
                                }
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isLoggingIn = false,
                                    error = "QuickConnect succeeded but user ID or token was missing."
                                )
                                return
                            }
                        }

                        is AuthRepository.AuthResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isLoggingIn = false,
                                error = "QuickConnect authentication failed: ${result.message}",
                                quickConnectCode = null,
                                quickConnectSecret = null
                            )
                            return
                        }
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoggingIn = false,
                error = "QuickConnect timed out. Please try again.",
                quickConnectCode = null,
                quickConnectSecret = null
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoggingIn = false,
                error = "QuickConnect error: ${e.message ?: "Unknown error"}",
                quickConnectCode = null,
                quickConnectSecret = null
            )
            Timber.e(e, "QuickConnect polling failed")
        }
    }

    fun cancelAddServer() {
        _uiState.update {
            it.copy(
                showAddServerInput = false,
                error = null,
                isConnecting = false
            )
        }
        _serverUrl.value = ""
        clearServerConnection()
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoggingIn = false,
            quickConnectCode = null,
            quickConnectSecret = null,
            error = null
        )
    }

    fun discoverServers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDiscovering = true,
                error = null,
                discoveredServers = emptyList()
            )

            try {
                jellyfinRepository.discoverServersFlow().collect { servers ->
                    _uiState.value = _uiState.value.copy(
                        discoveredServers = servers,
                        isDiscovering = servers.isEmpty()
                    )
                    Timber.d("Updated UI with ${servers.size} discovered servers")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDiscovering = false,
                    error = null
                )
                Timber.e(e, "Server discovery failed")
            } finally {
                _uiState.value = _uiState.value.copy(isDiscovering = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.logout()
                sessionManager.logout()
                _uiState.value = LoginUiState()
                _publicUsers.value = emptyList()
                _serverUrl.value = ""
                _connectedServerUrl.value = ""
                Timber.d("Successfully logged out")
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                _uiState.value = LoginUiState()
                _publicUsers.value = emptyList()
                _serverUrl.value = ""
                _connectedServerUrl.value = ""
            }
        }
    }

    fun resetState() {
        quickConnectJob?.cancel()
        _uiState.value = LoginUiState()
        _serverUrl.value = ""
        _publicUsers.value = emptyList()
        _connectedServerUrl.value = ""
    }
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val selectedUser: User? = null,
    val selectedServer: Server? = null,
    val showAddServerInput: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnectedToServer: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveredServers: List<Server> = emptyList(),
    val error: String? = null,
    val quickConnectCode: String? = null,
    val quickConnectSecret: String? = null
)

data class LoginState(
    val uiState: LoginUiState,
    val serverUrl: String,
    val publicUsers: List<User>,
    val hasServerUrl: Boolean,
    val isConnectedToServer: Boolean
)