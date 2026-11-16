package com.makd.afinity.data.paging

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RandomOrder {
    private val mutex = Mutex()
    private var order: List<UUID>? = null

    suspend fun reconcile(fresh: List<UUID>): List<UUID> = mutex.withLock {
        val previous = order
        val merged =
            if (previous == null) {
                fresh.distinct()
            } else {
                val freshIds = fresh.toHashSet()
                val kept = previous.filter { it in freshIds }
                val keptIds = kept.toHashSet()
                kept + fresh.filter { it !in keptIds }.distinct()
            }
        order = merged
        merged
    }
}
