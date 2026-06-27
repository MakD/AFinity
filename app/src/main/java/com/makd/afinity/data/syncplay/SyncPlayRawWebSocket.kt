package com.makd.afinity.data.syncplay

import com.makd.afinity.data.manager.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateUpdate
import org.jellyfin.sdk.model.api.GroupUpdateType
import org.jellyfin.sdk.model.api.PlayQueueUpdate
import org.jellyfin.sdk.model.api.SendCommand
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPlayRawWebSocket
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val okHttpClient: OkHttpClient,
) {
    private val _groupEvents = MutableSharedFlow<SyncPlayGroupEvent>(extraBufferCapacity = 16)
    val groupEvents: SharedFlow<SyncPlayGroupEvent> = _groupEvents.asSharedFlow()

    private val _playQueueUpdates = MutableSharedFlow<PlayQueueUpdate>(extraBufferCapacity = 4)
    val playQueueUpdates: SharedFlow<PlayQueueUpdate> = _playQueueUpdates.asSharedFlow()

    private val _commands = MutableSharedFlow<SendCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<SendCommand> = _commands.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val wsClient = okHttpClient.newBuilder().pingInterval(30, TimeUnit.SECONDS).build()

    @Volatile private var activeSocket: WebSocket? = null
    @Volatile private var started = false

    fun start() {
        if (started) return
        val apiClient = sessionManager.getCurrentApiClient() ?: return
        val baseUrl = apiClient.baseUrl ?: return
        val token = apiClient.accessToken ?: return
        started = true
        connect(baseUrl, token)
    }

    fun stop() {
        started = false
        activeSocket?.close(1000, "SyncPlay session ended")
        activeSocket = null
    }

    private fun connect(baseUrl: String, token: String) {
        val wsUrl =
            baseUrl.trimEnd('/').replace("https://", "wss://").replace("http://", "ws://") +
                "/socket"
        Timber.d("SyncPlay: opening dedicated WebSocket")
        val request =
            Request.Builder()
                .url(wsUrl)
                .header("Authorization", "MediaBrowser Token=\"$token\"")
                .build()
        activeSocket = wsClient.newWebSocket(request, Listener())
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.d("SyncPlay: dedicated WebSocket connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { parseMessage(text) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "SyncPlay: WebSocket failure")
            activeSocket = null
            if (!started) return
            scope.launch {
                delay(3_000L)
                if (!started) return@launch
                val apiClient = sessionManager.getCurrentApiClient() ?: return@launch
                val baseUrl = apiClient.baseUrl ?: return@launch
                val token = apiClient.accessToken ?: return@launch
                connect(baseUrl, token)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.d("SyncPlay: dedicated WebSocket closed ($code: $reason)")
            activeSocket = null
        }
    }

    private suspend fun parseMessage(text: String) {
        if (!text.contains("SyncPlay")) return
        try {
            val root = ApiSerializer.json.parseToJsonElement(text).jsonObject
            when (root["MessageType"]?.jsonPrimitive?.contentOrNull) {
                "SyncPlayCommand" -> parseSyncPlayCommand(root)
                "SyncPlayGroupUpdate" -> parseSyncPlayGroupUpdate(root)
                else -> {}
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: failed to parse raw frame")
        }
    }

    private suspend fun parseSyncPlayCommand(root: kotlinx.serialization.json.JsonObject) {
        val data = root["Data"]?.jsonObject ?: return
        try {
            val command = ApiSerializer.json.decodeFromJsonElement(SendCommand.serializer(), data)
            Timber.d("SyncPlay raw command: ${command.command}, ticks=${command.positionTicks}")
            _commands.emit(command)
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: failed to deserialize SendCommand")
        }
    }

    private suspend fun parseSyncPlayGroupUpdate(root: kotlinx.serialization.json.JsonObject) {
        try {
            val outerData = root["Data"]?.jsonObject ?: return
            val typeStr = outerData["Type"]?.jsonPrimitive?.contentOrNull ?: return
            val groupUpdateType = GroupUpdateType.fromNameOrNull(typeStr) ?: return
            val innerData = outerData["Data"]

            when (groupUpdateType) {
                GroupUpdateType.GROUP_JOINED,
                GroupUpdateType.GROUP_LEFT -> {
                    if (innerData == null) return
                    val groupInfo =
                        ApiSerializer.json.decodeFromJsonElement(
                            GroupInfoDto.serializer(),
                            innerData,
                        )
                    val event =
                        if (groupUpdateType == GroupUpdateType.GROUP_JOINED)
                            SyncPlayGroupEvent.GroupStateRefreshed(groupInfo)
                        else SyncPlayGroupEvent.GroupLeft(groupInfo.groupId)
                    _groupEvents.emit(event)
                    Timber.d(
                        "SyncPlay raw: $groupUpdateType — group=${groupInfo.groupName}, state=${groupInfo.state}"
                    )
                }
                GroupUpdateType.STATE_UPDATE -> {
                    if (innerData == null) return
                    val stateUpdate =
                        ApiSerializer.json.decodeFromJsonElement(
                            GroupStateUpdate.serializer(),
                            innerData,
                        )
                    val groupId =
                        outerData["GroupId"]?.let {
                            ApiSerializer.json.decodeFromJsonElement(
                                org.jellyfin.sdk.model.serializer.UUIDSerializer(),
                                it,
                            )
                        } ?: return
                    _groupEvents.emit(SyncPlayGroupEvent.StateChanged(groupId, stateUpdate.state))
                    Timber.d("SyncPlay raw: STATE_UPDATE → ${stateUpdate.state}")
                }
                GroupUpdateType.USER_JOINED,
                GroupUpdateType.USER_LEFT -> {
                    val userName = innerData?.jsonPrimitive?.contentOrNull ?: return
                    val groupId =
                        outerData["GroupId"]?.let {
                            ApiSerializer.json.decodeFromJsonElement(
                                org.jellyfin.sdk.model.serializer.UUIDSerializer(),
                                it,
                            )
                        } ?: return
                    val event =
                        if (groupUpdateType == GroupUpdateType.USER_JOINED)
                            SyncPlayGroupEvent.UserJoined(groupId, userName)
                        else SyncPlayGroupEvent.UserLeft(groupId, userName)
                    _groupEvents.emit(event)
                    Timber.d("SyncPlay raw: $groupUpdateType — user=$userName")
                }
                GroupUpdateType.PLAY_QUEUE -> {
                    if (innerData == null) return
                    val update =
                        ApiSerializer.json.decodeFromJsonElement(
                            PlayQueueUpdate.serializer(),
                            innerData,
                        )
                    _playQueueUpdates.emit(update)
                    Timber.d(
                        "SyncPlay raw: PLAY_QUEUE — ${update.playlist.size} items, idx=${update.playingItemIndex}"
                    )
                }
                else -> {}
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncPlay: failed to parse raw frame")
        }
    }
}
