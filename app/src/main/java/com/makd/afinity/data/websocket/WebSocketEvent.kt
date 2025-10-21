package com.makd.afinity.data.websocket

import java.util.UUID

sealed class WebSocketEvent {
    enum class Priority {
        IMMEDIATE,
        HIGH,
        NORMAL,
        LOW
    }

    abstract val priority: Priority
    abstract val timestamp: Long

    data class UserDataChanged(
        val itemId: UUID,
        val userId: UUID,
        override val priority: Priority = Priority.IMMEDIATE,
        override val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    data class BatchUserDataChanged(
        val itemIds: List<UUID>,
        val userId: UUID,
        override val priority: Priority = Priority.HIGH,
        override val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    data class LibraryChanged(
        val itemsAdded: List<UUID>,
        val itemsUpdated: List<UUID>,
        val itemsRemoved: List<UUID>,
        override val priority: Priority = Priority.NORMAL,
        override val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    data class ServerRestarting(
        override val priority: Priority = Priority.IMMEDIATE,
        override val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()

    data class ServerShuttingDown(
        override val priority: Priority = Priority.IMMEDIATE,
        override val timestamp: Long = System.currentTimeMillis()
    ) : WebSocketEvent()
}