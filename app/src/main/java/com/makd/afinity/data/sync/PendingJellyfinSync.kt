package com.makd.afinity.data.sync

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingJellyfinSync
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val syncScheduler: UserDataSyncScheduler,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    fun initMonitoring() {
        if (!started.compareAndSet(false, true)) return

        scope.launch(Dispatchers.IO) { ifPending("app initMonitoring") }

        scope.launch(Dispatchers.IO) {
            combine(sessionManager.currentSession, sessionManager.isServerReachable) {
                    session,
                    reachable ->
                    session?.let { it.serverId to it.userId } to reachable
                }
                .distinctUntilChanged()
                .filter { (account, reachable) -> account != null && reachable }
                .collect { ifPending("session reachable") }
        }
    }

    private suspend fun ifPending(reason: String) {
        try {
            val pending = databaseRepository.countUserDataToSync()
            if (pending > 0) {
                Timber.i("$pending unsynced user data rows pending ($reason), scheduling sync")
                syncScheduler.ifIdle()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for pending user data")
        }
    }
}
