package com.makd.afinity.data.repository.watchlist

import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.models.media.toAfinitySeason
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class WatchlistRepositoryImpl
@Inject
constructor(
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository,
) : WatchlistRepository {

    override suspend fun addToWatchlist(itemId: UUID, itemType: String): Boolean {
        return userDataRepository.setLike(itemId, isLiked = true)
    }

    override suspend fun removeFromWatchlist(itemId: UUID): Boolean {
        return userDataRepository.setLike(itemId, isLiked = false)
    }

    override suspend fun isInWatchlist(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userData = userDataRepository.getUserData(itemId)
                userData?.likes == true
            } catch (e: Exception) {
                Timber.e(e, "Failed to check if item is in watchlist: $itemId")
                false
            }
        }
    }

    override fun isInWatchlistFlow(itemId: UUID): Flow<Boolean> {
        return flow { emit(isInWatchlist(itemId)) }.flowOn(Dispatchers.IO)
    }

    override suspend fun getWatchlistMovies(): List<AfinityMovie> {
        return withContext(Dispatchers.IO) {
            try {
                mediaRepository.getMovies(
                    isLiked = true,
                    sortBy = SortBy.DATE_ADDED,
                    sortDescending = true,
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load liked movies")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistShows(): List<AfinityShow> {
        return withContext(Dispatchers.IO) {
            try {
                mediaRepository.getShows(
                    isLiked = true,
                    sortBy = SortBy.DATE_ADDED,
                    sortDescending = true,
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load liked shows")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistSeasons(): List<AfinitySeason> {
        return withContext(Dispatchers.IO) {
            try {
                val response =
                    mediaRepository.getItems(
                        isLiked = true,
                        includeItemTypes = listOf("SEASON"),
                        sortBy = SortBy.DATE_ADDED,
                        sortDescending = true,
                        fields = FieldSets.MEDIA_ITEM_CARDS,
                    )
                response.items
                    ?.filter { it.type?.name == "SEASON" }
                    ?.mapNotNull { it.toAfinitySeason(jellyfinRepository) } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load liked seasons")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistEpisodes(): List<AfinityEpisode> {
        return withContext(Dispatchers.IO) {
            try {
                val response =
                    mediaRepository.getItems(
                        isLiked = true,
                        includeItemTypes = listOf("EPISODE"),
                        sortBy = SortBy.DATE_ADDED,
                        sortDescending = true,
                        fields = FieldSets.MEDIA_ITEM_CARDS,
                    )
                response.items
                    ?.filter { it.type?.name == "EPISODE" }
                    ?.mapNotNull { it.toAfinityEpisode(jellyfinRepository) } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load liked episodes")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistCount(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val response = mediaRepository.getItems(isLiked = true, limit = 0)
                response.totalRecordCount ?: 0
            } catch (e: Exception) {
                Timber.e(e, "Failed to get watchlist count")
                0
            }
        }
    }

    override fun getWatchlistCountFlow(): Flow<Int> {
        return flow { emit(getWatchlistCount()) }.flowOn(Dispatchers.IO)
    }

    override suspend fun clearWatchlist() {
        return withContext(Dispatchers.IO) {
            try {
                val allLikedResponse = mediaRepository.getItems(isLiked = true, limit = 1000)
                val likedItemIds = allLikedResponse.items?.mapNotNull { it.id } ?: emptyList()

                likedItemIds.forEach { itemId ->
                    try {
                        userDataRepository.setLike(itemId, isLiked = false)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to unlike item: $itemId")
                    }
                }
                Timber.d("Cleared watchlist (unliked ${likedItemIds.size} items)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear watchlist")
            }
        }
    }
}
