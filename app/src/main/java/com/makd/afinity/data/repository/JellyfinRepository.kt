package com.makd.afinity.data.repository

import androidx.paging.PagingData
import com.makd.afinity.data.models.auth.QuickConnectState
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
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.ui.library.FilterType
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

interface JellyfinRepository {

    fun getBaseUrl(): String
    suspend fun setBaseUrl(baseUrl: String)
    suspend fun discoverServers(): List<Server>
    suspend fun discoverServersFlow(): Flow<List<Server>>
    suspend fun validateServer(serverUrl: String): JellyfinServerRepository.ServerConnectionResult
    suspend fun refreshServerInfo()

    suspend fun authenticateByName(username: String, password: String): AuthenticationResult?
    suspend fun authenticateWithQuickConnect(secret: String): AuthenticationResult?
    suspend fun logout()

    suspend fun initiateQuickConnect(): QuickConnectState?
    suspend fun getQuickConnectState(secret: String): QuickConnectState?

    suspend fun getCurrentUser(): User?
    suspend fun getPublicUsers(): List<User>
    suspend fun getUserProfileImageUrl(): String?

    suspend fun getNextUp(limit: Int = 16): List<AfinityEpisode>
    fun getNextUpFlow(): Flow<List<AfinityEpisode>>
    suspend fun getLibraries(): List<AfinityCollection>
    suspend fun getLatestMedia(
        parentId: UUID? = null,
        limit: Int = 16
    ): List<AfinityItem>

    suspend fun getContinueWatching(
        limit: Int = 12
    ): List<AfinityItem>

    suspend fun getRecommendationCategories(
        parentId: UUID? = null,
        categoryLimit: Int = 5,
        itemLimit: Int = 8
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
        fields: List<ItemFields>? = null,
        imageTypes: List<String> = emptyList(),
        hasOverview: Boolean? = null
    ): BaseItemDtoQueryResult

    suspend fun getItem(
        itemId: UUID,
        fields: List<ItemFields>? = null
    ): BaseItemDto?
    suspend fun getItemById(itemId: UUID): AfinityItem?
    suspend fun getSimilarItems(
        itemId: UUID,
        limit: Int = 12,
        fields: List<ItemFields>? = null
    ): List<AfinityItem>

    suspend fun getEpisodeToPlay(seriesId: UUID): AfinityEpisode?
    suspend fun getEpisodeToPlayForSeason(seasonId: UUID, seriesId: UUID): AfinityEpisode?

    suspend fun getMovies(
        parentId: UUID? = null,
        sortBy: SortBy = SortBy.NAME,
        sortDescending: Boolean = false,
        limit: Int? = null,
        startIndex: Int = 0,
        searchTerm: String? = null,
        isPlayed: Boolean? = null
    ): List<AfinityMovie>

    suspend fun getShows(
        parentId: UUID? = null,
        sortBy: SortBy = SortBy.NAME,
        sortDescending: Boolean = false,
        limit: Int? = null,
        startIndex: Int = 0,
        searchTerm: String? = null,
        isPlayed: Boolean? = null
    ): List<AfinityShow>

    suspend fun getSeasons(
        seriesId: UUID,
        sortBy: SortBy = SortBy.NAME,
        sortDescending: Boolean = false,
        fields: List<ItemFields>? = null
    ): List<AfinitySeason>

    suspend fun getEpisodes(
        seasonId: UUID,
        seriesId: UUID,
        fields: List<ItemFields>? = null
    ): List<AfinityEpisode>

    suspend fun getSeriesNextEpisode(seriesId: UUID): AfinityEpisode?

    suspend fun getSpecialFeatures(itemId: UUID, userId: UUID): List<AfinityItem>

    suspend fun getPersonDetail(personId: UUID): AfinityPersonDetail?
    suspend fun getPersonItems(
        personId: UUID,
        includeItemTypes: List<String> = emptyList(),
        limit: Int? = null,
        startIndex: Int = 0,
        fields: List<ItemFields>? = null
    ): List<AfinityItem>

    suspend fun reportPlaybackStart(
        itemId: UUID,
        positionTicks: Long = 0,
        sessionId: String? = null
    )

    suspend fun reportPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean = false,
        sessionId: String? = null
    )

    suspend fun reportPlaybackStopped(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String? = null
    )

    suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        maxBitrate: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        videoStreamIndex: Int? = null
    ): String

    suspend fun getImageUrl(
        itemId: UUID,
        imageType: String,
        imageIndex: Int = 0,
        tag: String? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int? = null
    ): String

    fun getItemsPaging(
        parentId: UUID,
        libraryType: CollectionType,
        sortBy: SortBy,
        sortDescending: Boolean,
        filter: FilterType,
        nameStartsWith: String? = null,
        fields: List<ItemFields>? = null
    ): Flow<PagingData<AfinityItem>>

    suspend fun getFavoriteMovies(): List<AfinityMovie>
    suspend fun getFavoriteShows(): List<AfinityShow>
    suspend fun getFavoriteEpisodes(): List<AfinityEpisode>
    suspend fun getFavoriteSeasons(): List<AfinitySeason>

    fun getLibrariesFlow(): Flow<List<AfinityCollection>>
    fun getLatestMediaFlow(parentId: UUID? = null): Flow<List<AfinityItem>>
    fun getContinueWatchingFlow(): Flow<List<AfinityItem>>
}