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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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

    private val _isRefreshInitiated = MutableStateFlow(false)
    private val isRefreshExecuting = AtomicBoolean(false)

    val isLibraryRefreshing: StateFlow<Boolean> =
        combine(_isRefreshInitiated, scheduledTasks) { initiated, tasks ->
                val isRunningOnServer = isRefreshTaskRunning(tasks)
                initiated || isRunningOnServer
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

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
                updateTasksState(instantTasks)
            }
        }
    }

    fun initialize(serverId: String) {
        currentServerId = serverId
        taskCache[serverId]?.let { updateTasksState(it) }
        _activeSessions.value = null
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

    private fun updateTasksState(tasks: List<TaskInfo>?) {
        _scheduledTasks.value = tasks
        taskCache[currentServerId] = tasks ?: emptyList()

        if (isRefreshTaskRunning(tasks)) {
            _isRefreshInitiated.value = false
        }
    }

    private fun isRefreshTaskRunning(tasks: List<TaskInfo>?): Boolean {
        return tasks?.any { task ->
            val isRefreshTask = task.key == "RefreshLibrary"
            val isActive = task.state == TaskState.RUNNING || task.state == TaskState.CANCELLING
            isRefreshTask && isActive
        } ?: false
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
        if (!isRefreshExecuting.compareAndSet(false, true)) return

        _isRefreshInitiated.value = true

        viewModelScope.launch {
            try {
                val result = jellyfinRepository.refreshAllLibraries()
                if (result.isFailure) {
                    _isRefreshInitiated.value = false
                }
            } finally {
                isRefreshExecuting.set(false)
            }
        }
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
                updateTasksState(result.getOrNull())
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
