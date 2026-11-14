package com.makd.afinity.data.repository.metadata

import com.makd.afinity.data.database.entities.ItemMetadataCacheEntity
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.tmdb.TmdbRegionProviders
import com.makd.afinity.data.models.tmdb.TmdbReview
import com.makd.afinity.data.network.TmdbApiService
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

data class FetchedItemMetadata(
    val tmdbReviews: List<TmdbReview> = emptyList(),
    val mdbRatings: List<MdbListRating> = emptyList(),
    val mdbRatingBadges: MdbListRatingBadges = MdbListRatingBadges(),
    val omdbAwards: String? = null,
    val watchProviders: TmdbRegionProviders? = null,
)

@Singleton
class ItemMetadataFetcher
@Inject
constructor(
    private val tmdbApiService: TmdbApiService,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val itemRatingsLoader: ItemRatingsLoader,
    private val databaseRepository: DatabaseRepository,
) {

    fun watchRegion(): String = Locale.getDefault().country.ifBlank { "US" }

    suspend fun cached(itemId: UUID, serverId: String, userId: UUID): ItemMetadataCacheEntity? =
        databaseRepository.getItemMetadata(itemId, serverId, userId.toString())

    fun isFresh(cached: ItemMetadataCacheEntity, region: String): Boolean {
        val hasData =
            cached.tmdbReviews.isNotEmpty() ||
                cached.mdbRatings.isNotEmpty() ||
                cached.mdbRatingBadges.hasAny ||
                !cached.omdbAwards.isNullOrBlank() ||
                cached.watchProviders?.hasAny == true
        val ttlMs = if (hasData) POPULATED_TTL_MS else EMPTY_TTL_MS
        val regionMatches = cached.watchProviders.let { it == null || it.region == region }
        return System.currentTimeMillis() - cached.lastUpdated < ttlMs && regionMatches
    }

    suspend fun fetchAndStore(
        item: AfinityItem,
        serverId: String?,
        userId: UUID?,
        region: String,
        onRatings: (ItemRatings) -> Unit = {},
    ): FetchedItemMetadata {
        val tmdbId = item.providerIds?.get("Tmdb")
        val imdbId = item.providerIds?.get("Imdb")
        var fetched = FetchedItemMetadata()

        if ((tmdbId != null || imdbId != null) && userId != null) {
            val tmdbKey = serverId?.let {
                securePreferencesRepository.getTmdbApiKey(it, userId.toString())
            }
            fetched = coroutineScope {
                val tmdbDeferred = async {
                    try {
                        if (tmdbId != null && !tmdbKey.isNullOrBlank()) {
                            when (item) {
                                is AfinityMovie -> tmdbApiService.getMovieDetails(tmdbId, tmdbKey)
                                is AfinityShow -> tmdbApiService.getSeriesDetails(tmdbId, tmdbKey)
                                else -> null
                            }
                        } else {
                            null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "TMDB details fetch failed")
                        null
                    }
                }

                val ratings = itemRatingsLoader.fetch(item)
                onRatings(ratings)

                val details = tmdbDeferred.await()
                FetchedItemMetadata(
                    tmdbReviews = details?.reviews?.results.orEmpty(),
                    mdbRatings = ratings.mdbRatings,
                    mdbRatingBadges = ratings.badges,
                    omdbAwards = ratings.omdbAwards,
                    watchProviders =
                        details?.let {
                            it.watchProviders?.forRegion(region)
                                ?: TmdbRegionProviders(region = region)
                        },
                )
            }
        }

        if (serverId != null && userId != null) {
            try {
                databaseRepository.insertItemMetadata(
                    ItemMetadataCacheEntity(
                        itemId = item.id,
                        serverId = serverId,
                        userId = userId.toString(),
                        tmdbReviews = fetched.tmdbReviews,
                        mdbRatings = fetched.mdbRatings,
                        mdbRatingBadges = fetched.mdbRatingBadges,
                        omdbAwards = fetched.omdbAwards,
                        watchProviders = fetched.watchProviders,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to cache metadata for ${item.id}")
            }
        }
        return fetched
    }

    companion object {
        private const val POPULATED_TTL_MS = 48L * 60L * 60L * 1000L
        private const val EMPTY_TTL_MS = 6L * 60L * 60L * 1000L
    }
}
