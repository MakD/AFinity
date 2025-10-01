package com.makd.afinity.data.repository.impl


import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.models.auth.QuickConnectState
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.*
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.ui.library.FilterType
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.paging.JellyfinItemsPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy


@Singleton
class JellyfinRepositoryImpl @Inject constructor(
    private val serverRepository: ServerRepository,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val playbackRepository: PlaybackRepository,
    private val database: AfinityDatabase
) : JellyfinRepository {

    override fun getBaseUrl(): String {
        return serverRepository.getBaseUrl()
    }

    override suspend fun setBaseUrl(baseUrl: String) {
        serverRepository.setBaseUrl(baseUrl)
    }

    override suspend fun discoverServers(): List<Server> {
        return try {
            serverRepository.discoverServers()
        } catch (e: Exception) {
            Timber.e(e, "Failed to discover servers")
            emptyList()
        }
    }

    override suspend fun discoverServersFlow(): Flow<List<Server>> {
        return try {
            serverRepository.discoverServersFlow()
        } catch (e: Exception) {
            Timber.e(e, "Failed to discover servers flow")
            flowOf(emptyList())
        }
    }

    override suspend fun validateServer(serverUrl: String): JellyfinServerRepository.ServerConnectionResult {
        return try {
            serverRepository.testServerConnection(serverUrl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate server: $serverUrl")
            JellyfinServerRepository.ServerConnectionResult.Error("Failed to validate server: ${e.message ?: "Unknown error"}")
        }
    }

    override suspend fun authenticateByName(username: String, password: String): AuthenticationResult? {
        return try {
            val result = authRepository.authenticateByName(username, password)
            when (result) {
                is AuthRepository.AuthResult.Success -> result.authResult
                is AuthRepository.AuthResult.Error -> {
                    Timber.e("Authentication failed: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to authenticate by name")
            null
        }
    }

    override suspend fun authenticateWithQuickConnect(secret: String): AuthenticationResult? {
        return try {
            val result = authRepository.authenticateWithQuickConnect(secret)
            when (result) {
                is AuthRepository.AuthResult.Success -> result.authResult
                is AuthRepository.AuthResult.Error -> {
                    Timber.e("QuickConnect authentication failed: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to authenticate with QuickConnect")
            null
        }
    }

    override suspend fun initiateQuickConnect(): QuickConnectState? {
        return try {
            authRepository.initiateQuickConnect()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate QuickConnect")
            null
        }
    }

    override suspend fun getQuickConnectState(secret: String): QuickConnectState? {
        return try {
            authRepository.getQuickConnectState(secret)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get QuickConnect state")
            null
        }
    }

    override suspend fun logout() {
        try {
            authRepository.logout()
        } catch (e: Exception) {
            Timber.e(e, "Failed to logout")
        }
    }

    override suspend fun getCurrentUser(): User? {
        return try {
            authRepository.getCurrentUser()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user")
            null
        }
    }

    override suspend fun getPublicUsers(): List<User> {
        return try {
            authRepository.getPublicUsers()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get public users")
            emptyList()
        }
    }

    override suspend fun getUserProfileImageUrl(): String? {
        return try {
            val currentUser = getCurrentUser()
            val serverUrl = getBaseUrl()

            currentUser?.primaryImageTag?.let { imageTag ->
                "$serverUrl/Users/${currentUser.id}/Images/Primary?tag=$imageTag"
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user profile image URL")
            null
        }
    }

    override suspend fun getLibraries(): List<AfinityCollection> {
        return try {
            mediaRepository.getLibraries()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get libraries")
            emptyList()
        }
    }

    override suspend fun getLatestMedia(parentId: UUID?, limit: Int): List<AfinityItem> {
        return try {
            mediaRepository.getLatestMedia(parentId, limit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get latest media")
            emptyList()
        }
    }

    override suspend fun getContinueWatching(limit: Int): List<AfinityItem> {
        return try {
            mediaRepository.getContinueWatching(limit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get continue watching")
            emptyList()
        }
    }

    override suspend fun getRecommendationCategories(
        parentId: UUID?,
        categoryLimit: Int,
        itemLimit: Int
    ): List<AfinityRecommendationCategory> {
        return try {
            mediaRepository.getRecommendationCategories(parentId, categoryLimit, itemLimit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get recommendation categories")
            emptyList()
        }
    }

    override fun getItemsPaging(
        parentId: UUID,
        libraryType: CollectionType,
        sortBy: SortBy,
        sortDescending: Boolean,
        filter: FilterType,
        nameStartsWith: String?,
        fields: List<ItemFields>?
    ): Flow<PagingData<AfinityItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                initialLoadSize = 50
            )
        ) {
            JellyfinItemsPagingSource(
                mediaRepository = mediaRepository,
                parentId = parentId,
                libraryType = libraryType,
                sortBy = sortBy,
                sortDescending = sortDescending,
                filter = filter,
                baseUrl = getBaseUrl(),
                nameStartsWith = nameStartsWith
            )
        }.flow
    }

    override suspend fun getItems(
        parentId: UUID?,
        collectionTypes: List<CollectionType>,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        includeItemTypes: List<String>,
        genres: List<String>,
        years: List<Int>,
        isFavorite: Boolean?,
        isPlayed: Boolean?,
        nameStartsWith: String?,
        fields: List<ItemFields>?
    ): BaseItemDtoQueryResult {
        return try {
            mediaRepository.getItems(
                parentId, collectionTypes, sortBy, sortDescending, limit, startIndex,
                searchTerm, includeItemTypes, genres, years, isFavorite, isPlayed, nameStartsWith, fields
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get items")
            BaseItemDtoQueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
        }
    }

    override suspend fun getItem(
        itemId: UUID,
        fields: List<ItemFields>?
    ): BaseItemDto? {
        return try {
            mediaRepository.getItem(itemId, fields)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get item: $itemId")
            null
        }
    }

    override suspend fun getItemById(itemId: UUID): AfinityItem? {
        return try {
            val baseItemDto = mediaRepository.getItem(itemId)

            baseItemDto?.let { dto ->
                dto.toAfinityItem(this@JellyfinRepositoryImpl)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get item by ID: $itemId")
            null
        }
    }

    override suspend fun getSimilarItems(
        itemId: UUID,
        limit: Int,
        fields: List<ItemFields>?
    ): List<AfinityItem> {
        return try {
            mediaRepository.getSimilarItems(itemId, limit, fields)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get similar items for: $itemId")
            emptyList()
        }
    }

    override suspend fun getMovies(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?
    ): List<AfinityMovie> {
        return try {
            mediaRepository.getMovies(parentId, sortBy, sortDescending, limit, startIndex, searchTerm, isPlayed)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get movies")
            emptyList()
        }
    }

    override suspend fun getShows(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?
    ): List<AfinityShow> {
        return try {
            mediaRepository.getShows(parentId, sortBy, sortDescending, limit, startIndex, searchTerm, isPlayed)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get shows")
            emptyList()
        }
    }

    override suspend fun getSeasons(
        seriesId: UUID,
        sortBy: SortBy,
        sortDescending: Boolean,
        fields: List<ItemFields>?
    ): List<AfinitySeason> {
        return try {
            mediaRepository.getSeasons(seriesId, fields)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get seasons for series: $seriesId")
            emptyList()
        }
    }

    override suspend fun getEpisodes(
        seasonId: UUID,
        seriesId: UUID,
        fields: List<ItemFields>?
    ): List<AfinityEpisode> {
        return try {
            mediaRepository.getEpisodes(seasonId, seriesId, fields)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get episodes for season: $seasonId")
            emptyList()
        }
    }

    override suspend fun getSpecialFeatures(itemId: UUID, userId: UUID): List<AfinityItem> {
        return try {
            mediaRepository.getSpecialFeatures(itemId, userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get special features for item: $itemId")
            emptyList()
        }
    }

    override suspend fun getSeriesNextEpisode(seriesId: UUID): AfinityEpisode? {
        return try {
            mediaRepository.getNextUp(seriesId, limit = 1).firstOrNull()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get next episode for series: $seriesId")
            null
        }
    }

    override suspend fun getPersonDetail(personId: UUID): AfinityPersonDetail? {
        return try {
            mediaRepository.getPerson(personId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get person detail: $personId")
            null
        }
    }

    override suspend fun getPersonItems(
        personId: UUID,
        includeItemTypes: List<String>,
        limit: Int?,
        startIndex: Int,
        fields: List<ItemFields>?
    ): List<AfinityItem> {
        return try {
            mediaRepository.getPersonItems(personId, includeItemTypes, fields)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get person items: $personId")
            emptyList()
        }
    }

    override suspend fun reportPlaybackStart(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackStart(
                itemId = itemId,
                sessionId = actualSessionId,
                mediaSourceId = itemId.toString(),
                playMethod = "DirectPlay"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start: $itemId")
        }
    }

    override suspend fun reportPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        sessionId: String?
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackProgress(
                itemId = itemId,
                sessionId = actualSessionId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                playMethod = "DirectPlay"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback progress: $itemId")
        }
    }

    override suspend fun reportPlaybackStopped(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?
    ) {
        try {
            val actualSessionId = sessionId ?: playbackRepository.getActiveSession() ?: return
            playbackRepository.reportPlaybackStop(
                itemId = itemId,
                sessionId = actualSessionId,
                positionTicks = positionTicks,
                mediaSourceId = itemId.toString()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback stopped: $itemId")
        }
    }

    override suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        videoStreamIndex: Int?
    ): String {
        return try {
            serverRepository.buildStreamUrl(
                itemId.toString(),
                mediaSourceId,
                maxBitrate,
                audioStreamIndex,
                subtitleStreamIndex,
                videoStreamIndex
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get stream URL for item: $itemId")
            ""
        }
    }

    override suspend fun getImageUrl(
        itemId: UUID,
        imageType: String,
        imageIndex: Int,
        tag: String?,
        maxWidth: Int?,
        maxHeight: Int?,
        quality: Int?
    ): String {
        return try {
            serverRepository.buildImageUrl(
                itemId = itemId.toString(),
                imageType = imageType,
                imageIndex = imageIndex,
                tag = tag,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                quality = quality
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get image URL for item: $itemId")
            ""
        }
    }

    override fun getLibrariesFlow(): Flow<List<AfinityCollection>> {
        return mediaRepository.getLibrariesFlow()
    }

    override fun getLatestMediaFlow(parentId: UUID?): Flow<List<AfinityItem>> {
        return mediaRepository.getLatestMediaFlow(parentId)
    }

    override fun getContinueWatchingFlow(): Flow<List<AfinityItem>> {
        return mediaRepository.getContinueWatchingFlow()
    }

    override suspend fun getEpisodeToPlay(seriesId: UUID): AfinityEpisode? {
        return try {
            Timber.d("Getting episode to play for series: $seriesId")

            val continueWatchingEpisodes = getContinueWatching(limit = 50)
            val seriesContinueWatching = continueWatchingEpisodes
                .filterIsInstance<AfinityEpisode>()
                .firstOrNull { it.seriesId == seriesId && it.playbackPositionTicks > 0 && !it.played }

            if (seriesContinueWatching != null) {
                Timber.d("Found continue watching episode: ${seriesContinueWatching.name}")
                return getFullEpisodeDetails(seriesContinueWatching.id)
            }

            try {
                val nextUpEpisodes = mediaRepository.getNextUp(seriesId, limit = 1)
                if (nextUpEpisodes.isNotEmpty()) {
                    Timber.d("Found next up episode: ${nextUpEpisodes.first().name}")
                    return getFullEpisodeDetails(nextUpEpisodes.first().id)
                }
            } catch (e: Exception) {
                Timber.w(e, "NextUp API failed, falling back to manual logic")
            }

            val seasons = getSeasons(seriesId, SortBy.NAME, sortDescending = false)
            if (seasons.isEmpty()) {
                Timber.w("No seasons found for series: $seriesId")
                return null
            }

            val sortedSeasons = seasons.sortedBy { it.indexNumber ?: 0 }

            for (season in sortedSeasons) {
                val episodes = getEpisodes(season.id, seriesId)
                if (episodes.isEmpty()) continue

                val sortedEpisodes = episodes.sortedBy { it.indexNumber ?: 0 }

                val nextEpisode = sortedEpisodes.firstOrNull { !it.played }
                if (nextEpisode != null) {
                    Timber.d("Found next unwatched episode: ${nextEpisode.name}")
                    return getFullEpisodeDetails(nextEpisode.id)
                }
            }

            val firstSeason = sortedSeasons.firstOrNull()
            if (firstSeason != null) {
                val firstSeasonEpisodes = getEpisodes(firstSeason.id, seriesId)
                val firstEpisode = firstSeasonEpisodes.sortedBy { it.indexNumber ?: 0 }.firstOrNull()
                if (firstEpisode != null) {
                    Timber.d("All episodes watched, returning first episode: ${firstEpisode.name}")
                    return getFullEpisodeDetails(firstEpisode.id)
                }
            }

            Timber.w("No episode found to play for series: $seriesId")
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to determine episode to play for series: $seriesId")
            null
        }
    }

    private suspend fun getFullEpisodeDetails(episodeId: UUID): AfinityEpisode? {
        return try {
            val baseItemDto = getItem(episodeId)
            baseItemDto?.toAfinityEpisode(this@JellyfinRepositoryImpl)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get full episode details for: $episodeId")
            null
        }
    }

    override suspend fun getFavoriteMovies(): List<AfinityMovie> {
        return mediaRepository.getFavoriteMovies()
    }

    override suspend fun getFavoriteShows(): List<AfinityShow> {
        return mediaRepository.getFavoriteShows()
    }

    override suspend fun getFavoriteEpisodes(): List<AfinityEpisode> {
        return mediaRepository.getFavoriteEpisodes()
    }

    suspend fun getPlayableItemForSeries(seriesId: UUID): AfinityItem? {
        return getEpisodeToPlay(seriesId)
    }
}