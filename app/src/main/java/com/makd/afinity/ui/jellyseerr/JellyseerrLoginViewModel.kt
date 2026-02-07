package com.makd.afinity.ui.jellyseerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.repository.JellyseerrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class JellyseerrLoginViewModel
@Inject
constructor(private val jellyseerrRepository: JellyseerrRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyseerrLoginUiState())
    val uiState: StateFlow<JellyseerrLoginUiState> = _uiState.asStateFlow()

    init {
        loadSavedServerUrl()
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                val isAuthenticated = jellyseerrRepository.isLoggedIn()
                if (isAuthenticated) {
                    jellyseerrRepository
                        .getCurrentUser()
                        .fold(
                            onSuccess = { user -> _uiState.update { it.copy(currentUser = user) } },
                            onFailure = { error ->
                                Timber.e(error, "Failed to load current user")
                                _uiState.update { it.copy(currentUser = null) }
                            },
                        )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check auth status")
            }
        }
    }

    private fun loadSavedServerUrl() {
        viewModelScope.launch {
            try {
                val savedUrl = jellyseerrRepository.getServerUrl()
                if (!savedUrl.isNullOrBlank()) {
                    _uiState.update { it.copy(serverUrl = savedUrl) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load saved server URL")
            }
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url, serverUrlError = null) }
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, emailError = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null) }
    }

    fun setUseJellyfinAuth(useJellyfin: Boolean) {
        if (_uiState.value.useJellyfinAuth == useJellyfin) return

        _uiState.update {
            it.copy(
                useJellyfinAuth = useJellyfin,
                email = "",
                password = "",
                emailError = null,
                passwordError = null,
            )
        }
    }

    fun login() {
        viewModelScope.launch {
            try {
                if (!validateInputs()) {
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, error = null) }

                jellyseerrRepository.setServerUrl(_uiState.value.serverUrl.trim())

                jellyseerrRepository
                    .login(
                        email = _uiState.value.email.trim(),
                        password = _uiState.value.password,
                        useJellyfinAuth = _uiState.value.useJellyfinAuth,
                    )
                    .fold(
                        onSuccess = { user ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    loginSuccess = true,
                                    loggedInUser = user.displayName ?: user.username ?: user.email,
                                    currentUser = user,
                                )
                            }
                            Timber.d("Login successful for user: ${user.username}")
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(isLoading = false, error = parseErrorMessage(error.message))
                            }
                            Timber.e(error, "Login failed")
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "An unexpected error occurred: ${e.message}")
                }
                Timber.e(e, "Error during login")
            }
        }
    }

    private fun validateInputs(): Boolean {
        val state = _uiState.value
        var isValid = true

        if (state.serverUrl.isBlank()) {
            _uiState.update { it.copy(serverUrlError = "Server URL is required") }
            isValid = false
        } else if (!isValidUrl(state.serverUrl)) {
            _uiState.update { it.copy(serverUrlError = "Invalid URL format") }
            isValid = false
        }

        if (state.email.isBlank()) {
            _uiState.update { it.copy(emailError = "Email or username is required") }
            isValid = false
        }

        if (state.password.isBlank()) {
            _uiState.update { it.copy(passwordError = "Password is required") }
            isValid = false
        }

        return isValid
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val trimmedUrl = url.trim()
            when {
                trimmedUrl.isBlank() -> false
                trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> true
                trimmedUrl.contains(".") || trimmedUrl.contains(":") -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseErrorMessage(message: String?): String {
        return when {
            message == null -> "Login failed. Please check your credentials."
            message.contains("401") -> "Invalid email or password"
            message.contains("403") -> "Access forbidden. Check your permissions."
            message.contains("404") -> "Server not found. Check your server URL."
            message.contains("network", ignoreCase = true) ->
                "Network error. Check your connection."

            message.contains("timeout", ignoreCase = true) ->
                "Connection timeout. Please try again."

            else -> message
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetLoginSuccess() {
        _uiState.update { it.copy(loginSuccess = false) }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                jellyseerrRepository
                    .logout()
                    .fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    currentUser = null,
                                    email = "",
                                    password = "",
                                )
                            }
                            Timber.d("Logout successful")
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Logout failed: ${error.message}",
                                )
                            }
                            Timber.e(error, "Logout failed")
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "An unexpected error occurred: ${e.message}")
                }
                Timber.e(e, "Error during logout")
            }
        }
    }
}

data class JellyseerrLoginUiState(
    val serverUrl: String = "",
    val email: String = "",
    val password: String = "",
    val useJellyfinAuth: Boolean = true,
    val serverUrlError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val loggedInUser: String? = null,
    val currentUser: JellyseerrUser? = null,
)
