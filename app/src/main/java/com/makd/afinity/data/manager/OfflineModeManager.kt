package com.makd.afinity.data.manager

import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.util.Locality
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.NetworkLocality
import com.makd.afinity.util.isLocalAddress
import com.makd.afinity.util.isTailscaleAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

sealed interface Connectivity {
    data object Online : Connectivity

    data object ForcedOffline : Connectivity

    data object NoNetwork : Connectivity

    data class ServerUnreachable(val reason: UnreachableReason) : Connectivity
}

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
    val connectivity: StateFlow<Connectivity> =
        combine(
                preferencesRepository.getOfflineModeFlow(),
                networkConnectivityMonitor.isNetworkAvailable,
                sessionManager.isServerReachable,
                sessionManager.unreachableReason,
            ) { manualOfflineMode, isNetworkAvailable, isServerReachable, reason ->
                when {
                    manualOfflineMode -> Connectivity.ForcedOffline
                    !isNetworkAvailable -> Connectivity.NoNetwork
                    !isServerReachable ->
                        Connectivity.ServerUnreachable(
                            reason ?: UnreachableReason.ALL_ADDRESSES_FAILED
                        )

                    else -> Connectivity.Online
                }
            }
            .distinctUntilChanged()
            .onEach { Timber.d("Connectivity changed: $it") }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    when {
                        !networkConnectivityMonitor.isCurrentlyConnected() -> Connectivity.NoNetwork
                        !sessionManager.isServerReachable.value ->
                            Connectivity.ServerUnreachable(
                                sessionManager.unreachableReason.value
                                    ?: UnreachableReason.ALL_ADDRESSES_FAILED
                            )

                        else -> Connectivity.Online
                    },
            )

    val isOffline: StateFlow<Boolean> =
        connectivity
            .map { it != Connectivity.Online }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = connectivity.value != Connectivity.Online,
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
                Locality.UNKNOWN -> lastKnownConnectionType ?: connectionTypeOf(false, serverUrl)
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
        return !manualOfflineMode && networkConnectivityMonitor.hasValidatedInternet()
    }
}
