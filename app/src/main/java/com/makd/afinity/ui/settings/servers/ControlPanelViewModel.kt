package com.makd.afinity.ui.settings.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.StorageDevice
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.ui.admin.plugins.isProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.PluginStatus
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SystemInfo
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState

@HiltViewModel
class ControlPanelViewModel
@Inject
constructor(
    private val adminRepository: ServerAdminRepository,
    private val sessionManager: SessionManager,
    private val jellyfinRepositoryProvider: Provider<JellyfinRepository>,
) : ViewModel() {

    companion object {
        private const val FULL_DISK_FRACTION = 0.95f
        private const val TICKS_PER_SECOND = 10_000_000L
        private const val MESSAGE_TIMEOUT_MS = 5_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TASK_REFRESH_DELAY_MS = 500L
        private const val APP_NAME = "AFinity"
        private const val REFRESH_LIBRARY_TASK_KEY = "RefreshLibrary"
    }

    private val _pendingTaskCommands = MutableStateFlow<Set<String>>(emptySet())
    val pendingTaskCommands: StateFlow<Set<String>> = _pendingTaskCommands.asStateFlow()

    private val _isRefreshInitiated = MutableStateFlow(false)
    private val isRefreshExecuting = AtomicBoolean(false)

    private val _pendingPause = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val pendingPause: StateFlow<Map<String, Boolean>> = _pendingPause.asStateFlow()

    private val _commandError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commandError: SharedFlow<Unit> = _commandError.asSharedFlow()

    val scheduledTasks: StateFlow<List<TaskInfo>?> =
        adminRepository.scheduledTasks
            .map { it?.getOrNull() }
            .onEach { tasks -> if (isRefreshTaskRunning(tasks)) _isRefreshInitiated.value = false }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                adminRepository.scheduledTasks.value?.getOrNull(),
            )

    val tasksFailed: StateFlow<Boolean> =
        adminRepository.scheduledTasks
            .map { it?.isFailure == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val activeSessions: StateFlow<List<SessionInfoDto>?> =
        adminRepository.sessions
            .map { it?.getOrNull() }
            .onEach { sessions -> if (sessions != null) reconcilePendingPause(sessions) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                adminRepository.sessions.value?.getOrNull(),
            )

    val sessionsFailed: StateFlow<Boolean> =
        adminRepository.sessions
            .map { it?.isFailure == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val isLibraryRefreshing: StateFlow<Boolean> =
        combine(_isRefreshInitiated, scheduledTasks) { initiated, tasks ->
                initiated || isRefreshTaskRunning(tasks)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = false,
            )

    val systemInfo: StateFlow<SystemInfo?> =
        adminRepository.systemInfo
            .map { it?.getOrNull() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                adminRepository.systemInfo.value?.getOrNull(),
            )

    val pluginProblems: StateFlow<List<PluginInfo>> =
        adminRepository.plugins
            .map { result -> result?.getOrNull().orEmpty().filter { it.status.isProblem } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val pluginsPendingRestart: StateFlow<Boolean> =
        adminRepository.plugins
            .map { result ->
                result?.getOrNull().orEmpty().any { it.status == PluginStatus.RESTART }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val fullDisks: StateFlow<List<StorageDevice>> =
        sessionManager.currentSession
            .map { session -> session?.takeIf { it.isAdmin == true }?.serverId }
            .distinctUntilChanged()
            .flatMapLatest { serverId ->
                if (serverId == null) {
                    flowOf(emptyList())
                } else {
                    jellyfinRepositoryProvider.get().getServerStorageFlow(serverId).map { storage ->
                        storage.devices.filter { device ->
                            device.totalSpace > 0 && device.usedFraction >= FULL_DISK_FRACTION
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val isCreatingBackup: StateFlow<Boolean> = adminRepository.isCreatingBackup

    val failedSignIns: StateFlow<Int> =
        adminRepository.recentFailedSignIns
            .map { it?.getOrNull() ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

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

    val serverName: StateFlow<String> =
        sessionManager.currentSession
            .map { it?.server?.name.orEmpty() }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                sessionManager.currentSession.value?.server?.name.orEmpty(),
            )

    val serverVersion: StateFlow<String?> =
        sessionManager.currentSession
            .map { it?.server?.version }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                sessionManager.currentSession.value?.server?.version,
            )

    val currentUserId: UUID?
        get() = sessionManager.currentSession.value?.userId

    val userCount: StateFlow<Int?> =
        adminRepository.userCountUpdates.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val deviceCount: StateFlow<Int?> =
        adminRepository.deviceCountUpdates.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { adminRepository.getUsers() }
        viewModelScope.launch { adminRepository.getDevices() }
    }

    fun retry() {
        adminRepository.refresh()
    }

    private fun reconcilePendingPause(sessions: List<SessionInfoDto>) {
        if (_pendingPause.value.isEmpty()) return
        _pendingPause.update { pending ->
            pending.filterNot { (sessionId, optimisticPaused) ->
                val actual =
                    sessions.firstOrNull { it.id == sessionId }?.playState?.isPaused
                        ?: return@filterNot true
                actual == optimisticPaused
            }
        }
    }

    fun togglePause(session: SessionInfoDto) {
        val sessionId = session.id ?: return
        val target = !(session.playState?.isPaused ?: true)
        _pendingPause.update { it + (sessionId to target) }
        dispatch(sessionId) {
            adminRepository.sendPlaystateCommand(
                sessionId = sessionId,
                command = if (target) PlaystateCommand.PAUSE else PlaystateCommand.UNPAUSE,
            )
        }
    }

    fun sendPlaystate(sessionId: String, command: PlaystateCommand) {
        dispatch(sessionId) {
            adminRepository.sendPlaystateCommand(sessionId = sessionId, command = command)
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
            adminRepository.sendPlaystateCommand(
                sessionId = sessionId,
                command = PlaystateCommand.SEEK,
                seekPositionTicks = target,
            )
        }
    }

    fun seekTo(sessionId: String, positionTicks: Long) {
        dispatch(sessionId) {
            adminRepository.sendPlaystateCommand(
                sessionId = sessionId,
                command = PlaystateCommand.SEEK,
                seekPositionTicks = positionTicks.coerceAtLeast(0L),
            )
        }
    }

    fun setVolume(sessionId: String, volume: Int) {
        dispatch(sessionId) { adminRepository.setVolume(sessionId, volume) }
    }

    fun toggleMute(sessionId: String, muted: Boolean) {
        dispatch(sessionId) {
            adminRepository.sendGeneralCommand(
                sessionId = sessionId,
                command = if (muted) GeneralCommandType.UNMUTE else GeneralCommandType.MUTE,
            )
        }
    }

    fun sendMessage(sessionId: String, text: String) {
        dispatch(sessionId) {
            adminRepository.sendMessage(
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

    private fun isRefreshTaskRunning(tasks: List<TaskInfo>?): Boolean {
        return tasks?.any { task ->
            val isRefreshTask = task.key == REFRESH_LIBRARY_TASK_KEY
            val isActive = task.state == TaskState.RUNNING || task.state == TaskState.CANCELLING
            isRefreshTask && isActive
        } ?: false
    }

    fun restartServer() {
        viewModelScope.launch { adminRepository.restartServer() }
    }

    fun shutdownServer() {
        viewModelScope.launch { adminRepository.shutdownServer() }
    }

    fun refreshAllLibraries() {
        if (!isRefreshExecuting.compareAndSet(false, true)) return

        _isRefreshInitiated.value = true

        viewModelScope.launch {
            try {
                val result = adminRepository.scanAllLibraries()
                if (result.isFailure) {
                    _isRefreshInitiated.value = false
                }
            } finally {
                isRefreshExecuting.set(false)
            }
        }
    }

    fun stopTask(taskId: String) {
        sendTaskCommand(taskId) { adminRepository.stopTask(taskId) }
    }

    private fun sendTaskCommand(taskId: String, block: suspend () -> Result<Unit>) {
        if (taskId in _pendingTaskCommands.value) return
        _pendingTaskCommands.update { it + taskId }
        viewModelScope.launch {
            try {
                if (block().isSuccess) {
                    delay(TASK_REFRESH_DELAY_MS)
                } else {
                    _commandError.tryEmit(Unit)
                }
                adminRepository.refresh()
            } finally {
                _pendingTaskCommands.update { it - taskId }
            }
        }
    }
}
