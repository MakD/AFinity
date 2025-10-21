package com.makd.afinity.data.websocket

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<WebSocketEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    suspend fun emit(event: WebSocketEvent) {
        Timber.d("WebSocket event emitted: $event")
        _events.emit(event)
    }
}