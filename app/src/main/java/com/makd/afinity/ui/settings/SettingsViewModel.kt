package com.makd.afinity.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.data.models.player.VideoZoomMode
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
    private val appDataRepository: AppDataRepository,
    private val serverRepository: ServerRepository,
    private val offlineModeManager: OfflineModeManager,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val jellyseerrRepository: JellyseerrRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _combineLibrarySections = MutableStateFlow(false)
    val combineLibrarySections: StateFlow<Boolean> = _combineLibrarySections.asStateFlow()

    private val _homeSortByDateAdded = MutableStateFlow(true)
    val homeSortByDateAdded: StateFlow<Boolean> = _homeSortByDateAdded.asStateFlow()

    val episodeLayout: StateFlow<EpisodeLayout> = preferencesRepository.getEpisodeLayoutFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EpisodeLayout.HORIZONTAL
        )

    private val _manualOfflineMode = MutableStateFlow(false)
    val manualOfflineMode: StateFlow<Boolean> = _manualOfflineMode.asStateFlow()

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    val effectiveOfflineMode: StateFlow<Boolean> = combine(
        _manualOfflineMode,
        _isNetworkAvailable
    ) { manual, networkAvailable ->
        manual || !networkAvailable
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isJellyseerrAuthenticated: StateFlow<Boolean> = jellyseerrRepository.isAuthenticated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
                Timber.d("SettingsViewModel - Server name: ${server?.name ?: "NULL"}, Server ID: ${server?.id ?: "NULL"}")
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    userProfileImageUrl = profileImageUrl,
                    serverName = server?.name,
                    serverVersion = server?.version,
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
            preferencesRepository.getThemeModeFlow()
                .collect { _uiState.value = _uiState.value.copy(themeMode = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getDynamicColorsFlow()
                .collect { _uiState.value = _uiState.value.copy(dynamicColors = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getAutoPlayFlow()
                .collect { _uiState.value = _uiState.value.copy(autoPlay = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getSkipIntroEnabledFlow()
                .collect { _uiState.value = _uiState.value.copy(skipIntroEnabled = it) }
        }

        viewModelScope.launch {
            preferencesRepository.getSkipOutroEnabledFlow()
                .collect { _uiState.value = _uiState.value.copy(skipOutroEnabled = it) }
        }

        viewModelScope.launch {
            preferencesRepository.useExoPlayer.collect {
                _uiState.value = _uiState.value.copy(useExoPlayer = it)
            }
        }

        viewModelScope.launch {
            preferencesRepository.getPipGestureEnabledFlow().collect {
                _uiState.value = _uiState.value.copy(pipGestureEnabled = it)
            }
        }

        viewModelScope.launch {
            preferencesRepository.getPipBackgroundPlayFlow().collect {
                _uiState.value = _uiState.value.copy(pipBackgroundPlay = it)
            }
        }

        viewModelScope.launch {
            preferencesRepository.getOfflineModeFlow().collect {
                _manualOfflineMode.value = it
            }
        }

        viewModelScope.launch {
            networkConnectivityMonitor.isNetworkAvailable.collect { isAvailable ->
                _isNetworkAvailable.value = isAvailable
                Timber.d("Network availability changed: $isAvailable")
            }
        }

        viewModelScope.launch {
            preferencesRepository.getLogoAutoHideFlow().collect {
                _uiState.value = _uiState.value.copy(logoAutoHide = it)
            }
        }

        viewModelScope.launch {
            preferencesRepository.getDefaultVideoZoomModeFlow().collect { mode ->
                _uiState.value = _uiState.value.copy(defaultVideoZoomMode = mode)
            }
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

    fun togglePipGesture(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setPipGestureEnabled(enabled)
                Timber.d("PIP gesture set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle PIP gesture")
            }
        }
    }

    fun togglePipBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setPipBackgroundPlay(enabled)
                Timber.d("PIP background play set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle PIP background play")
            }
        }
    }

    fun toggleUseExoPlayer(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val currentSubtitlePrefs = preferencesRepository.getSubtitlePreferences()

                val updatedPrefs = when {
                    enabled && currentSubtitlePrefs.outlineStyle == com.makd.afinity.data.models.player.SubtitleOutlineStyle.BACKGROUND_BOX -> {
                        Timber.d("Switching to ExoPlayer: Resetting BACKGROUND_BOX to NONE")
                        currentSubtitlePrefs.copy(
                            outlineStyle = com.makd.afinity.data.models.player.SubtitleOutlineStyle.NONE,
                            outlineSize = 0f
                        )
                    }

                    !enabled && (currentSubtitlePrefs.outlineStyle == com.makd.afinity.data.models.player.SubtitleOutlineStyle.RAISED ||
                            currentSubtitlePrefs.outlineStyle == com.makd.afinity.data.models.player.SubtitleOutlineStyle.DEPRESSED) -> {
                        Timber.d("Switching to MPV: Resetting ${currentSubtitlePrefs.outlineStyle} to NONE")
                        currentSubtitlePrefs.copy(
                            outlineStyle = com.makd.afinity.data.models.player.SubtitleOutlineStyle.NONE
                        )
                    }

                    else -> null
                }

                updatedPrefs?.let { preferencesRepository.setSubtitlePreferences(it) }

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

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setOfflineMode(enabled)
                Timber.d("Offline mode set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle offline mode")
            }
        }
    }

    fun toggleLogoAutoHide(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setLogoAutoHide(enabled)
                Timber.d("Logo auto-hide set to: $enabled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle logo auto-hide")
            }
        }
    }

    fun setDefaultVideoZoomMode(mode: VideoZoomMode) {
        viewModelScope.launch {
            try {
                preferencesRepository.setDefaultVideoZoomMode(mode)
                Timber.d("Default video zoom mode set to: ${mode.getDisplayName()}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to set default video zoom mode")
            }
        }
    }

    fun setEpisodeLayout(layout: EpisodeLayout) {
        viewModelScope.launch {
            try {
                preferencesRepository.setEpisodeLayout(layout)
                Timber.d("Episode layout set to: ${layout.getDisplayName()}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to set episode layout")
            }
        }
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoggingOut = true)

                withContext(NonCancellable) {
                    appDataRepository.clearAllData()

                    authRepository.logout()

                    try {
                        jellyseerrRepository.logout()
                        Timber.d("Jellyseerr logout successful during AFinity logout")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to logout from Jellyseerr during AFinity logout")
                    }
                }

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

    fun logoutFromJellyseerr() {
        viewModelScope.launch {
            try {
                jellyseerrRepository.logout()
                Timber.d("Jellyseerr logout successful")
            } catch (e: Exception) {
                Timber.e(e, "Failed to logout from Jellyseerr")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to logout from Jellyseerr: ${e.message}"
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
    val serverVersion: String? = null,
    val serverUrl: String? = null,
    val userProfileImageUrl: String? = null,
    val themeMode: String = "SYSTEM",
    val dynamicColors: Boolean = true,
    val autoPlay: Boolean = true,
    val pipGestureEnabled: Boolean = false,
    val pipBackgroundPlay: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    val skipOutroEnabled: Boolean = true,
    val useExoPlayer: Boolean = true,
    val logoAutoHide: Boolean = false,
    val defaultVideoZoomMode: VideoZoomMode = VideoZoomMode.FIT,
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val error: String? = null
)