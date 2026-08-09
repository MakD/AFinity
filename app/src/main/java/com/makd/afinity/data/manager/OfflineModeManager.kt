package com.makd.afinity.data.manager

import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.util.Locality
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.NetworkLocality
import com.makd.afinity.util.isLocalAddress
import com.makd.afinity.util.isTailscaleAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineModeManager
@Inject
constructor(
    private val preferencesRepository: PreferencesRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val sessionManager: SessionManager,
    private val networkLocality: NetworkLocality,
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

    @Volatile private var lastKnownConnectionType: ConnectionType? = null

    val connectionType: StateFlow<ConnectionType> =
        combine(isOffline, sessionManager.currentSession) { offline, session ->
                offline to session?.serverUrl
            }
            .distinctUntilChanged()
            .mapLatest { (offline, serverUrl) -> resolveConnectionType(offline, serverUrl) }
            .distinctUntilChanged()
            .onEach { Timber.d("Connection type changed: $it") }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    connectionTypeOf(
                        isOffline.value,
                        sessionManager.currentSession.value?.serverUrl,
                    ),
            )

    private suspend fun resolveConnectionType(
        offline: Boolean,
        serverUrl: String?,
    ): ConnectionType {
        if (offline) return ConnectionType.OFFLINE
        if (serverUrl == null) return lastKnownConnectionType ?: ConnectionType.REMOTE

        val resolved =
            when (networkLocality.resolve(serverUrl)) {
                Locality.ON_LINK -> ConnectionType.LOCAL
                Locality.TAILSCALE -> ConnectionType.TAILSCALE
                Locality.TUNNELLED -> ConnectionType.VPN
                Locality.PUBLIC -> ConnectionType.REMOTE
                Locality.UNKNOWN ->
                    lastKnownConnectionType ?: connectionTypeOf(false, serverUrl)
            }
        lastKnownConnectionType = resolved
        return resolved
    }

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