package com.makd.afinity.data.repository.impl

import android.content.Context
import androidx.paging.PagingData
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.entities.AfinityEpisodeDto
import com.makd.afinity.data.database.entities.AfinityMovieDto
import com.makd.afinity.data.database.entities.AfinitySeasonDto
import com.makd.afinity.data.database.entities.AfinityShowDto
import com.makd.afinity.data.models.auth.QuickConnectState
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.models.media.toAfinitySource
import com.makd.afinity.data.models.media.toAfinityTrickplayInfo
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.ui.library.FilterType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepositoryOfflineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AfinityDatabase,
    private val authRepository: AuthRepository
) : JellyfinRepository {

    override fun getBaseUrl(): String = ""

    override suspend fun setBaseUrl(baseUrl: String) {
        Timber.d("Offline mode: Cannot set base URL")
    }

    override suspend fun discoverServers(): List<Server> = emptyList()

    override suspend fun discoverServersFlow(): Flow<List<Server>> = flowOf(emptyList())

    override suspend fun validateServer(serverUrl: String): JellyfinServerRepository.ServerConnectionResult {
        return JellyfinServerRepository.ServerConnectionResult.Error("Offline mode: Cannot validate server")
    }

    override suspend fun refreshServerInfo() {
        Timber.d("Offline mode: Cannot refresh server info")
    }

    override suspend fun authenticateByName(username: String, password: String): AuthenticationResult? = null

    override suspend fun authenticateWithQuickConnect(secret: String): AuthenticationResult? = null

    override suspend fun logout() {
        Timber.d("Offline mode: Logout requested")
    }

    override suspend fun initiateQuickConnect(): QuickConnectState? = null

    override suspend fun getQuickConnectState(secret: String): QuickConnectState? = null

    override suspend fun getCurrentUser(): User? = authRepository.currentUser.value

    override suspend fun getPublicUsers(): List<User> = emptyList()

    override suspend fun getUserProfileImageUrl(): String? = null

    override suspend fun getContinueWatching(limit: Int): List<AfinityItem> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()

        val movies: List<AfinityItem> = database.serverDatabaseDao()
            .getContinueWatchingMovies(userId, limit)
            .map { it.toAfinityMovie(database.serverDatabaseDao(), userId) }

        val episodes: List<AfinityItem> = database.serverDatabaseDao()
            .getContinueWatchingEpisodes(userId, limit)
            .map { it.toAfinityEpisode(database.serverDatabaseDao(), userId) }

        (movies + episodes).take(limit)
    }

    override suspend fun getSeasons(
        seriesId: UUID,
        sortBy: SortBy,
        sortDescending: Boolean,
        fields: List<ItemFields>?
    ): List<AfinitySeason> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        database.serverDatabaseDao().getSeasonsForSeries(seriesId)
            .map { it.toAfinitySeason(database.serverDatabaseDao(), userId) }
    }

    override suspend fun getEpisodes(
        seasonId: UUID,
        seriesId: UUID,
        fields: List<ItemFields>?
    ): List<AfinityEpisode> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        database.serverDatabaseDao().getEpisodesForSeason(seasonId)
            .map { it.toAfinityEpisode(database.serverDatabaseDao(), userId) }
    }

    override suspend fun reportPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        sessionId: String?
    ) = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext
        database.serverDatabaseDao().setPlaybackPositionTicks(itemId, userId, positionTicks)
        database.serverDatabaseDao().setUserDataToBeSynced(userId, itemId, true)
        Timber.d("Offline: Saved playback progress for $itemId at $positionTicks ticks")
    }

    override suspend fun reportPlaybackStopped(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?
    ) = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext

        val movieDto = database.serverDatabaseDao().getMovie(itemId)
        val episodeDto = database.serverDatabaseDao().getEpisode(itemId)
        val runtimeTicks = movieDto?.runtimeTicks ?: episodeDto?.runtimeTicks ?: 0L

        val playedPercentage = if (runtimeTicks > 0) {
            ((positionTicks.toDouble() / runtimeTicks) * 100).toInt()
        } else 0

        when {
            playedPercentage < 10 -> {
                database.serverDatabaseDao().setPlaybackPositionTicks(itemId, userId, 0)
                database.serverDatabaseDao().setPlayed(userId, itemId, false)
            }
            playedPercentage > 90 -> {
                database.serverDatabaseDao().setPlaybackPositionTicks(itemId, userId, 0)
                database.serverDatabaseDao().setPlayed(userId, itemId, true)
            }
            else -> {
                database.serverDatabaseDao().setPlaybackPositionTicks(itemId, userId, positionTicks)
            }
        }

        database.serverDatabaseDao().setUserDataToBeSynced(userId, itemId, true)
        Timber.d("Offline: Saved playback stopped for $itemId (${playedPercentage}% watched)")
    }

    override suspend fun getLibraries(): List<AfinityCollection> = emptyList()

    override suspend fun getLatestMedia(parentId: UUID?, limit: Int): List<AfinityItem> = emptyList()

    override suspend fun getNextUp(limit: Int): List<AfinityEpisode> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        val result = mutableListOf<AfinityEpisode>()

        val shows = database.serverDatabaseDao().getShowsByServerId(null)

        for (show in shows) {
            val episodes = database.serverDatabaseDao().getEpisodesForSeries(show.id)
                .map { it.toAfinityEpisode(database.serverDatabaseDao(), userId) }

            val indexOfLastPlayed = episodes.indexOfLast { it.played }

            if (indexOfLastPlayed == -1 && episodes.isNotEmpty()) {
                result.add(episodes.first())
            } else {
                episodes.getOrNull(indexOfLastPlayed + 1)?.let { result.add(it) }
            }
        }

        result.filter { it.playbackPositionTicks == 0L }.take(limit)
    }

    override suspend fun getRecommendationCategories(
        parentId: UUID?,
        categoryLimit: Int,
        itemLimit: Int
    ): List<AfinityRecommendationCategory> = emptyList()

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
    ): BaseItemDtoQueryResult {
        return BaseItemDtoQueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
    }

    override fun getItemsPaging(
        parentId: UUID,
        libraryType: CollectionType,
        sortBy: SortBy,
        sortDescending: Boolean,
        filter: FilterType,
        nameStartsWith: String?,
        fields: List<ItemFields>?
    ): Flow<PagingData<AfinityItem>> = flowOf(PagingData.empty())

    override suspend fun getItem(itemId: UUID, fields: List<ItemFields>?): BaseItemDto? = null

    override suspend fun getItemById(itemId: UUID): AfinityItem? = null

    override suspend fun getSimilarItems(
        itemId: UUID,
        limit: Int,
        fields: List<ItemFields>?
    ): List<AfinityItem> = emptyList()

    override suspend fun getMovies(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?
    ): List<AfinityMovie> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        database.serverDatabaseDao().getMoviesByServerId(null)
            .map { it.toAfinityMovie(database.serverDatabaseDao(), userId) }
    }

    override suspend fun getShows(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?
    ): List<AfinityShow> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        database.serverDatabaseDao().getShowsByServerId(null)
            .map { it.toAfinityShow(database.serverDatabaseDao(), userId) }
    }

    override suspend fun getSpecialFeatures(itemId: UUID, userId: UUID): List<AfinityItem> = emptyList()

    override suspend fun getSeriesNextEpisode(seriesId: UUID): AfinityEpisode? = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext null
        val episodes = database.serverDatabaseDao().getEpisodesForSeries(seriesId)
            .map { it.toAfinityEpisode(database.serverDatabaseDao(), userId) }
        episodes.firstOrNull { !it.played }
    }

    override suspend fun getPersonDetail(personId: UUID): AfinityPersonDetail? = null

    override suspend fun getPersonItems(
        personId: UUID,
        includeItemTypes: List<String>,
        limit: Int?,
        startIndex: Int,
        fields: List<ItemFields>?
    ): List<AfinityItem> = emptyList()

    override suspend fun reportPlaybackStart(
        itemId: UUID,
        positionTicks: Long,
        sessionId: String?
    ) {
        Timber.d("Offline: Playback started for $itemId")
    }

    override suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        maxBitrate: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        videoStreamIndex: Int?
    ): String {
        return withContext(Dispatchers.IO) {
            val source = database.serverDatabaseDao().getSource(mediaSourceId)
            source?.path ?: ""
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
    ): String = ""


    override fun getLibrariesFlow(): Flow<List<AfinityCollection>> = flowOf(emptyList())

    override fun getLatestMediaFlow(parentId: UUID?): Flow<List<AfinityItem>> = flowOf(emptyList())

    override fun getContinueWatchingFlow(): Flow<List<AfinityItem>> = flowOf(emptyList())

    override fun getNextUpFlow(): Flow<List<AfinityEpisode>> = flowOf(emptyList())

    override suspend fun getEpisodeToPlay(seriesId: UUID): AfinityEpisode? = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext null
        val episodes = database.serverDatabaseDao().getEpisodesForSeries(seriesId)
            .map { it.toAfinityEpisode(database.serverDatabaseDao(), userId) }
            .sortedWith(compareBy<AfinityEpisode> { it.parentIndexNumber }.thenBy { it.indexNumber })

        episodes.firstOrNull { !it.played } ?: episodes.firstOrNull()
    }

    override suspend fun getEpisodeToPlayForSeason(seasonId: UUID, seriesId: UUID): AfinityEpisode? = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext null
        val episodes = database.serverDatabaseDao().getEpisodesForSeason(seasonId)
            .map { it.toAfinityEpisode(database.serverDatabaseDao(), userId) }
            .sortedBy<AfinityEpisode, Int> { it.indexNumber }

        episodes.firstOrNull { !it.played } ?: episodes.firstOrNull()
    }

    override suspend fun getFavoriteMovies(): List<AfinityMovie> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        database.serverDatabaseDao().getFavoriteMovies(userId)
            .map { it.toAfinityMovie(database.serverDatabaseDao(), userId) }
    }

    override suspend fun getFavoriteShows(): List<AfinityShow> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        database.serverDatabaseDao().getFavoriteShows(userId)
            .map { it.toAfinityShow(database.serverDatabaseDao(), userId) }
    }

    override suspend fun getFavoriteEpisodes(): List<AfinityEpisode> = withContext(Dispatchers.IO) {
        val userId = authRepository.currentUser.value?.id ?: return@withContext emptyList()
        database.serverDatabaseDao().getFavoriteEpisodes(userId)
            .map { it.toAfinityEpisode(database.serverDatabaseDao(), userId) }
    }

    override suspend fun getFavoriteSeasons(): List<AfinitySeason> = emptyList()

    override suspend fun getTrickplayTileImage(itemId: UUID, width: Int, index: Int): ByteArray? = null

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? = null

    override suspend fun getTrickplayManifest(itemId: UUID): Map<String, Map<Int, AfinityTrickplayInfo>>? = null

    private suspend fun AfinityMovieDto.toAfinityMovie(
        database: ServerDatabaseDao,
        userId: UUID
    ): AfinityMovie {
        val userData = database.getUserDataOrCreateNew(id, userId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        val trickplayInfos = mutableMapOf<String, AfinityTrickplayInfo>()
        for (source in sources) {
            database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
                trickplayInfos[source.id] = it
            }
        }
        return AfinityMovie(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            premiereDate = premiereDate,
            dateCreated = dateCreated,
            genres = emptyList(),
            people = emptyList(),
            communityRating = communityRating,
            officialRating = officialRating,
            criticRating = criticRating,
            status = status,
            productionYear = productionYear,
            endDate = endDate,
            canDownload = false,
            canPlay = true,
            sources = sources,
            trailer = null,
            images = AfinityImages(),
            chapters = chapters ?: emptyList(),
            tagline = null,
            unplayedItemCount = null,
            trickplayInfo = trickplayInfos.ifEmpty { null },
            providerIds = null,
            externalUrls = null,
        )
    }

    private suspend fun AfinityShowDto.toAfinityShow(
        database: ServerDatabaseDao,
        userId: UUID
    ): AfinityShow {
        val userData = database.getUserDataOrCreateNew(id, userId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        return AfinityShow(
            id = id,
            name = name,
            originalTitle = originalTitle,
            overview = overview,
            played = userData.played,
            favorite = userData.favorite,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            communityRating = communityRating,
            officialRating = officialRating,
            status = status,
            productionYear = productionYear,
            premiereDate = premiereDate,
            dateCreated = dateCreated,
            dateLastContentAdded = dateLastContentAdded,
            endDate = endDate,
            canDownload = false,
            canPlay = true,
            sources = sources,
            seasons = emptyList(),
            genres = emptyList(),
            people = emptyList(),
            trailer = null,
            tagline = null,
            images = AfinityImages(),
            seasonCount = null,
            episodeCount = null,
            unplayedItemCount = null,
            providerIds = null,
            externalUrls = null,
        )
    }

    private suspend fun AfinitySeasonDto.toAfinitySeason(
        database: ServerDatabaseDao,
        userId: UUID
    ): AfinitySeason {
        val userData = database.getUserDataOrCreateNew(id, userId)
        return AfinitySeason(
            id = id,
            name = name,
            seriesId = seriesId,
            seriesName = seriesName,
            originalTitle = null,
            overview = overview,
            sources = emptyList(),
            indexNumber = indexNumber,
            episodes = emptyList(),
            played = userData.played,
            favorite = userData.favorite,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = null,
            images = AfinityImages(),
            episodeCount = null,
            productionYear = null,
            premiereDate = null,
            people = emptyList(),
            providerIds = null,
            externalUrls = null,
        )
    }

    private suspend fun AfinityEpisodeDto.toAfinityEpisode(
        database: ServerDatabaseDao,
        userId: UUID
    ): AfinityEpisode {
        val userData = database.getUserDataOrCreateNew(id, userId)
        val sources = database.getSources(id).map { it.toAfinitySource(database) }
        val trickplayInfos = mutableMapOf<String, AfinityTrickplayInfo>()
        for (source in sources) {
            database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
                trickplayInfos[source.id] = it
            }
        }
        return AfinityEpisode(
            id = id,
            name = name,
            originalTitle = null,
            overview = overview,
            indexNumber = indexNumber,
            indexNumberEnd = indexNumberEnd,
            parentIndexNumber = parentIndexNumber,
            sources = sources,
            played = userData.played,
            favorite = userData.favorite,
            canPlay = true,
            canDownload = false,
            runtimeTicks = runtimeTicks,
            playbackPositionTicks = userData.playbackPositionTicks,
            premiereDate = premiereDate,
            seriesName = seriesName,
            seriesId = seriesId,
            seriesLogo = null,
            seriesLogoBlurHash = null,
            seasonId = seasonId,
            communityRating = communityRating,
            people = emptyList(),
            missing = false,
            images = AfinityImages(),
            chapters = chapters ?: emptyList(),
            trickplayInfo = trickplayInfos.ifEmpty { null },
            providerIds = null,
            externalUrls = null,
        )
    }
}