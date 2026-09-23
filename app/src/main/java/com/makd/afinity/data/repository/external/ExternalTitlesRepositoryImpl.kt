package com.makd.afinity.data.repository.external

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.external.ExternalTitles
import com.makd.afinity.data.models.external.ExternalTitlesSource
import com.makd.afinity.data.network.TmdbApiService
import com.makd.afinity.data.repository.ExternalTitlesRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@Singleton
class ExternalTitlesRepositoryImpl
@Inject
constructor(
    private val jellyseerrRepository: JellyseerrRepository,
    private val tmdbApiService: TmdbApiService,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val sessionManager: SessionManager,
) : ExternalTitlesRepository {

    override suspend fun getCollectionParts(collectionTmdbId: Int): ExternalTitles? {
        if (jellyseerrRepository.isAuthenticated.value) {
            jellyseerrRepository.getCollection(collectionTmdbId).getOrNull()?.let {
                return ExternalTitles(ExternalTitlesSource.SEERR, it.parts)
            }
        }
        val apiKey = tmdbApiKey() ?: return null
        return try {
            val collection = tmdbApiService.getCollection(collectionTmdbId.toString(), apiKey)
            ExternalTitles(
                ExternalTitlesSource.TMDB,
                collection.parts.map { it.toSearchResultItem() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load TMDB collection $collectionTmdbId")
            null
        }
    }

    override suspend fun getPersonCredits(personTmdbId: Int): ExternalTitles? {
        if (jellyseerrRepository.isAuthenticated.value) {
            jellyseerrRepository.getPersonCombinedCredits(personTmdbId).getOrNull()?.let {
                return ExternalTitles(ExternalTitlesSource.SEERR, it.meaningfulCredits())
            }
        }
        val apiKey = tmdbApiKey() ?: return null
        return try {
            val credits =
                tmdbApiService.getPersonCombinedCredits(personTmdbId.toString(), apiKey)
            ExternalTitles(
                ExternalTitlesSource.TMDB,
                credits.toPersonCombinedCredits().meaningfulCredits(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load TMDB credits for person $personTmdbId")
            null
        }
    }

    private suspend fun tmdbApiKey(): String? {
        val session = sessionManager.currentSession.value ?: return null
        return securePreferencesRepository
            .getTmdbApiKey(session.serverId, session.userId.toString())
            ?.takeIf { it.isNotBlank() }
    }
}