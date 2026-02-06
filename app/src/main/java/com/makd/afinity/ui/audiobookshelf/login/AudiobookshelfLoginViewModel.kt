package com.makd.afinity.ui.audiobookshelf.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AudiobookshelfLoginViewModel
@Inject
constructor(private val audiobookshelfRepository: AudiobookshelfRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookshelfLoginUiState())
    val uiState: StateFlow<AudiobookshelfLoginUiState> = _uiState.asStateFlow()

    val isAuthenticated = audiobookshelfRepository.isAuthenticated
    val currentConfig = audiobookshelfRepository.currentConfig

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url.trim(), error = null)
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun testConnection() {
        val serverUrl = _uiState.value.serverUrl
        if (serverUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Server URL is required")
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isTestingConnection = true,
                    error = null,
                    connectionTestSuccess = false,
                )

            try {
                audiobookshelfRepository.setServerUrl(normalizeUrl(serverUrl))
                _uiState.value =
                    _uiState.value.copy(isTestingConnection = false, connectionTestSuccess = true)
                Timber.d("Server URL set: $serverUrl")
            } catch (e: Exception) {
                _uiState.value =
                    _uiState.value.copy(
                        isTestingConnection = false,
                        connectionTestSuccess = false,
                        error = "Failed to set server URL: ${e.message}",
                    )
                Timber.e(e, "Failed to test connection")
            }
        }
    }

    fun login() {
        val currentState = _uiState.value

        if (currentState.serverUrl.isBlank()) {
            _uiState.value = currentState.copy(error = "Server URL is required")
            return
        }
        if (currentState.username.isBlank()) {
            _uiState.value = currentState.copy(error = "Username is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoggingIn = true, error = null)

            val result =
                audiobookshelfRepository.login(
                    serverUrl = normalizeUrl(currentState.serverUrl),
                    username = currentState.username,
                    password = currentState.password,
                )

            result.fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(isLoggingIn = false, isLoggedIn = true)
                    Timber.d("Audiobookshelf login successful for user: ${user.username}")
                },
                onFailure = { error ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoggingIn = false,
                            error = "Login failed: ${error.message}",
                        )
                    Timber.e(error, "Audiobookshelf login failed")
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingIn = true)

            val result = audiobookshelfRepository.logout()

            result.fold(
                onSuccess = {
                    _uiState.value = AudiobookshelfLoginUiState()
                    Timber.d("Audiobookshelf logout successful")
                },
                onFailure = { error ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoggingIn = false,
                            error = "Logout failed: ${error.message}",
                        )
                    Timber.e(error, "Audiobookshelf logout failed")
                },
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }

        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        return normalized
    }
}

data class AudiobookshelfLoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isTestingConnection: Boolean = false,
    val connectionTestSuccess: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
)
