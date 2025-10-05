package com.makd.afinity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val webSocketManager: JellyfinWebSocketManager,
    private val appDataRepository: AppDataRepository
) : ViewModel() {

    private val _authenticationState = MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    val authenticationState: StateFlow<AuthenticationState> = _authenticationState.asStateFlow()
    val webSocketState = webSocketManager.connectionState

    init {
        checkAuthenticationState()

        observeAuthenticationChanges()
    }

    private fun checkAuthenticationState() {
        viewModelScope.launch {
            try {
                Timber.d("Checking authentication state...")

                val restored = authRepository.restoreAuthenticationState()

                if (restored) {
                    Timber.d("Authentication restored successfully")
                    _authenticationState.value = AuthenticationState.Authenticated
                } else {
                    Timber.d("No valid authentication found, user needs to login")
                    _authenticationState.value = AuthenticationState.NotAuthenticated
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking authentication state")
                _authenticationState.value = AuthenticationState.NotAuthenticated
            }
        }
    }

    private fun observeAuthenticationChanges() {
        viewModelScope.launch {
            authRepository.isAuthenticated.collect { isAuthenticated ->
                if (isAuthenticated && _authenticationState.value != AuthenticationState.Authenticated) {
                    Timber.d("User authenticated via auth repository")
                    _authenticationState.value = AuthenticationState.Authenticated
                    webSocketManager.connect()
                } else if (!isAuthenticated && _authenticationState.value == AuthenticationState.Authenticated) {
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