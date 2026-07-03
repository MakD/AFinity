package com.makd.afinity.data.websocket

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.util.NetworkConnectivityMonitor
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
private data class AbsProgressEventPayload(@SerialName("data") val data: MediaProgress? = null)

@Serializable
private data class AbsUserUpdatedPayload(
    @SerialName("mediaProgress") val mediaProgress: List<MediaProgress> = emptyList()
)

@Serializable private data class AbsItemIdPayload(@SerialName("id") val id: String? = null)

private const val IDLE_TIMEOUT_MS = 5 * 60_000L

sealed interface AbsSocketEvent {
    data object ProgressChanged : AbsSocketEvent

    data object ItemsChanged : AbsSocketEvent

    data object SeriesChanged : AbsSocketEvent
}

@Singleton
class AudiobookshelfSocketManager
@Inject
constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val networkMonitor: NetworkConnectivityMonitor,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val json = Json { ignoreUnknownKeys = true }

    private var socket: Socket? = null
    private var isForeground = false
    private var idleJob: Job? = null

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<AbsSocketEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AbsSocketEvent> = _events.asSharedFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        securePreferencesRepository.onAbsTokenRefreshed = { newToken ->
            socket?.let {
                if (it.connected()) {
                    Timber.d("AbsSocket: re-authenticating with refreshed token")
                    it.emit("auth", newToken)
                }
            }
        }

        scope.launch {
            combine(
                    audiobookshelfRepository.isAuthenticated,
                    audiobookshelfRepository.currentConfig,
                ) { authenticated, config ->
                    if (authenticated) config?.serverUrl else null
                }
                .distinctUntilChanged()
                .collect { serverUrl ->
                    disconnect()
                    if (serverUrl != null && isForeground) {
                        connect(serverUrl)
                    }
                }
        }

        scope.launch {
            playbackManager.playbackState
                .map { it.sessionId != null && it.isPlaying }
                .distinctUntilChanged()
                .collect { playing ->
                    if (playing) {
                        idleJob?.cancel()
                        if (isForeground && socket == null) {
                            maybeConnect()
                        }
                    } else if (socket != null) {
                        restartIdleTimer()
                    }
                }
        }

        scope.launch {
            networkMonitor.isNetworkAvailable.collect { available ->
                if (available && isForeground && socket == null) {
                    maybeConnect()
                }
            }
        }
    }

    private fun restartIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            val state = playbackManager.playbackState.value
            val playing = state.sessionId != null && state.isPlaying
            if (!playing) {
                Timber.d("AbsSocket: idle timeout without playback — disconnecting")
                disconnect()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
        maybeConnect()
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
        disconnect()
    }

    private fun maybeConnect() {
        if (!audiobookshelfRepository.isAuthenticated.value) return
        val serverUrl = audiobookshelfRepository.currentConfig.value?.serverUrl ?: return
        connect(serverUrl)
    }

    @Synchronized
    private fun connect(serverUrl: String) {
        if (socket != null) return
        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
        if (token == null) {
            Timber.w("AbsSocket: no token available, skipping connect")
            return
        }

        Timber.d("AbsSocket: connecting to $serverUrl")
        _connectionState.value = WebSocketState.CONNECTING

        try {
            val options =
                IO.Options().apply {
                    transports = arrayOf(WebSocket.NAME)
                    reconnection = true
                    reconnectionDelay = 1_000
                    reconnectionDelayMax = 30_000
                    reconnectionAttempts = 5
                }

            val newSocket = IO.socket(URI.create(serverUrl), options)
            socket = newSocket

            newSocket.on(Socket.EVENT_CONNECT) {
                Timber.d("AbsSocket: connected, sending auth")
                newSocket.emit("auth", token)
            }
            newSocket.on("init") {
                Timber.d("AbsSocket: authenticated (init received)")
                _connectionState.value = WebSocketState.CONNECTED
                restartIdleTimer()
            }
            newSocket.on("auth_failed") {
                Timber.w("AbsSocket: auth_failed")
                disconnect()
            }
            newSocket.on(Socket.EVENT_DISCONNECT) { args ->
                Timber.d("AbsSocket: disconnected (${args.joinToString()})")
                _connectionState.value = WebSocketState.DISCONNECTED
            }
            newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Timber.w("AbsSocket: connect error (${args.joinToString()})")
                _connectionState.value = WebSocketState.ERROR
            }
            newSocket.io().on(Manager.EVENT_RECONNECT_FAILED) {
                Timber.w("AbsSocket: reconnection attempts exhausted")
                teardown(WebSocketState.ERROR)
            }

            newSocket.on("user_item_progress_updated") { args ->
                handleProgressEvent(args.firstOrNull())
            }
            newSocket.on("user_updated") { args -> handleUserUpdated(args.firstOrNull()) }

            newSocket.on("item_added") { args ->
                handleItemUpsert(args.firstOrNull(), "item_added")
            }
            newSocket.on("item_updated") { args ->
                handleItemUpsert(args.firstOrNull(), "item_updated")
            }
            newSocket.on("item_removed") { args -> handleItemRemoved(args.firstOrNull()) }

            listOf("series_added", "series_updated", "series_removed").forEach { event ->
                newSocket.on(event) {
                    Timber.d("AbsSocket: event=$event")
                    _events.tryEmit(AbsSocketEvent.SeriesChanged)
                }
            }

            newSocket.connect()
        } catch (e: Exception) {
            Timber.e(e, "AbsSocket: failed to create socket")
            socket = null
            _connectionState.value = WebSocketState.ERROR
        }
    }

    private fun handleProgressEvent(payload: Any?) {
        val raw = payload?.toString() ?: return
        scope.launch {
            try {
                val progress =
                    json.decodeFromString<AbsProgressEventPayload>(raw).data ?: return@launch
                mergeRemoteProgress(listOf(progress), source = "user_item_progress_updated")
            } catch (e: Exception) {
                Timber.e(e, "AbsSocket: failed to parse user_item_progress_updated")
            }
        }
    }

    private fun handleUserUpdated(payload: Any?) {
        val raw = payload?.toString() ?: return
        scope.launch {
            try {
                val progressList = json.decodeFromString<AbsUserUpdatedPayload>(raw).mediaProgress
                if (progressList.isNotEmpty()) {
                    mergeRemoteProgress(progressList, source = "user_updated")
                }
            } catch (e: Exception) {
                Timber.e(e, "AbsSocket: failed to parse user_updated")
            }
        }
    }

    private suspend fun mergeRemoteProgress(progressList: List<MediaProgress>, source: String) {
        val playing = playbackManager.playbackState.value
        val filtered = progressList.filterNot {
            playing.sessionId != null &&
                it.libraryItemId == playing.itemId &&
                it.episodeId == playing.episodeId
        }
        if (filtered.isEmpty()) return
        val applied = audiobookshelfRepository.applyRemoteProgress(filtered)
        if (applied > 0) {
            Timber.d("AbsSocket: $source applied $applied/${filtered.size} progress entries")
            _events.tryEmit(AbsSocketEvent.ProgressChanged)
        }
    }

    private fun handleItemUpsert(payload: Any?, source: String) {
        val raw = payload?.toString() ?: return
        scope.launch {
            try {
                val item = json.decodeFromString<LibraryItem>(raw)
                audiobookshelfRepository.applyRemoteItem(item)
                Timber.d("AbsSocket: $source applied for ${item.id}")
                _events.tryEmit(AbsSocketEvent.ItemsChanged)
            } catch (e: Exception) {
                Timber.e(e, "AbsSocket: failed to parse $source")
            }
        }
    }

    private fun handleItemRemoved(payload: Any?) {
        val raw = payload?.toString() ?: return
        scope.launch {
            try {
                val itemId = json.decodeFromString<AbsItemIdPayload>(raw).id ?: return@launch
                audiobookshelfRepository.removeRemoteItem(itemId)
                Timber.d("AbsSocket: item_removed applied for $itemId")
                _events.tryEmit(AbsSocketEvent.ItemsChanged)
            } catch (e: Exception) {
                Timber.e(e, "AbsSocket: failed to parse item_removed")
            }
        }
    }

    fun disconnect() = teardown(WebSocketState.DISCONNECTED)

    @Synchronized
    private fun teardown(newState: WebSocketState) {
        idleJob?.cancel()
        socket?.let {
            Timber.d("AbsSocket: tearing down socket")
            it.off()
            it.io().off(Manager.EVENT_RECONNECT_FAILED)
            it.disconnect()
        }
        socket = null
        _connectionState.value = newState
    }
}
