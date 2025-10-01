package com.makd.afinity.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.server.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val appDataRepository: AppDataRepository,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                authRepository.currentUser,
                preferencesRepository.getDarkThemeFlow(),
                appDataRepository.userProfileImageUrl,
                serverRepository.currentServer
            ) { user, darkTheme, profileImageUrl, server ->
                SettingsUiState(
                    currentUser = user,
                    darkTheme = darkTheme,
                    serverInfo = server?.name,
                    userProfileImageUrl = profileImageUrl,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        viewModelScope.launch {
            try {
                val dynamicColors = preferencesRepository.getDynamicColors()
                val autoPlay = preferencesRepository.getAutoPlay()
                val skipIntro = preferencesRepository.getSkipIntroEnabled()
                val skipOutro = preferencesRepository.getSkipOutroEnabled()

                _uiState.value = _uiState.value.copy(
                    dynamicColors = dynamicColors,
                    autoPlay = autoPlay,
                    skipIntroEnabled = skipIntro,
                    skipOutroEnabled = skipOutro
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load settings")
            }
        }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setDarkTheme(enabled)
                _uiState.value = _uiState.value.copy(darkTheme = enabled)
                Timber.d("Dark theme set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle dark theme")
            }
        }
    }

    fun toggleDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setDynamicColors(enabled)
                _uiState.value = _uiState.value.copy(dynamicColors = enabled)
                Timber.d("Dynamic colors set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle dynamic colors")
            }
        }
    }

    fun toggleAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setAutoPlay(enabled)
                _uiState.value = _uiState.value.copy(autoPlay = enabled)
                Timber.d("Auto-play set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle auto-play")
            }
        }
    }

    fun toggleSkipIntro(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setSkipIntroEnabled(enabled)
                _uiState.value = _uiState.value.copy(skipIntroEnabled = enabled)
                Timber.d("Skip intro set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle skip intro")
            }
        }
    }

    fun toggleSkipOutro(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setSkipOutroEnabled(enabled)
                _uiState.value = _uiState.value.copy(skipOutroEnabled = enabled)
                Timber.d("Skip outro set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle skip outro")
            }
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoggingOut = true)
                Timber.d("Initiating logout from Settings...")
                appDataRepository.clearAllData()
                authRepository.logout()
                Timber.d("Logout successful")
                onLogoutComplete()
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                _uiState.value = _uiState.value.copy(
                    isLoggingOut = false,
                    error = "Logout failed: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class SettingsUiState(
    val currentUser: User? = null,
    val serverInfo: String? = null,
    val userProfileImageUrl: String? = null,
    val darkTheme: Boolean = false,
    val dynamicColors: Boolean = true,
    val autoPlay: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    val skipOutroEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val error: String? = null
)