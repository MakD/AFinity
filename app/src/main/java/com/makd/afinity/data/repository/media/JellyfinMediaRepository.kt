package com.makd.afinity.data.repository.media

import android.content.Context
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityBoxSet
import com.makd.afinity.data.models.extensions.toAfinityCollection
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityImages
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.extensions.toAfinityShow
import com.makd.afinity.data.models.extensions.toAfinityVideo
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.toAfinityExternalUrl
import com.makd.afinity.data.repository.FieldSets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.GenresApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.StudiosApi
import org.jellyfin.sdk.api.operations.TrickplayApi
import org.jellyfin.sdk.api.operations.TvShowsApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinMediaRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
    private val boxSetCache: BoxSetCache,
) : MediaRepository {
    override suspend fun refreshItemUserData(
        itemId: UUID,
        fields: List<ItemFields>?,
    ): AfinityItem? {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null
                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getItems(
                        userId = userId,
                        ids = listOf(itemId),
                        fields = fields ?: FieldSets.REFRESH_USER_DATA,
                        enableImages = true,
                        enableUserData = true,
                    )

                val freshBaseItemDto = response.content?.items?.firstOrNull()
                val freshItem = freshBaseItemDto?.toAfinityItem(getBaseUrl())

                if (freshItem != null) {
                    updateItemInCache(_continueWatching, freshItem)
                    updateItemInCache(_latestMedia, freshItem)

                    if (freshItem is AfinityEpisode) {
                        updateEpisodeInNextUpCache(freshItem)
                        freshItem.seriesId?.let { seriesId ->
                            launch {
                                try {
                                    kotlinx.coroutines.delay(500)

                                    val seriesResponse =
                                        itemsApi.getItems(
                                            userId = userId,
                                            ids = listOf(seriesId),
                                            fields = FieldSets.MEDIA_ITEM_CARDS,
                                            enableImages = true,
                                            enableUserData = true,
                                        )
                                    val seriesItem =
                                        seriesResponse.content
                                            ?.items
                                            ?.firstOrNull()
                                            ?.toAfinityItem(getBaseUrl())

                                    if (seriesItem != null) {
                                        updateItemInCache(_latestMedia, seriesItem)
                                    }
                                } catch (e: Exception) {
                                    Timber.w("Failed to background sync parent series")
                                }
                            }
                        }
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

                (updatedItem.playbackPositionTicks.toFloat() / updatedItem.runtimeTicks * 100f) >
                    0f && !updatedItem.played && cache == _continueWatching -> {
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
            if (updatedItem is AfinityEpisode && updatedItem.seriesId != null) {
                val parentSeriesIndex =
                    newList.indexOfFirst { it is AfinityShow && it.id == updatedItem.seriesId }

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
                updatedEpisode.played -> {
                    if (existingIndex != -1) {
                        newList.removeAt(existingIndex)
                        Timber.d("Removed completed episode from next up: ${updatedEpisode.name}")
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

    override suspend fun invalidateContinueWatchingCache() {
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId() ?: return@withContext
                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getResumeItems(
                        userId = userId,
                        limit = 12,
                        fields = FieldSets.CACHE_CONTINUE_WATCHING,
                        enableImages = true,
                        enableUserData = true,
                    )

                val continueWatchingItems =
                    response.content?.items?.mapNotNull { baseItemDto ->
                        baseItemDto.toAfinityItem(getBaseUrl())
                    } ?: emptyList()

                _continueWatching.value = continueWatchingItems
                Timber.d("Full refresh of continue watching cache completed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh continue watching cache")
            }
        }
    }

    override suspend fun invalidateLatestMediaCache() {
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId() ?: return@withContext
                val userLibraryApi = UserLibraryApi(apiClient)
                val response =
                    userLibraryApi.getLatestMedia(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        limit = 15,
                        isPlayed = false,
                        fields = FieldSets.CACHE_LATEST_MEDIA,
                        enableImages = true,
                        enableUserData = true,
                    )

                val latestItems =
                    response.content?.mapNotNull { baseItemDto ->
                        baseItemDto.toAfinityItem(getBaseUrl())
                    } ?: emptyList()

                _latestMedia.value = latestItems
                Timber.d("Full refresh of latest media cache completed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh latest media cache")
            }
        }
    }

    override suspend fun invalidateNextUpCache() {
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext
                val userId = getCurrentUserId() ?: return@withContext
                val tvShowsApi = TvShowsApi(apiClient)
                val response =
                    tvShowsApi.getNextUp(
                        userId = userId,
                        limit = 16,
                        fields = FieldSets.CACHE_NEXT_UP,
                        enableImages = true,
                        enableUserData = true,
                        enableResumable = false,
                        enableRewatching = false,
                    )

                val nextUpEpisodes =
                    response.content?.items?.mapNotNull { baseItemDto ->
                        baseItemDto.toAfinityEpisode(getBaseUrl())
                    } ?: emptyList()

                _nextUp.value = nextUpEpisodes
                Timber.d("Full refresh of next up cache completed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh next up cache")
            }
        }
    }

    override suspend fun invalidateAllCaches() {
        Timber.d("Full cache invalidation requested - refreshing all caches")
        invalidateContinueWatchingCache()
        invalidateLatestMediaCache()
        invalidateNextUpCache()
    }

    override suspend fun invalidateItemCache(itemId: UUID) {
        refreshItemUserData(itemId)
    }

    private val _libraries = MutableStateFlow<List<AfinityCollection>>(emptyList())
    override val libraries: Flow<List<AfinityCollection>> = _libraries.asStateFlow()

    private val _latestMedia = MutableStateFlow<List<AfinityItem>>(emptyList())
    override val latestMedia: Flow<List<AfinityItem>> = _latestMedia.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<AfinityItem>>(emptyList())
    override val continueWatching: Flow<List<AfinityItem>> = _continueWatching.asStateFlow()

    private val _nextUp = MutableStateFlow<List<AfinityEpisode>>(emptyList())
    override val nextUp: Flow<List<AfinityEpisode>> = _nextUp.asStateFlow()

    private suspend fun getCurrentUserId(): UUID? =
        withContext(Dispatchers.IO) {
            return@withContext sessionManager.currentSession.value?.userId
        }

    private fun getBaseUrl(): String {
        return sessionManager.currentSession.value?.serverUrl ?: ""
    }

    override suspend fun getLibraries(): List<AfinityCollection> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val userViewsApi = UserViewsApi(apiClient)
                val response = userViewsApi.getUserViews(userId = userId)

                val libraries =
                    response.content
                        ?.items
                        ?.filter {
                            it.collectionType != org.jellyfin.sdk.model.api.CollectionType.LIVETV
                        }
                        ?.mapNotNull { baseItemDto ->
                            try {
                                baseItemDto.toAfinityCollection(getBaseUrl())
                            } catch (e: Exception) {
                                Timber.w(
                                    e,
                                    "Failed to convert item to collection: ${baseItemDto.name}",
                                )
                                null
                            }
                        } ?: emptyList()

                _libraries.value = libraries
                Timber.d("Successfully retrieved ${libraries.size} libraries via UserViews API")
                libraries
            } catch (e: Exception) {
                Timber.e(e, "Failed to get libraries")
                emptyList()
            }
        }

    override suspend fun getLatestMedia(
        parentId: UUID?,
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val userLibraryApi = UserLibraryApi(apiClient)
                val response =
                    userLibraryApi.getLatestMedia(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes =
                            listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
                        limit = limit,
                        isPlayed = false,
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                        groupItems = true,
                    )

                val latestItems =
                    response.content?.mapNotNull { baseItemDto ->
                        baseItemDto.toAfinityItem(getBaseUrl())
                    } ?: emptyList()

                _latestMedia.value = latestItems
                latestItems
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get latest media")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting latest media")
                emptyList()
            }
        }

    override suspend fun getContinueWatching(
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getResumeItems(
                        userId = userId,
                        limit = limit,
                        fields = fields ?: FieldSets.CONTINUE_WATCHING,
                        enableImages = true,
                        enableUserData = true,
                    )

                val continueWatchingItems =
                    response.content?.items?.mapNotNull { baseItemDto ->
                        baseItemDto.toAfinityItem(getBaseUrl())
                    } ?: emptyList()

                _continueWatching.value = continueWatchingItems
                continueWatchingItems
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get continue watching")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting continue watching")
                emptyList()
            }
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
        isLiked: Boolean?,
        nameStartsWith: String?,
        fields: List<ItemFields>?,
        imageTypes: List<String>,
        hasOverview: Boolean?,
        studios: List<String>,
    ): BaseItemDtoQueryResult =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient()
                        ?: return@withContext BaseItemDtoQueryResult(
                            items = emptyList(),
                            totalRecordCount = 0,
                            startIndex = 0,
                        )
                val userId =
                    getCurrentUserId()
                        ?: return@withContext BaseItemDtoQueryResult(
                            items = emptyList(),
                            totalRecordCount = 0,
                            startIndex = 0,
                        )

                val itemsApi = ItemsApi(apiClient)

                val filters = buildList { if (isLiked == true) add(ItemFilter.LIKES) }

                val response =
                    itemsApi.getItems(
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
                            includeItemTypes.mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            },
                        recursive =
                            if (parentId == null) true
                            else if (
                                includeItemTypes.size == 1 && includeItemTypes.contains("SERIES")
                            )
                                true
                            else if (searchTerm != null) true else null,
                        collapseBoxSetItems =
                            if (includeItemTypes.size == 1 && includeItemTypes.contains("SERIES"))
                                false
                            else null,
                        genres = genres,
                        years = years,
                        isFavorite = isFavorite,
                        isPlayed = isPlayed,
                        filters = filters.ifEmpty { null },
                        nameStartsWith = nameStartsWith,
                        studios = studios.ifEmpty { null },
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
                        hasOverview = hasOverview,
                        enableImages = true,
                        enableUserData = true,
                    )
                response.content
                    ?: BaseItemDtoQueryResult(
                        items = emptyList(),
                        totalRecordCount = 0,
                        startIndex = 0,
                    )
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get items")
                BaseItemDtoQueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting items")
                BaseItemDtoQueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
            }
        }

    override suspend fun getItem(itemId: UUID, fields: List<ItemFields>?): BaseItemDto? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null

                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getItems(
                        userId = userId,
                        ids = listOf(itemId),
                        fields = fields ?: FieldSets.ITEM_DETAIL,
                        enableImages = true,
                        enableUserData = true,
                    )
                response.content?.items?.firstOrNull()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get item with id: $itemId")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting item with id: $itemId")
                null
            }
        }

    override suspend fun getSimilarItems(
        itemId: UUID,
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val libraryApi = LibraryApi(apiClient)
                val response =
                    libraryApi.getSimilarItems(
                        itemId = itemId,
                        userId = userId,
                        limit = limit,
                        fields = fields ?: FieldSets.SIMILAR_ITEMS,
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityItem(getBaseUrl())
                } ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get similar items")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting similar items")
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
        isPlayed: Boolean?,
        isFavorite: Boolean?,
        isLiked: Boolean?,
        fields: List<ItemFields>?,
    ): List<AfinityMovie> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)

                val filters = buildList { if (isLiked == true) add(ItemFilter.LIKES) }

                val response =
                    itemsApi.getItems(
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
                    )

                response.content
                    ?.items
                    ?.filter { it.type == BaseItemKind.MOVIE }
                    ?.mapNotNull { baseItem -> baseItem.toAfinityMovie(getBaseUrl()) }
                    ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get movies")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting movies")
                emptyList()
            }
        }

    override suspend fun getMoviesByGenre(
        genre: String,
        parentId: UUID?,
        limit: Int,
        shuffle: Boolean,
        fields: List<ItemFields>?,
    ): List<AfinityMovie> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        genres = listOf(genre),
                        limit = limit,
                        sortBy =
                            if (shuffle) listOf(ItemSortBy.RANDOM)
                            else listOf(ItemSortBy.SORT_NAME),
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                    )

                response.content
                    ?.items
                    ?.filter { it.type == BaseItemKind.MOVIE }
                    ?.mapNotNull { baseItem -> baseItem.toAfinityMovie(getBaseUrl()) }
                    ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get movies for genre: $genre")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting movies for genre: $genre")
                emptyList()
            }
        }

    override suspend fun getShowsByGenre(
        genre: String,
        parentId: UUID?,
        limit: Int,
        shuffle: Boolean,
        fields: List<ItemFields>?,
    ): List<AfinityShow> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.SERIES),
                        recursive = true,
                        genres = listOf(genre),
                        limit = limit,
                        sortBy =
                            if (shuffle) listOf(ItemSortBy.RANDOM)
                            else listOf(ItemSortBy.SORT_NAME),
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                    )

                response.content
                    ?.items
                    ?.filter { it.type == BaseItemKind.SERIES }
                    ?.mapNotNull { baseItem -> baseItem.toAfinityShow(getBaseUrl()) } ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get shows for genre: $genre")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting shows for genre: $genre")
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
        isPlayed: Boolean?,
        isFavorite: Boolean?,
        isLiked: Boolean?,
        fields: List<ItemFields>?,
    ): List<AfinityShow> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)

                val filters = buildList { if (isLiked == true) add(ItemFilter.LIKES) }

                val response =
                    itemsApi.getItems(
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
                    )

                response.content
                    ?.items
                    ?.filter { it.type == BaseItemKind.SERIES }
                    ?.mapNotNull { baseItem -> baseItem.toAfinityShow(getBaseUrl()) } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get shows")
                emptyList()
            }
        }

    override suspend fun getSeasons(
        seriesId: UUID,
        fields: List<ItemFields>?,
    ): List<AfinitySeason> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getItems(
                        userId = userId,
                        parentId = seriesId,
                        includeItemTypes = listOf(BaseItemKind.SEASON),
                        fields = fields ?: FieldSets.SEASON_DETAIL,
                        enableImages = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinitySeason(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get seasons")
                emptyList()
            }
        }

    override suspend fun getEpisodes(
        seasonId: UUID,
        seriesId: UUID?,
        fields: List<ItemFields>?,
    ): List<AfinityEpisode> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val actualSeriesId =
                    seriesId
                        ?: run {
                            val seasonItem = getItem(seasonId)
                            seasonItem?.seriesId ?: return@withContext emptyList()
                        }

                val tvShowsApi = TvShowsApi(apiClient)
                val response =
                    tvShowsApi.getEpisodes(
                        seriesId = actualSeriesId,
                        userId = userId,
                        seasonId = seasonId,
                        isMissing = false,
                        fields = fields ?: FieldSets.EPISODE_LIST,
                        enableImages = true,
                        enableUserData = true,
                        sortBy = ItemSortBy.SORT_NAME,
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityEpisode(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get episodes")
                emptyList()
            }
        }

    override suspend fun getFavoriteMovies(fields: List<ItemFields>?): List<AfinityMovie> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        isFavorite = true,
                        recursive = true,
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityMovie(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get favorite episodes")
                emptyList()
            }
        }

    override suspend fun getFavoriteShows(fields: List<ItemFields>?): List<AfinityShow> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.SERIES),
                        isFavorite = true,
                        recursive = true,
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    )

                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityShow(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get favorite episodes")
                emptyList()
            }
        }

    override suspend fun getFavoriteEpisodes(fields: List<ItemFields>?): List<AfinityEpisode> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        isFavorite = true,
                        recursive = true,
                        fields = fields ?: FieldSets.EPISODE_LIST,
                        enableImages = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    )

                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityEpisode(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get favorite episodes")
                emptyList()
            }
        }

    override suspend fun getFavoriteSeasons(fields: List<ItemFields>?): List<AfinitySeason> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.SEASON),
                        isFavorite = true,
                        recursive = true,
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinitySeason(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get favorite seasons")
                emptyList()
            }
        }

    override suspend fun getFavoriteBoxSets(fields: List<ItemFields>?): List<AfinityBoxSet> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val itemsApi = ItemsApi(apiClient)

                val response =
                    itemsApi.getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.BOX_SET),
                        isFavorite = true,
                        recursive = true,
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityBoxSet(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get favorite box sets")
                emptyList()
            }
        }

    override suspend fun getNextUp(
        seriesId: UUID?,
        limit: Int,
        fields: List<ItemFields>?,
        enableResumable: Boolean,
    ): List<AfinityEpisode> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val tvShowsApi = TvShowsApi(apiClient)
                val response =
                    tvShowsApi.getNextUp(
                        userId = userId,
                        seriesId = seriesId,
                        limit = limit,
                        fields = fields ?: FieldSets.EPISODE_LIST,
                        enableResumable = enableResumable,
                        enableImages = true,
                        enableUserData = true,
                    )
                val nextUpItems =
                    response.content?.items?.mapNotNull { baseItem ->
                        baseItem.toAfinityEpisode(getBaseUrl())
                    } ?: emptyList()

                if (seriesId == null) {
                    _nextUp.value = nextUpItems
                }

                nextUpItems
            } catch (e: Exception) {
                Timber.e(e, "Failed to get next up")
                emptyList()
            }
        }

    override suspend fun getSpecialFeatures(itemId: UUID, userId: UUID): List<AfinityItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userLibraryApi = UserLibraryApi(apiClient)
                val response = userLibraryApi.getSpecialFeatures(itemId = itemId, userId = userId)

                Timber.d("Special features API response: ${response.content?.size ?: 0} items")
                response.content?.forEach { item ->
                    Timber.d(
                        "Special feature: name=${item.name}, type=${item.type}, seriesId=${item.seriesId}, seasonId=${item.seasonId}"
                    )
                }

                response.content?.mapNotNull { baseItem ->
                    val result =
                        when (baseItem.type) {
                            BaseItemKind.EPISODE -> baseItem.toAfinityEpisode(getBaseUrl())
                            BaseItemKind.MOVIE -> baseItem.toAfinityMovie(getBaseUrl())
                            BaseItemKind.VIDEO -> baseItem.toAfinityVideo(getBaseUrl())
                            else -> {
                                Timber.d("Unsupported special feature type: ${baseItem.type}")
                                null
                            }
                        }
                    if (result == null) {
                        Timber.d(
                            "Failed to convert special feature: ${baseItem.name} (${baseItem.type})"
                        )
                    }
                    result
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get special features for item: $itemId")
                emptyList()
            }
        }

    override suspend fun searchItems(
        query: String,
        limit: Int,
        includeItemTypes: List<String>,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getItems(
                        userId = userId,
                        searchTerm = query,
                        limit = limit,
                        includeItemTypes =
                            includeItemTypes.mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            },
                        fields = fields ?: FieldSets.SEARCH_RESULTS,
                        enableImages = true,
                        enableUserData = true,
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityItem(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to search items")
                emptyList()
            }
        }

    override suspend fun getPerson(personId: UUID): AfinityPersonDetail? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val userId = getCurrentUserId() ?: return@withContext null

                val userLibraryApi = UserLibraryApi(apiClient)
                val response = userLibraryApi.getItem(itemId = personId, userId = userId)

                response.content?.let { baseItemDto ->
                    AfinityPersonDetail(
                        id = baseItemDto.id,
                        name = baseItemDto.name.orEmpty(),
                        overview = baseItemDto.overview.orEmpty(),
                        images = baseItemDto.toAfinityImages(getBaseUrl()),
                        premiereDate = baseItemDto.premiereDate,
                        productionLocations = baseItemDto.productionLocations ?: emptyList(),
                        externalUrls = baseItemDto.externalUrls?.map { it.toAfinityExternalUrl() },
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get person details for ID: $personId")
                null
            }
        }

    override suspend fun getPersonItems(
        personId: UUID,
        includeItemTypes: List<String>,
        fields: List<ItemFields>?,
    ): List<AfinityItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getItems(
                        userId = userId,
                        personIds = listOf(personId),
                        includeItemTypes =
                            includeItemTypes.mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            },
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        imageTypeLimit = 1,
                        enableImageTypes = listOf(ImageType.PRIMARY),
                        enableUserData = true,
                        recursive = true,
                        limit = 150,
                    )
                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityItem(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get person items for ID: $personId")
                emptyList()
            }
        }

    override suspend fun getMoviesWithPeople(
        startIndex: Int,
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityMovie> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val itemsApi = ItemsApi(apiClient)
                val response =
                    itemsApi.getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        fields =
                            fields
                                ?: listOf(
                                    ItemFields.PEOPLE,
                                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                                    ItemFields.DATE_CREATED,
                                    ItemFields.OVERVIEW,
                                ),
                        startIndex = startIndex,
                        limit = limit,
                        enableImages = true,
                        recursive = true,
                    )

                response.content?.items?.mapNotNull { baseItem ->
                    baseItem.toAfinityMovie(getBaseUrl())
                } ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get movies with people at index: $startIndex")
                emptyList()
            }
        }

    override suspend fun getSimilarMovies(
        movieId: UUID,
        limit: Int,
        fields: List<ItemFields>?,
    ): List<AfinityMovie> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val libraryApi = LibraryApi(apiClient)
                val response =
                    libraryApi.getSimilarItems(
                        itemId = movieId,
                        userId = userId,
                        limit = limit,
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                    )

                response.content
                    ?.items
                    ?.filter { it.id != movieId }
                    ?.mapNotNull { baseItem -> baseItem.toAfinityMovie(getBaseUrl()) }
                    ?.shuffled() ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get similar movies for ID: $movieId")
                emptyList()
            }
        }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val externalFilesDir = context.getExternalFilesDir(null)
                Timber.d(
                    "Attempting to load trickplay tile: itemId=$itemId, width=$width, index=$index"
                )

                if (externalFilesDir != null) {
                    val downloadDir = File(externalFilesDir, "AFinity/Downloads")
                    val itemDir = File(downloadDir, itemId.toString())
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
                } else {
                    Timber.w("External files directory is null")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load trickplay from local storage, falling back to API")
            }

            return@withContext try {
                Timber.d("Fetching trickplay tile from API: $width/$index")
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val trickplayApi = TrickplayApi(apiClient)
                val response = trickplayApi.getTrickplayTileImage(itemId, width, index)
                Timber.d("Fetched trickplay tile from API: ${response.content?.size ?: 0} bytes")
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
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val genresApi = GenresApi(apiClient)

                val response =
                    genresApi.getGenres(
                        userId = userId,
                        parentId = parentId,
                        limit = limit,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        enableImages = false,
                        enableTotalRecordCount = false,
                        includeItemTypes =
                            includeItemTypes.mapNotNull {
                                try {
                                    BaseItemKind.valueOf(it.uppercase())
                                } catch (e: Exception) {
                                    null
                                }
                            },
                    )

                response.content?.items?.mapNotNull { genreDto ->
                    genreDto.name?.takeIf { it.isNotBlank() }
                } ?: emptyList()
            } catch (e: ApiClientException) {
                Timber.e(e, "API error getting genres: ${e.message}")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting genres")
                emptyList()
            }
        }

    override suspend fun getStudios(
        parentId: UUID?,
        limit: Int?,
        includeItemTypes: List<String>,
    ): List<AfinityStudio> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val userId = getCurrentUserId() ?: return@withContext emptyList()

                val librariesResponse = getLibraries()
                val tvLibraries = librariesResponse.filter { it.type == CollectionType.TvShows }

                if (tvLibraries.isEmpty()) {
                    Timber.d("No TV libraries found")
                    return@withContext emptyList()
                }

                Timber.d("Found ${tvLibraries.size} TV libraries")

                val studiosApi = StudiosApi(apiClient)
                val allStudios = mutableListOf<Triple<UUID, String, Pair<String?, Int>>>()

                for (library in tvLibraries) {
                    try {
                        val response =
                            studiosApi.getStudios(
                                userId = userId,
                                parentId = library.id,
                                includeItemTypes = listOf(BaseItemKind.SERIES),
                                enableImages = true,
                            )

                        response.content?.items?.forEach { studioDto ->
                            val id: UUID = studioDto.id ?: return@forEach
                            val name: String =
                                studioDto.name?.takeIf { it.isNotBlank() } ?: return@forEach
                            val childCount: Int = studioDto.childCount ?: 0

                            val imageTags: Map<ImageType, String>? = studioDto.imageTags
                            val thumbImageUrl: String? =
                                imageTags?.get(ImageType.THUMB)?.let { tag: String ->
                                    "${getBaseUrl()}/Items/$id/Images/Thumb?tag=$tag"
                                }

                            allStudios.add(Triple(id, name, Pair(thumbImageUrl, childCount)))
                        }

                        Timber.d(
                            "Fetched ${response.content?.items?.size ?: 0} studios from library: ${library.name}"
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch studios from library: ${library.name}")
                    }
                }

                val uniqueStudios = mutableMapOf<UUID, Triple<String, String?, Int>>()
                for ((id, name, imageData) in allStudios) {
                    val (thumbUrl, childCount) = imageData

                    if (uniqueStudios.containsKey(id)) {
                        val existing = uniqueStudios[id]!!
                        if (childCount > existing.third) {
                            uniqueStudios[id] = Triple(name, thumbUrl, childCount)
                        }
                    } else {
                        uniqueStudios[id] = Triple(name, thumbUrl, childCount)
                    }
                }

                Timber.d("Deduplicated to ${uniqueStudios.size} unique studios")

                val filteredStudios =
                    uniqueStudios
                        .filter { (_, data) -> data.third >= 5 }
                        .filter { (_, data) -> data.second != null }

                Timber.d("Filtered to ${filteredStudios.size} studios (min 5 shows, has Thumb)")

                val sortedStudios =
                    filteredStudios
                        .map { (id, data) ->
                            AfinityStudio(
                                id = id,
                                name = data.first,
                                primaryImageUrl = data.second,
                                itemCount = data.third,
                            )
                        }
                        .sortedByDescending { it.itemCount }

                sortedStudios.take(limit ?: 15)
            } catch (e: ApiClientException) {
                Timber.e(e, "API error getting studios: ${e.message}")
                emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting studios")
                emptyList()
            }
        }

    override suspend fun ensureBoxSetCacheBuilt() =
        withContext(Dispatchers.IO) {
            try {
                if (boxSetCache.isEmpty() || boxSetCache.isStale()) {
                    val stats = boxSetCache.getStats()
                    Timber.d(
                        "BoxSet cache needs rebuild - Empty: ${stats.isEmpty}, Stale: ${stats.isStale}, Age: ${stats.ageMs}ms"
                    )

                    boxSetCache.buildCache { fetchAllBoxSetsWithChildren() }
                } else {
                    val stats = boxSetCache.getStats()
                    Timber.d(
                        "BoxSet cache is fresh - ${stats.itemCount} items cached, Age: ${stats.ageMs}ms"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to ensure BoxSet cache is built")
            }
        }

    private suspend fun fetchAllBoxSetsWithChildren(): List<BoxSetWithChildren> {
        val apiClient = sessionManager.getCurrentApiClient() ?: return emptyList()
        val userId = getCurrentUserId() ?: return emptyList()
        val itemsApi = ItemsApi(apiClient)

        val boxSetsResponse =
            itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                recursive = true,
                fields = listOf(ItemFields.CHILD_COUNT),
                enableImages = false,
                enableUserData = false,
                limit = null,
            )

        val allBoxSets = boxSetsResponse.content?.items ?: emptyList()
        Timber.d("Fetching children for ${allBoxSets.size} BoxSets")

        val nonEmptyBoxSets = allBoxSets.filter { (it.childCount ?: 0) > 0 }

        val semaphore = Semaphore(10)
        return coroutineScope {
            nonEmptyBoxSets
                .map { boxSetDto ->
                    async {
                        semaphore.withPermit {
                            try {
                                val childrenResponse =
                                    itemsApi.getItems(
                                        userId = userId,
                                        parentId = boxSetDto.id,
                                        recursive = false,
                                        fields = emptyList(),
                                        enableImages = false,
                                        enableUserData = false,
                                    )

                                val childItemIds =
                                    childrenResponse.content?.items?.mapNotNull { it.id }
                                        ?: emptyList()

                                BoxSetWithChildren(
                                    boxSetId = boxSetDto.id,
                                    childItemIds = childItemIds,
                                )
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to fetch children for BoxSet ${boxSetDto.name}")
                                BoxSetWithChildren(boxSetDto.id, emptyList())
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }

    override suspend fun getBoxSetsContaining(
        itemId: UUID,
        fields: List<ItemFields>?,
    ): List<AfinityBoxSet> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                ensureBoxSetCacheBuilt()

                val boxSetIds = boxSetCache.getBoxSetIdsForItem(itemId)

                if (boxSetIds.isEmpty()) {
                    Timber.d("Item $itemId is not in any BoxSets (cache lookup)")
                    return@withContext emptyList()
                }

                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val itemsApi = ItemsApi(apiClient)

                val boxSetsResponse =
                    itemsApi.getItems(
                        userId = userId,
                        ids = boxSetIds,
                        fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                        enableImages = true,
                        enableUserData = true,
                    )

                val boxSets =
                    boxSetsResponse.content?.items?.mapNotNull { boxSetDto ->
                        boxSetDto.toAfinityBoxSet(getBaseUrl())
                    } ?: emptyList()

                Timber.d("Item $itemId is in ${boxSets.size} BoxSets (cache lookup)")
                boxSets
            } catch (e: Exception) {
                Timber.e(e, "Failed to get BoxSets containing item $itemId")
                emptyList()
            }
        }

    override fun getLibrariesFlow(): Flow<List<AfinityCollection>> = libraries

    override fun getLatestMediaFlow(parentId: UUID?): Flow<List<AfinityItem>> = latestMedia

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
}
