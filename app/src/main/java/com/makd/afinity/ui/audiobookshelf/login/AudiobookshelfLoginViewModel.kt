package com.makd.afinity.ui.audiobookshelf.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.discovery.AfinityServiceTypes
import com.makd.afinity.data.discovery.DiscoveredService
import com.makd.afinity.data.discovery.LocalServiceDiscovery
import com.makd.afinity.data.network.UrlCandidates
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
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
    private val localServiceDiscovery: LocalServiceDiscovery,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookshelfLoginUiState())
    val uiState: StateFlow<AudiobookshelfLoginUiState> = _uiState.asStateFlow()

    private val _discoveredServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredServices: StateFlow<List<DiscoveredService>> = _discoveredServices.asStateFlow()

    private var discoveryJob: Job? = null

    val isAuthenticated = audiobookshelfRepository.isAuthenticated
    val currentConfig = audiobookshelfRepository.currentConfig

    fun discoverLocalServers() {
        discoveryJob?.cancel()
        discoveryJob =
            viewModelScope.launch {
                localServiceDiscovery.discover(AfinityServiceTypes.AUDIOBOOKSHELF).collect {
                    services ->
                    _discoveredServices.value = services
                }
            }
    }

    fun updateServerUrl(url: String) {
        val trimmed = url.trim()
        val urlError =
            if (trimmed.isNotBlank() && !isValidUrl(trimmed)) "Invalid URL format" else null
        _uiState.value =
            _uiState.value.copy(serverUrl = trimmed, serverUrlError = urlError, error = null)
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
            val candidateUrls = UrlCandidates.audiobookshelf(rawUrl)

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
                        error = context.getString(R.string.error_abs_server_unreachable),
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
                        context.getString(R.string.error_invalid_username_password)
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
}

data class AudiobookshelfLoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val serverUrlError: String? = null,
)
