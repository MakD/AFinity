package com.makd.afinity.ui.audiobookshelf.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfLoginViewModel
@Inject
constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

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

            val rawUrl = currentState.serverUrl.trim().removeSuffix("/")
            val candidateUrls =
                if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    listOf(rawUrl)
                } else {
                    listOf("https://$rawUrl", "http://$rawUrl")
                }
            var successUser: com.makd.afinity.data.models.audiobookshelf.AudiobookshelfUser? = null
            var lastError: Throwable? = null

            for (url in candidateUrls) {
                val result =
                    audiobookshelfRepository.login(
                        serverUrl = url,
                        username = currentState.username,
                        password = currentState.password,
                    )

                if (result.isSuccess) {
                    successUser = result.getOrNull()
                    _uiState.update { it.copy(serverUrl = url) }
                    break
                } else {
                    lastError = result.exceptionOrNull()
                    val errMsg = lastError?.message ?: ""
                    if (errMsg.contains("401") || errMsg.contains("403")) {
                        break
                    }
                    Timber.d("Connection to $url failed, trying next protocol. Error: $errMsg")
                }
            }

            if (successUser != null) {
                _uiState.value = _uiState.value.copy(isLoggingIn = false, isLoggedIn = true)
                Timber.d("Audiobookshelf login successful for user: ${successUser.username}")
            } else {
                _uiState.value =
                    _uiState.value.copy(
                        isLoggingIn = false,
                        error = "Login failed: ${lastError?.message ?: "Unknown error"}",
                    )
                Timber.e(lastError, "Audiobookshelf login failed across all protocols")
            }
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

    suspend fun isNotificationPermissionDeclined(): Boolean {
        return preferencesRepository.getNotificationPermissionDeclined()
    }

    fun declineNotificationPermission() {
        viewModelScope.launch { preferencesRepository.setNotificationPermissionDeclined(true) }
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
