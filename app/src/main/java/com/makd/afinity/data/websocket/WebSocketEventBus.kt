package com.makd.afinity.data.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketEventBus @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _events = MutableSharedFlow<WebSocketEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    private val pendingUserDataChanges = ConcurrentHashMap<java.util.UUID, Long>()
    private val batchMutex = Mutex()
    private val batchWindow = 100L
    private var lastBatchTime = System.currentTimeMillis()

    private val eventsEmitted = AtomicInteger(0)
    private val eventsFailed = AtomicInteger(0)
    private val eventsBatched = AtomicInteger(0)

    suspend fun emit(event: WebSocketEvent) {
        try {
            when (event) {
                is WebSocketEvent.UserDataChanged -> {
                    emitUserDataChangeWithBatching(event)
                }
                else -> {
                    emitDirectly(event)
                }
            }
        } catch (e: Exception) {
            eventsFailed.incrementAndGet()
            Timber.e(e, "Failed to emit WebSocket event (non-critical, continuing)")
        }
    }

    private suspend fun emitDirectly(event: WebSocketEvent) {
        val success = _events.tryEmit(event)
        if (success) {
            eventsEmitted.incrementAndGet()
            Timber.d("WebSocket event emitted [${event::class.simpleName}] priority=${event.priority}")
        } else {
            Timber.w("Event buffer full, oldest event dropped")
        }
    }

    private suspend fun emitUserDataChangeWithBatching(event: WebSocketEvent.UserDataChanged) {
        batchMutex.withLock {
            val now = System.currentTimeMillis()
            val itemId = event.itemId

            pendingUserDataChanges[itemId] = now

            val timeSinceLastBatch = now - lastBatchTime
            if (timeSinceLastBatch >= batchWindow || pendingUserDataChanges.size >= 10) {
                flushBatch(event.userId)
            }
        }
    }

    private suspend fun flushBatch(userId: java.util.UUID) {
        if (pendingUserDataChanges.isEmpty()) return

        val itemIds = pendingUserDataChanges.keys.toList()
        pendingUserDataChanges.clear()
        lastBatchTime = System.currentTimeMillis()

        val batchEvent = if (itemIds.size == 1) {
            WebSocketEvent.UserDataChanged(itemIds.first(), userId)
        } else {
            eventsBatched.incrementAndGet()
            Timber.d("Batching ${itemIds.size} user data changes")
            WebSocketEvent.BatchUserDataChanged(itemIds, userId)
        }

        emitDirectly(batchEvent)
    }

    suspend fun flush() {
        try {
            batchMutex.withLock {
                if (pendingUserDataChanges.isNotEmpty()) {
                    val firstKey = pendingUserDataChanges.keys.first()
                    flushBatch(java.util.UUID.randomUUID())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to flush events (non-critical)")
        }
    }

    fun getMetrics(): EventBusMetrics {
        return EventBusMetrics(
            eventsEmitted = eventsEmitted.get(),
            eventsFailed = eventsFailed.get(),
            eventsBatched = eventsBatched.get(),
            pendingEvents = pendingUserDataChanges.size
        )
    }

    fun resetMetrics() {
        eventsEmitted.set(0)
        eventsFailed.set(0)
        eventsBatched.set(0)
    }
}

data class EventBusMetrics(
    val eventsEmitted: Int,
    val eventsFailed: Int,
    val eventsBatched: Int,
    val pendingEvents: Int
)