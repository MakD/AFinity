package com.makd.afinity.data.repository.watchlist

import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaChangeSource
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.di.ApplicationScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class SeerrWatchlistMirror
@Inject
constructor(
    private val jellyseerrRepository: JellyseerrRepository,
    private val preferencesRepository: PreferencesRepository,
    private val sessionManager: SessionManager,
    private val userDataRepository: UserDataRepository,
    private val watchlistRepository: WatchlistRepository,
    private val mediaChangeManager: MediaChangeManager,
    private val mediaRefreshBus: MediaRefreshBus,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @Volatile private var plexCheck: Pair<String, Boolean>? = null

    fun onJellyfinWatchlistChanged(item: AfinityItem, onWatchlist: Boolean) {
        val mediaType =
            when (item) {
                is AfinityMovie -> MediaType.MOVIE
                is AfinityShow -> MediaType.TV
                else -> return
            }
        val tmdbId =
            item.providerIds
                ?.entries
                ?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }
                ?.value
                ?.toIntOrNull()
                ?.takeIf { it > 0 } ?: return
        scope.launch {
            try {
                if (!isActive()) return@launch
                val result =
                    if (onWatchlist) {
                        jellyseerrRepository.addToWatchlist(tmdbId, mediaType, item.name)
                    } else {
                        jellyseerrRepository.removeFromWatchlist(tmdbId, mediaType)
                    }
                result.onFailure { Timber.w(it, "Seerr watchlist mirror failed for $tmdbId") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Seerr watchlist mirror failed for $tmdbId")
            }
        }
    }

    fun onSeerrWatchlistChanged(jellyfinItemId: String?, onWatchlist: Boolean) {
        val itemId =
            jellyfinItemId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        scope.launch {
            try {
                if (!isActive()) return@launch
                if (!userDataRepository.setLike(itemId, onWatchlist)) {
                    Timber.w("Jellyfin watchlist mirror failed for $itemId")
                    return@launch
                }
                mediaChangeManager.notifyItemChanged(
                    itemId = itemId,
                    source = MediaChangeSource.MANUAL,
                    patch = UserDataPatch(liked = onWatchlist),
                )
                watchlistRepository.refreshWatchlistCount()
                mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Jellyfin watchlist mirror failed for $itemId")
            }
        }
    }

    private suspend fun isActive(): Boolean {
        if (!jellyseerrRepository.isAuthenticated.value) return false
        val session = sessionManager.currentSession.value ?: return false
        if (
            !preferencesRepository.getSeerrWatchlistSync(
                session.serverId,
                session.userId.toString(),
            )
        ) {
            return false
        }
        return !isPlexUser()
    }

    private suspend fun isPlexUser(): Boolean {
        val sessionId = jellyseerrRepository.currentSessionId.value ?: return true
        plexCheck?.let { (cachedSession, isPlex) -> if (cachedSession == sessionId) return isPlex }
        val user = jellyseerrRepository.getCurrentUser().getOrNull() ?: return true
        return user.isPlexUser().also { plexCheck = sessionId to it }
    }
}
