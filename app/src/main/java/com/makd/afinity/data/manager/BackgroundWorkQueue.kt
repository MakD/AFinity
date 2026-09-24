package com.makd.afinity.data.manager

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

class BackgroundRequest : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BackgroundRequest>
}

@Singleton
class BackgroundWorkQueue @Inject constructor() {

    private val permits = Semaphore(MAX_PARALLEL_JOBS)

    suspend fun <T> run(label: String, block: suspend () -> T): T {
        if (coroutineContext[BackgroundRequest] != null) return block()
        return permits.withPermit {
            val startedAt = System.currentTimeMillis()
            Timber.d("BackgroundQueue: start $label")
            try {
                withContext(BackgroundRequest()) { block() }
            } finally {
                Timber.d(
                    "BackgroundQueue: done $label in ${System.currentTimeMillis() - startedAt}ms"
                )
            }
        }
    }

    private companion object {
        const val MAX_PARALLEL_JOBS = 2
    }
}
