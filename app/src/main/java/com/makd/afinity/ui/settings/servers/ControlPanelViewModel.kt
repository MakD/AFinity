package com.makd.afinity.ui.settings.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.ServerStorage
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import timber.log.Timber
import java.util.UUID
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
        private const val TICKS_PER_SECOND = 10_000_000L
        private const val MESSAGE_TIMEOUT_MS = 5_000L
        private const val APP_NAME = "AFinity"
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

    private val _serverStorage = MutableStateFlow<ServerStorage?>(null)
    val serverStorage: StateFlow<ServerStorage?> = _serverStorage.asStateFlow()

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
    private var storageJob: Job? = null

    private val _pendingPause = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val pendingPause: StateFlow<Map<String, Boolean>> = _pendingPause.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    val currentUserId: UUID?
        get() = sessionManager.currentSession.value?.userId

    init {
        viewModelScope.launch {
            jellyfinWebSocketManager.liveSessions.collect { instantSessions ->
                _activeSessions.value = instantSessions
                sessionCache[currentServerId] = instantSessions
                reconcilePendingPause(instantSessions)
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
        _serverStorage.value = null
        loadServerStorage(serverId)
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

    private fun reconcilePendingPause(sessions: List<SessionInfoDto>) {
        if (_pendingPause.value.isEmpty()) return
        _pendingPause.update { pending ->
            pending.filterNot { (sessionId, optimisticPaused) ->
                val actual =
                    sessions.firstOrNull { it.id == sessionId }?.playState?.isPaused ?: return@filterNot true
                actual == optimisticPaused
            }
        }
    }

    fun togglePause(session: SessionInfoDto) {
        val sessionId = session.id ?: return
        val target = !(session.playState?.isPaused ?: true)
        _pendingPause.update { it + (sessionId to target) }
        dispatch(sessionId) {
            jellyfinRepository.sendSessionPlaystateCommand(
                sessionId = sessionId,
                command = if (target) PlaystateCommand.PAUSE else PlaystateCommand.UNPAUSE,
            )
        }
    }

    fun sendPlaystate(sessionId: String, command: PlaystateCommand) {
        dispatch(sessionId) {
            jellyfinRepository.sendSessionPlaystateCommand(sessionId = sessionId, command = command)
        }
    }

    fun seekBy(session: SessionInfoDto, deltaSeconds: Long) {
        val sessionId = session.id ?: return
        val current = session.playState?.positionTicks ?: 0L
        val runtime = session.nowPlayingItem?.runTimeTicks
        val target =
            (current + deltaSeconds * TICKS_PER_SECOND).coerceAtLeast(0L).let {
                if (runtime != null && runtime > 0) it.coerceAtMost(runtime) else it
            }
        dispatch(sessionId) {
            jellyfinRepository.sendSessionPlaystateCommand(
                sessionId = sessionId,
                command = PlaystateCommand.SEEK,
                seekPositionTicks = target,
            )
        }
    }

    fun seekTo(sessionId: String, positionTicks: Long) {
        dispatch(sessionId) {
            jellyfinRepository.sendSessionPlaystateCommand(
                sessionId = sessionId,
                command = PlaystateCommand.SEEK,
                seekPositionTicks = positionTicks.coerceAtLeast(0L),
            )
        }
    }

    fun setVolume(sessionId: String, volume: Int) {
        dispatch(sessionId) { jellyfinRepository.setSessionVolume(sessionId, volume) }
    }

    fun toggleMute(sessionId: String, muted: Boolean) {
        dispatch(sessionId) {
            jellyfinRepository.sendSessionGeneralCommand(
                sessionId = sessionId,
                command = if (muted) GeneralCommandType.UNMUTE else GeneralCommandType.MUTE,
            )
        }
    }

    fun sendMessage(sessionId: String, text: String) {
        dispatch(sessionId) {
            jellyfinRepository.sendSessionMessage(
                sessionId = sessionId,
                header = APP_NAME,
                text = text,
                timeoutMs = MESSAGE_TIMEOUT_MS,
            )
        }
    }

    private fun dispatch(sessionId: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val result = block()
            if (result.isFailure) {
                _pendingPause.update { it - sessionId }
                _commandError.tryEmit(Unit)
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

        val justCompletedIds = previousRunningTaskIds - currentRunningIds
        if (justCompletedIds.isNotEmpty()) {
            val completedLibraryTasks = newTasks.filter {
                it.id in justCompletedIds && it.isLibraryRelated()
            }
            if (completedLibraryTasks.isNotEmpty()) {
                Timber.d(
                    "Library tasks completed: ${completedLibraryTasks.map { it.key }} — invalidating media caches"
                )
                appDataRepository.scheduleHomeRefreshAfterTaskCompletion()
            }
        }
        previousRunningTaskIds = currentRunningIds
    }

    private fun TaskInfo.isLibraryRelated(): Boolean {
        val text = listOfNotNull(key, name, category, description).joinToString(" ").lowercase()
        return "library" in text || "scan" in text || "refresh" in text
    }

    private fun loadServerStorage(serverId: String) {
        storageJob?.cancel()
        storageJob = viewModelScope.launch {
            if (isAdmin.first { it != null } != true) return@launch
            jellyfinRepository.getServerStorageFlow(serverId).collect { storage ->
                _serverStorage.value = storage
            }
        }
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
        storageJob?.cancel()
    }
}
