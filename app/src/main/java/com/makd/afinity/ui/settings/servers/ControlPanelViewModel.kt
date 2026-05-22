package com.makd.afinity.ui.settings.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class ControlPanelViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val sessionManager: SessionManager,
    private val appDataRepository: AppDataRepository,
    private val jellyfinWebSocketManager: JellyfinWebSocketManager,
) : ViewModel() {

    companion object {
        private val taskCache = ConcurrentHashMap<String, List<TaskInfo>>()
        private val sessionCache = ConcurrentHashMap<String, List<SessionInfoDto>>()
    }

    private var currentServerId: String = ""
    private var previousRunningTaskIds: Set<String> = emptySet()

    private val _scheduledTasks = MutableStateFlow<List<TaskInfo>?>(null)
    val scheduledTasks: StateFlow<List<TaskInfo>?> = _scheduledTasks.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<SessionInfoDto>?>(null)
    val activeSessions: StateFlow<List<SessionInfoDto>?> = _activeSessions.asStateFlow()

    val baseUrl: String
        get() = sessionManager.currentSession.value?.serverUrl ?: ""

    val isAdmin: StateFlow<Boolean?> =
        sessionManager.currentSession
            .map { it?.isAdmin }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                sessionManager.currentSession.value?.isAdmin,
            )

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            jellyfinWebSocketManager.liveSessions.collect { instantSessions ->
                _activeSessions.value = instantSessions
                sessionCache[currentServerId] = instantSessions
            }
        }

        viewModelScope.launch {
            jellyfinWebSocketManager.liveTasks.collect { instantTasks ->
                checkForCompletedTasks(instantTasks)
                _scheduledTasks.value = instantTasks
                taskCache[currentServerId] = instantTasks
            }
        }
    }

    fun initialize(serverId: String) {
        currentServerId = serverId
        taskCache[serverId]?.let { _scheduledTasks.value = it }
        sessionCache[serverId]?.let { _activeSessions.value = it }
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            pollTasksNow()

            while (isActive) {
                try {
                    val result = jellyfinRepository.getActiveSessions()
                    if (result.isSuccess) {
                        val sessions = result.getOrNull() ?: emptyList()
                        _activeSessions.value = sessions
                        sessionCache[serverId] = sessions
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed session fetch")
                }
                delay(5000)
            }
        }
    }

    private fun checkForCompletedTasks(newTasks: List<TaskInfo>) {
        val currentRunningIds =
            newTasks
                .filter { it.state == TaskState.RUNNING || it.state == TaskState.CANCELLING }
                .mapNotNull { it.id }
                .toSet()

        val justCompleted = previousRunningTaskIds - currentRunningIds
        if (justCompleted.isNotEmpty()) {
            Timber.d("Tasks completed: $justCompleted — invalidating media caches")
            appDataRepository.scheduleHomeRefreshAfterTaskCompletion()
        }
        previousRunningTaskIds = currentRunningIds
    }

    fun restartServer() {
        viewModelScope.launch { jellyfinRepository.restartServer() }
    }

    fun shutdownServer() {
        viewModelScope.launch { jellyfinRepository.shutdownServer() }
    }

    fun refreshAllLibraries() {
        viewModelScope.launch { jellyfinRepository.refreshAllLibraries() }
    }

    fun runTask(taskId: String) {
        viewModelScope.launch {
            if (jellyfinRepository.startScheduledTask(taskId).isSuccess) {
                delay(500)
                pollTasksNow()
            }
        }
    }

    fun stopTask(taskId: String) {
        viewModelScope.launch {
            if (jellyfinRepository.stopScheduledTask(taskId).isSuccess) {
                delay(500)
                pollTasksNow()
            }
        }
    }

    private suspend fun pollTasksNow() {
        try {
            val result = jellyfinRepository.getScheduledTasks()
            if (result.isSuccess) {
                _scheduledTasks.value = result.getOrNull() ?: emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to force poll scheduled tasks")
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
