package com.makd.afinity.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _publicUsers = MutableStateFlow<List<User>>(emptyList())
    val publicUsers: StateFlow<List<User>> = _publicUsers.asStateFlow()

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
            val currentUrl = jellyfinRepository.getBaseUrl()
            if (currentUrl.isNotBlank()) {
                _serverUrl.value = currentUrl
                _connectedServerUrl.value = currentUrl
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                isConnectedToServer = false,
                error = null
            )

            try {
                Timber.d("Validating Jellyfin server at: $url")
                val validationResult = jellyfinRepository.validateServer(url)

                when (validationResult) {
                    is JellyfinServerRepository.ServerConnectionResult.Success -> {
                        Timber.d("Server validation successful for: $url")

                        jellyfinRepository.setBaseUrl(url)

                        _connectedServerUrl.value = url

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
                        _uiState.value = _uiState.value.copy(
                            isLoggingIn = false,
                            isLoggedIn = true
                        )
                        Timber.d("Successfully logged in user: ${currentState.username}")
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
                    val result = authRepository.authenticateWithQuickConnect(secret)

                    when (result) {
                        is AuthRepository.AuthResult.Success -> {
                            _uiState.value = _uiState.value.copy(
                                isLoggingIn = false,
                                isLoggedIn = true,
                                quickConnectCode = null,
                                quickConnectSecret = null
                            )
                            Timber.d("Successfully authenticated with QuickConnect")
                            return
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
    val isLoggingIn: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnectedToServer: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveredServers: List<com.makd.afinity.data.models.server.Server> = emptyList(),
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