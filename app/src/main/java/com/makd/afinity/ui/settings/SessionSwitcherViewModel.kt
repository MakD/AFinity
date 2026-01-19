package com.makd.afinity.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.auth.JellyfinAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class SessionSwitcherState(
    val sessionGroups: List<ServerSessionGroup> = emptyList(),
    val currentSession: Session? = null,
    val isSwitching: Boolean = false,
    val error: String? = null,
    val switchSuccess: Boolean = false
)

data class ServerSessionGroup(
    val server: Server,
    val sessions: List<UserSession>
)

data class UserSession(
    val serverId: String,
    val userId: UUID,
    val username: String,
    val userAvatar: String?,
    val isCurrent: Boolean
)

@HiltViewModel
class SessionSwitcherViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val authRepository: AuthRepository,
    private val appDataRepository: AppDataRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SessionSwitcherState())
    val state: StateFlow<SessionSwitcherState> = _state.asStateFlow()

    init {
        loadSavedSessions()
        observeCurrentSession()
    }

    private fun observeCurrentSession() {
        viewModelScope.launch {
            sessionManager.currentSession.collect { session ->
                _state.value = _state.value.copy(currentSession = session)
                if (!_state.value.isSwitching) {
                    loadSavedSessions()
                }
            }
        }
    }

    fun loadSavedSessions() {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.currentSession.value

                val servers = databaseRepository.getAllServers()

                val sessionGroups = servers.mapNotNull { server ->
                    val tokens = securePreferencesRepository.getAllServerUserTokens()
                        .filter { it.serverId == server.id }

                    if (tokens.isEmpty()) {
                        return@mapNotNull null
                    }

                    val userSessions = tokens.map { token ->
                        val user = databaseRepository.getUser(token.userId)
                        UserSession(
                            serverId = server.id,
                            userId = token.userId,
                            username = token.username,
                            userAvatar = user?.primaryImageTag?.let { tag ->
                                "${server.address}/Users/${user.id}/Images/Primary?tag=$tag"
                            },
                            isCurrent = currentSession?.serverId == server.id &&
                                    currentSession.userId == token.userId
                        )
                    }

                    ServerSessionGroup(
                        server = server,
                        sessions = userSessions
                    )
                }

                _state.value = _state.value.copy(
                    sessionGroups = sessionGroups,
                    currentSession = currentSession
                )
            } catch (e: Exception) {
                Timber.e(e, "Error loading saved sessions")
                _state.value = _state.value.copy(
                    error = "Failed to load sessions: ${e.message}"
                )
            }
        }
    }

    fun switchSession(serverId: String, userId: UUID) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isSwitching = true,
                    error = null,
                    switchSuccess = false
                )

                val token = securePreferencesRepository.getServerUserToken(serverId, userId)
                if (token == null) {
                    _state.value = _state.value.copy(
                        isSwitching = false,
                        error = "Session token not found"
                    )
                    return@launch
                }

                val server = databaseRepository.getServer(serverId)
                if (server == null) {
                    _state.value = _state.value.copy(
                        isSwitching = false,
                        error = "Server not found"
                    )
                    return@launch
                }

                val user = databaseRepository.getUser(userId)
                if (user != null) {
                    (authRepository as? JellyfinAuthRepository)?.setSessionActive(user)
                }

                val result = sessionManager.startSession(
                    serverUrl = server.address,
                    serverId = serverId,
                    userId = userId,
                    accessToken = token,
                    caller = "SessionSwitcherViewModel.switchSession"
                )

                result.onSuccess {
                    Timber.d("Successfully switched to session: $serverId / $userId")
                    loadSavedSessions()
                    _state.value = _state.value.copy(
                        isSwitching = false,
                        switchSuccess = true
                    )
                }.onFailure { error ->
                    Timber.e(error, "Failed to switch session")
                    _state.value = _state.value.copy(
                        isSwitching = false,
                        error = "Failed to switch session: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error switching session")
                _state.value = _state.value.copy(
                    isSwitching = false,
                    error = "Error switching session: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun resetSwitchSuccess() {
        _state.value = _state.value.copy(switchSuccess = false)
    }
}