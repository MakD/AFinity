package com.makd.afinity.data.books

import android.os.SystemClock
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.PreferencesRepository
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class PaceKind(val defaultMillisPerUnit: Double) {
    TEXT(41_000.0),
    PAGES(20_000.0),
}

@Singleton
class ReadingPaceStore
@Inject
constructor(
    private val preferencesRepository: PreferencesRepository,
    private val sessionManager: SessionManager,
) {
    private data class Pace(val units: Double, val millis: Double)

    private val cache = ConcurrentHashMap<String, Pace>()

    suspend fun millisPerUnit(kind: PaceKind): Double {
        val pace = load(kind)
        return (pace.millis + kind.defaultMillisPerUnit * PRIOR_UNITS) / (pace.units + PRIOR_UNITS)
    }

    suspend fun record(kind: PaceKind, units: Double, millis: Long) {
        val key = paceKey(kind) ?: return
        val current = load(kind)
        var next = Pace(current.units + units, current.millis + millis)
        if (next.units > MAX_REMEMBERED_UNITS) {
            next = Pace(next.units * DECAY, next.millis * DECAY)
        }
        cache[key] = next
        preferencesRepository.setStringPreference(
            key,
            String.format(Locale.ROOT, "%.3f:%.0f", next.units, next.millis),
        )
    }

    suspend fun textLength(itemId: UUID): Int? =
        preferencesRepository.getStringPreference(lengthKey(itemId))?.toIntOrNull()

    suspend fun setTextLength(itemId: UUID, positions: Int) {
        preferencesRepository.setStringPreference(lengthKey(itemId), positions.toString())
    }

    private suspend fun load(kind: PaceKind): Pace {
        val key = paceKey(kind) ?: return Pace(0.0, 0.0)
        cache[key]?.let {
            return it
        }
        val stored = preferencesRepository.getStringPreference(key)
        val parts = stored?.split(':')
        val pace =
            if (parts != null && parts.size == 2) {
                Pace(parts[0].toDoubleOrNull() ?: 0.0, parts[1].toDoubleOrNull() ?: 0.0)
            } else {
                Pace(0.0, 0.0)
            }
        cache[key] = pace
        return pace
    }

    private fun paceKey(kind: PaceKind): String? {
        val session = sessionManager.currentSession.value ?: return null
        return "reader_pace_${kind.name.lowercase()}_${session.serverId}_${session.userId}"
    }

    private fun lengthKey(itemId: UUID): String = "reader_positions_$itemId"

    private companion object {
        const val PRIOR_UNITS = 10.0
        const val MAX_REMEMBERED_UNITS = 2_000.0
        const val DECAY = 0.5
    }
}

class PaceTracker(private val maxStep: Double) {
    private var lastPosition: Double? = null
    private var lastAt = 0L

    fun onPosition(position: Double): Pair<Double, Long>? {
        val now = SystemClock.elapsedRealtime()
        val previous = lastPosition
        val previousAt = lastAt
        lastPosition = position
        lastAt = now
        if (previous == null) return null
        val step = position - previous
        val elapsed = now - previousAt
        if (step <= 0.0 || step > maxStep) return null
        if (elapsed < MIN_ELAPSED_MS || elapsed > IDLE_LIMIT_MS) return null
        return step to elapsed
    }

    fun reset() {
        lastPosition = null
    }

    private companion object {
        const val MIN_ELAPSED_MS = 1_500L
        const val IDLE_LIMIT_MS = 120_000L
    }
}
