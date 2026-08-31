package com.makd.afinity.data.repository.media

import android.content.Context
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityBoxSet
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.extensions.toAfinityPersonDetail
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.extensions.toAfinityShow
import com.makd.afinity.data.models.extensions.toAfinityTrack
import com.makd.afinity.data.models.extensions.toAfinityVideo
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.mdblist.MdbListRatingsResult
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.ItemFilterCriteria
import com.makd.afinity.data.models.media.LibraryFilterOptions
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.media.LibraryLanguageOption
import com.makd.afinity.data.models.media.PlaylistEntry
import com.makd.afinity.data.models.media.toAfinityCollection
import com.makd.afinity.data.models.media.withPatchedImages
import com.makd.afinity.data.models.music.AfinityPlaylistContents
import com.makd.afinity.data.models.omdb.OmdbApiResult
import com.makd.afinity.data.network.MdbListApiService
import com.makd.afinity.data.network.OmdbApiService
import com.makd.afinity.data.paging.JellyfinItemsPagingSource
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.FilterApi
import org.jellyfin.sdk.api.operations.GenreApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.PersonApi
import org.jellyfin.sdk.api.operations.PlaylistApi
import org.jellyfin.sdk.api.operations.ShowApi
import org.jellyfin.sdk.api.operations.StudioApi
import org.jellyfin.sdk.api.operations.TrickPlayApi
import org.jellyfin.sdk.api.operations.UserViewApi
import org.jellyfin.sdk.api.operations.VideoApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.NameValuePair
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.VideoType
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val RESUMABLE_ITEM_TYPES = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE)

@Singleton
class JellyfinMediaRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    @param:ApplicationContext private val context: Context,
    private val mdbListApiService: MdbListApiService,
    private val omdbApiService: OmdbApiService,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val databaseRepository: DatabaseRepository,
    private val storageLocationProvider: StorageLocationProvider,
    private val apiInvoker: JellyfinApiInvoker,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : MediaRepository {
    override suspend fun refreshItemUserData(
        itemId: UUID,
        fields: List<ItemFields>?,
    ): AfinityItem? {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null
                val libraryApi = LibraryApi(apiClient)
                val freshItem =
                    libraryApi
                        .getItem(userId = userId, itemId = itemId)
                        .content
                        .toAfinityItem(getBaseUrl())

                if (freshItem != null) {
                    updateItemInCache(_continueWatching, freshItem)

                    if (freshItem is AfinityEpisode) {
                        updateEpisodeInNextUpCache(freshItem)
                    }
                }
                return@withContext freshItem
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh UserData")
                null
            }
        }
    }

    private fun updateItemInCache(
        cache: MutableStateFlow<List<AfinityItem>>,
        updatedItem: AfinityItem,
    ) {
        cache.update { currentList ->
            val newList = currentList.toMutableList()
            val existingIndex = newList.indexOfFirst { it.id == updatedItem.id }

            when {
                updatedItem.played && cache == _continueWatching -> {
                    if (existingIndex != -1) {
                        newList.removeAt(existingIndex)
                        Timber.d("Removed completed item: ${updatedItem.name}")
                    }
                }

                updatedItem.playbackPositionTicks > 0 &&
                    !updatedItem.played &&
                    cache == _continueWatching -> {
                    if (existingIndex != -1) {
                        newList.removeAt(existingIndex)
                    }
                    newList.add(0, updatedItem)
                    Timber.d("Moved item to start of continue watching: ${updatedItem.name}")
                }

                existingIndex != -1 -> {
                    newList[existingIndex] = updatedItem
                    Timber.d("Updated item in cache: ${updatedItem.name}")
                }
            }
            if (updatedItem is AfinityEpisode) {
                val parentSeriesIndex = newList.indexOfFirst {
                    it is AfinityShow && it.id == updatedItem.seriesId
                }

                if (parentSeriesIndex != -1) {
                    val parent = newList[parentSeriesIndex] as AfinityShow
                    val currentCount = parent.unplayedItemCount ?: 0
                    val newCount =
                        if (updatedItem.played) {
                            (currentCount - 1).coerceAtLeast(0)
                        } else {
                            currentCount + 1
                        }
                    if (currentCount != newCount) {
                        newList[parentSeriesIndex] = parent.copy(unplayedItemCount = newCount)
                        Timber.d(
                            "Optimistic Update: Series '${parent.name}' badge $currentCount -> $newCount"
                        )
                    }
                }
            }
            newList
        }
    }

    private fun updateEpisodeInNextUpCache(updatedEpisode: AfinityEpisode) {
        _nextUp.update { currentList ->
            val newList = currentList.toMutableList()
            val existingIndex = newList.indexOfFirst { it.id == updatedEpisode.id }

            when {
                updatedEpisode.played || updatedEpisode.playbackPositionTicks > 0 -> {
                    if (existingIndex != -1) {
                        newList.removeAt(existingIndex)
                        Timber.d(
                            "Removed episode from next up (played=${updatedEpisode.played}, resumable=${updatedEpisode.playbackPositionTicks > 0}): ${updatedEpisode.name}"
                        )
                    }
                }

                existingIndex != -1 -> {
                    newList[existingIndex] = updatedEpisode
                    Timber.d("Updated episode in next up: ${updatedEpisode.name}")
                }
            }
            newList
        }
    }

    private fun currentSessionKey(): String? =
        sessionManager.currentSession.value?.let { "${it.serverId}_${it.userId}" }

    override suspend fun invalidateContinueWatchingCache() {
        withContext(Dispatchers.IO) {
            try {
                val sessionKeyAtStart = currentSessionKey() ?: return@withContext
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId() ?: return@withContext
                val libraryApi = LibraryApi(apiClient)
                val response =
                    libraryApi.getResumeItems(
                        userId = userId,
                        limit = 12,
                        fields = FieldSets.CACHE_CONTINUE_WATCHING,
                        includeItemTypes = RESUMABLE_ITEM_TYPES,
                        enableImages = true,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                    )

                val continueWatchingItems =
                    response.content.items.mapNotNull { baseItemDto ->
                        baseItemDto.toAfinityItem(getBaseUrl())
                    }

                if (currentSessionKey() != sessionKeyAtStart) {
                    Timber.d("Session changed during continue watching refresh — discarding")
                    return@withContext
                }
                _continueWatching.value = continueWatchingItems
                Timber.d("Full refresh of continue watching cache completed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh continue watching cache")
            }
        }
    }

    override suspend fun invalidateNextUpCache() {
        withContext(Dispatchers.IO) {
            try {
                val sessionKeyAtStart = currentSessionKey() ?: return@withContext
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId() ?: return@withContext
                val showApi = ShowApi(apiClient)
                val response =
                    showApi.getNextUp(
                        userId = userId,
                        limit = 16,
                        fields = FieldSets.CACHE_NEXT_UP,
                        enableImages = true,
                        enableUserData = true,
                        enableResumable = false,
                        enableRewatching = false,
                        enableTotalRecordCount = false,
                    )

                val nextUpEpisodes =
                    response.content.items.mapNotNull { baseItemDto ->
                        baseItemDto.toAfinityEpisode(getBaseUrl())
                    }

                if (currentSessionKey() != sessionKeyAtStart) {
                    Timber.d("Session changed during next up refresh — discarding")
                    return@withContext
                }
                _nextUp.value = nextUpEpisodes
                Timber.d("Full refresh of next up cache completed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh next up cache")
            }
        }
    }

    override suspend fun invalidateAllCaches() {
        Timber.d("Full cache invalidation requested - refreshing all caches")
        coroutineScope {
            launch { invalidateContinueWatchingCache() }
            launch { invalidateNextUpCache() }
        }
    }

    override suspend fun invalidateItemCache(itemId: UUID) {
        refreshItemUserData(itemId)
    }

    private val _libraries = MutableStateFlow<List<AfinityCollection>>(emptyList())
    override val libraries: Flow<List<AfinityCollection>> = _libraries.asStateFlow()

    private val _hasLiveTvLibrary = MutableStateFlow<Boolean?>(null)
    override val hasLiveTvLibrary: StateFlow<Boolean?> = _hasLiveTvLibrary.asStateFlow()

    override fun patchItemImages(updatedItem: AfinityItem) {
        _continueWatching.update { it.withPatchedImages(updatedItem) }
        _nextUp.update { it.withPatchedImages(updatedItem) }
    }

    override fun clearPlaybackCaches() {
        Timber.d("Clearing in-memory playback caches")
        _continueWatching.value = emptyList()
        _nextUp.value = emptyList()
    }

    override fun removeItemFromCache(itemId: String) {
        val uuid =
            try {
                UUID.fromString(itemId)
            } catch (e: Exception) {
                null
            }
        val matches: (UUID) -> Boolean = { id ->
            id.toString() == itemId || (uuid != null && id == uuid)
        }

        _continueWatching.update { list -> list.filterNot { matches(it.id) } }
        _nextUp.update { list -> list.filterNot { matches(it.id) || matches(it.seriesId) } }
    }

    private val _continueWatching = MutableStateFlow<List<AfinityItem>>(emptyList())
    override val continueWatching: Flow<List<AfinityItem>> = _continueWatching.asStateFlow()

    private val _nextUp = MutableStateFlow<List<AfinityEpisode>>(emptyList())
    override val nextUp: Flow<List<AfinityEpisode>> = _nextUp.asStateFlow()

    private fun getCurrentUserId(): UUID? = sessionManager.currentSession.value?.userId

    private suspend fun <T> apiCall(
        default: T,
        errorMessage: String,
        block: suspend (apiClient: ApiClient, userId: UUID) -> T,
    ): T = apiInvoker.apiCall(default, errorMessage, block)

    override fun getBaseUrl(): String {
        return sessionManager.currentSession.value?.serverUrl ?: ""
    }

    override fun getItemsPaging(
        parentId: UUID?,
        libraryType: CollectionType,
        sortBy: SortBy,
        sortDescending: Boolean,
        filters: LibraryFilters,
        nameStartsWith: String?,
        fields: List<ItemFields>?,
        studioNames: List<String>,
        includeItemTypes: List<String>?,
        onSourceCreated: ((PagingSource<Int, AfinityItem>) -> Unit)?,
    ): Flow<PagingData<AfinityItem>> =
        Pager(
                config =
                    PagingConfig(pageSize = 50, enablePlaceholders = false, initialLoadSize = 50)
            ) {
                JellyfinItemsPagingSource(
                        mediaRepository = this,
                        parentId = parentId,
                        libraryType = libraryType,
                        sortBy = sortBy,
                        sortDescending = sortDescending,
                        filters = filters,
                        baseUrl = getBaseUrl(),
                        nameStartsWith = nameStartsWith,
                        studioNames = studioNames,
                        includeItemTypes = includeItemTypes,
                    )
                    .also { source -> onSourceCreated?.invoke(source) }
            }
            .flow

    override suspend fun getFilterOptions(
        parentId: UUID?,
        libraryType: CollectionType,
        includeItemTypes: List<String>,
    ): LibraryFilterOptions =
        getFilterOptionsResult(parentId, libraryType, includeItemTypes).getOrElse { e ->
            if (e !is NoActiveSessionException) Timber.e(e, "Failed to get filter options")
            LibraryFilterOptions()
        }

    override suspend fun getFilterOptionsResult(
        parentId: UUID?,
        libraryType: CollectionType,
        includeItemTypes: List<String>,
    ): Result<LibraryFilterOptions> = apiInvoker.apiResult { apiClient, userId ->
        val requestedTypes =
            includeItemTypes
                .mapNotNull {
                    try {
                        BaseItemKind.valueOf(it.uppercase())
                    } catch (e: Exception) {
                        Timber.w("Unknown item type dropped from filter query: $it")
                        null
                    }
                }
                .ifEmpty {
                    when (libraryType) {
                        CollectionType.TvShows -> listOf(BaseItemKind.SERIES)
                        CollectionType.Movies -> listOf(BaseItemKind.MOVIE)
                        CollectionType.BoxSets -> listOf(BaseItemKind.BOX_SET)
                        else -> emptyList()
                    }
                }

        val filterApi = FilterApi(apiClient)
        val types = requestedTypes.ifEmpty { null }

        val (legacy, modern) =
            coroutineScope {
                val legacyDeferred = async {
                    filterApi
                        .getQueryFiltersLegacy(
                            userId = userId,
                            parentId = parentId,
                            includeItemTypes = types,
                        )
                        .content
                }
                val modernDeferred = async {
                    try {
                        filterApi
                            .getQueryFilters(
                                userId = userId,
                                parentId = parentId,
                                includeItemTypes = types,
                            )
                            .content
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Language filter options unavailable")
                        null
                    }
                }
                legacyDeferred.await() to modernDeferred.await()
            }

        LibraryFilterOptions(
            genres = legacy.genres.orEmpty(),
            tags = legacy.tags.orEmpty(),
            officialRatings = legacy.officialRatings.orEmpty(),
            years = legacy.years.orEmpty().sortedDescending(),
            audioLanguages = modern?.audioLanguages.toLanguageOptions(),
            subtitleLanguages = modern?.subtitleLanguages.toLanguageOptions(),
        )
    }

    private fun List<NameValuePair>?.toLanguageOptions(): List<LibraryLanguageOption> =
        orEmpty()
            .mapNotNull { pair ->
                val code = pair.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                LibraryLanguageOption(
                    name = pair.name?.takeIf { it.isNotBlank() } ?: code,
                    code = code,
                )
            }
            .distinctBy { it.code }
            .sortedBy { it.name.lowercase() }

    override suspend fun getLibraries(): List<AfinityCollection> =
        getLibrariesResult().getOrElse { e ->
            if (e !is NoActiveSessionException) Timber.e(e, "Failed to get libraries")
            emptyList()
        }

    override suspend fun getLibrariesResult(): Result<List<AfinityCollection>> =
        apiInvoker.apiResult { apiClient, userId ->
            val views = UserViewApi(apiClient).getUserViews(userId = userId).content.items

            _hasLiveTvLibrary.value = views.any {
                it.collectionType == org.jellyfin.sdk.model.api.CollectionType.LIVETV
            }

            val libraries =
                views
                    .filter {
                        it.collectionType != org.jellyfin.sdk.model.api.CollectionType.LIVETV
                    }
                    .mapNotNull { baseItemDto ->
                        try {
                            baseItemDto.toAfinityCollection(getBaseUrl())
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to convert item to collection: ${baseItemDto.name}")
                            null
                        }
                    }

            _libraries.value = libraries
            Timber.d("Successfully retrieved ${libraries.size} libraries via UserViews API")
            libraries
        }

    override suspend fun getLatestMedia(
        parentId: UUID?,
        limit: Int,
        fields: List<ItemFields>?,
        groupItems: Boolean,
        includeItemTypes: List<BaseItemKind>?,
    ): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get latest media") { apiClient, userId ->
            LibraryApi(apiClient)
                .getLatestMedia(
                    userId = userId,
                    parentId = parentId,
                    limit = limit,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    groupItems = groupItems,
                    includeItemTypes = includeItemTypes,
                )
                .content
                .mapNotNull { baseItemDto -> baseItemDto.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getContinueWatching(
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get continue watching") { apiClient, userId ->
            val sessionKeyAtStart = currentSessionKey()
            val continueWatchingItems =
                LibraryApi(apiClient)
                    .getResumeItems(
                        userId = userId,
                        limit = limit,
                        fields = fields ?: FieldSets.CONTINUE_WATCHING,
                        includeItemTypes = RESUMABLE_ITEM_TYPES,
                        enableImages = true,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                        imageTypeLimit = 1,
                    )
                    .content
                    .items
                    .mapNotNull { baseItemDto -> baseItemDto.toAfinityItem(getBaseUrl()) }

            if (currentSessionKey() == sessionKeyAtStart) {
                _continueWatching.value = continueWatchingItems
            }
            continueWatchingItems
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
        nameStartsWith: String?,
        fields: List<ItemFields>?,
        imageTypes: List<String>,
        recursive: Boolean?,
        criteria: ItemFilterCriteria,
        enableTotalRecordCount: Boolean,
        enableImageTypes: List<String>,
    ): BaseItemDtoQueryResult =
        getItemsResult(
                parentId = parentId,
                collectionTypes = collectionTypes,
                sortBy = sortBy,
                sortDescending = sortDescending,
                limit = limit,
                startIndex = startIndex,
                searchTerm = searchTerm,
                includeItemTypes = includeItemTypes,
                nameStartsWith = nameStartsWith,
                fields = fields,
                imageTypes = imageTypes,
                recursive = recursive,
                criteria = criteria,
                enableTotalRecordCount = enableTotalRecordCount,
                enableImageTypes = enableImageTypes,
            )
            .getOrElse { e ->
                if (e !is NoActiveSessionException) Timber.e(e, "Failed to get items")
                BaseItemDtoQueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
            }

    override suspend fun getItemsResult(
        parentId: UUID?,
        collectionTypes: List<CollectionType>,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        includeItemTypes: List<String>,
        nameStartsWith: String?,
        fields: List<ItemFields>?,
        imageTypes: List<String>,
        recursive: Boolean?,
        criteria: ItemFilterCriteria,
        enableTotalRecordCount: Boolean,
        enableImageTypes: List<String>,
    ): Result<BaseItemDtoQueryResult> = apiInvoker.apiResult { apiClient, userId ->
        val filters = buildList {
            if (criteria.isLiked == true) add(ItemFilter.LIKES)
            if (criteria.isResumable == true) add(ItemFilter.IS_RESUMABLE)
        }

        LibraryApi(apiClient)
            .getItems(
                userId = userId,
                parentId = parentId,
                limit = limit,
                startIndex = startIndex,
                searchTerm = searchTerm,
                sortBy = listOf(sortBy.toJellyfinSortBy()),
                sortOrder =
                    if (sortDescending) listOf(SortOrder.DESCENDING)
                    else listOf(SortOrder.ASCENDING),
                includeItemTypes =
                    includeItemTypes
                        .mapNotNull {
                            try {
                                BaseItemKind.valueOf(it.uppercase())
                            } catch (e: Exception) {
                                Timber.w("Unknown item type dropped from filter: $it")
                                null
                            }
                        }
                        .ifEmpty { null },
                recursive =
                    recursive
                        ?: if (parentId == null) true
                        else if (
                            includeItemTypes.size == 1 &&
                                (includeItemTypes.contains("SERIES") ||
                                    includeItemTypes.contains("BOX_SET"))
                        )
                            true
                        else if (searchTerm != null) true else null,
                collapseBoxSetItems =
                    if (includeItemTypes.size == 1 && includeItemTypes.contains("SERIES")) false
                    else null,
                genres = criteria.genres,
                years = criteria.years,
                isFavorite = criteria.isFavorite,
                isPlayed = criteria.isPlayed,
                isMissing = false,
                filters = filters.ifEmpty { null },
                nameStartsWith = nameStartsWith,
                studios = criteria.studios.ifEmpty { null },
                officialRatings = criteria.officialRatings.ifEmpty { null },
                tags = criteria.tags.ifEmpty { null },
                audioLanguages = criteria.audioLanguages.ifEmpty { null },
                subtitleLanguages = criteria.subtitleLanguages.ifEmpty { null },
                videoTypes =
                    criteria.videoTypes
                        .mapNotNull { VideoType.fromNameOrNull(it) }
                        .ifEmpty { null },
                seriesStatus =
                    criteria.seriesStatuses
                        .mapNotNull { SeriesStatus.fromNameOrNull(it) }
                        .ifEmpty { null },
                hasSubtitles = criteria.hasSubtitles,
                hasTrailer = criteria.hasTrailer,
                hasSpecialFeature = criteria.hasSpecialFeature,
                hasThemeSong = criteria.hasThemeSong,
                hasThemeVideo = criteria.hasThemeVideo,
                isHd = criteria.isHd,
                is4k = criteria.is4k,
                is3d = criteria.is3d,
                enableTotalRecordCount = enableTotalRecordCount,
                fields = fields ?: FieldSets.LIBRARY_GRID,
                imageTypes =
                    if (imageTypes.isNotEmpty()) {
                        imageTypes.mapNotNull {
                            try {
                                ImageType.valueOf(it.uppercase())
                            } catch (e: Exception) {
                                null
                            }
                        }
                    } else {
                        null
                    },
                hasOverview = criteria.hasOverview,
                enableImages = true,
                enableImageTypes =
                    enableImageTypes
                        .mapNotNull {
                            try {
                                ImageType.valueOf(it.uppercase())
                            } catch (e: Exception) {
                                null
                            }
                        }
                        .ifEmpty { null },
                imageTypeLimit = if (enableImageTypes.isNotEmpty()) 1 else null,
                enableUserData = true,
            )
            .content
    }

    override suspend fun getPlaylistItems(
        playlistId: UUID,
        fields: List<ItemFields>?,
    ): List<BaseItemDto> =
        apiCall(emptyList(), "Failed to get items for playlist: $playlistId") { apiClient, userId ->
            PlaylistApi(apiClient)
                .getPlaylistItems(
                    playlistId = playlistId,
                    userId = userId,
                    fields = fields,
                    enableUserData = true,
                )
                .content
                .items
        }

    override suspend fun getPlaylistEntries(
        playlistId: UUID,
        fields: List<ItemFields>?,
    ): AfinityPlaylistContents {
        val baseUrl = getBaseUrl()
        val items = getPlaylistItems(playlistId, fields = fields ?: FieldSets.MINIMAL)
        return withContext(Dispatchers.Default) {
            yield()
            val entries = items.mapNotNull { dto ->
                when (dto.mediaType) {
                    MediaType.AUDIO -> runCatching {
                            PlaylistEntry.Audio(
                                playlistItemId = dto.playlistItemId,
                                track = dto.toAfinityTrack(baseUrl),
                            )
                        }
                            .getOrNull()
                    MediaType.VIDEO ->
                        dto.toAfinityItem(baseUrl)?.let {
                            PlaylistEntry.Video(playlistItemId = dto.playlistItemId, item = it)
                        }
                    else -> null
                }
            }
            AfinityPlaylistContents(
                entries = entries,
                audioCount = entries.count { it is PlaylistEntry.Audio },
                videoCount = entries.count { it is PlaylistEntry.Video },
            )
        }
    }

    override suspend fun movePlaylistItem(
        playlistId: UUID,
        playlistItemId: String,
        newIndex: Int,
    ): Boolean =
        apiCall(false, "Failed to move item $playlistItemId in playlist $playlistId") { apiClient, _
            ->
            PlaylistApi(apiClient)
                .moveItem(
                    playlistId = playlistId.toString(),
                    itemId = playlistItemId,
                    newIndex = newIndex,
                )
            true
        }

    override suspend fun getItem(itemId: UUID, fields: List<ItemFields>?): BaseItemDto? =
        apiCall(null, "Failed to get item with id: $itemId") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    ids = listOf(itemId),
                    fields = fields,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .firstOrNull()
        }

    override suspend fun getItemById(itemId: UUID): AfinityItem? =
        getItem(itemId, FieldSets.ITEM_DETAIL)?.toAfinityItem(getBaseUrl())

    override suspend fun getItemsByIds(
        ids: List<UUID>,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        apiCall(emptyList(), "Failed to batch-fetch items by ids") { apiClient, userId ->
            if (ids.isEmpty()) return@apiCall emptyList()
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    ids = ids,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { it.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getIntros(itemId: UUID): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get intros for item: $itemId") { apiClient, userId ->
            LibraryApi(apiClient)
                .getIntros(itemId = itemId, userId = userId)
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getAdditionalParts(itemId: UUID): List<AfinityItem> =
        apiCall(emptyList(), "[MultiPart] Exception in getAdditionalParts for item: $itemId") {
            apiClient,
            userId ->
            VideoApi(apiClient)
                .getAdditionalPart(itemId = itemId, userId = userId)
                .content
                .items
                ?.mapNotNull { baseItem -> baseItem.toAfinityItem(getBaseUrl()) } ?: emptyList()
        }

    override suspend fun getSimilarItems(
        itemId: UUID,
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get similar items") { apiClient, userId ->
            LibraryApi(apiClient)
                .getSimilarItems(
                    itemId = itemId,
                    userId = userId,
                    limit = limit,
                    fields = fields ?: FieldSets.SIMILAR_ITEMS,
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getMovies(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?,
        isFavorite: Boolean?,
        isLiked: Boolean?,
        fields: List<ItemFields>?,
    ): List<AfinityMovie> =
        apiCall(emptyList(), "Failed to get movies") { apiClient, userId ->
            val filters = buildList { if (isLiked == true) add(ItemFilter.LIKES) }
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    limit = limit,
                    startIndex = startIndex,
                    searchTerm = searchTerm,
                    sortBy = listOf(sortBy.toJellyfinSortBy()),
                    sortOrder =
                        if (sortDescending) listOf(SortOrder.DESCENDING)
                        else listOf(SortOrder.ASCENDING),
                    isPlayed = isPlayed,
                    isFavorite = isFavorite,
                    filters = filters.ifEmpty { null },
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityMovie(getBaseUrl()) }
        }

    override suspend fun getMoviesByGenre(
        genre: String,
        parentId: UUID?,
        limit: Int,
        shuffle: Boolean,
        fields: List<ItemFields>?,
    ): List<AfinityMovie> =
        apiCall(emptyList(), "Failed to get movies for genre: $genre") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    genres = listOf(genre),
                    limit = limit,
                    sortBy =
                        if (shuffle) listOf(ItemSortBy.RANDOM) else listOf(ItemSortBy.SORT_NAME),
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityMovie(getBaseUrl()) }
        }

    override suspend fun getShowsByGenre(
        genre: String,
        parentId: UUID?,
        limit: Int,
        shuffle: Boolean,
        fields: List<ItemFields>?,
    ): List<AfinityShow> =
        apiCall(emptyList(), "Failed to get shows for genre: $genre") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    recursive = true,
                    genres = listOf(genre),
                    limit = limit,
                    sortBy =
                        if (shuffle) listOf(ItemSortBy.RANDOM) else listOf(ItemSortBy.SORT_NAME),
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityShow(getBaseUrl()) }
        }

    override suspend fun getTopRatedByGenre(
        genre: String,
        type: GenreType,
        limit: Int,
    ): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get top-rated items for genre: $genre") { apiClient, userId
            ->
            val includeTypes =
                when (type) {
                    GenreType.MOVIE -> listOf(BaseItemKind.MOVIE)
                    GenreType.SHOW -> listOf(BaseItemKind.SERIES)
                }
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = includeTypes,
                    recursive = true,
                    genres = listOf(genre),
                    limit = limit,
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    imageTypes = listOf(ImageType.BACKDROP),
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { it.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getTopRatedByStudio(studioName: String, limit: Int): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get top-rated items for studio: $studioName") {
            apiClient,
            userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    studios = listOf(studioName),
                    limit = limit,
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    imageTypes = listOf(ImageType.BACKDROP),
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { it.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getShows(
        parentId: UUID?,
        sortBy: SortBy,
        sortDescending: Boolean,
        limit: Int?,
        startIndex: Int,
        searchTerm: String?,
        isPlayed: Boolean?,
        isFavorite: Boolean?,
        isLiked: Boolean?,
        fields: List<ItemFields>?,
    ): List<AfinityShow> =
        apiCall(emptyList(), "Failed to get shows") { apiClient, userId ->
            val filters = buildList { if (isLiked == true) add(ItemFilter.LIKES) }
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    recursive = true,
                    collapseBoxSetItems = false,
                    limit = limit,
                    startIndex = startIndex,
                    searchTerm = searchTerm,
                    sortBy = listOf(sortBy.toJellyfinSortBy()),
                    sortOrder =
                        if (sortDescending) listOf(SortOrder.DESCENDING)
                        else listOf(SortOrder.ASCENDING),
                    isPlayed = isPlayed,
                    isFavorite = isFavorite,
                    filters = filters.ifEmpty { null },
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityShow(getBaseUrl()) }
        }

    override suspend fun getSeasons(
        seriesId: UUID,
        fields: List<ItemFields>?,
    ): List<AfinitySeason> =
        apiCall(emptyList(), "Failed to get seasons") { apiClient, userId ->
            ShowApi(apiClient)
                .getSeasons(
                    seriesId = seriesId,
                    userId = userId,
                    fields = fields ?: FieldSets.SEASON_DETAIL,
                    enableImages = true,
                    enableUserData = true,
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinitySeason(getBaseUrl()) }
        }

    override suspend fun getEpisodes(
        seasonId: UUID,
        seriesId: UUID?,
        fields: List<ItemFields>?,
        startIndex: Int,
        limit: Int?,
    ): List<AfinityEpisode> =
        apiCall(emptyList(), "Failed to get episodes") { apiClient, userId ->
            val actualSeriesId =
                seriesId ?: getItem(seasonId)?.seriesId ?: return@apiCall emptyList()

            ShowApi(apiClient)
                .getEpisodes(
                    seriesId = actualSeriesId,
                    userId = userId,
                    seasonId = seasonId,
                    isMissing = null,
                    fields = fields ?: FieldSets.EPISODE_LIST,
                    enableImages = true,
                    enableUserData = true,
                    sortBy = ItemSortBy.SORT_NAME,
                    startIndex = startIndex.takeIf { it > 0 },
                    limit = limit,
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityEpisode(getBaseUrl()) }
                .distinctBy { it.id }
        }

    override suspend fun getSeriesEpisodes(
        seriesId: UUID,
        fields: List<ItemFields>?,
        limit: Int?,
    ): List<AfinityEpisode> =
        apiCall(emptyList(), "Failed to get series episodes") { apiClient, userId ->
            ShowApi(apiClient)
                .getEpisodes(
                    seriesId = seriesId,
                    userId = userId,
                    isMissing = null,
                    fields = fields ?: FieldSets.EPISODE_LIST,
                    enableImages = true,
                    enableUserData = true,
                    limit = limit,
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityEpisode(getBaseUrl()) }
                .distinctBy { it.id }
        }

    override suspend fun getFavoriteMovies(fields: List<ItemFields>?): List<AfinityMovie> =
        apiCall(emptyList(), "Failed to get favorite movies") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    isFavorite = true,
                    recursive = true,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityMovie(getBaseUrl()) }
        }

    override suspend fun getFavoriteShows(fields: List<ItemFields>?): List<AfinityShow> =
        apiCall(emptyList(), "Failed to get favorite shows") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    isFavorite = true,
                    recursive = true,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityShow(getBaseUrl()) }
        }

    override suspend fun getFavoriteEpisodes(fields: List<ItemFields>?): List<AfinityEpisode> =
        apiCall(emptyList(), "Failed to get favorite episodes") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.EPISODE),
                    isFavorite = true,
                    recursive = true,
                    fields = fields ?: FieldSets.EPISODE_LIST,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityEpisode(getBaseUrl()) }
        }

    override suspend fun getFavoriteSeasons(fields: List<ItemFields>?): List<AfinitySeason> =
        apiCall(emptyList(), "Failed to get favorite seasons") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.SEASON),
                    isFavorite = true,
                    recursive = true,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinitySeason(getBaseUrl()) }
        }

    override suspend fun getFavoriteBoxSets(fields: List<ItemFields>?): List<AfinityBoxSet> =
        apiCall(emptyList(), "Failed to get favorite box sets") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                    isFavorite = true,
                    recursive = true,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityBoxSet(getBaseUrl()) }
        }

    override suspend fun getFavoriteMedia(fields: List<ItemFields>?): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get favorite media") { apiClient, userId ->
            val baseUrl = getBaseUrl()
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    includeItemTypes =
                        listOf(
                            BaseItemKind.MOVIE,
                            BaseItemKind.SERIES,
                            BaseItemKind.SEASON,
                            BaseItemKind.EPISODE,
                            BaseItemKind.BOX_SET,
                        ),
                    isFavorite = true,
                    recursive = true,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityItem(baseUrl) }
        }

    override suspend fun getFavoritePeople(fields: List<ItemFields>?): List<AfinityPersonDetail> =
        apiCall(emptyList(), "Failed to get favorite people") { apiClient, userId ->
            PersonApi(apiClient)
                .getPersons(
                    userId = userId,
                    isFavorite = true,
                    fields = fields ?: listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                    enableImages = true,
                    enableUserData = true,
                    imageTypeLimit = 1,
                )
                .content
                .items
                .map { baseItem -> baseItem.toAfinityPersonDetail(getBaseUrl()) }
        }

    override suspend fun getNextUp(
        seriesId: UUID?,
        limit: Int,
        fields: List<ItemFields>?,
        enableResumable: Boolean,
    ): List<AfinityEpisode> =
        apiCall(emptyList(), "Failed to get next up") { apiClient, userId ->
            val sessionKeyAtStart = currentSessionKey()
            val nextUpItems =
                ShowApi(apiClient)
                    .getNextUp(
                        userId = userId,
                        seriesId = seriesId,
                        limit = limit,
                        fields = fields ?: FieldSets.EPISODE_LIST,
                        enableResumable = enableResumable,
                        enableImages = true,
                        enableUserData = true,
                        enableTotalRecordCount = false,
                        imageTypeLimit = 1,
                    )
                    .content
                    .items
                    .mapNotNull { baseItem -> baseItem.toAfinityEpisode(getBaseUrl()) }

            if (seriesId == null && currentSessionKey() == sessionKeyAtStart) {
                _nextUp.value = nextUpItems
            }

            nextUpItems
        }

    override suspend fun getUpcomingEpisodes(
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityEpisode> =
        apiCall(emptyList(), "Failed to get upcoming episodes") { apiClient, userId ->
            val now = java.time.LocalDateTime.now()
            ShowApi(apiClient)
                .getUpcomingEpisodes(
                    userId = userId,
                    limit = limit,
                    fields = fields ?: (FieldSets.EPISODE_LIST + ItemFields.MEDIA_SOURCES),
                    enableImages = true,
                    enableUserData = true,
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityEpisode(getBaseUrl()) }
                .filter { episode ->
                    episode.premiereDate?.isAfter(now) == true &&
                        (episode.missing || episode.sources.isEmpty())
                }
                .distinctBy { episode ->
                    "${episode.seriesName}_${episode.parentIndexNumber}_${episode.indexNumber}"
                }
        }

    override suspend fun getSpecialFeatures(itemId: UUID, userId: UUID): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get special features for item: $itemId") { apiClient, _ ->
            LibraryApi(apiClient)
                .getSpecialFeatures(itemId = itemId, userId = userId)
                .content
                .mapNotNull { baseItem ->
                    when (baseItem.type) {
                        BaseItemKind.EPISODE -> baseItem.toAfinityEpisode(getBaseUrl())
                        BaseItemKind.MOVIE -> baseItem.toAfinityMovie(getBaseUrl())
                        BaseItemKind.VIDEO -> baseItem.toAfinityVideo(getBaseUrl())
                        else -> {
                            Timber.d("Unsupported special feature type: ${baseItem.type}")
                            null
                        }
                    }
                }
        }

    override suspend fun searchItems(
        query: String,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        apiCall(emptyList(), "Failed to search items") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    searchTerm = query,
                    limit = limit,
                    includeItemTypes =
                        includeItemTypes
                            .mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            .ifEmpty { null },
                    fields = fields ?: FieldSets.SEARCH_RESULTS,
                    enableImages = true,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getPerson(personId: UUID): AfinityPersonDetail? =
        apiCall(null, "Failed to get person details for ID: $personId") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItem(itemId = personId, userId = userId)
                .content
                .toAfinityPersonDetail(getBaseUrl())
        }

    override suspend fun getPersonWithoutRefresh(
        personId: UUID,
        fields: List<ItemFields>?,
    ): AfinityPersonDetail? =
        apiCall(null, "Failed to get stored person for ID: $personId") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    ids = listOf(personId),
                    fields = fields ?: FieldSets.PERSON_DETAIL,
                    enableImages = true,
                    enableUserData = true,
                    imageTypeLimit = 1,
                    enableTotalRecordCount = false,
                    limit = 1,
                )
                .content
                .items
                .firstOrNull()
                ?.toAfinityPersonDetail(getBaseUrl())
        }

    override suspend fun getPersonItems(
        personId: UUID,
        includeItemTypes: List<String>,
        fields: List<ItemFields>?,
        personTypes: List<String>,
    ): List<AfinityItem> =
        apiCall(emptyList(), "Failed to get person items for ID: $personId") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItems(
                    userId = userId,
                    personIds = listOf(personId),
                    personTypes = personTypes.ifEmpty { null },
                    includeItemTypes =
                        includeItemTypes
                            .mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            .ifEmpty { null },
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes = listOf(ImageType.PRIMARY),
                    enableUserData = true,
                    recursive = true,
                    limit = 150,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityItem(getBaseUrl()) }
        }

    override suspend fun getSimilarMovies(
        movieId: UUID,
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityMovie> =
        apiCall(emptyList(), "Failed to get similar movies for ID: $movieId") { apiClient, userId ->
            LibraryApi(apiClient)
                .getSimilarItems(
                    itemId = movieId,
                    userId = userId,
                    limit = limit,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                )
                .content
                .items
                .filter { it.id != movieId }
                .map { baseItem -> baseItem.toAfinityMovie(getBaseUrl()) }
        }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                Timber.d(
                    "Attempting to load trickplay tile: itemId=$itemId, width=$width, index=$index"
                )

                val download = databaseRepository.getDownloadByItemId(itemId)
                val volumeId =
                    download?.storageVolumeId ?: StorageLocationProvider.PRIMARY_VOLUME_ID
                val baseDir =
                    storageLocationProvider.resolveBaseDir(volumeId)
                        ?: storageLocationProvider.primaryBaseDir()
                val itemDir = File(baseDir, download?.folderPath ?: itemId.toString())
                val trickplayFile = File(itemDir, "trickplay/$width/$index.jpg")

                Timber.d("Looking for trickplay file: ${trickplayFile.absolutePath}")
                Timber.d("File exists: ${trickplayFile.exists()}")

                if (trickplayFile.exists()) {
                    Timber.i(
                        "Loading trickplay tile from local storage: $width/$index.jpg (${trickplayFile.length()} bytes)"
                    )
                    return@withContext trickplayFile.readBytes()
                } else {
                    Timber.d("Trickplay file not found locally, trying API")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load trickplay from local storage, falling back to API")
            }

            return@withContext try {
                Timber.d("Fetching trickplay tile from API: $width/$index")
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val trickPlayApi = TrickPlayApi(apiClient)
                val response = trickPlayApi.getTrickplayTileImage(itemId, width, index)
                Timber.d("Fetched trickplay tile from API: ${response.content.size} bytes")
                response.content
            } catch (e: Exception) {
                Timber.w(e, "Failed to get trickplay data for tile $index")
                null
            }
        }

    override suspend fun getGenres(
        parentId: UUID?,
        limit: Int?,
        includeItemTypes: List<String>,
    ): List<String> =
        apiCall(emptyList(), "Failed to get genres") { apiClient, userId ->
            GenreApi(apiClient)
                .getGenres(
                    userId = userId,
                    parentId = parentId,
                    limit = limit,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    enableImages = false,
                    enableTotalRecordCount = false,
                    includeItemTypes =
                        includeItemTypes
                            .mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            .ifEmpty { null },
                )
                .content
                .items
                .mapNotNull { genreDto -> genreDto.name?.takeIf { it.isNotBlank() } }
        }

    override suspend fun getStudios(
        includeItemTypes: List<String>,
        parentId: UUID?,
        limit: Int?,
        requireImages: Boolean,
        minItemCount: Int,
    ): List<AfinityStudio> =
        getStudiosResult(includeItemTypes, parentId, limit, requireImages, minItemCount)
            .getOrElse { e ->
                if (e !is NoActiveSessionException) Timber.e(e, "Failed to get studios")
                emptyList()
            }

    override suspend fun getStudiosResult(
        includeItemTypes: List<String>,
        parentId: UUID?,
        limit: Int?,
        requireImages: Boolean,
        minItemCount: Int,
    ): Result<List<AfinityStudio>> = apiInvoker.apiResult { apiClient, userId ->
        val response =
            StudioApi(apiClient)
                .getStudios(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes =
                        includeItemTypes
                            .mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    Timber.w("Unknown item type dropped from studio query: $it")
                                    null
                                }
                            }
                            .ifEmpty { null },
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes = listOf(ImageType.THUMB),
                    enableTotalRecordCount = false,
                )

        Timber.d("Fetched ${response.content.items.size} studios server-wide")

        response.content.items
            .mapNotNull { studioDto ->
                val id: UUID = studioDto.id
                val name = studioDto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val childCount = studioDto.childCount ?: 0
                val thumbImageUrl =
                    studioDto.imageTags?.get(ImageType.THUMB)?.let { tag ->
                        getBaseUrl()
                            .toUri()
                            .buildUpon()
                            .appendEncodedPath("Items/$id/Images/Thumb")
                            .appendQueryParameter("tag", tag)
                            .build()
                            .toString()
                    }
                AfinityStudio(
                    id = id,
                    name = name,
                    primaryImageUrl = thumbImageUrl,
                    itemCount = childCount,
                )
            }
            .filter {
                it.itemCount >= minItemCount && (!requireImages || it.primaryImageUrl != null)
            }
            .sortedByDescending { it.itemCount }
            .take(limit ?: 15)
            .also { Timber.d("Returning ${it.size} studios after filtering") }
    }

    override suspend fun getBoxSetsForSpotlight(
        minChildCount: Int,
        maxBoxSets: Int,
    ): List<Pair<AfinityBoxSet, List<AfinityItem>>> =
        apiCall(emptyList(), "Failed to get boxsets for spotlight") { apiClient, userId ->
            val libraryApi = LibraryApi(apiClient)
            val baseUrl = getBaseUrl()

            val boxSetsResponse =
                libraryApi.getItems(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                    recursive = true,
                    fields = FieldSets.MEDIA_ITEM_CARDS,
                    enableImages = true,
                    enableUserData = false,
                    enableTotalRecordCount = false,
                )

            val qualifying =
                boxSetsResponse.content.items
                    .filter { (it.childCount ?: 0) >= minChildCount }
                    .shuffled()
                    .take(maxBoxSets)

            Timber.d(
                "BoxSet spotlight: ${qualifying.size} qualifying sets (min $minChildCount children)"
            )

            val semaphore = Semaphore(5)
            coroutineScope {
                qualifying
                    .map { boxSetDto ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val childrenResponse =
                                        libraryApi.getItems(
                                            userId = userId,
                                            parentId = boxSetDto.id,
                                            recursive = false,
                                            fields = FieldSets.MEDIA_ITEM_CARDS,
                                            enableImages = true,
                                            enableUserData = false,
                                            sortBy = listOf(ItemSortBy.PRODUCTION_YEAR),
                                            enableTotalRecordCount = false,
                                        )
                                    val children =
                                        childrenResponse.content.items.mapNotNull {
                                            it.toAfinityItem(baseUrl)
                                        }
                                    if (children.size >= minChildCount) {
                                        boxSetDto.toAfinityBoxSet(baseUrl) to children
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    Timber.w(
                                        e,
                                        "Failed to fetch children for BoxSet spotlight: ${boxSetDto.name}",
                                    )
                                    null
                                }
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
            }
        }

    override suspend fun getBoxSetsContaining(
        itemId: UUID,
        fields: List<ItemFields>?,
    ): List<AfinityBoxSet> =
        apiCall(emptyList(), "Failed to get BoxSets containing item $itemId") { apiClient, userId ->
            LibraryApi(apiClient)
                .getItemCollections(
                    itemId = itemId,
                    userId = userId,
                    fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                )
                .content
                .items
                .map { boxSetDto -> boxSetDto.toAfinityBoxSet(getBaseUrl()) }
        }

    override fun getLibrariesFlow(): Flow<List<AfinityCollection>> = libraries

    override fun getContinueWatchingFlow(): Flow<List<AfinityItem>> = continueWatching

    override fun getNextUpFlow(): Flow<List<AfinityEpisode>> = nextUp

    private fun SortBy.toJellyfinSortBy(): ItemSortBy {
        return when (this) {
            SortBy.NAME -> ItemSortBy.SORT_NAME
            SortBy.IMDB_RATING -> ItemSortBy.COMMUNITY_RATING
            SortBy.PARENTAL_RATING -> ItemSortBy.OFFICIAL_RATING
            SortBy.DATE_ADDED -> ItemSortBy.DATE_CREATED
            SortBy.DATE_PLAYED -> ItemSortBy.DATE_PLAYED
            SortBy.RELEASE_DATE -> ItemSortBy.PREMIERE_DATE
            SortBy.SERIES_DATE_PLAYED -> ItemSortBy.SERIES_SORT_NAME
            SortBy.DATE_LAST_CONTENT_ADDED -> ItemSortBy.DATE_LAST_CONTENT_ADDED
            SortBy.RANDOM -> ItemSortBy.RANDOM
        }
    }

    override suspend fun getMdbListRatings(tmdbId: String, isMovie: Boolean): MdbListRatingsResult =
        withContext(Dispatchers.IO) {
            try {
                val serverId =
                    sessionManager.currentSession.value?.serverId
                        ?: return@withContext MdbListRatingsResult()
                val userId =
                    sessionManager.currentSession.value?.userId?.toString()
                        ?: return@withContext MdbListRatingsResult()

                val apiKey = securePreferencesRepository.getMdbListApiKey(serverId, userId)
                if (apiKey.isNullOrBlank()) {
                    return@withContext MdbListRatingsResult()
                }

                val type = if (isMovie) "movie" else "show"
                val result = mdbListApiService.getRatings(type, tmdbId, apiKey)
                val keywords =
                    (result.keywords + result.keyword).map { it.name.lowercase() }.toSet()

                MdbListRatingsResult(
                    ratings = result.ratings,
                    badges =
                        MdbListRatingBadges(
                            certifiedFresh = "certified-fresh" in keywords,
                            verifiedHot = "certified-hot" in keywords || "verified-hot" in keywords,
                        ),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to get MDBList ratings for TMDB ID: $tmdbId")
                MdbListRatingsResult()
            }
        }

    override suspend fun getOmdbDetails(imdbId: String): OmdbApiResult? =
        withContext(Dispatchers.IO) {
            try {
                val serverId =
                    sessionManager.currentSession.value?.serverId ?: return@withContext null
                val userId =
                    sessionManager.currentSession.value?.userId?.toString()
                        ?: return@withContext null
                val apiKey = securePreferencesRepository.getOmdbApiKey(serverId, userId)

                if (apiKey.isNullOrBlank()) {
                    return@withContext null
                }

                val result = omdbApiService.getTitleDetails(imdbId = imdbId, apiKey = apiKey)

                if (result.response == "True") {
                    result
                } else {
                    Timber.w("OMDb API Error: ${result.error}")
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get OMDb details for IMDb ID: $imdbId")
                null
            }
        }

    private suspend fun episodesForSelection(seriesId: UUID): List<AfinityEpisode> =
        apiCall(emptyList(), "Failed to scan episodes for series $seriesId") { apiClient, userId ->
            ShowApi(apiClient)
                .getEpisodes(
                    seriesId = seriesId,
                    userId = userId,
                    isMissing = false,
                    fields = emptyList(),
                    enableImages = false,
                    enableUserData = true,
                    sortBy = ItemSortBy.SORT_NAME,
                )
                .content
                .items
                .mapNotNull { baseItem -> baseItem.toAfinityEpisode(getBaseUrl()) }
                .distinctBy { it.id }
        }

    private suspend fun withPlaybackSources(episode: AfinityEpisode): AfinityEpisode =
        try {
            getItem(episode.id, fields = FieldSets.PLAYABLE_EPISODE)?.toAfinityEpisode(getBaseUrl())
                ?: episode
        } catch (e: Exception) {
            Timber.w(e, "Failed to hydrate playback sources for episode ${episode.id}")
            episode
        }

    override suspend fun getEpisodeToPlay(seriesId: UUID): AfinityEpisode? {
        return try {
            Timber.d("Getting episode to play for series: $seriesId")
            try {
                val nextUpEpisodes =
                    getNextUp(
                        seriesId = seriesId,
                        limit = 1,
                        fields = FieldSets.PLAYABLE_EPISODE,
                        enableResumable = true,
                    )
                val playableNextUp = nextUpEpisodes.filter {
                    !it.missing && it.parentIndexNumber != 0
                }
                if (playableNextUp.isNotEmpty()) {
                    Timber.d("Found NextUp episode: ${playableNextUp.first().name}")
                    return playableNextUp.first()
                }
            } catch (e: Exception) {
                Timber.w(e, "NextUp API failed")
            }
            Timber.d("Fallback to manual logic")
            val episodesBySeason =
                episodesForSelection(seriesId)
                    .groupBy { it.parentIndexNumber }
                    .toSortedMap(compareBy({ it == 0 }, { it }))
                    .mapValues { (_, episodes) -> episodes.sortedBy { it.indexNumber } }

            var firstEpisodeOfSeries: AfinityEpisode? = null
            for ((_, episodes) in episodesBySeason) {
                if (episodes.isEmpty()) continue
                if (firstEpisodeOfSeries == null) firstEpisodeOfSeries = episodes.firstOrNull()
                val nextEpisode = episodes.firstOrNull { !it.played }
                if (nextEpisode != null) return withPlaybackSources(nextEpisode)
            }
            return firstEpisodeOfSeries?.let { withPlaybackSources(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to determine episode to play for series: $seriesId")
            null
        }
    }

    override suspend fun getEpisodeToPlayForSeason(
        seasonId: UUID,
        seriesId: UUID,
    ): AfinityEpisode? {
        return try {
            Timber.d("Getting episode to play for season: $seasonId")
            val episodes = getEpisodes(seasonId, seriesId, fields = FieldSets.PLAYABLE_EPISODE)
            val playableEpisodes = episodes.filter { !it.missing }
            if (playableEpisodes.isEmpty()) return null

            playableEpisodes.firstOrNull { !it.played } ?: playableEpisodes.firstOrNull()
        } catch (e: Exception) {
            Timber.e(e, "Failed to determine episode to play for season: $seasonId")
            null
        }
    }

    override suspend fun getSeriesNextEpisode(seriesId: UUID): AfinityEpisode? {
        return try {
            getNextUp(seriesId, limit = 1).firstOrNull()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get next episode for series: $seriesId")
            null
        }
    }
}
