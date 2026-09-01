package com.makd.afinity.ui.jellyseerr

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.auth.QuickConnectAuthorization
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.PublicSettings
import com.makd.afinity.data.network.UrlCandidates
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.auth.AuthRepository
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
    private val authRepository: AuthRepository,
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
            _uiState.update { it.copy(publicSettings = null, quickConnectAvailable = false) }
            return
        }

        probeJob = viewModelScope.launch {
            delay(PROBE_DEBOUNCE_MS)
            for (url in UrlCandidates.jellyseerr(trimmed)) {
                val settings = jellyseerrRepository.verifyServer(url) ?: continue
                _uiState.update { it.copy(publicSettings = settings) }
                applyDetectedAuthMode(settings)
                updateQuickConnectAvailability(settings)
                return@launch
            }
            _uiState.update { it.copy(publicSettings = null, quickConnectAvailable = false) }
        }
    }

    private suspend fun updateQuickConnectAvailability(settings: PublicSettings) {
        val jellyfinBacked =
            settings.mediaServerLogin && settings.mediaServerType == MEDIA_SERVER_TYPE_JELLYFIN
        val hasJellyfinSession = sessionManager.currentSession.value != null

        val available =
            jellyfinBacked && hasJellyfinSession && authRepository.isQuickConnectEnabled()

        _uiState.update { it.copy(quickConnectAvailable = available) }
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

                val validUrl = resolveVerifiedServerUrl() ?: return@launch

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

    private suspend fun resolveVerifiedServerUrl(): String? {
        val rawUrl = _uiState.value.serverUrl.trim().removeSuffix("/")

        for (url in UrlCandidates.jellyseerr(rawUrl)) {
            val settings = jellyseerrRepository.verifyServer(url)
            if (settings != null) {
                _uiState.update { it.copy(publicSettings = settings) }
                return url
            }
            Timber.d("Verification failed for candidate URL: $url")
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isQuickConnecting = false,
                error = "Could not connect. Please verify this is a valid Seerr server.",
            )
        }
        return null
    }

    fun loginWithQuickConnect() {
        viewModelScope.launch {
            try {
                if (!validateServerUrl()) {
                    return@launch
                }

                _uiState.update { it.copy(isQuickConnecting = true, error = null) }

                val validUrl = resolveVerifiedServerUrl() ?: return@launch
                jellyseerrRepository.setServerUrl(validUrl)

                val initiateResult = jellyseerrRepository.initiateQuickConnect()
                val request =
                    initiateResult.getOrElse { error ->
                        val statusCode = (error as? JellyseerrLoginException)?.code
                        if (statusCode == 404) {
                            _uiState.update {
                                it.copy(
                                    isQuickConnecting = false,
                                    quickConnectAvailable = false,
                                    error =
                                        context.getString(
                                            R.string.error_seerr_quick_connect_unsupported
                                        ),
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isQuickConnecting = false,
                                    error =
                                        context.getString(
                                            R.string.error_seerr_quick_connect_initiate
                                        ),
                                )
                            }
                        }
                        Timber.e(error, "Jellyseerr Quick Connect initiate failed")
                        return@launch
                    }

                when (val authorization = authRepository.authorizeQuickConnect(request.code)) {
                    QuickConnectAuthorization.APPROVED -> Unit

                    QuickConnectAuthorization.UNKNOWN_CODE -> {
                        _uiState.update {
                            it.copy(
                                isQuickConnecting = false,
                                error =
                                    context.getString(
                                        R.string.error_seerr_quick_connect_other_server
                                    ),
                            )
                        }
                        return@launch
                    }

                    else -> {
                        Timber.e("Quick Connect authorization returned $authorization")
                        _uiState.update {
                            it.copy(
                                isQuickConnecting = false,
                                error =
                                    context.getString(
                                        R.string.error_seerr_quick_connect_not_approved
                                    ),
                            )
                        }
                        return@launch
                    }
                }

                jellyseerrRepository
                    .authenticateQuickConnect(request.secret)
                    .fold(
                        onSuccess = { successUser ->
                            _uiState.update {
                                it.copy(
                                    isQuickConnecting = false,
                                    loginSuccess = true,
                                    serverUrl = validUrl,
                                    loggedInUser =
                                        successUser.displayName
                                            ?: successUser.username
                                            ?: successUser.email,
                                    currentUser = successUser,
                                )
                            }
                            Timber.d(
                                "Quick Connect sign-in successful for user: ${successUser.username}"
                            )
                        },
                        onFailure = { error ->
                            val statusCode = (error as? JellyseerrLoginException)?.code
                            val message =
                                when {
                                    statusCode == 403 ->
                                        context.getString(
                                            R.string.error_seerr_quick_connect_access_denied
                                        )

                                    statusCode != null && statusCode >= 500 ->
                                        context.getString(R.string.error_seerr_account_setup_failed)

                                    else -> parseErrorMessage(error.message)
                                }
                            _uiState.update {
                                it.copy(isQuickConnecting = false, error = message)
                            }
                            Timber.e(error, "Jellyseerr Quick Connect authentication failed")
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isQuickConnecting = false,
                        error = context.getString(R.string.error_unexpected_fmt, e.message ?: ""),
                    )
                }
                Timber.e(e, "Error during Quick Connect sign-in")
            }
        }
    }

    private fun validateServerUrl(): Boolean {
        val state = _uiState.value

        if (state.serverUrl.isBlank()) {
            _uiState.update {
                it.copy(serverUrlError = context.getString(R.string.error_server_url_required))
            }
            return false
        }
        if (!isValidUrl(state.serverUrl)) {
            _uiState.update {
                it.copy(serverUrlError = context.getString(R.string.error_invalid_url_format))
            }
            return false
        }
        return true
    }

    private fun validateInputs(): Boolean {
        val state = _uiState.value
        var isValid = validateServerUrl()

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

    private fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.isNotBlank() && !trimmed.contains(" ")
    }

    private fun parseErrorMessage(message: String?): String {
        return when {
            message == null -> context.getString(R.string.error_login_check_credentials)
            message.contains("401") -> context.getString(R.string.error_invalid_credentials)
            message.contains("403") -> context.getString(R.string.error_access_forbidden)
            message.contains("404") -> context.getString(R.string.error_server_not_found)
            message.contains("network", ignoreCase = true) ->
                context.getString(R.string.error_network_check_connection)

            message.contains("timeout", ignoreCase = true) ->
                context.getString(R.string.error_connection_timeout)

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
    val isQuickConnecting: Boolean = false,
    val quickConnectAvailable: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val loggedInUser: String? = null,
    val currentUser: JellyseerrUser? = null,
    val publicSettings: PublicSettings? = null,
    val authModeUserSelected: Boolean = false,
)
