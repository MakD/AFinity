package com.makd.afinity.data.repository.watchlist

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {

    suspend fun addToWatchlist(itemId: UUID, itemType: String): Boolean

    suspend fun removeFromWatchlist(itemId: UUID): Boolean

    suspend fun isInWatchlist(itemId: UUID): Boolean

    fun isInWatchlistFlow(itemId: UUID): Flow<Boolean>

    suspend fun getWatchlistMovies(): List<AfinityMovie>

    suspend fun getWatchlistShows(): List<AfinityShow>

    suspend fun getWatchlistSeasons(): List<AfinitySeason>

    suspend fun getWatchlistEpisodes(): List<AfinityEpisode>

    suspend fun getWatchlistCount(): Int

    fun getWatchlistCountFlow(): Flow<Int>

    suspend fun clearWatchlist()
}
