package com.makd.afinity.data.repository.media

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityCollection
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityImages
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.extensions.toAfinityShow
import com.makd.afinity.data.models.extensions.toAfinityVideo
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.RecommendationType
import com.makd.afinity.data.models.media.buildTitle
import com.makd.afinity.data.models.media.toAfinityRecommendationType
import com.makd.afinity.data.repository.FieldSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.GenresApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.MoviesApi
import org.jellyfin.sdk.api.operations.TrickplayApi
import org.jellyfin.sdk.api.operations.TvShowsApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinMediaRepository @Inject constructor(
    private val apiClient: ApiClient
) : MediaRepository {
    override suspend fun refreshItemUserData(
        itemId: UUID,
        fields: List<ItemFields>?
    ): AfinityItem? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext null
                val itemsApi = ItemsApi(apiClient)
                val response = itemsApi.getItems(
                    userId = userId,
                    ids = listOf(itemId),
                    fields = fields ?: FieldSets.REFRESH_USER_DATA,
                    enableImages = true,
                    enableUserData = true
                )
                val freshBaseItemDto = response.content?.items?.firstOrNull()
                val freshItem = freshBaseItemDto?.toAfinityItem(getBaseUrl())
                if (freshItem != null) {
                    updateItemInCache(_continueWatching, freshItem)
                    updateItemInCache(_latestMedia, freshItem)
                    if (freshItem is AfinityEpisode) {
                        updateEpisodeInNextUpCache(freshItem)
                    }
                    Timber.d("Successfully refreshed UserData for item: ${freshItem.name} (${freshItem.id})")
                    Timber.d("- Played: ${freshItem.played}")
                    Timber.d("- Progress: ${(freshItem.playbackPositionTicks.toFloat() / freshItem.runtimeTicks * 100f)}%")
                } else {
                    Timber.w("No fresh item data received for itemId: $itemId")
                }
                return@withContext freshItem
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh UserData for item: $itemId")
                null
            }
        }
    }

    private fun updateItemInCache(cache: MutableStateFlow<List<AfinityItem>>, updatedItem: AfinityItem) {
        val currentList = cache.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == updatedItem.id }

        when {
            updatedItem.played && cache == _continueWatching -> {
                if (existingIndex != -1) {
                    currentList.removeAt(existingIndex)
                    Timber.d("Removed completed item from continue watching: ${updatedItem.name}")
                }
            }

            (updatedItem.playbackPositionTicks.toFloat() / updatedItem.runtimeTicks * 100f) > 0f &&
                    !updatedItem.played &&
                    cache == _continueWatching -> {
                if (existingIndex != -1) {
                    currentList[existingIndex] = updatedItem
                    Timber.d("Updated item in continue watching: ${updatedItem.name} (${updatedItem.playbackPositionTicks.toFloat() / updatedItem.runtimeTicks * 100f}%)")
                } else {
                    currentList.add(0, updatedItem)
                    Timber.d("Added item to continue watching: ${updatedItem.name} (${updatedItem.playbackPositionTicks.toFloat() / updatedItem.runtimeTicks * 100f}%)")
                }
            }

            existingIndex != -1 -> {
                currentList[existingIndex] = updatedItem
                Timber.d("Updated item in cache: ${updatedItem.name}")
            }

            else -> {
            }
        }

        cache.value = currentList
    }

    private fun updateEpisodeInNextUpCache(updatedEpisode: AfinityEpisode) {
        val currentList = _nextUp.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == updatedEpisode.id }

        when {
            updatedEpisode.played -> {
                if (existingIndex != -1) {
                    currentList.removeAt(existingIndex)
                    Timber.d("Removed completed episode from next up: ${updatedEpisode.name}")
                }
            }

            existingIndex != -1 -> {
                currentList[existingIndex] = updatedEpisode
                Timber.d("Updated episode in next up: ${updatedEpisode.name}")
            }

            else -> {
                // Episode not in next up, don't add it here
            }
        }

        _nextUp.value = currentList
    }

    override suspend fun invalidateContinueWatchingCache() {
        withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext
                val itemsApi = ItemsApi(apiClient)
                val response = itemsApi.getResumeItems(
                    userId = userId,
                    limit = 12,
                    fields = FieldSets.CACHE_CONTINUE_WATCHING,
                    enableImages = true,
                    enableUserData = true
                )

                val continueWatchingItems = response.content?.items?.mapNotNull { baseItemDto ->
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
                val userId = getCurrentUserId() ?: return@withContext
                val userLibraryApi = UserLibraryApi(apiClient)
                val response = userLibraryApi.getLatestMedia(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    limit = 15,
                    isPlayed = false,
                    fields = FieldSets.CACHE_LATEST_MEDIA,
                    enableImages = true,
                    enableUserData = true
                )

                val latestItems = response.content?.mapNotNull { baseItemDto ->
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
                val userId = getCurrentUserId() ?: return@withContext
                val tvShowsApi = TvShowsApi(apiClient)
                val response = tvShowsApi.getNextUp(
                    userId = userId,
                    limit = 16,
                    fields = FieldSets.CACHE_NEXT_UP,
                    enableImages = true,
                    enableUserData = true,
                    enableResumable = false,
                    enableRewatching = false
                )

                val nextUpEpisodes = response.content?.items?.mapNotNull { baseItemDto ->
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

    private suspend fun getCurrentUserId(): UUID? = withContext(Dispatchers.IO) {
        return@withContext try {
            val userApi = UserApi(apiClient)
            val response = userApi.getCurrentUser()
            response.content?.id
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            null
        }
    }

    private fun getBaseUrl(): String {
        return apiClient.baseUrl ?: ""
    }

    override suspend fun getLibraries(): List<AfinityCollection> = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Attempting to get libraries via MediaFolders API")
            try {
                val libraryApi = LibraryApi(apiClient)
                val response = libraryApi.getMediaFolders()
                val libraries = response.content?.items?.mapNotNull { baseItemDto ->
                    baseItemDto.toAfinityCollection(getBaseUrl())
                } ?: emptyList()

                if (libraries.isNotEmpty()) {
                    _libraries.value = libraries
                    Timber.d("Successfully retrieved ${libraries.size} libraries via MediaFolders API")
                    return@withContext libraries
                }
            } catch (e: ApiClientException) {
                val errorMessage = e.message ?: ""
                if (errorMessage.contains("403")) {
                    Timber.w("MediaFolders API returned 403 Forbidden. User doesn't have admin privileges. Trying Items API approach...")
                } else {
                    Timber.e(e, "MediaFolders API failed: ${e.message}")
                }
            }

            Timber.d("Using Items API to get user views (libraries)")
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
            )

            val libraries = response.content?.items?.mapNotNull { baseItemDto ->
                try {
                    Timber.d("Processing library: ${baseItemDto.name} (Type: ${baseItemDto.type}, CollectionType: ${baseItemDto.collectionType})")
                    baseItemDto.toAfinityCollection(getBaseUrl())
                } catch (e: Exception) {
                    Timber.w(e, "Failed to convert item to collection: ${baseItemDto.name}")
                    null
                }
            } ?: emptyList()

            _libraries.value = libraries
            Timber.d("Successfully retrieved ${libraries.size} libraries via Items API")
            libraries

        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to get libraries: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error getting libraries")
            emptyList()
        }
    }

    override suspend fun getLatestMedia(
        parentId: UUID?,
        limit: Int,
        fields: List<ItemFields>?
        ): List<AfinityItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val userLibraryApi = UserLibraryApi(apiClient)
            val response = userLibraryApi.getLatestMedia(
                userId = userId,
                parentId = parentId,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                limit = limit,
                isPlayed = false,
                fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                enableImages = true,
                enableUserData = true
            )

            val latestItems = response.content?.mapNotNull { baseItemDto ->
                if (baseItemDto.type == BaseItemKind.SERIES) {
                    Timber.d("Series '${baseItemDto.name}': childCount=${baseItemDto.childCount}")
                }
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
        fields: List<ItemFields>?
    ): List<AfinityItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getResumeItems(
                userId = userId,
                limit = limit,
                fields = fields ?: FieldSets.CONTINUE_WATCHING,
                enableImages = true,
                enableUserData = true
            )

            val continueWatchingItems = response.content?.items?.mapNotNull { baseItemDto ->
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

    override suspend fun getRecommendationCategories(
        parentId: UUID?,
        categoryLimit: Int,
        itemLimit: Int,
        fields: List<ItemFields>?
    ): List<AfinityRecommendationCategory> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val libraries = getLibraries()
            val movieLibraries = libraries.filter { it.type == CollectionType.Movies }

            val allRecommendationCategories = coroutineScope {
                movieLibraries.map { library ->
                    async {
                        try {
                            val moviesApi = MoviesApi(apiClient)
                            val recommendations = moviesApi.getMovieRecommendations(
                                userId = userId,
                                parentId = library.id,
                                categoryLimit = 4,
                                itemLimit = itemLimit,
                                fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                            )

                            recommendations.content?.mapNotNull { recommendationDto ->
                                val items = recommendationDto.items?.mapNotNull { baseItem ->
                                    baseItem.toAfinityItem(getBaseUrl())
                                } ?: emptyList()

                                if (items.isNotEmpty()) {
                                    AfinityRecommendationCategory(
                                        title = recommendationDto.recommendationType?.toAfinityRecommendationType()
                                            ?.buildTitle(recommendationDto.baselineItemName)
                                            ?: "Recommended for You",
                                        items = items,
                                        recommendationType = recommendationDto.recommendationType?.toAfinityRecommendationType()
                                            ?: RecommendationType.RECOMMENDED_FOR_YOU,
                                        baselineItemName = recommendationDto.baselineItemName
                                    )
                                } else null
                            } ?: emptyList()

                        } catch (e: Exception) {
                            Timber.w(e, "Failed to get movie recommendations for library ${library.id}")
                            emptyList<AfinityRecommendationCategory>()
                        }
                    }
                }.awaitAll().flatten()
            }

            val uniqueCategories = allRecommendationCategories
                .distinctBy { "${it.recommendationType}-${it.baselineItemName}" }

            val selectedCategories = mutableListOf<AfinityRecommendationCategory>()

            val becauseYouWatched = uniqueCategories
                .filter { it.recommendationType == RecommendationType.SIMILAR_TO_RECENTLY_PLAYED }
                .shuffled()
                .take(2)
            selectedCategories.addAll(becauseYouWatched)

            val becauseYouLiked = uniqueCategories
                .filter { it.recommendationType == RecommendationType.SIMILAR_TO_LIKED_ITEM }
                .shuffled()
                .take(1)
            selectedCategories.addAll(becauseYouLiked)

            val directedBy = uniqueCategories
                .filter {
                    it.recommendationType == RecommendationType.HAS_DIRECTOR_FROM_RECENTLY_PLAYED ||
                            it.recommendationType == RecommendationType.HAS_LIKED_DIRECTOR
                }
                .shuffled()
                .take(1)
            selectedCategories.addAll(directedBy)

            val starring = uniqueCategories
                .filter {
                    it.recommendationType == RecommendationType.HAS_ACTOR_FROM_RECENTLY_PLAYED ||
                            it.recommendationType == RecommendationType.HAS_LIKED_ACTOR
                }
                .shuffled()
                .take(1)
            selectedCategories.addAll(starring)

            val remainingSlots = categoryLimit - selectedCategories.size
            if (remainingSlots > 0) {
                val usedCategories = selectedCategories.map { "${it.recommendationType}-${it.baselineItemName}" }.toSet()
                val remaining = uniqueCategories
                    .filter { "${it.recommendationType}-${it.baselineItemName}" !in usedCategories }
                    .shuffled()
                    .take(remainingSlots)
                selectedCategories.addAll(remaining)
            }

            selectedCategories.shuffled().take(categoryLimit).map { category ->
                category.copy(items = category.items.shuffled())
            }

        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to get recommendation categories")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error getting recommendation categories")
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
        nameStartsWith: String?,
        fields: List<ItemFields>?
    ): BaseItemDtoQueryResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext BaseItemDtoQueryResult(
                items = emptyList(),
                totalRecordCount = 0,
                startIndex = 0
            )

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
                parentId = parentId,
                limit = limit,
                startIndex = startIndex,
                searchTerm = searchTerm,
                sortBy = listOf(sortBy.toJellyfinSortBy()),
                sortOrder = if (sortDescending) listOf(SortOrder.DESCENDING) else listOf(SortOrder.ASCENDING),
                includeItemTypes = includeItemTypes.mapNotNull {
                    try { BaseItemKind.valueOf(it.uppercase()) } catch (e: Exception) { null }
                },
                recursive = if (parentId == null) true else if (includeItemTypes.size == 1 && includeItemTypes.contains("SERIES")) true else if (searchTerm != null) true else null,
                collapseBoxSetItems = if (includeItemTypes.size == 1 && includeItemTypes.contains("SERIES")) false else null,
                genres = genres,
                years = years,
                isFavorite = isFavorite,
                isPlayed = isPlayed,
                nameStartsWith = nameStartsWith,
                fields = fields ?: FieldSets.LIBRARY_GRID,
                enableImages = true,
                enableUserData = true
            )
            response.content ?: BaseItemDtoQueryResult(
                items = emptyList(),
                totalRecordCount = 0,
                startIndex = 0
            )
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to get items")
            BaseItemDtoQueryResult(
                items = emptyList(),
                totalRecordCount = 0,
                startIndex = 0
            )
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error getting items")
            BaseItemDtoQueryResult(
                items = emptyList(),
                totalRecordCount = 0,
                startIndex = 0
            )
        }
    }

    override suspend fun getItem(
        itemId: UUID,
        fields: List<ItemFields>?
    ): BaseItemDto? = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext null

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
                ids = listOf(itemId),
                fields = fields ?: FieldSets.ITEM_DETAIL,
                enableImages = true,
                enableUserData = true
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
        fields: List<ItemFields>?
    ): List<AfinityItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val libraryApi = LibraryApi(apiClient)
            val response = libraryApi.getSimilarItems(
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
        fields: List<ItemFields>?
    ): List<AfinityMovie> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
                parentId = parentId,
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                limit = limit,
                startIndex = startIndex,
                searchTerm = searchTerm,
                sortBy = listOf(sortBy.toJellyfinSortBy()),
                sortOrder = if (sortDescending) listOf(SortOrder.DESCENDING) else listOf(SortOrder.ASCENDING),
                isPlayed = isPlayed,
                fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                enableImages = true,
                enableUserData = true
            )

            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityMovie(getBaseUrl())
            } ?: emptyList()
        } catch (e: ApiClientException) {
            Timber.e(e, "Failed to get movies")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error getting movies")
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
        fields: List<ItemFields>?
    ): List<AfinityShow> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
                parentId = parentId,
                includeItemTypes = listOf(BaseItemKind.SERIES),
                recursive = true,
                collapseBoxSetItems = false,
                limit = limit,
                startIndex = startIndex,
                searchTerm = searchTerm,
                sortBy = listOf(sortBy.toJellyfinSortBy()),
                sortOrder = if (sortDescending) listOf(SortOrder.DESCENDING) else listOf(SortOrder.ASCENDING),
                isPlayed = isPlayed,
                fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                enableImages = true,
                enableUserData = true
            )
            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityShow(getBaseUrl())
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get shows")
            emptyList()
        }
    }

    override suspend fun getSeasons(
        seriesId: UUID,
        fields: List<ItemFields>?
    ): List<AfinitySeason> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
                parentId = seriesId,
                includeItemTypes = listOf(BaseItemKind.SEASON),
                fields = fields ?: FieldSets.SEASON_DETAIL,
                enableImages = true,
                enableUserData = true,
                sortBy = listOf(ItemSortBy.SORT_NAME)
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
        fields: List<ItemFields>?
    ): List<AfinityEpisode> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val actualSeriesId = seriesId ?: run {
                val seasonItem = getItem(seasonId)
                seasonItem?.seriesId ?: return@withContext emptyList()
            }

            val tvShowsApi = TvShowsApi(apiClient)
            val response = tvShowsApi.getEpisodes(
                seriesId = actualSeriesId,
                userId = userId,
                seasonId = seasonId,
                isMissing = false,
                fields = fields ?: FieldSets.EPISODE_LIST,
                enableImages = true,
                enableUserData = true,
                sortBy = ItemSortBy.SORT_NAME
            )
            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityEpisode(getBaseUrl())
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get episodes")
            emptyList()
        }
    }

    override suspend fun getFavoriteMovies(
        fields: List<ItemFields>?
    ): List<AfinityMovie> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()
            val itemsApi = ItemsApi(apiClient)

            val response = itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                isFavorite = true,
                recursive = true,
                fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                enableImages = true,
                enableUserData = true,
                sortBy = listOf(ItemSortBy.SORT_NAME)
            )
            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityMovie(getBaseUrl())
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get favorite episodes")
            emptyList()
        }
    }

    override suspend fun getFavoriteShows(
        fields: List<ItemFields>?
    ): List<AfinityShow> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()
            val itemsApi = ItemsApi(apiClient)

            val response = itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.SERIES),
                isFavorite = true,
                recursive = true,
                fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                enableImages = true,
                enableUserData = true,
                sortBy = listOf(ItemSortBy.SORT_NAME)
            )

            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityShow(getBaseUrl())
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get favorite episodes")
            emptyList()
        }
    }

    override suspend fun getFavoriteEpisodes(
        fields: List<ItemFields>?
    ): List<AfinityEpisode> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()
            val itemsApi = ItemsApi(apiClient)

            val response = itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                isFavorite = true,
                recursive = true,
                fields = fields ?: FieldSets.EPISODE_LIST,
                enableImages = true,
                enableUserData = true,
                sortBy = listOf(ItemSortBy.SORT_NAME)
            )

            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityEpisode(getBaseUrl())
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get favorite episodes")
            emptyList()
        }
    }

    override suspend fun getNextUp(
        seriesId: UUID?,
        limit: Int,
        fields: List<ItemFields>?,
        enableResumable: Boolean
    ): List<AfinityEpisode> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val tvShowsApi = TvShowsApi(apiClient)
            val response = tvShowsApi.getNextUp(
                userId = userId,
                seriesId = seriesId,
                limit = limit,
                fields = fields ?: FieldSets.EPISODE_LIST,
                enableResumable = enableResumable,
                enableImages = true,
                enableUserData = true
            )
            val nextUpItems = response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityEpisode(getBaseUrl())
            } ?: emptyList()

            _nextUp.value = nextUpItems
            nextUpItems
        } catch (e: Exception) {
            Timber.e(e, "Failed to get next up")
            emptyList()
        }
    }

    override suspend fun getSpecialFeatures(itemId: UUID, userId: UUID): List<AfinityItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userLibraryApi = UserLibraryApi(apiClient)
            val response = userLibraryApi.getSpecialFeatures(
                itemId = itemId,
                userId = userId
            )

            Timber.d("Special features API response: ${response.content?.size ?: 0} items")
            response.content?.forEach { item ->
                Timber.d("Special feature: name=${item.name}, type=${item.type}, seriesId=${item.seriesId}, seasonId=${item.seasonId}")
            }

            response.content?.mapNotNull { baseItem ->
                val result = when (baseItem.type) {
                    BaseItemKind.EPISODE -> baseItem.toAfinityEpisode(getBaseUrl())
                    BaseItemKind.MOVIE -> baseItem.toAfinityMovie(getBaseUrl())
                    BaseItemKind.VIDEO -> baseItem.toAfinityVideo(getBaseUrl())
                    else -> {
                        Timber.d("Unsupported special feature type: ${baseItem.type}")
                        null
                    }
                }
                if (result == null) {
                    Timber.d("Failed to convert special feature: ${baseItem.name} (${baseItem.type})")
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
        fields: List<ItemFields>?
    ): List<AfinityItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
                searchTerm = query,
                limit = limit,
                includeItemTypes = includeItemTypes.mapNotNull {
                    try { BaseItemKind.valueOf(it.uppercase()) } catch (e: Exception) { null }
                },
                fields = fields ?: FieldSets.SEARCH_RESULTS,
                enableImages = true,
                enableUserData = true
            )
            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityItem(getBaseUrl())
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to search items")
            emptyList()
        }
    }

    override suspend fun getPerson(personId: UUID): AfinityPersonDetail? = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext null

            val userLibraryApi = UserLibraryApi(apiClient)
            val response = userLibraryApi.getItem(
                itemId = personId,
                userId = userId
            )

            response.content?.let { baseItemDto ->
                Timber.d("Person data received - Name: ${baseItemDto.name}, Overview length: ${baseItemDto.overview?.length ?: 0}")
                AfinityPersonDetail(
                    id = baseItemDto.id,
                    name = baseItemDto.name.orEmpty(),
                    overview = baseItemDto.overview.orEmpty(),
                    images = baseItemDto.toAfinityImages(getBaseUrl())
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
        fields: List<ItemFields>?
    ): List<AfinityItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()

            val itemsApi = ItemsApi(apiClient)
            val response = itemsApi.getItems(
                userId = userId,
                personIds = listOf(personId),
                includeItemTypes = includeItemTypes.mapNotNull {
                    try { BaseItemKind.valueOf(it.uppercase()) } catch (e: Exception) { null }
                },
                fields = fields ?: FieldSets.MEDIA_ITEM_CARDS,
                enableImages = true,
                enableUserData = true,
                recursive = true,
                limit = 200
            )
            response.content?.items?.mapNotNull { baseItem ->
                baseItem.toAfinityItem(getBaseUrl())
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get person items for ID: $personId")
            emptyList()
        }
    }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val trickplayApi = TrickplayApi(apiClient)
                val response = trickplayApi.getTrickplayTileImage(itemId, width, index)
                response.content
            } catch (e: Exception) {
                return@withContext null
            }
        }

    override suspend fun getGenres(
        parentId: UUID?,
        limit: Int?
    ): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()
            val genresApi = GenresApi(apiClient)

            val response = genresApi.getGenres(
                userId = userId,
                parentId = parentId,
                limit = limit,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                enableImages = false,
                enableTotalRecordCount = false
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