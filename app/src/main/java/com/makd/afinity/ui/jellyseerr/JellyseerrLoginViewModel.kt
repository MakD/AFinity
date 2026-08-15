package com.makd.afinity.ui.jellyseerr

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.PublicSettings
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.jellyseerr.JellyseerrLoginException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class JellyseerrLoginViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val jellyseerrRepository: JellyseerrRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JellyseerrLoginUiState())
    val uiState: StateFlow<JellyseerrLoginUiState> = _uiState.asStateFlow()

    private var probeJob: Job? = null

    private companion object {
        const val MEDIA_SERVER_TYPE_JELLYFIN = 2
        const val PROBE_DEBOUNCE_MS = 600L
    }

    init {
        loadSavedServerUrl()
        checkAuthStatus()
        seedJellyfinUsername()
    }

    private fun sessionUsername(): String? = sessionManager.currentSession.value?.user?.name

    private fun seedJellyfinUsername() {
        val username = sessionUsername() ?: return
        _uiState.update {
            if (it.useJellyfinAuth) it.copy(email = username, emailError = null) else it
        }
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
                    probeServer(savedUrl)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load saved server URL")
            }
        }
    }

    fun updateServerUrl(url: String) {
        val error =
            if (url.isNotBlank() && !isValidUrl(url)) {
                context.getString(R.string.error_invalid_url_format)
            } else {
                null
            }
        _uiState.update { it.copy(serverUrl = url, serverUrlError = error) }
        probeServer(url)
    }

    private fun probeServer(rawUrl: String) {
        probeJob?.cancel()

        val trimmed = rawUrl.trim().removeSuffix("/")
        if (trimmed.isBlank() || !isValidUrl(trimmed)) {
            _uiState.update { it.copy(publicSettings = null) }
            return
        }

        probeJob = viewModelScope.launch {
            delay(PROBE_DEBOUNCE_MS)
            for (url in generateCandidateUrls(trimmed)) {
                val settings = jellyseerrRepository.verifyServer(url) ?: continue
                _uiState.update { it.copy(publicSettings = settings) }
                applyDetectedAuthMode(settings)
                return@launch
            }
            _uiState.update { it.copy(publicSettings = null) }
        }
    }

    private fun applyDetectedAuthMode(settings: PublicSettings) {
        if (_uiState.value.authModeUserSelected) return

        val jellyfinAuthAvailable =
            settings.mediaServerLogin && settings.mediaServerType == MEDIA_SERVER_TYPE_JELLYFIN

        val useJellyfin =
            when {
                jellyfinAuthAvailable -> true
                settings.localLogin -> false
                else -> return
            }

        switchAuthMode(useJellyfin)
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, emailError = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null) }
    }

    fun setUseJellyfinAuth(useJellyfin: Boolean) {
        _uiState.update { it.copy(authModeUserSelected = true) }
        switchAuthMode(useJellyfin)
    }

    private fun switchAuthMode(useJellyfin: Boolean) {
        if (_uiState.value.useJellyfinAuth == useJellyfin) return

        _uiState.update {
            it.copy(
                useJellyfinAuth = useJellyfin,
                email = if (useJellyfin) sessionUsername().orEmpty() else "",
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

                val rawUrl = _uiState.value.serverUrl.trim().removeSuffix("/")
                val candidateUrls = generateCandidateUrls(rawUrl)

                var validUrl: String? = null
                var resolvedSettings: PublicSettings? = null

                for (url in candidateUrls) {
                    val settings = jellyseerrRepository.verifyServer(url)
                    if (settings != null) {
                        validUrl = url
                        resolvedSettings = settings
                        break
                    } else {
                        Timber.d("Verification failed for candidate URL: $url")
                    }
                }

                if (validUrl == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error =
                                "Could not connect. Please verify this is a valid Seerr server.",
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(publicSettings = resolvedSettings) }
                jellyseerrRepository.setServerUrl(validUrl)
                val result =
                    jellyseerrRepository.login(
                        email = _uiState.value.email.trim(),
                        password = _uiState.value.password,
                        useJellyfinAuth = _uiState.value.useJellyfinAuth,
                    )

                if (result.isSuccess) {
                    val successUser = result.getOrNull()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccess = true,
                            serverUrl = validUrl,
                            loggedInUser =
                                successUser?.displayName
                                    ?: successUser?.username
                                    ?: successUser?.email,
                            currentUser = successUser,
                        )
                    }
                    Timber.d("Login successful for user: ${successUser?.username}")
                } else {
                    val lastError = result.exceptionOrNull()
                    val errMsg = lastError?.message ?: ""
                    val statusCode = (lastError as? JellyseerrLoginException)?.code

                    val finalErrorMessage =
                        when {
                            statusCode == 401 || statusCode == 403 ->
                                if (_uiState.value.useJellyfinAuth) {
                                    context.getString(R.string.error_invalid_jellyfin_password)
                                } else {
                                    context.getString(R.string.error_invalid_credentials)
                                }

                            statusCode != null && statusCode >= 500 ->
                                context.getString(R.string.error_seerr_account_setup_failed)

                            else -> parseErrorMessage(errMsg)
                        }

                    _uiState.update { it.copy(isLoading = false, error = finalErrorMessage) }
                    Timber.e(lastError, "Jellyseerr login failed on validated server")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_unexpected_fmt, e.message ?: ""),
                    )
                }
                Timber.e(e, "Error during login")
            }
        }
    }

    private fun validateInputs(): Boolean {
        val state = _uiState.value
        var isValid = true

        if (state.serverUrl.isBlank()) {
            _uiState.update {
                it.copy(serverUrlError = context.getString(R.string.error_server_url_required))
            }
            isValid = false
        } else if (!isValidUrl(state.serverUrl)) {
            _uiState.update {
                it.copy(serverUrlError = context.getString(R.string.error_invalid_url_format))
            }
            isValid = false
        }

        if (state.email.isBlank()) {
            if (state.useJellyfinAuth) {
                _uiState.update {
                    it.copy(error = context.getString(R.string.error_no_jellyfin_session))
                }
            } else {
                _uiState.update {
                    it.copy(emailError = context.getString(R.string.error_email_username_required))
                }
            }
            isValid = false
        }

        if (!state.useJellyfinAuth && state.password.isBlank()) {
            _uiState.update {
                it.copy(passwordError = context.getString(R.string.error_password_required))
            }
            isValid = false
        }

        return isValid
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
            hasScheme && scheme == "https" -> listOf(input, "https://$host:5055")
            hasScheme && scheme == "http" -> listOf(input, "http://$host:5055")
            else ->
                listOf("https://$host", "https://$host:5055", "http://$host:5055", "http://$host")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.isNotBlank() && !trimmed.contains(" ")
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
                                    email =
                                        if (it.useJellyfinAuth) sessionUsername().orEmpty() else "",
                                    password = "",
                                )
                            }
                            Timber.d("Logout successful")
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error =
                                        context.getString(
                                            R.string.error_logout_failed_fmt,
                                            error.message ?: "",
                                        ),
                                )
                            }
                            Timber.e(error, "Logout failed")
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_unexpected_fmt, e.message ?: ""),
                    )
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
    val publicSettings: PublicSettings? = null,
    val authModeUserSelected: Boolean = false,
)
