package com.makd.afinity.ui.settings.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.repository.DatabaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ServerManagementState(
    val servers: List<ServerWithUserCount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverToDelete: ServerWithUserCount? = null
)

data class ServerWithUserCount(
    val server: Server,
    val userCount: Int
)

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ServerManagementState())
    val state: StateFlow<ServerManagementState> = _state.asStateFlow()

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)

                val servers = databaseRepository.getAllServers()
                val serversWithCounts = servers.map { server ->
                    val users = databaseRepository.getUsersForServer(server.id)
                    ServerWithUserCount(
                        server = server,
                        userCount = users.size
                    )
                }

                _state.value = _state.value.copy(
                    servers = serversWithCounts,
                    isLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Error loading servers")
                _state.value = _state.value.copy(
                    error = e.message ?: "Failed to load servers",
                    isLoading = false
                )
            }
        }
    }

    fun showDeleteConfirmation(serverWithUserCount: ServerWithUserCount) {
        _state.value = _state.value.copy(serverToDelete = serverWithUserCount)
    }

    fun hideDeleteConfirmation() {
        _state.value = _state.value.copy(serverToDelete = null)
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null, serverToDelete = null)

                val currentSession = sessionManager.currentSession.value
                if (currentSession?.serverId == serverId) {
                    _state.value = _state.value.copy(
                        error = "Cannot delete the currently active server. Please switch to another server first.",
                        isLoading = false
                    )
                    return@launch
                }

                databaseRepository.deleteServer(serverId)

                databaseRepository.clearServerData(serverId)

                loadServers()

                Timber.d("Server deleted successfully: $serverId")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting server")
                _state.value = _state.value.copy(
                    error = e.message ?: "Failed to delete server",
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}