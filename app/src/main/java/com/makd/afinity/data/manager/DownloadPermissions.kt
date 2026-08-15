package com.makd.afinity.data.manager

import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.util.NetworkConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadPermissions
@Inject
constructor(
    sessionManager: SessionManager,
    preferencesRepository: PreferencesRepository,
    networkMonitor: NetworkConnectivityMonitor,
    @ApplicationScope scope: CoroutineScope,
) {

    val isAllowedByServer: StateFlow<Boolean> =
        sessionManager.currentSession
            .map { it?.canDownload != false }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    val isAllowedOnNetwork: StateFlow<Boolean> =
        combine(preferencesRepository.getDownloadWifiOnlyFlow(), networkMonitor.isOnWifiFlow) {
                wifiOnly,
                onWifi ->
                !wifiOnly || onWifi
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)
}