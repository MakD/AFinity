package com.makd.afinity.data.websocket

import java.util.UUID

sealed class WebSocketEvent {

    data class UserDataChanged(
        val itemId: UUID,
        val userId: UUID
    ) : WebSocketEvent()

    data class LibraryChanged(
        val itemsAdded: List<UUID>,
        val itemsUpdated: List<UUID>,
        val itemsRemoved: List<UUID>
    ) : WebSocketEvent()

    data object ServerRestarting : WebSocketEvent()

    data object ServerShuttingDown : WebSocketEvent()
}