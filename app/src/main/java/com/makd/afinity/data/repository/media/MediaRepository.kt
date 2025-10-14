package com.makd.afinity.data.repository.media

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

interface MediaRepository {

    val libraries: Flow<List<AfinityCollection>>
    val latestMedia: Flow<List<AfinityItem>>
    val continueWatching: Flow<List<AfinityItem>>
    val nextUp: Flow<List<AfinityEpisode>>

    fun getNextUpFlow(): Flow<List<AfinityEpisode>>

    suspend fun invalidateContinueWatchingCache()
    suspend fun invalidateLatestMediaCache()
    suspend fun invalidateNextUpCache()
    suspend fun invalidateAllCaches()
    suspend fun invalidateItemCache(itemId: UUID)

    suspend fun refreshItemUserData(
        itemId: UUID,
        fields: List<ItemFields>? = null
    ): AfinityItem?

    suspend fun getLibraries(): List<AfinityCollection>

    suspend fun getLatestMedia(
        parentId: UUID? = null,
        limit: Int = 16,
        fields: List<ItemFields>? = null
    ): List<AfinityItem>

    suspend fun getContinueWatching(
        limit: Int = 16,
        fields: List<ItemFields>? = null
    ): List<AfinityItem>

    suspend fun getRecommendationCategories(
        parentId: UUID? = null,
        categoryLimit: Int = 5,
        itemLimit: Int = 8,
        fields: List<ItemFields>? = null
    ): List<AfinityRecommendationCategory>

    suspend fun getItems(
        parentId: UUID? = null,
        collectionTypes: List<CollectionType> = emptyList(),
        sortBy: SortBy = SortBy.NAME,
        sortDescending: Boolean = false,
        limit: Int? = null,
        startIndex: Int = 0,
        searchTerm: String? = null,
        includeItemTypes: List<String> = emptyList(),
        genres: List<String> = emptyList(),
        years: List<Int> = emptyList(),
        isFavorite: Boolean? = null,
        isPlayed: Boolean? = null,
        nameStartsWith: String? = null,
        fields: List<ItemFields>? = null
    ): BaseItemDtoQueryResult

    suspend fun getItem(
        itemId: UUID,
        fields: List<ItemFields>? = null
    ): BaseItemDto?

    suspend fun getSimilarItems(
        itemId: UUID,
        limit: Int = 12,
        fields: List<ItemFields>? = null): List<AfinityItem>

    suspend fun getMovies(
        parentId: UUID? = null,
        sortBy: SortBy = SortBy.NAME,
        sortDescending: Boolean = false,
        limit: Int? = null,
        startIndex: Int = 0,
        searchTerm: String? = null,
        isPlayed: Boolean? = null,
        isFavorite: Boolean? = null,
        fields: List<ItemFields>? = null
    ): List<AfinityMovie>

    suspend fun getShows(
        parentId: UUID? = null,
        sortBy: SortBy = SortBy.NAME,
        sortDescending: Boolean = false,
        limit: Int? = null,
        startIndex: Int = 0,
        searchTerm: String? = null,
        isPlayed: Boolean? = null,
        isFavorite: Boolean? = null,
        fields: List<ItemFields>? = null
    ): List<AfinityShow>

    suspend fun getSeasons(
        seriesId: UUID,
        fields: List<ItemFields>? = null
    ): List<AfinitySeason>

    suspend fun getEpisodes(
        seasonId: UUID,
        seriesId: UUID? = null,
        fields: List<ItemFields>? = null
    ): List<AfinityEpisode>

    suspend fun getFavoriteShows(
        fields: List<ItemFields>? = null
    ): List<AfinityShow>

    suspend fun getFavoriteMovies(
        fields: List<ItemFields>? = null
    ): List<AfinityMovie>

    suspend fun getFavoriteEpisodes(
        fields: List<ItemFields>? = null
    ): List<AfinityEpisode>

    suspend fun getGenres(
        parentId: UUID? = null,
        limit: Int? = null,
        includeItemTypes: List<String> = emptyList(),
    ): List<String>

    suspend fun getNextUp(
        seriesId: UUID? = null,
        limit: Int = 16,
        fields: List<ItemFields>? = null,
        enableResumable: Boolean = true
    ): List<AfinityEpisode>

    suspend fun getSpecialFeatures(itemId: UUID, userId: UUID): List<AfinityItem>
    suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray?
    suspend fun searchItems(
        query: String,
        limit: Int = 50,
        includeItemTypes: List<String> = emptyList(),
        fields: List<ItemFields>? = null
    ): List<AfinityItem>

    suspend fun getPerson(personId: UUID): AfinityPersonDetail?
    suspend fun getPersonItems(
        personId: UUID,
        includeItemTypes: List<String> = emptyList(),
        fields: List<ItemFields>? = null
    ): List<AfinityItem>

    fun getLibrariesFlow(): Flow<List<AfinityCollection>>
    fun getLatestMediaFlow(parentId: UUID? = null): Flow<List<AfinityItem>>
    fun getContinueWatchingFlow(): Flow<List<AfinityItem>>
}