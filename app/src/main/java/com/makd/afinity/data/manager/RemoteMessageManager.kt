package com.makd.afinity.data.manager

import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.GeneralCommandType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteMessage(val header: String?, val text: String)

private const val DEFAULT_TIMEOUT_MS = 6_000L
private const val MIN_TIMEOUT_MS = 1_000L
private const val MAX_TIMEOUT_MS = 30_000L

@Singleton
class RemoteMessageManager
@Inject
constructor(
    jellyfinWebSocketManager: JellyfinWebSocketManager,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _message = MutableStateFlow<RemoteMessage?>(null)
    val message: StateFlow<RemoteMessage?> = _message.asStateFlow()

    private var hideJob: Job? = null

    init {
        scope.launch {
            jellyfinWebSocketManager.remoteGeneralCommands.collect { command ->
                if (command.name != GeneralCommandType.DISPLAY_MESSAGE) return@collect
                val text = command.arguments["Text"]
                if (text.isNullOrBlank()) {
                    Timber.w("DisplayMessage command received with no Text argument")
                    return@collect
                }
                val timeoutMs =
                    command.arguments["TimeoutMs"]?.toLongOrNull() ?: DEFAULT_TIMEOUT_MS
                show(
                    RemoteMessage(
                        header = command.arguments["Header"]?.takeIf { it.isNotBlank() },
                        text = text,
                    ),
                    timeoutMs,
                )
            }
        }
    }

    private fun show(message: RemoteMessage, timeoutMs: Long) {
        _message.value = message
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS))
            _message.value = null
        }
    }

    fun dismiss() {
        hideJob?.cancel()
        _message.value = null
    }
}