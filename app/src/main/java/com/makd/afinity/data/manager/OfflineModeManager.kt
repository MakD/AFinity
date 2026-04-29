package com.makd.afinity.data.manager

import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineModeManager
@Inject
constructor(
    private val preferencesRepository: PreferencesRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val sessionManager: SessionManager,
) {
    val isOffline: Flow<Boolean> =
        combine(
            preferencesRepository.getOfflineModeFlow(),
            networkConnectivityMonitor.isNetworkAvailable,
            sessionManager.isServerReachable,
        ) { manualOfflineMode, isNetworkAvailable, isServerReachable ->
            val isOffline = manualOfflineMode || !isNetworkAvailable || !isServerReachable

            Timber.d(
                "Offline mode status: manual=$manualOfflineMode, network=$isNetworkAvailable, serverReachable=$isServerReachable, result=$isOffline"
            )
            isOffline
        }

    suspend fun isCurrentlyOffline(): Boolean {
        val manualOfflineMode = preferencesRepository.getOfflineMode()
        val isNetworkAvailable = networkConnectivityMonitor.isCurrentlyConnected()
        val isServerReachable = sessionManager.isServerReachable.value

        return manualOfflineMode || !isNetworkAvailable || !isServerReachable
    }
}
