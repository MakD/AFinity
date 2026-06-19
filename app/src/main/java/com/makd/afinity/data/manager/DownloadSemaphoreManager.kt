package com.makd.afinity.data.manager

import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSemaphoreManager @Inject constructor() {

    private val lock = Any()
    private var currentPermits = 3

    @Volatile
    private var _semaphore = Semaphore(3)

    val semaphore: Semaphore
        get() = _semaphore

    fun updatePermits(newPermits: Int) {
        val clamped = newPermits.coerceIn(1, 10)
        synchronized(lock) {
            if (clamped != currentPermits) {
                currentPermits = clamped
                _semaphore = Semaphore(clamped)
            }
        }
    }
}