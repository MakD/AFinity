package com.makd.afinity.data.manager

import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineModeManager @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor
) {
    val isOffline: Flow<Boolean> = combine(
        preferencesRepository.getOfflineModeFlow(),
        networkConnectivityMonitor.isNetworkAvailable
    ) { manualOfflineMode, isNetworkAvailable ->
        val isOffline = manualOfflineMode || !isNetworkAvailable
        Timber.d("Offline mode status: manual=$manualOfflineMode, network=$isNetworkAvailable, result=$isOffline")
        isOffline
    }

    suspend fun isCurrentlyOffline(): Boolean {
        val manualOfflineMode = preferencesRepository.getOfflineMode()
        val isNetworkAvailable = networkConnectivityMonitor.isCurrentlyConnected()
        return manualOfflineMode || !isNetworkAvailable
    }
}
