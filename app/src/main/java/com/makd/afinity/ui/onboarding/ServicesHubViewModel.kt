package com.makd.afinity.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.Session
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URI
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ServicesHubViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val serverRepository: ServerRepository,
    private val databaseRepository: DatabaseRepository,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val serverName =
        sessionManager.currentSession
            .map { it?.server?.name }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isJellyseerrConnected = jellyseerrRepository.isAuthenticated

    val isAudiobookshelfConnected = audiobookshelfRepository.isAuthenticated

    val jellyseerrHost =
        jellyseerrRepository.isAuthenticated
            .map { connected -> if (connected) hostOf(jellyseerrRepository.getServerUrl()) else null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val audiobookshelfHost =
        audiobookshelfRepository.isAuthenticated
            .map { connected ->
                if (connected) hostOf(audiobookshelfRepository.getServerUrl()) else null
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _remoteConfiguredHost = MutableStateFlow<String?>(null)
    val remoteConfiguredHost = _remoteConfiguredHost.asStateFlow()

    private val _remoteVerifying = MutableStateFlow(false)
    val remoteVerifying = _remoteVerifying.asStateFlow()

    private val _remoteError = MutableStateFlow<String?>(null)
    val remoteError = _remoteError.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.currentSession.collect { session ->
                if (session != null) refreshRemoteAccess(session)
            }
        }
    }

    fun markFirstRunDone() {
        viewModelScope.launch { preferencesRepository.setOnboardingFirstRunDone(true) }
    }

    fun disconnectJellyseerr() {
        viewModelScope.launch {
            runCatching { jellyseerrRepository.logout() }
                .onFailure { Timber.e(it, "Failed to disconnect Jellyseerr") }
        }
    }

    fun disconnectAudiobookshelf() {
        viewModelScope.launch {
            runCatching {
                    audiobookshelfPlayer.release()
                    audiobookshelfRepository.logout()
                }
                .onFailure { Timber.e(it, "Failed to disconnect Audiobookshelf") }
        }
    }

    fun clearRemoteError() {
        _remoteError.value = null
    }

    fun verifyAndSaveRemoteAddress(input: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            _remoteError.value = null
            _remoteVerifying.value = true
            try {
                val session = sessionManager.currentSession.value
                if (session == null) {
                    _remoteError.value =
                        context.getString(R.string.services_hub_remote_error_no_server)
                    return@launch
                }
                when (val result = serverRepository.testServerConnection(input.trim())) {
                    is JellyfinServerRepository.ServerConnectionResult.Success -> {
                        if (result.server.id != session.serverId) {
                            _remoteError.value =
                                context.getString(
                                    R.string.services_hub_remote_error_different_server
                                )
                            return@launch
                        }
                        val address = result.serverAddress
                        val existing =
                            databaseRepository.getServerAddressByUrl(session.serverId, address)
                        if (existing == null) {
                            databaseRepository.insertServerAddress(
                                ServerAddress(
                                    id = UUID.randomUUID(),
                                    serverId = session.serverId,
                                    address = address,
                                )
                            )
                        }
                        refreshRemoteAccess(session)
                        onSaved()
                    }
                    is JellyfinServerRepository.ServerConnectionResult.Error -> {
                        _remoteError.value = result.message
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to verify remote address")
                _remoteError.value =
                    e.message
                        ?: context.getString(R.string.services_hub_remote_error_unreachable)
            } finally {
                _remoteVerifying.value = false
            }
        }
    }

    private suspend fun refreshRemoteAccess(session: Session) {
        val currentHost = hostOf(session.serverUrl)
        val addresses = databaseRepository.getServerAddresses(session.serverId)
        _remoteConfiguredHost.value =
            addresses.map { hostOf(it.address) }.firstOrNull { it != null && it != currentHost }
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val withScheme = if (url.contains("://")) url else "http://$url"
        return runCatching { URI(withScheme).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
    }
}