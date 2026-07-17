package com.makd.afinity.data.syncplay

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.syncplay.SyncPlayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.operations.TimeSyncApi
import org.jellyfin.sdk.model.DateTime
import timber.log.Timber
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val PING_ROUNDS = 4
private const val RESYNC_INTERVAL_MS = 30_000L

@Singleton
class SyncPlayTimeSyncEngine
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val syncPlayRepository: SyncPlayRepository,
) {
    private val _clockOffsetMs = AtomicLong(0L)

    val clockOffsetMs: Long
        get() = _clockOffsetMs.get()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reSyncJob: Job? = null

    suspend fun syncOnJoin() {
        performSync()
        startPeriodicSync()
    }

    fun stop() {
        reSyncJob?.cancel()
        reSyncJob = null
        _clockOffsetMs.set(0L)
    }

    fun toScheduledDelayMs(serverTime: DateTime): Long {
        val fireAtMs = serverTime.toEpochMs() - _clockOffsetMs.get()
        return fireAtMs - System.currentTimeMillis()
    }

    private fun startPeriodicSync() {
        reSyncJob?.cancel()
        reSyncJob = scope.launch {
            while (isActive) {
                delay(RESYNC_INTERVAL_MS)
                if (isActive) performSync()
            }
        }
    }

    private suspend fun performSync() {
        withContext(Dispatchers.IO) {
            val api = timeSyncApi() ?: return@withContext
            val offsets = mutableListOf<Long>()

            repeat(PING_ROUNDS) { round ->
                try {
                    val t1 = System.currentTimeMillis()
                    val response = api.getUtcTime().content
                    val t2 = System.currentTimeMillis()

                    val ts1 = response.requestReceptionTime.toEpochMs()
                    val ts2 = response.responseTransmissionTime.toEpochMs()

                    val rtt = t2 - t1
                    val serverProcessingMs = ts2 - ts1
                    val oneWayLatency = (rtt - serverProcessingMs) / 2
                    val offset = ts1 - t1 - oneWayLatency

                    offsets += offset

                    syncPlayRepository.ping(clientTimeMs = rtt)

                    Timber.d(
                        "SyncPlay time sync round ${round + 1}/$PING_ROUNDS: rtt=${rtt}ms offset=${offset}ms"
                    )
                } catch (e: Exception) {
                    Timber.w(e, "SyncPlay time sync round ${round + 1} failed, skipping")
                }
            }

            if (offsets.isNotEmpty()) {
                _clockOffsetMs.set(offsets.average().toLong())
                Timber.d(
                    "SyncPlay clock offset updated: ${_clockOffsetMs.get()}ms (${offsets.size}/${PING_ROUNDS} rounds)"
                )
            } else {
                Timber.w("SyncPlay time sync: all rounds failed, keeping previous offset")
            }
        }
    }

    private fun timeSyncApi(): TimeSyncApi? {
        val client = sessionManager.getCurrentApiClient() ?: return null
        return TimeSyncApi(client)
    }
}

private fun DateTime.toEpochMs(): Long = this.toInstant(ZoneOffset.UTC).toEpochMilli()
