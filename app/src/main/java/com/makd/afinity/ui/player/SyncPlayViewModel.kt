package com.makd.afinity.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.syncplay.SyncPlayMemberInfo
import com.makd.afinity.data.models.syncplay.SyncPlayState
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.syncplay.SyncPlayRepository
import com.makd.afinity.data.syncplay.SyncPlayGroupEvent
import com.makd.afinity.data.syncplay.SyncPlayGroupUpdate
import com.makd.afinity.data.syncplay.SyncPlayRawWebSocket
import com.makd.afinity.data.syncplay.SyncPlayTimeSyncEngine
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupUpdateType
import org.jellyfin.sdk.model.api.PlayQueueUpdate
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import timber.log.Timber
import javax.inject.Inject

fun interface SyncPlayInterceptor {
    fun handle(event: PlayerEvent): Boolean
}

interface SyncPlayPlayerActions {
    fun executePlay()

    fun executePause()

    fun executeSeek(positionMs: Long)

    val currentPositionMs: Long

    val currentIsPlaying: Boolean

    val currentItemId: UUID?
}

data class SyncPlayUiState(
    val availableGroups: List<GroupInfoDto> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val showGroupSheet: Boolean = false,
    val error: String? = null,
    val isJoining: Boolean = false,
)

sealed class SyncPlayEffect {
    data object GroupJoined : SyncPlayEffect()

    data object GroupLeft : SyncPlayEffect()

    data class ShowError(val message: String) : SyncPlayEffect()

    data class LoadContent(
        val item: AfinityItem,
        val mediaSourceId: String,
        val startPositionMs: Long,
    ) : SyncPlayEffect()
}

@HiltViewModel
class SyncPlayViewModel
@Inject
constructor(
    private val syncPlayRepository: SyncPlayRepository,
    private val webSocketManager: JellyfinWebSocketManager,
    private val timeSyncEngine: SyncPlayTimeSyncEngine,
    private val rawWebSocket: SyncPlayRawWebSocket,
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {
    val syncPlayState: StateFlow<SyncPlayState> = syncPlayRepository.syncPlayState

    private val _uiState = MutableStateFlow(SyncPlayUiState())
    val uiState: StateFlow<SyncPlayUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SyncPlayEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<SyncPlayEffect> = _effects.asSharedFlow()

    private val _memberInfoMap = MutableStateFlow<Map<String, SyncPlayMemberInfo>>(emptyMap())
    val memberInfoMap: StateFlow<Map<String, SyncPlayMemberInfo>> = _memberInfoMap.asStateFlow()

    private var playerActions: SyncPlayPlayerActions? = null

    private var currentPlaylistItemId: UUID? = null

    private var scheduledCommandJob: Job? = null
    private var bufferingFlowJob: Job? = null

    init {
        viewModelScope.launch { collectSyncPlayCommands() }
        viewModelScope.launch { collectRawCommands() }
        viewModelScope.launch { collectGroupUpdates() }
        viewModelScope.launch { collectRawGroupEvents() }
        viewModelScope.launch { collectMemberSessionChanges() }
        viewModelScope.launch { collectPlayQueueUpdates() }
    }

    fun setPlayerActions(actions: SyncPlayPlayerActions) {
        playerActions = actions
    }

    fun setBufferingFlow(flow: Flow<Boolean>) {
        bufferingFlowJob?.cancel()
        bufferingFlowJob = viewModelScope.launch {
            flow.collect { isBuffering -> onBufferingStateChanged(isBuffering) }
        }
    }

    fun handleLocalPlayerEvent(event: PlayerEvent): Boolean {
        if (!syncPlayRepository.syncPlayState.value.isInGroup) return false
        return when (event) {
            is PlayerEvent.Play -> {
                viewModelScope.launch { syncPlayRepository.unpause() }
                true
            }
            is PlayerEvent.Pause -> {
                viewModelScope.launch { syncPlayRepository.pause() }
                true
            }
            is PlayerEvent.Seek -> {
                viewModelScope.launch { syncPlayRepository.seek(event.positionMs * 10_000L) }
                true
            }
            is PlayerEvent.SeekRelative -> {
                val currentPos = playerActions?.currentPositionMs ?: 0L
                val newPos = (currentPos + event.deltaMs).coerceAtLeast(0L)
                viewModelScope.launch { syncPlayRepository.seek(newPos * 10_000L) }
                false
            }
            is PlayerEvent.OnSeekBarDragFinished -> {
                viewModelScope.launch { syncPlayRepository.seek(event.positionMs * 10_000L) }
                false
            }
            else -> false
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGroups = true, error = null) }
            val groups = syncPlayRepository.getGroups()
            _uiState.update { it.copy(availableGroups = groups, isLoadingGroups = false) }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, isJoining = true) }
            rawWebSocket.start()
            syncPlayRepository.createGroup(name)
            val itemId = playerActions?.currentItemId ?: return@launch
            val positionMs = playerActions?.currentPositionMs ?: 0L
            syncPlayRepository.setNewQueue(
                itemIds = listOf(itemId),
                position = 0,
                startPositionTicks = positionMs * 10_000L,
            )
        }
    }

    fun joinGroup(groupId: UUID) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, isJoining = true) }
            rawWebSocket.start()
            syncPlayRepository.joinGroup(groupId)
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            rawWebSocket.stop()
            syncPlayRepository.leaveGroup()
            timeSyncEngine.stop()
            currentPlaylistItemId = null
            _effects.emit(SyncPlayEffect.GroupLeft)
        }
    }

    fun toggleGroupSheet() {
        val willOpen = !_uiState.value.showGroupSheet
        _uiState.update { it.copy(showGroupSheet = willOpen) }
        if (willOpen) {
            rawWebSocket.start()
            loadGroups()
        }
    }

    fun dismissGroupSheet() {
        _uiState.update { it.copy(showGroupSheet = false, isJoining = false) }
    }

    fun onAppBackground() {
        if (!syncPlayRepository.syncPlayState.value.isInGroup) return
        val playlistItemId = currentPlaylistItemId ?: return
        val positionMs = playerActions?.currentPositionMs ?: 0L
        viewModelScope.launch {
            syncPlayRepository.reportBuffering(
                positionTicks = positionMs * 10_000L,
                isPlaying = false,
                playlistItemId = playlistItemId,
            )
        }
    }

    fun onAppForeground() {
        if (!syncPlayRepository.syncPlayState.value.isInGroup) return
        viewModelScope.launch {
            timeSyncEngine.syncOnJoin()
            val playlistItemId = currentPlaylistItemId ?: return@launch
            val positionMs = playerActions?.currentPositionMs ?: 0L
            syncPlayRepository.reportReady(
                positionTicks = positionMs * 10_000L,
                isPlaying = playerActions?.currentIsPlaying ?: false,
                playlistItemId = playlistItemId,
            )
        }
    }

    private suspend fun collectSyncPlayCommands() {
        webSocketManager.syncPlayCommands
            .catch { e -> Timber.e(e, "Error collecting SyncPlay commands (SDK path)") }
            .collect { command -> handleSyncPlayCommand(command) }
    }

    private suspend fun collectRawCommands() {
        rawWebSocket.commands
            .catch { e -> Timber.e(e, "Error collecting SyncPlay commands (raw WS path)") }
            .collect { command -> handleSyncPlayCommand(command) }
    }

    private suspend fun collectGroupUpdates() {
        webSocketManager.syncPlayGroupUpdates
            .catch { e -> Timber.e(e, "Error collecting SyncPlay group updates") }
            .collect { update -> handleGroupUpdate(update) }
    }

    private suspend fun collectRawGroupEvents() {
        rawWebSocket.groupEvents
            .catch { e -> Timber.e(e, "Error collecting SyncPlay raw group events") }
            .collect { event ->
                syncPlayRepository.updateFromGroupEvent(event)
                when (event) {
                    is SyncPlayGroupEvent.GroupStateRefreshed -> {
                        timeSyncEngine.syncOnJoin()
                        _uiState.update { it.copy(isJoining = false) }
                        _effects.emit(SyncPlayEffect.GroupJoined)
                    }
                    is SyncPlayGroupEvent.GroupLeft -> {
                        timeSyncEngine.stop()
                        rawWebSocket.stop()
                        currentPlaylistItemId = null
                        _effects.emit(SyncPlayEffect.GroupLeft)
                    }
                    else -> {}
                }
            }
    }

    private suspend fun collectPlayQueueUpdates() {
        rawWebSocket.playQueueUpdates
            .catch { e -> Timber.e(e, "Error collecting SyncPlay queue updates") }
            .collect { update -> handlePlayQueueUpdate(update) }
    }

    private suspend fun handlePlayQueueUpdate(update: PlayQueueUpdate) {
        val groupId = syncPlayRepository.syncPlayState.value.groupId ?: return

        syncPlayRepository.updateFromGroupEvent(
            SyncPlayGroupEvent.QueueChanged(groupId = groupId, update = update)
        )

        val playingItem = update.playlist.getOrNull(update.playingItemIndex) ?: return

        currentPlaylistItemId = playingItem.playlistItemId

        val startPositionMs = update.startPositionTicks / 10_000L

        val alreadyPlaying = playerActions?.currentItemId == playingItem.itemId
        if (alreadyPlaying) {
            playerActions?.executeSeek(startPositionMs)
            syncPlayRepository.reportReady(
                positionTicks = startPositionMs * 10_000L,
                isPlaying = playerActions?.currentIsPlaying ?: false,
                playlistItemId = playingItem.playlistItemId,
            )
            Timber.d(
                "SyncPlay: already playing ${playingItem.itemId}, synced to ${startPositionMs}ms"
            )
            return
        }

        try {
            val item =
                mediaRepository.getItemById(playingItem.itemId)
                    ?: run {
                        Timber.w("SyncPlay: could not load item ${playingItem.itemId}")
                        return
                    }
            val mediaSourceId =
                item.sources.firstOrNull()?.id
                    ?: run {
                        Timber.w("SyncPlay: item ${playingItem.itemId} has no sources")
                        return
                    }
            Timber.d("SyncPlay: loading ${item.name} at ${startPositionMs}ms")
            _effects.emit(SyncPlayEffect.LoadContent(item, mediaSourceId, startPositionMs))
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: failed to load item ${playingItem.itemId}")
        }
    }

    private suspend fun handleSyncPlayCommand(command: SendCommand) {
        currentPlaylistItemId = command.playlistItemId
        val rawDelayMs = timeSyncEngine.toScheduledDelayMs(command.`when`)
        val delayMs = rawDelayMs.coerceIn(0L, 3_000L)
        Timber.d(
            "SyncPlay command: ${command.command}, rawDelayMs=$rawDelayMs, clampedDelayMs=$delayMs, ticks=${command.positionTicks}, clockOffset=${timeSyncEngine.clockOffsetMs}ms"
        )
        if (rawDelayMs > 3_000L) {
            Timber.w(
                "SyncPlay: rawDelayMs=$rawDelayMs clamped to 3000ms — likely clock skew. Check time sync."
            )
        }

        scheduledCommandJob?.cancel()
        scheduledCommandJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            executeCommand(command)
        }
    }

    private fun executeCommand(command: SendCommand) {
        val actions = playerActions ?: return
        when (command.command) {
            SendCommandType.UNPAUSE -> actions.executePlay()
            SendCommandType.PAUSE -> actions.executePause()
            SendCommandType.SEEK -> {
                val positionMs = (command.positionTicks ?: 0L) / 10_000L
                actions.executeSeek(positionMs)
            }
            SendCommandType.STOP -> actions.executePause()
        }
    }

    private suspend fun handleGroupUpdate(update: SyncPlayGroupUpdate) {
        Timber.d("SyncPlay SDK update: type=${update.type}, groupId=${update.groupId}")
        when (update.type) {
            GroupUpdateType.GROUP_JOINED -> {
                if (_uiState.value.isJoining) {
                    syncPlayRepository.setGroupJoined(update.groupId)
                    timeSyncEngine.syncOnJoin()
                    _uiState.update { it.copy(isJoining = false) }
                    _effects.emit(SyncPlayEffect.GroupJoined)
                    Timber.w("SyncPlay: GROUP_JOINED received via SDK fallback (raw WS was late)")
                }
            }
            GroupUpdateType.GROUP_LEFT,
            GroupUpdateType.NOT_IN_GROUP,
            GroupUpdateType.GROUP_DOES_NOT_EXIST -> {
                if (syncPlayRepository.syncPlayState.value.isInGroup) {
                    syncPlayRepository.updateFromGroupEvent(
                        SyncPlayGroupEvent.GroupLeft(update.groupId)
                    )
                    timeSyncEngine.stop()
                    rawWebSocket.stop()
                    currentPlaylistItemId = null
                    _effects.emit(SyncPlayEffect.GroupLeft)
                }
            }
            GroupUpdateType.CREATE_GROUP_DENIED,
            GroupUpdateType.JOIN_GROUP_DENIED,
            GroupUpdateType.LIBRARY_ACCESS_DENIED -> {
                val message = "Access denied: ${update.type.serialName}"
                syncPlayRepository.updateFromGroupEvent(
                    SyncPlayGroupEvent.Error(update.type, message)
                )
                _uiState.update { it.copy(error = message, isJoining = false) }
                _effects.emit(SyncPlayEffect.ShowError(message))
            }
            else -> {}
        }
    }

    private suspend fun onBufferingStateChanged(isBuffering: Boolean) {
        if (!syncPlayRepository.syncPlayState.value.isInGroup) return
        val playlistItemId = currentPlaylistItemId ?: return
        val positionMs = playerActions?.currentPositionMs ?: 0L
        if (isBuffering) {
            syncPlayRepository.reportBuffering(
                positionTicks = positionMs * 10_000L,
                isPlaying = false,
                playlistItemId = playlistItemId,
            )
        } else {
            syncPlayRepository.reportReady(
                positionTicks = positionMs * 10_000L,
                isPlaying = playerActions?.currentIsPlaying ?: false,
                playlistItemId = playlistItemId,
            )
        }
    }

    private suspend fun collectMemberSessionChanges() {
        syncPlayRepository.syncPlayState
            .map { it.members }
            .distinctUntilChanged()
            .collect { members ->
                if (members.isEmpty()) {
                    _memberInfoMap.value = emptyMap()
                    return@collect
                }

                jellyfinRepository.getActiveSessions().onSuccess { sessions ->
                    val baseUrl = jellyfinRepository.getBaseUrl()

                    _memberInfoMap.value =
                        sessions
                            .filter { it.userName != null }
                            .groupBy { it.userName!! }
                            .mapValues { (_, userSessions) ->
                                val activeSession =
                                    userSessions.firstOrNull { session ->
                                        session.nowPlayingItem != null ||
                                            session.playState?.isPaused == false
                                    } ?: userSessions.first()

                                val imageUrl =
                                    activeSession.userId?.let { uid ->
                                        "$baseUrl/Users/$uid/Images/Primary"
                                    }

                                SyncPlayMemberInfo(
                                    username = activeSession.userName!!,
                                    deviceName = activeSession.deviceName,
                                    clientName = activeSession.client,
                                    appVersion = activeSession.applicationVersion,
                                    profileImageUrl = imageUrl,
                                )
                            }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        rawWebSocket.stop()
        timeSyncEngine.stop()
    }
}
