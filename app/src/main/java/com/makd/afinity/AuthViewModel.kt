package com.makd.afinity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class AuthenticationState {
    object Loading : AuthenticationState()

    object Authenticated : AuthenticationState()

    object NotAuthenticated : AuthenticationState()
}

@HiltViewModel
class AuthViewModel
@Inject
constructor(
    private val authRepository: AuthRepository,
    private val webSocketManager: JellyfinWebSocketManager,
) : ViewModel() {

    private val _authenticationState =
        MutableStateFlow<AuthenticationState>(AuthenticationState.Loading)
    val authenticationState: StateFlow<AuthenticationState> = _authenticationState.asStateFlow()

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
}