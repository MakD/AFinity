package com.makd.afinity.data.websocket

import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.ServerRestartingMessage
import org.jellyfin.sdk.model.api.ServerShuttingDownMessage
import org.jellyfin.sdk.model.api.SessionsMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinWebSocketManager @Inject constructor(
    private val apiClient: ApiClient,
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    fun connect() {
        scope.launch {
            try {
                _connectionState.value = WebSocketState.CONNECTING

                apiClient.webSocket.state.collect { socketState ->
                    _connectionState.value = when (socketState) {
                        is SocketApiState.Connected -> WebSocketState.CONNECTED
                        is SocketApiState.Connecting -> WebSocketState.CONNECTING
                        is SocketApiState.Disconnected -> WebSocketState.DISCONNECTED
                        else -> WebSocketState.DISCONNECTED
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to monitor WebSocket state")
                _connectionState.value = WebSocketState.ERROR
            }
        }

        subscribeToLibraryChanges()
        subscribeToUserDataChanges()
        subscribeToSessionChanges()
        subscribeToPlayCommands()
        subscribeToServerMessages()
    }

    fun disconnect() {
        _connectionState.value = WebSocketState.DISCONNECTED
    }

    private fun subscribeToLibraryChanges() {
        scope.launch {
            try {
                apiClient.webSocket.subscribe(LibraryChangedMessage::class).collect { message ->
                    handleLibraryChanged(message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Library changes subscription failed")
            }
        }
    }

    private fun subscribeToUserDataChanges() {
        scope.launch {
            try {
                apiClient.webSocket.subscribe(UserDataChangedMessage::class).collect { message ->
                    handleUserDataChanged(message)
                }
            } catch (e: Exception) {
                Timber.e(e, "User data changes subscription failed")
            }
        }
    }

    private fun subscribeToSessionChanges() {
        scope.launch {
            try {
                apiClient.webSocket.subscribe(SessionsMessage::class).collect { message ->
                    handleSessionsUpdate(message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Sessions subscription failed")
            }
        }
    }

    private fun subscribeToPlayCommands() {
        scope.launch {
            try {
                apiClient.webSocket.subscribe(PlayMessage::class).collect { message ->
                    handlePlayCommand(message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Play commands subscription failed")
            }
        }

        scope.launch {
            try {
                apiClient.webSocket.subscribe(PlaystateMessage::class).collect { message ->
                    handlePlaystateCommand(message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Playstate commands subscription failed")
            }
        }
    }

    private fun subscribeToServerMessages() {
        scope.launch {
            try {
                apiClient.webSocket.subscribe(ServerRestartingMessage::class).collect {
                    handleServerRestarting()
                }
            } catch (e: Exception) {
                Timber.e(e, "Server restarting subscription failed")
            }
        }

        scope.launch {
            try {
                apiClient.webSocket.subscribe(ServerShuttingDownMessage::class).collect {
                    handleServerShutdown()
                }
            } catch (e: Exception) {
                Timber.e(e, "Server shutdown subscription failed")
            }
        }
    }

    private suspend fun handleLibraryChanged(message: LibraryChangedMessage) {
        Timber.d("Library changed - refreshing caches")
        mediaRepository.invalidateAllCaches()
    }

    private suspend fun handleUserDataChanged(message: UserDataChangedMessage) {
        val userDataChangeInfo = message.data

        userDataChangeInfo?.userDataList?.forEach { userData ->
            val itemId = userData.itemId

            if (itemId != null) {
                Timber.d("User data changed for item: $itemId")
                mediaRepository.invalidateItemCache(itemId)
                mediaRepository.invalidateNextUpCache()
            }
        }
    }

    private suspend fun handleSessionsUpdate(message: SessionsMessage) {
        Timber.d("Sessions updated")
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
    }

    private suspend fun handleServerShutdown() {
        Timber.w("Server is shutting down")
        _connectionState.value = WebSocketState.SERVER_SHUTDOWN
    }
}