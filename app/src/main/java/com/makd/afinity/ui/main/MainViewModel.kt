package com.makd.afinity.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.util.LocalNetworkPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
@Inject
constructor(
    private val appDataRepository: AppDataRepository,
    private val webSocketManager: JellyfinWebSocketManager,
    private val sessionManager: SessionManager,
    private val serverRepository: ServerRepository,
    private val localNetworkPermission: LocalNetworkPermission,
) : ViewModel() {

    val webSocketState = webSocketManager.connectionState

    val needsLocalNetworkPermission = sessionManager.needsLocalNetworkPermission

    fun onLocalNetworkPermissionGranted() {
        localNetworkPermission.refresh()
        sessionManager.clearLocalNetworkPermissionPrompt()
        viewModelScope.launch { serverRepository.forceReconnect() }
    }

    fun dismissLocalNetworkPermissionPrompt() {
        sessionManager.clearLocalNetworkPermissionPrompt()
    }

    val uiState: StateFlow<MainUiState> =
        combine(appDataRepository.userName, appDataRepository.userProfileImageUrl) { name, imageUrl
                ->
                MainUiState(userName = name, userProfileImageUrl = imageUrl)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MainUiState(),
            )
}
