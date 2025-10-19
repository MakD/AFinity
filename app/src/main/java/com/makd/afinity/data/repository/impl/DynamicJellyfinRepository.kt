package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.ui.library.FilterType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicJellyfinRepository @Inject constructor(
    private val onlineRepository: JellyfinRepositoryImpl,
    private val offlineRepository: JellyfinRepositoryOfflineImpl,
    private val preferencesRepository: PreferencesRepository
) : JellyfinRepository {

    private suspend fun getCurrentRepository(): JellyfinRepository {
        val isOffline = preferencesRepository.getOfflineMode()
        return if (isOffline) {
            Timber.d("Using offline repository")
            offlineRepository
        } else {
            onlineRepository
        }
    }

    private fun getCurrentRepositorySync(): JellyfinRepository {
        return onlineRepository
    }

    override fun getBaseUrl(): String = getCurrentRepositorySync().getBaseUrl()
    override suspend fun setBaseUrl(baseUrl: String) = getCurrentRepository().setBaseUrl(baseUrl)
    override suspend fun discoverServers(): List<Server> = getCurrentRepository().discoverServers()
    override suspend fun discoverServersFlow(): Flow<List<Server>> =
        getCurrentRepository().discoverServersFlow()

    override suspend fun validateServer(serverUrl: String) =
        getCurrentRepository().validateServer(serverUrl)

    override suspend fun refreshServerInfo() = getCurrentRepository().refreshServerInfo()
    override suspend fun authenticateByName(username: String, password: String) =
        getCurrentRepository().authenticateByName(username, password)

    override suspend fun authenticateWithQuickConnect(secret: String) =
        getCurrentRepository().authenticateWithQuickConnect(secret)

    override suspend fun logout() = getCurrentRepository().logout()
    override suspend fun initiateQuickConnect() = getCurrentRepository().initiateQuickConnect()
    override suspend fun getQuickConnectState(secret: String) =
        getCurrentRepository().getQuickConnectState(secret)

    override suspend fun getCurrentUser() = getCurrentRepository().getCurrentUser()
    override suspend fun getPublicUsers() = getCurrentRepository().getPublicUsers()
    override suspend fun getUserProfileImageUrl() = getCurrentRepository().getUserProfileImageUrl()
    override suspend fun getLibraries() = getCurrentRepository().getLibraries()
    override suspend fun getLatestMedia(parentId: UUID?, limit: Int) =
        getCurrentRepository().getLatestMedia(parentId, limit)

    override suspend fun getNextUp(limit: Int) = getCurrentRepository().getNextUp(limit)
    override suspend fun getContinueWatching(limit: Int) =
        getCurrentRepository().getContinueWatching(limit)

    override suspend fun getRecommendationCategories(
        parentId: UUID?,
        categoryLimit: Int,
        itemLimit: Int
    ) = getCurrentRepository().getRecommendationCategories(parentId, categoryLimit, itemLimit)

    override fun getItemsPaging(
        parentId: UUID,
        libraryType: CollectionType,
        sortBy: SortBy,
        sortDescending: Boolean,
        filter: FilterType,
        nameStartsWith: String?,
        fields: List<ItemFields>?
    ) = getCurrentRepositorySync().getItemsPaging(
        parentId,
        libraryType,
        sortBy,
        sortDescending,
        filter,
        nameStartsWith,
        fields
    )

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
        fields: List<ItemFields>?,
        imageTypes: List<String>,
        hasOverview: Boolean?
    ) = getCurrentRepository().getItems(
        parentId,
        collectionTypes,
        sortBy,
        sortDescending,
        limit,
        startIndex,
        searchTerm,
        includeItemTypes,
        genres,
        years,
        isFavorite,
        isPlayed,
        nameStartsWith,
        fields,
        imageTypes,
        hasOverview
    )

    override suspend fun getItem(itemId: UUID, fields: List<ItemFields>?) =
        getCurrentRepository().getItem(itemId, fields)

    override suspend fun getItemById(itemId: UUID) = getCurrentRepository().getItemById(itemId)
    override suspend fun getSimilarItems(itemId: UUID, limit: Int, fields: List<ItemFields>?) =
        getCurrentRepository().getSimilarItems(itemId, limit, fields)

    override suspend fun getMovies(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?
    ) = getCurrentRepository().getMovies(
        parentId,
        sortBy,
        sortDescending,
        limit,
        startIndex,
        searchTerm,
        isPlayed
    )

    override suspend fun getShows(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?
    ) = getCurrentRepository().getShows(
        parentId,
        sortBy,
        sortDescending,
        limit,
        startIndex,
        searchTerm,
        isPlayed
    )

    override suspend fun getSeasons(
        seriesId: UUID,
        sortBy: SortBy,
        sortDescending: Boolean,
        fields: List<ItemFields>?
    ) = getCurrentRepository().getSeasons(seriesId, sortBy, sortDescending, fields)

    override suspend fun getEpisodes(seasonId: UUID, seriesId: UUID, fields: List<ItemFields>?) =
        getCurrentRepository().getEpisodes(seasonId, seriesId, fields)

    override suspend fun getSpecialFeatures(itemId: UUID, userId: UUID) =
        getCurrentRepository().getSpecialFeatures(itemId, userId)

    override suspend fun getSeriesNextEpisode(seriesId: UUID) =
        getCurrentRepository().getSeriesNextEpisode(seriesId)

    override suspend fun getPersonDetail(personId: UUID) =
        getCurrentRepository().getPersonDetail(personId)

    override suspend fun getPersonItems(
        personId: UUID,
        includeItemTypes: List<String>,
        limit: Int?,
        startIndex: Int,
        fields: List<ItemFields>?
    ) = getCurrentRepository().getPersonItems(personId, includeItemTypes, limit, startIndex, fields)

    override suspend fun reportPlaybackStart(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?
    ) = getCurrentRepository().reportPlaybackStart(itemId, positionTicks, sessionId)

    override suspend fun reportPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        sessionId: String?
    ) = getCurrentRepository().reportPlaybackProgress(itemId, positionTicks, isPaused, sessionId)

    override suspend fun reportPlaybackStopped(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?
    ) = getCurrentRepository().reportPlaybackStopped(itemId, positionTicks, sessionId)

    override suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        videoStreamIndex: Int?
    ) = getCurrentRepository().getStreamUrl(
        itemId,
        mediaSourceId,
        maxBitrate,
        audioStreamIndex,
        subtitleStreamIndex,
        videoStreamIndex
    )

    override suspend fun getImageUrl(
        itemId: UUID,
        imageType: String,
        imageIndex: Int,
        tag: String?,
        maxWidth: Int?,
        maxHeight: Int?,
        quality: Int?
    ) = getCurrentRepository().getImageUrl(
        itemId,
        imageType,
        imageIndex,
        tag,
        maxWidth,
        maxHeight,
        quality
    )

    override fun getLibrariesFlow() = flow {
        emitAll(getCurrentRepository().getLibrariesFlow())
    }

    override fun getLatestMediaFlow(parentId: UUID?) = flow {
        emitAll(getCurrentRepository().getLatestMediaFlow(parentId))
    }

    override fun getContinueWatchingFlow() = flow {
        emitAll(getCurrentRepository().getContinueWatchingFlow())
    }

    override fun getNextUpFlow() = flow {
        emitAll(getCurrentRepository().getNextUpFlow())
    }
    override suspend fun getEpisodeToPlay(seriesId: UUID) =
        getCurrentRepository().getEpisodeToPlay(seriesId)

    override suspend fun getEpisodeToPlayForSeason(seasonId: UUID, seriesId: UUID) =
        getCurrentRepository().getEpisodeToPlayForSeason(seasonId, seriesId)

    override suspend fun getFavoriteMovies() = getCurrentRepository().getFavoriteMovies()
    override suspend fun getFavoriteShows() = getCurrentRepository().getFavoriteShows()
    override suspend fun getFavoriteEpisodes() = getCurrentRepository().getFavoriteEpisodes()
    override suspend fun getFavoriteSeasons() = getCurrentRepository().getFavoriteSeasons()
    override suspend fun getTrickplayTileImage(itemId: UUID, width: Int, index: Int) =
        getCurrentRepository().getTrickplayTileImage(itemId, width, index)

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int) =
        getCurrentRepository().getTrickplayData(itemId, width, index)

    override suspend fun getTrickplayManifest(itemId: UUID) =
        getCurrentRepository().getTrickplayManifest(itemId)
}