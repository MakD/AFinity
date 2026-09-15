package com.makd.afinity.util.logging

import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogSecretsCollector
@Inject
constructor(
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val databaseRepository: DatabaseRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
) {

    suspend fun collect(extras: List<String?> = emptyList()): List<String> = buildList {
        extras.forEach { extra -> extra?.let { add(it) } }

        securePreferencesRepository.getAccessToken()?.let { add(it) }
        securePreferencesRepository.getSavedUsername()?.let { add(it) }
        securePreferencesRepository.getAllServerUserTokens().forEach { token ->
            add(token.serverUrl)
            add(token.accessToken)
            add(token.username)
        }

        databaseRepository.getAllServers().forEach { server ->
            add(server.address)
            databaseRepository.getServerAddresses(server.id).forEach { address ->
                add(address.address)
            }
        }

        securePreferencesRepository.getCachedJellyseerrServerUrl()?.let { add(it) }
        securePreferencesRepository.getCachedJellyseerrCookie()?.let { add(it) }
        addAll(jellyseerrRepository.getAllKnownAddresses())

        securePreferencesRepository.getCachedAudiobookshelfServerUrl()?.let { add(it) }
        securePreferencesRepository.getCachedAudiobookshelfToken()?.let { add(it) }
        securePreferencesRepository.getCachedAudiobookshelfRefreshToken()?.let { add(it) }
        addAll(audiobookshelfRepository.getAllKnownAddresses())
    }
}
