package com.makd.afinity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.navigation.AppLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val authRepository: AuthRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val webSocketManager: JellyfinWebSocketManager,
    private val appDataRepository: AppDataRepository,
    private val offlineModeManager: OfflineModeManager,
) : ViewModel() {

    private val _authenticationState =
        MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    val authenticationState: StateFlow<AuthenticationState> = _authenticationState.asStateFlow()
    val webSocketState = webSocketManager.connectionState

    val appLoadingState =
        combine(
                appDataRepository.isInitialDataLoaded,
                appDataRepository.loadingProgress,
                appDataRepository.loadingPhase,
            ) { isLoaded, progress, phase ->
                AppLoadingState(
                    isLoading = !isLoaded,
                    loadingProgress = progress,
                    loadingPhase = phase,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AppLoadingState(isLoading = true),
            )

    init {
        checkAuthenticationState()

        observeAuthenticationChanges()
    }

    private fun checkAuthenticationState() {
        viewModelScope.launch {
            try {
                Timber.d("Checking authentication state...")

                when (val result = authRepository.restoreAuthenticationState()) {
                    is AuthRepository.RestoreResult.Success -> {
                        Timber.d("Authentication restored successfully")
                        _authenticationState.value = AuthenticationState.Authenticated
                    }
                    is AuthRepository.RestoreResult.Degraded -> {
                        Timber.w(
                            result.reason,
                            "Session restored in degraded state (server unreachable)",
                        )
                        _authenticationState.value = AuthenticationState.Authenticated
                    }
                    is AuthRepository.RestoreResult.Failed -> {
                        Timber.d("No valid saved authentication, user needs to login")
                        _authenticationState.value = AuthenticationState.NotAuthenticated
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking authentication state")
                _authenticationState.value = AuthenticationState.NotAuthenticated
            }
        }
    }

    private fun observeAuthenticationChanges() {
        viewModelScope.launch {
            combine(authRepository.isAuthenticated, authRepository.isSwitchingSession) {
                    isAuthenticated,
                    isSwitchingSession ->
                    isAuthenticated to isSwitchingSession
                }
                .collect { (isAuthenticated, isSwitchingSession) ->
                    if (isSwitchingSession) {
                        if (_authenticationState.value != AuthenticationState.Loading) {
                            Timber.d("Session switch started, showing loading state")
                            _authenticationState.value = AuthenticationState.Loading
                        }
                    } else if (
                        isAuthenticated &&
                            _authenticationState.value != AuthenticationState.Authenticated
                    ) {
                        Timber.d("User authenticated via auth repository")
                        _authenticationState.value = AuthenticationState.Authenticated
                        webSocketManager.connect()
                    } else if (
                        !isAuthenticated &&
                            _authenticationState.value == AuthenticationState.Authenticated
                    ) {
                        Timber.d("User logged out via auth repository")
                        _authenticationState.value = AuthenticationState.NotAuthenticated
                        webSocketManager.disconnect()
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                Timber.d("Logging out user...")
                webSocketManager.disconnect()
                authRepository.logout()
                appDataRepository.clearAllData()
                _authenticationState.value = AuthenticationState.NotAuthenticated
                Timber.d("User logged out successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error during logout")
                _authenticationState.value = AuthenticationState.NotAuthenticated
            }
        }
    }

    fun refreshAuthState() {
        checkAuthenticationState()
    }
}
