package com.makd.afinity.ui.audiobookshelf.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @param:ApplicationContext private val context: Context,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookshelfLoginUiState())
    val uiState: StateFlow<AudiobookshelfLoginUiState> = _uiState.asStateFlow()

    val isAuthenticated = audiobookshelfRepository.isAuthenticated
    val currentConfig = audiobookshelfRepository.currentConfig

    fun updateServerUrl(url: String) {
        val trimmed = url.trim()
        val urlError = if (trimmed.isNotBlank() && !isValidUrl(trimmed)) "Invalid URL format" else null
        _uiState.value = _uiState.value.copy(serverUrl = trimmed, serverUrlError = urlError, error = null)
    }

    private fun isValidUrl(url: String): Boolean {
        return url.isNotBlank() && !url.contains(" ")
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
            _uiState.value =
                _uiState.value.copy(error = context.getString(R.string.error_server_url_required))
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
                        error =
                            context.getString(R.string.error_set_server_url_fmt, e.message ?: ""),
                    )
                Timber.e(e, "Failed to test connection")
            }
        }
    }

    fun login() {
        val currentState = _uiState.value

        if (currentState.serverUrl.isBlank()) {
            _uiState.value =
                currentState.copy(error = context.getString(R.string.error_server_url_required))
            return
        }
        if (currentState.username.isBlank()) {
            _uiState.value =
                currentState.copy(error = context.getString(R.string.error_username_required))
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoggingIn = true, error = null)

            val rawUrl = currentState.serverUrl.trim().removeSuffix("/")
            val candidateUrls = generateCandidateUrls(rawUrl)

            var validUrl: String? = null
            for (url in candidateUrls) {
                val isServerValid = audiobookshelfRepository.verifyServer(url)
                if (isServerValid) {
                    validUrl = url
                    break
                } else {
                    Timber.d("Ping failed for candidate URL: $url")
                }
            }

            if (validUrl == null) {
                _uiState.value =
                    _uiState.value.copy(
                        isLoggingIn = false,
                        error =
                            "Could not connect. Please verify this is a valid Audiobookshelf server.",
                    )
                return@launch
            }

            val result =
                audiobookshelfRepository.login(
                    serverUrl = validUrl,
                    username = currentState.username,
                    password = currentState.password,
                )

            if (result.isSuccess) {
                val successUser = result.getOrNull()
                _uiState.update {
                    it.copy(serverUrl = validUrl, isLoggingIn = false, isLoggedIn = true)
                }
                Timber.d("Audiobookshelf login successful for user: ${successUser?.username}")
            } else {
                val lastError = result.exceptionOrNull()
                val errMsg = lastError?.message ?: ""
                val finalErrorMessage =
                    if (errMsg.contains("401") || errMsg.contains("403")) {
                        "Invalid username or password."
                    } else {
                        context.getString(R.string.error_login_failed_fmt, errMsg)
                    }

                _uiState.value = _uiState.value.copy(isLoggingIn = false, error = finalErrorMessage)
                Timber.e(lastError, "Audiobookshelf login failed on validated server")
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
                            error =
                                context.getString(
                                    R.string.error_logout_failed_fmt,
                                    error.message ?: "",
                                ),
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

    private fun generateCandidateUrls(input: String): List<String> {
        val hasScheme = input.startsWith("http://") || input.startsWith("https://")
        val withScheme = if (hasScheme) input else "http://$input"
        val uri = runCatching { java.net.URI(withScheme) }.getOrNull()
        val host = uri?.host?.takeIf { it.isNotBlank() } ?: input
        val port = uri?.port ?: -1
        val scheme = if (hasScheme) uri?.scheme else null

        return when {
            hasScheme && port != -1 -> listOf(input)
            !hasScheme && port != -1 -> listOf("https://$input", "http://$input")
            hasScheme && scheme == "https" -> listOf(input, "https://$host:13378")
            hasScheme && scheme == "http" -> listOf(input, "http://$host:13378")
            else -> listOf("https://$host", "https://$host:13378", "http://$host:13378", "http://$host")
        }
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
    val serverUrlError: String? = null,
)
