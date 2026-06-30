package com.makd.afinity.data.websocket

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.syncplay.SyncPlayGroupUpdate
import com.makd.afinity.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.ScheduledTasksInfoMessage
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.ServerRestartingMessage
import org.jellyfin.sdk.model.api.ServerShuttingDownMessage
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.SessionsMessage
import org.jellyfin.sdk.model.api.SyncPlayCommandMessage
import org.jellyfin.sdk.model.api.SyncPlayGroupUpdateCommandMessage
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinWebSocketManager
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val mediaChangeManager: MediaChangeManager,
    private val mediaRefreshBus: MediaRefreshBus,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    private val _liveSessions = MutableSharedFlow<List<SessionInfoDto>>(replay = 0)
    val liveSessions: SharedFlow<List<SessionInfoDto>> = _liveSessions.asSharedFlow()

    private val _liveTasks = MutableSharedFlow<List<TaskInfo>>(replay = 1)
    val liveTasks = _liveTasks.asSharedFlow()

    private val _syncPlayCommands = MutableSharedFlow<SendCommand>(extraBufferCapacity = 16)
    val syncPlayCommands: SharedFlow<SendCommand> = _syncPlayCommands.asSharedFlow()

    private val _syncPlayGroupUpdates =
        MutableSharedFlow<SyncPlayGroupUpdate>(extraBufferCapacity = 16)
    val syncPlayGroupUpdates: SharedFlow<SyncPlayGroupUpdate> = _syncPlayGroupUpdates.asSharedFlow()

    init {

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        scope.launch {
            sessionManager.currentSession.collect { session ->
                if (session != null) {
                    disconnect()
                    connect()
                } else {
                    disconnect()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (sessionManager.currentSession.value != null) {
            connect()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        disconnect()
    }

    fun connect() {
        if (connectionJob?.isActive == true) return

        val currentApiClient = sessionManager.getCurrentApiClient() ?: return

        connectionJob = scope.launch {
            launch { monitorSocketState(currentApiClient) }
            launch { subscribeToLibraryChanges(currentApiClient) }
            launch { subscribeToUserDataChanges(currentApiClient) }
            launch { subscribeToSessionChanges(currentApiClient) }
            launch { subscribeToPlayCommands(currentApiClient) }
            launch { subscribeToServerMessages(currentApiClient) }
            launch { subscribeToTaskChanges(currentApiClient) }
            launch { subscribeToSyncPlayCommands(currentApiClient) }
            launch { subscribeToSyncPlayGroupUpdates(currentApiClient) }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = WebSocketState.DISCONNECTED
    }

    private suspend fun monitorSocketState(apiClient: ApiClient) {
        try {
            _connectionState.value = WebSocketState.CONNECTING

            apiClient.webSocket.state.collect { socketState ->
                _connectionState.value =
                    when (socketState) {
                        is SocketApiState.Connected -> WebSocketState.CONNECTED
                        is SocketApiState.Connecting -> WebSocketState.CONNECTING
                        is SocketApiState.Disconnected -> WebSocketState.DISCONNECTED
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to monitor WebSocket state")
            _connectionState.value = WebSocketState.ERROR
        }
    }

    private suspend fun subscribeToLibraryChanges(apiClient: ApiClient) {
        apiClient.webSocket
            .subscribe(LibraryChangedMessage::class)
            .catch { e -> Timber.e(e, "Library changes subscription failed") }
            .collect { message -> handleLibraryChanged(message) }
    }

    private suspend fun subscribeToUserDataChanges(apiClient: ApiClient) {
        apiClient.webSocket
            .subscribe(UserDataChangedMessage::class)
            .catch { e -> Timber.e(e, "User data changes subscription failed") }
            .collect { message -> handleUserDataChanged(message) }
    }

    private suspend fun subscribeToSessionChanges(apiClient: ApiClient) {
        apiClient.webSocket
            .subscribe(SessionsMessage::class)
            .catch { e -> Timber.e(e, "Sessions subscription failed") }
            .collect { message -> handleSessionsUpdate(message) }
    }

    private suspend fun subscribeToPlayCommands(apiClient: ApiClient) {
        coroutineScope {
            launch {
                apiClient.webSocket
                    .subscribe(PlayMessage::class)
                    .catch { e -> Timber.e(e, "Play commands subscription failed") }
                    .collect { message -> handlePlayCommand(message) }
            }

            launch {
                apiClient.webSocket
                    .subscribe(PlaystateMessage::class)
                    .catch { e -> Timber.e(e, "Playstate commands subscription failed") }
                    .collect { message -> handlePlaystateCommand(message) }
            }
        }
    }

    private suspend fun subscribeToServerMessages(apiClient: ApiClient) {
        coroutineScope {
            launch {
                apiClient.webSocket
                    .subscribe(ServerRestartingMessage::class)
                    .catch { e -> Timber.e(e, "Server restarting subscription failed") }
                    .collect { handleServerRestarting() }
            }

            launch {
                apiClient.webSocket
                    .subscribe(ServerShuttingDownMessage::class)
                    .catch { e -> Timber.e(e, "Server shutdown subscription failed") }
                    .collect { handleServerShutdown() }
            }
        }
    }

    private fun handleLibraryChanged(message: LibraryChangedMessage) {
        val update = message.data
        Timber.d(
            "Library changed - added=${update?.itemsAdded?.size ?: 0}, updated=${update?.itemsUpdated?.size ?: 0}, removed=${update?.itemsRemoved?.size ?: 0}"
        )
        mediaRefreshBus.emit(RefreshTrigger.LIBRARY_CHANGED)
        mediaChangeManager.notifyLibraryContentChanged("library changed websocket event")
    }

    private suspend fun subscribeToTaskChanges(apiClient: ApiClient) {
        apiClient.webSocket
            .subscribe(ScheduledTasksInfoMessage::class)
            .catch { e -> Timber.e(e, "Tasks subscription failed") }
            .collect { message ->
                message.data?.let { tasks ->
                    _liveTasks.emit(tasks)
                    handleScheduledTasksChanged(tasks)
                }
            }
    }

    private fun handleScheduledTasksChanged(tasks: List<TaskInfo>) {
        val runningLibraryTask = tasks.firstOrNull { task ->
            task.state == TaskState.RUNNING && task.libraryScanTask()
        }

        if (runningLibraryTask != null) {
            Timber.d("Library task running: ${runningLibraryTask.key ?: runningLibraryTask.name}")
            mediaRefreshBus.emit(RefreshTrigger.LIBRARY_CHANGED)
        }
    }

    private fun TaskInfo.libraryScanTask(): Boolean {
        val text =
            listOfNotNull(key, name, category, description)
                .joinToString(separator = " ")
                .lowercase()

        return "library" in text || "scan" in text || "refresh" in text
    }

    private suspend fun handleUserDataChanged(message: UserDataChangedMessage) {
        val userDataList = message.data?.userDataList ?: return
        if (userDataList.isNotEmpty()) {
            Timber.d("Batch processing ${userDataList.size} user data changes")
            mediaChangeManager.applyUserDataChangesBatch(userDataList)
            mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
        }
    }

    private suspend fun handleSessionsUpdate(message: SessionsMessage) {
        Timber.d("WebSocket: Sessions updated !")
        message.data?.let { sessions -> _liveSessions.emit(sessions) }
    }

    private suspend fun handlePlayCommand(message: PlayMessage) {
        Timber.d("Received play command from server")
    }

    private suspend fun handlePlaystateCommand(message: PlaystateMessage) {
        Timber.d("Received playstate command")
    }

    private suspend fun handleServerRestarting() {
        Timber.w("Server is restarting")
        _connectionState.value = WebSocketState.SERVER_RESTARTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(20_000L)
            disconnect()
            connect()
        }
    }

    private suspend fun handleServerShutdown() {
        Timber.w("Server is shutting down")
        _connectionState.value = WebSocketState.SERVER_SHUTDOWN
        disconnect()
    }

    private suspend fun subscribeToSyncPlayCommands(apiClient: ApiClient) {
        apiClient.webSocket
            .subscribe(SyncPlayCommandMessage::class)
            .catch { e -> Timber.e(e, "SyncPlay commands subscription failed") }
            .collect { message ->
                if (message.data == null) {
                    Timber.w(
                        "SyncPlay: SyncPlayCommandMessage received but data is null — SDK deserialization failed"
                    )
                } else {
                    Timber.d(
                        "SyncPlay: command received — type=${message.data!!.command}, ticks=${message.data!!.positionTicks}"
                    )
                    _syncPlayCommands.emit(message.data!!)
                }
            }
    }

    private suspend fun subscribeToSyncPlayGroupUpdates(apiClient: ApiClient) {
        apiClient.webSocket
            .subscribe(SyncPlayGroupUpdateCommandMessage::class)
            .catch { e -> Timber.e(e, "SyncPlay group updates subscription failed") }
            .collect { message ->
                message.data?.let { groupUpdate ->
                    _syncPlayGroupUpdates.emit(
                        SyncPlayGroupUpdate(
                            type = groupUpdate.type,
                            groupId = groupUpdate.groupId,
                        )
                    )
                }
            }
    }
}
