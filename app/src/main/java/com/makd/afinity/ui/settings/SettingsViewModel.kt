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

    private val _combineLibrarySections = MutableStateFlow(false)
    val combineLibrarySections: StateFlow<Boolean> = _combineLibrarySections.asStateFlow()

    private val _homeSortByDateAdded = MutableStateFlow(true)
    val homeSortByDateAdded: StateFlow<Boolean> = _homeSortByDateAdded.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                authRepository.currentUser,
                appDataRepository.userProfileImageUrl,
                serverRepository.currentServer
            ) { user, profileImageUrl, server ->
                Triple(user, profileImageUrl, server)
            }.collect { (user, profileImageUrl, server) ->
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    userProfileImageUrl = profileImageUrl,
                    serverName = server?.name,
                    serverUrl = serverRepository.getBaseUrl().ifEmpty { null },
                    isLoading = false
                )
            }
        }

        viewModelScope.launch {
            preferencesRepository.getCombineLibrarySectionsFlow().collect { combine ->
                _combineLibrarySections.value = combine
            }
        }

        viewModelScope.launch {
            preferencesRepository.getHomeSortByDateAddedFlow().collect { sortByDateAdded ->
                _homeSortByDateAdded.value = sortByDateAdded
            }
        }

        viewModelScope.launch {
            preferencesRepository.getThemeModeFlow().collect { _uiState.value = _uiState.value.copy(themeMode = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getDynamicColorsFlow().collect { _uiState.value = _uiState.value.copy(dynamicColors = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getAutoPlayFlow().collect { _uiState.value = _uiState.value.copy(autoPlay = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getSkipIntroEnabledFlow().collect { _uiState.value = _uiState.value.copy(skipIntroEnabled = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getSkipOutroEnabledFlow().collect { _uiState.value = _uiState.value.copy(skipOutroEnabled = it) }
        }

        viewModelScope.launch {
            preferencesRepository.useExoPlayer.collect { _uiState.value = _uiState.value.copy(useExoPlayer = it) }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            try {
                preferencesRepository.setThemeMode(mode)
                Timber.d("Theme mode set to: $mode")
            } catch (e: Exception) {
                Timber.e(e, "Failed to set theme mode")
            }
        }
    }

    fun toggleDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setDynamicColors(enabled)
                Timber.d("Dynamic colors set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle dynamic colors")
            }
        }
    }

    fun toggleCombineLibrarySections(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCombineLibrarySections(enabled)
        }
    }

    fun toggleHomeSortByDateAdded(sortByDateAdded: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHomeSortByDateAdded(sortByDateAdded)
        }
    }

    fun toggleAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setAutoPlay(enabled)
                Timber.d("Auto-play set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle auto-play")
            }
        }
    }

    fun toggleUseExoPlayer(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setUseExoPlayer(enabled)
                Timber.d("Use ExoPlayer set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle use exoplayer")
            }
        }
    }

    fun toggleSkipIntro(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setSkipIntroEnabled(enabled)
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
    val serverName: String? = null,
    val serverUrl: String? = null,
    val userProfileImageUrl: String? = null,
    val themeMode: String = "SYSTEM",
    val dynamicColors: Boolean = true,
    val autoPlay: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    val skipOutroEnabled: Boolean = true,
    val useExoPlayer: Boolean = true,
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val error: String? = null
)