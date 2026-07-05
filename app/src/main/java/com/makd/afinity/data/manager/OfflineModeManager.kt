package com.makd.afinity.data.manager

import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.isLocalAddress
import com.makd.afinity.util.isTailscaleAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
    @ApplicationScope private val scope: CoroutineScope,
) {
    val isOffline: StateFlow<Boolean> =
        combine(
                preferencesRepository.getOfflineModeFlow(),
                networkConnectivityMonitor.isNetworkAvailable,
                sessionManager.isServerReachable,
            ) { manualOfflineMode, isNetworkAvailable, isServerReachable ->
                manualOfflineMode || !isNetworkAvailable || !isServerReachable
            }
            .distinctUntilChanged()
            .onEach { isOffline -> Timber.d("Offline mode changed: $isOffline") }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    !networkConnectivityMonitor.isCurrentlyConnected() ||
                        !sessionManager.isServerReachable.value,
            )

    val connectionType: StateFlow<ConnectionType> =
        combine(isOffline, sessionManager.currentSession) { offline, session ->
                connectionTypeOf(offline, session?.serverUrl)
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    connectionTypeOf(
                        isOffline.value,
                        sessionManager.currentSession.value?.serverUrl,
                    ),
            )

    private fun connectionTypeOf(offline: Boolean, serverUrl: String?): ConnectionType {
        return when {
            offline -> ConnectionType.OFFLINE
            serverUrl != null && isLocalAddress(serverUrl) -> ConnectionType.LOCAL
            serverUrl != null && isTailscaleAddress(serverUrl) -> ConnectionType.TAILSCALE
            else -> ConnectionType.REMOTE
        }
    }

    suspend fun isCurrentlyOffline(): Boolean {
        val manualOfflineMode = preferencesRepository.getOfflineMode()
        val isNetworkAvailable = networkConnectivityMonitor.isCurrentlyConnected()
        val isServerReachable = sessionManager.isServerReachable.value

        return manualOfflineMode || !isNetworkAvailable || !isServerReachable
    }

    suspend fun isInternetAvailable(): Boolean {
        val manualOfflineMode = preferencesRepository.getOfflineMode()
        val isNetworkAvailable = networkConnectivityMonitor.isCurrentlyConnected()
        return !manualOfflineMode && isNetworkAvailable
    }
}