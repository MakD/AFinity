package com.makd.afinity.data.repository.metadata

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.mdblist.MdbListRatingsResult
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.media.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ItemRatings(
    val mdbRatings: List<MdbListRating> = emptyList(),
    val badges: MdbListRatingBadges = MdbListRatingBadges(),
    val omdbAwards: String? = null,
    val fromCache: Boolean = false,
) {
    val hasAny: Boolean
        get() = mdbRatings.isNotEmpty() || badges.hasAny || !omdbAwards.isNullOrBlank()
}

@Singleton
class ItemRatingsLoader
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val databaseRepository: DatabaseRepository,
    private val sessionManager: SessionManager,
) {

    suspend fun fetch(item: AfinityItem): ItemRatings = coroutineScope {
        val tmdbId = item.providerIds?.get("Tmdb")
        val imdbId = item.providerIds?.get("Imdb")

        val ratingsDeferred = async {
            if (tmdbId == null) {
                MdbListRatingsResult()
            } else {
                runCatching {
                        val result = mediaRepository.getMdbListRatings(tmdbId, item is AfinityMovie)
                        result.copy(ratings = result.ratings.filter { it.value != null })
                    }
                    .getOrDefault(MdbListRatingsResult())
            }
        }

        val awardsDeferred = async {
            if (imdbId == null) {
                null
            } else {
                runCatching {
                    mediaRepository.getOmdbDetails(imdbId)?.awards?.takeIf { it != "N/A" }
                }
                    .getOrNull()
            }
        }

        val ratings = ratingsDeferred.await()
        ItemRatings(
            mdbRatings = ratings.ratings,
            badges = ratings.badges,
            omdbAwards = awardsDeferred.await(),
            fromCache = false,
        )
    }

    suspend fun cached(itemId: UUID): ItemRatings? {
        val session = sessionManager.currentSession.value ?: return null
        val cached =
            runCatching {
                databaseRepository.getItemMetadata(
                    itemId,
                    session.serverId,
                    session.userId.toString(),
                )
            }
                .getOrNull() ?: return null

        if (System.currentTimeMillis() - cached.lastUpdated >= CACHE_TTL_MS) return null

        return ItemRatings(
                mdbRatings = cached.mdbRatings,
                badges = cached.mdbRatingBadges,
                omdbAwards = cached.omdbAwards,
                fromCache = true,
            )
            .takeIf { it.hasAny }
    }

    suspend fun load(item: AfinityItem): ItemRatings = cached(item.id) ?: fetch(item)

    companion object {
        const val CACHE_TTL_MS = 48L * 60L * 60L * 1000L
    }
}
