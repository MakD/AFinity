package com.makd.afinity.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.makd.afinity.data.database.entities.ItemMetadataCacheEntity
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackEvent
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.extensions.toAfinityBoxSet
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.models.media.toAfinityMovie
import com.makd.afinity.data.models.media.toAfinityShow
import com.makd.afinity.data.models.tmdb.TmdbReview
import com.makd.afinity.data.network.TmdbApiService
import com.makd.afinity.data.paging.EpisodesPagingSource
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class ItemDetailViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository,
    private val databaseRepository: DatabaseRepository,
    private val offlineModeManager: OfflineModeManager,
    private val authRepository: AuthRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val serverRepository: ServerRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val tmdbApiService: TmdbApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: UUID =
        UUID.fromString(
            savedStateHandle.get<String>("itemId")
                ?: throw IllegalArgumentException("itemId is required")
        )

    private val _episodesPagingData = MutableStateFlow<Flow<PagingData<AfinityEpisode>>?>(null)
    private val _episodeStatusUpdates = MutableStateFlow<Map<UUID, Boolean>>(emptyMap())
    private val _seasonWatchStatusOverride = MutableStateFlow<Boolean?>(null)

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val _selectedMediaSource = MutableStateFlow<MediaSourceOption?>(null)
    val selectedMediaSource = _selectedMediaSource.asStateFlow()

    fun selectMediaSource(source: MediaSourceOption) {
        _selectedMediaSource.value = source
    }

    init {
        viewModelScope.launch {
            try {
                val boxSets =
                    mediaRepository.getBoxSetsContaining(
                        itemId = itemId,
                        fields = FieldSets.MEDIA_ITEM_CARDS,
                    )
                _uiState.value = _uiState.value.copy(containingBoxSets = boxSets)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load containing BoxSets in init")
            }
        }

        loadItem()
        observeDownloadStatus()

        viewModelScope.launch {
            playbackStateManager.playbackEvents.collect { event ->
                when (event) {
                    is PlaybackEvent.Stopped -> {
                        if (event.itemId == itemId) {
                            applyOptimisticUpdate(event.positionTicks)
                        }
                    }

                    is PlaybackEvent.Synced -> {
                        val isNextEpisode = _uiState.value.nextEpisode?.id == event.itemId
                        val isBoxSetItem = _uiState.value.boxSetItems.any { it.id == event.itemId }
                        val isChildUpdate =
                            _uiState.value.seasons.any { season ->
                                season.episodes.any { it.id == event.itemId } ||
                                    season.id == event.itemId
                            }

                        val isDirectParentMatch =
                            event.seriesId == itemId || event.seasonId == itemId

                        val belongsToLoadedSeason =
                            event.seasonId != null &&
                                _uiState.value.seasons.any { it.id == event.seasonId }

                        var isRelated =
                            event.itemId == itemId ||
                                isChildUpdate ||
                                isNextEpisode ||
                                isBoxSetItem ||
                                isDirectParentMatch ||
                                belongsToLoadedSeason
                        var syncedEpisode: AfinityEpisode? = null

                        if (!isRelated) {
                            try {
                                val syncedItem = jellyfinRepository.getItemById(event.itemId)
                                if (syncedItem is AfinityEpisode) {
                                    val belongsToSeriesBySeriesId = syncedItem.seriesId == itemId
                                    val belongsToSeriesBySeasonId =
                                        syncedItem.seasonId != null &&
                                            _uiState.value.seasons.any {
                                                it.id == syncedItem.seasonId
                                            }
                                    isRelated =
                                        (belongsToSeriesBySeriesId ||
                                            syncedItem.seasonId == itemId ||
                                            belongsToSeriesBySeasonId)
                                    syncedEpisode = syncedItem
                                } else if (syncedItem is AfinitySeason) {
                                    isRelated = (syncedItem.seriesId == itemId)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error checking synced item")
                            }
                        } else {
                            try {
                                val syncedItem = jellyfinRepository.getItemById(event.itemId)
                                if (syncedItem is AfinityEpisode) {
                                    syncedEpisode = syncedItem
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error fetching synced episode")
                            }
                        }

                        if (isRelated) {
                            syncedEpisode?.let { ep ->
                                if (_episodeStatusUpdates.value[ep.id] != ep.played) {
                                    _episodeStatusUpdates.value += (ep.id to ep.played)
                                }
                            }

                            refreshFromCacheImmediate()
                        }

                        updateSimilarItemsOnSync(event.itemId)
                    }
                }
            }
        }
    }

    private fun applyOptimisticUpdate(positionTicks: Long) {
        val currentItem = _uiState.value.item ?: return
        val percentage =
            if (currentItem.runtimeTicks > 0) {
                positionTicks.toDouble() / currentItem.runtimeTicks.toDouble()
            } else 0.0
        val isPlayed = currentItem.played || (percentage > 0.9)
        val finalTicks = if (isPlayed) 0L else positionTicks
        val updatedItem =
            when (currentItem) {
                is AfinityMovie ->
                    currentItem.copy(playbackPositionTicks = finalTicks, played = isPlayed)

                is AfinityEpisode ->
                    currentItem.copy(playbackPositionTicks = finalTicks, played = isPlayed)

                is AfinityVideo ->
                    currentItem.copy(playbackPositionTicks = finalTicks, played = isPlayed)

                else -> currentItem
            }
        _uiState.value = _uiState.value.copy(item = updatedItem)
        Timber.d("Optimistic update applied: Pos=${finalTicks}, Played=${isPlayed}")
    }

    private fun observeDownloadStatus() {
        viewModelScope.launch {
            try {
                val existingDownload = downloadRepository.getDownloadByItemId(itemId)
                if (existingDownload != null) {
                    _uiState.value = _uiState.value.copy(downloadInfo = existingDownload)
                }

                downloadRepository.getAllDownloadsFlow().collect { downloads ->
                    val itemDownload = downloads.find { it.itemId == itemId }
                    _uiState.value = _uiState.value.copy(downloadInfo = itemDownload)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to observe download status")
            }
        }
    }

    private fun refreshFromCacheImmediate() {
        viewModelScope.launch {
            try {
                val cachedItem = jellyfinRepository.getItemById(itemId)
                if (cachedItem != null) {
                    updateItemUserData(cachedItem)

                    when (cachedItem) {
                        is AfinityShow -> {
                            launch {
                                try {
                                    val nextEpisode =
                                        jellyfinRepository.getEpisodeToPlay(cachedItem.id)
                                    _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to get next episode")
                                }
                            }
                        }

                        is AfinitySeason -> {
                            launch {
                                try {
                                    val nextEpisode =
                                        jellyfinRepository.getEpisodeToPlayForSeason(
                                            cachedItem.id,
                                            cachedItem.seriesId,
                                        )
                                    _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to get next episode for season")
                                }
                            }
                        }

                        is AfinityBoxSet -> {
                            loadBoxSetItems(cachedItem.id)
                        }
                    }
                }

                launch { syncWithServerInBackground() }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh from cache")
            }
        }
    }

    private fun updateSimilarItemsOnSync(syncedItemId: UUID) {
        viewModelScope.launch {
            try {
                val targetId =
                    when (val syncedItem = jellyfinRepository.getItemById(syncedItemId)) {
                        is AfinityEpisode -> syncedItem.seriesId
                        is AfinitySeason -> syncedItem.seriesId
                        else -> syncedItemId
                    }
                val index = _uiState.value.similarItems.indexOfFirst { it.id == targetId }
                if (index == -1) return@launch
                val updatedItem = jellyfinRepository.getItemById(targetId) ?: return@launch
                val updatedList = _uiState.value.similarItems.toMutableList()
                updatedList[index] = updatedItem
                _uiState.value = _uiState.value.copy(similarItems = updatedList)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update similar items on sync")
            }
        }
    }

    private suspend fun syncWithServerInBackground() {
        try {
            val serverItem =
                jellyfinRepository.getItem(itemId, fields = FieldSets.ITEM_DETAIL)?.let {
                    baseItemDto ->
                    when (baseItemDto.type) {
                        BaseItemKind.MOVIE -> baseItemDto.toAfinityMovie(jellyfinRepository, null)

                        BaseItemKind.SERIES -> baseItemDto.toAfinityShow(jellyfinRepository)

                        BaseItemKind.EPISODE ->
                            baseItemDto.toAfinityEpisode(jellyfinRepository, null)

                        BaseItemKind.BOX_SET ->
                            baseItemDto.toAfinityBoxSet(jellyfinRepository.getBaseUrl())

                        BaseItemKind.SEASON -> {
                            val season =
                                baseItemDto.toAfinitySeason(jellyfinRepository.getBaseUrl())
                            if (season.runtimeTicks == 0L) {
                                try {
                                    val series =
                                        jellyfinRepository.getItem(
                                            season.seriesId,
                                            fields = FieldSets.REFRESH_USER_DATA,
                                        )
                                    season.copy(runtimeTicks = series?.runTimeTicks ?: 0L)
                                } catch (e: Exception) {
                                    season
                                }
                            } else season
                        }

                        else -> null
                    }
                }

            if (serverItem != null) {
                val currentItem = _uiState.value.item
                if (currentItem == null) {
                    _uiState.value = _uiState.value.copy(item = serverItem)
                } else {
                    updateItemUserData(serverItem)
                }
                if (serverItem is AfinityShow) {
                    try {
                        val seasons = jellyfinRepository.getSeasons(serverItem.id)
                        _uiState.value = _uiState.value.copy(seasons = seasons)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to refresh seasons in background sync")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Background server sync failed (non-critical)")
        }
    }

    private fun updateItemUserData(newItem: AfinityItem) {
        val currentItem = _uiState.value.item ?: return
        val hasChanges = hasSignificantChanges(currentItem, newItem)
        if (!hasChanges) return

        val updatedItem =
            when (currentItem) {
                is AfinityMovie ->
                    currentItem.copy(
                        played = newItem.played,
                        playbackPositionTicks = newItem.playbackPositionTicks,
                        favorite = newItem.favorite,
                    )

                is AfinityShow ->
                    currentItem.copy(
                        played = newItem.played,
                        playbackPositionTicks = newItem.playbackPositionTicks,
                        favorite = newItem.favorite,
                        unplayedItemCount =
                            (newItem as? AfinityShow)?.unplayedItemCount
                                ?: currentItem.unplayedItemCount,
                    )

                is AfinityEpisode ->
                    currentItem.copy(
                        played = newItem.played,
                        playbackPositionTicks = newItem.playbackPositionTicks,
                        favorite = newItem.favorite,
                    )

                is AfinityBoxSet ->
                    currentItem.copy(
                        played = newItem.played,
                        playbackPositionTicks = newItem.playbackPositionTicks,
                        favorite = newItem.favorite,
                        unplayedItemCount =
                            (newItem as? AfinityBoxSet)?.unplayedItemCount
                                ?: currentItem.unplayedItemCount,
                    )

                is AfinitySeason ->
                    currentItem.copy(
                        played = newItem.played,
                        playbackPositionTicks = newItem.playbackPositionTicks,
                        favorite = newItem.favorite,
                        unplayedItemCount =
                            (newItem as? AfinitySeason)?.unplayedItemCount
                                ?: currentItem.unplayedItemCount,
                    )

                is AfinityVideo ->
                    currentItem.copy(
                        played = newItem.played,
                        playbackPositionTicks = newItem.playbackPositionTicks,
                        favorite = newItem.favorite,
                    )

                else -> currentItem
            }
        _uiState.value = _uiState.value.copy(item = updatedItem)
        playbackStateManager.notifyItemChanged(
            updatedItem.id,
            (updatedItem as? AfinityEpisode)?.seriesId,
            (updatedItem as? AfinityEpisode)?.seasonId,
        )
    }

    private fun hasSignificantChanges(cached: AfinityItem, server: AfinityItem): Boolean {
        return cached.playbackPositionTicks != server.playbackPositionTicks ||
            cached.played != server.played ||
            cached.favorite != server.favorite ||
            (cached is AfinityBoxSet &&
                server is AfinityBoxSet &&
                cached.unplayedItemCount != server.unplayedItemCount) ||
            (cached is AfinityShow &&
                server is AfinityShow &&
                cached.unplayedItemCount != server.unplayedItemCount) ||
            (cached is AfinitySeason &&
                server is AfinitySeason &&
                cached.unplayedItemCount != server.unplayedItemCount)
    }

    private fun loadBoxSetItems(boxSetId: UUID) {
        viewModelScope.launch {
            try {
                val response =
                    jellyfinRepository.getItems(
                        parentId = boxSetId,
                        includeItemTypes = listOf("MOVIE", "SERIES", "SEASON", "EPISODE"),
                        limit = 100,
                        sortBy = SortBy.RELEASE_DATE,
                        fields = FieldSets.MINIMAL,
                    )
                val items =
                    response.items?.mapNotNull { baseItem ->
                        val item = baseItem.toAfinityItem(jellyfinRepository.getBaseUrl())
                        if (item is AfinitySeason && item.runtimeTicks == 0L) {
                            try {
                                val series =
                                    jellyfinRepository.getItem(
                                        item.seriesId,
                                        fields = FieldSets.MINIMAL,
                                    )
                                item.copy(runtimeTicks = series?.runTimeTicks ?: 0L)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to get series runtime for season in boxset")
                                item
                            }
                        } else {
                            item
                        }
                    } ?: emptyList()
                _uiState.value = _uiState.value.copy(boxSetItems = items)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load boxset items")
            }
        }
    }

    fun loadItem() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val isOffline = offlineModeManager.isCurrentlyOffline()

                val item =
                    if (isOffline) {
                        Timber.d("Device is offline, loading item from database")
                        loadItemFromDatabase()
                    } else {
                        jellyfinRepository.getItem(itemId, fields = FieldSets.ITEM_DETAIL)?.let {
                            baseItemDto ->
                            Timber.d("MediaSources count: ${baseItemDto.mediaSources?.size ?: 0}")

                            when (baseItemDto.type) {
                                BaseItemKind.MOVIE -> {
                                    baseItemDto.toAfinityMovie(jellyfinRepository, null)
                                }

                                BaseItemKind.SERIES -> {
                                    baseItemDto.toAfinityShow(jellyfinRepository)
                                }

                                BaseItemKind.EPISODE -> {
                                    baseItemDto.toAfinityEpisode(jellyfinRepository, null)
                                }

                                BaseItemKind.BOX_SET -> {
                                    baseItemDto.toAfinityBoxSet(jellyfinRepository.getBaseUrl())
                                }

                                BaseItemKind.SEASON -> {
                                    val season =
                                        baseItemDto.toAfinitySeason(jellyfinRepository.getBaseUrl())
                                    if (season.runtimeTicks == 0L) {
                                        try {
                                            val series =
                                                jellyfinRepository.getItem(
                                                    season.seriesId,
                                                    fields = FieldSets.REFRESH_USER_DATA,
                                                )
                                            season.copy(runtimeTicks = series?.runTimeTicks ?: 0L)
                                        } catch (e: Exception) {
                                            season
                                        }
                                    } else season
                                }

                                else -> null
                            }
                        }
                    }

                if (item == null) {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error =
                                if (isOffline) "Item not available offline" else "Item not found",
                        )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(item = item, isLoading = false)

                if (!isOffline) {
                    loadAdditionalDetails(item)
                } else {
                    val cachedReviews =
                        when (item) {
                            is AfinityMovie -> item.tmdbReviews
                            is AfinityShow -> item.tmdbReviews
                            else -> emptyList()
                        }
                    val cachedRatings =
                        when (item) {
                            is AfinityMovie -> item.mdbRatings
                            is AfinityShow -> item.mdbRatings
                            else -> emptyList()
                        }
                    _uiState.value =
                        _uiState.value.copy(
                            tmdbReviews = cachedReviews,
                            mdbRatings = cachedRatings,
                            isRatingsFromCache = true,
                        )
                    when (item) {
                        is AfinityShow -> {
                            if (item.seasons.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(seasons = item.seasons)
                                Timber.d(
                                    "Loaded ${item.seasons.size} seasons from database for show: ${item.name}"
                                )

                                val nextEpisode = getNextEpisodeOffline(item)
                                _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                                Timber.d("Next episode for show: ${nextEpisode?.name ?: "none"}")
                            }
                        }

                        is AfinitySeason -> {
                            if (item.episodes.isNotEmpty()) {
                                Timber.d(
                                    "Setting up ${item.episodes.size} episodes from database for season: ${item.name}"
                                )
                                val pagingData =
                                    kotlinx.coroutines.flow.flowOf(
                                        PagingData.from(item.episodes.toList())
                                    )
                                _episodesPagingData.value = pagingData
                                _uiState.value =
                                    _uiState.value.copy(episodesPagingData = pagingData)
                                Timber.d("Episodes paging data set in UI state for offline season")

                                val nextEpisode = getNextEpisodeForSeasonOffline(item)
                                _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                                Timber.d("Next episode for season: ${nextEpisode?.name ?: "none"}")
                            } else {
                                Timber.d("No episodes cached for season, setting empty paging data")
                                val emptyPagingData =
                                    kotlinx.coroutines.flow.flowOf(
                                        PagingData.empty<AfinityEpisode>()
                                    )
                                _episodesPagingData.value = emptyPagingData
                                _uiState.value =
                                    _uiState.value.copy(episodesPagingData = emptyPagingData)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load item: $itemId")
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load item: ${e.message}",
                    )
            }
        }
    }

    private suspend fun loadItemFromDatabase(): AfinityItem? {
        return try {
            val userId = authRepository.currentUser.value?.id ?: return null

            val movie = databaseRepository.getMovie(itemId, userId)
            if (movie != null) {
                Timber.d("Loaded movie from database: ${movie.name}")
                return movie
            }

            val show = databaseRepository.getShow(itemId, userId)
            if (show != null) {
                Timber.d("Loaded show from database: ${show.name}")
                return show
            }

            val season = databaseRepository.getSeason(itemId, userId)
            if (season != null) {
                Timber.d(
                    "Loaded season from database: ${season.name} with ${season.episodes.size} episodes"
                )
                return season
            }

            val episode = databaseRepository.getEpisode(itemId, userId)
            if (episode != null) {
                Timber.d("Loaded episode from database: ${episode.name}")
                return episode
            }

            Timber.w("Item $itemId not found in database")
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load item from database")
            null
        }
    }

    private fun loadAdditionalDetails(item: AfinityItem) {
        viewModelScope.launch {
            if (item is AfinityMovie || item is AfinityShow) {
                val userId = authRepository.currentUser.value?.id

                launch {
                    try {
                        _uiState.value = _uiState.value.copy(isLoadingReviews = true)

                        val cachedMetadata = databaseRepository.getItemMetadata(item.id)

                        if (
                            cachedMetadata != null &&
                                (cachedMetadata.tmdbReviews.isNotEmpty() ||
                                    cachedMetadata.mdbRatings.isNotEmpty())
                        ) {
                            Timber.d("Loaded metadata from cache for ${item.name}")
                            _uiState.value =
                                _uiState.value.copy(
                                    tmdbReviews = cachedMetadata.tmdbReviews,
                                    mdbRatings = cachedMetadata.mdbRatings,
                                    isRatingsFromCache = true,
                                    isLoadingReviews = false,
                                )
                        } else {
                            val tmdbId = item.providerIds?.get("Tmdb")?.toString()

                            var fetchedReviews = emptyList<TmdbReview>()
                            var fetchedRatings = emptyList<MdbListRating>()

                            if (tmdbId != null && userId != null) {
                                val serverId = serverRepository.currentServer.value?.id
                                val tmdbKey =
                                    serverId?.let {
                                        securePreferencesRepository.getTmdbApiKey(
                                            it,
                                            userId.toString(),
                                        )
                                    }

                                val reviewsDeferred = async {
                                    if (!tmdbKey.isNullOrBlank()) {
                                        val response =
                                            when (item) {
                                                is AfinityMovie ->
                                                    tmdbApiService.getMovieReviews(tmdbId, tmdbKey)
                                                is AfinityShow ->
                                                    tmdbApiService.getSeriesReviews(tmdbId, tmdbKey)
                                                else -> null
                                            }
                                        response?.results ?: emptyList()
                                    } else emptyList()
                                }

                                val ratingsDeferred = async {
                                    val isMovie = item is AfinityMovie
                                    val ratings = mediaRepository.getMdbListRatings(tmdbId, isMovie)
                                    val excludedSources = listOf("imdb", "tomatoes")
                                    ratings.filter { rating ->
                                        rating.source.lowercase() !in excludedSources &&
                                            rating.value != null
                                    }
                                }
                                fetchedReviews = reviewsDeferred.await()
                                fetchedRatings = ratingsDeferred.await()

                                Timber.d(
                                    "Fetched ${fetchedReviews.size} TMDB reviews and ${fetchedRatings.size} MDBList ratings for ${item.name}"
                                )
                            }
                            _uiState.value =
                                _uiState.value.copy(
                                    tmdbReviews = fetchedReviews,
                                    mdbRatings = fetchedRatings,
                                    isRatingsFromCache = false,
                                    isLoadingReviews = false,
                                )

                            if (fetchedReviews.isNotEmpty() || fetchedRatings.isNotEmpty()) {
                                try {
                                    databaseRepository.insertItemMetadata(
                                        ItemMetadataCacheEntity(
                                            itemId = item.id,
                                            tmdbReviews = fetchedReviews,
                                            mdbRatings = fetchedRatings,
                                        )
                                    )
                                } catch (dbError: Exception) {
                                    Timber.e(dbError, "Failed to cache metadata to database")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load item metadata")
                        _uiState.value = _uiState.value.copy(isLoadingReviews = false)
                    }
                }
            }

            when (item) {
                is AfinityShow -> {
                    launch {
                        try {
                            val nextEpisode = jellyfinRepository.getEpisodeToPlay(item.id)
                            _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load next episode")
                        }
                    }
                    launch {
                        try {
                            val similarItems = jellyfinRepository.getSimilarItems(itemId)
                            _uiState.value = _uiState.value.copy(similarItems = similarItems)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load similar items")
                        }
                    }
                    launch {
                        try {
                            val seasons =
                                item.seasons.ifEmpty { jellyfinRepository.getSeasons(item.id) }
                            _uiState.value = _uiState.value.copy(seasons = seasons)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load seasons")
                        }
                    }
                    launch {
                        try {
                            val userId = getCurrentUserId()
                            if (userId != null) {
                                val features = jellyfinRepository.getSpecialFeatures(itemId, userId)
                                _uiState.value = _uiState.value.copy(specialFeatures = features)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load special features")
                        }
                    }
                }

                is AfinitySeason -> {
                    launch {
                        try {
                            val episodeToPlay =
                                jellyfinRepository.getEpisodeToPlayForSeason(item.id, item.seriesId)
                            _uiState.value = _uiState.value.copy(nextEpisode = episodeToPlay)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load episode to play for season")
                        }
                    }
                    launch {
                        try {
                            val isCurrentlyOffline = offlineModeManager.isCurrentlyOffline()
                            val basePagerFlow =
                                if (isCurrentlyOffline) {
                                    if (item.episodes.isNotEmpty()) {
                                        Timber.d(
                                            "Using ${item.episodes.size} episodes from database for season: ${item.name}"
                                        )
                                        kotlinx.coroutines.flow.flowOf(
                                            PagingData.from(item.episodes.toList())
                                        )
                                    } else {
                                        Timber.d(
                                            "No episodes cached for season: ${item.name}, showing empty list"
                                        )
                                        kotlinx.coroutines.flow.flowOf(PagingData.empty())
                                    }
                                } else {
                                    Pager(
                                            config =
                                                PagingConfig(
                                                    pageSize = 50,
                                                    enablePlaceholders = false,
                                                    initialLoadSize = 50,
                                                )
                                        ) {
                                            EpisodesPagingSource(
                                                mediaRepository = mediaRepository,
                                                seasonId = item.id,
                                                seriesId = item.seriesId,
                                            )
                                        }
                                        .flow
                                        .cachedIn(viewModelScope)
                                }
                            val combinedFlow =
                                combine(
                                    basePagerFlow,
                                    _episodeStatusUpdates,
                                    _seasonWatchStatusOverride,
                                ) { pagingData, updates, seasonOverride ->
                                    pagingData.map { episode ->
                                        val isPlayed =
                                            updates[episode.id] ?: seasonOverride ?: episode.played
                                        episode.copy(
                                            played = isPlayed,
                                            playbackPositionTicks =
                                                if (isPlayed && !episode.played) 0L
                                                else episode.playbackPositionTicks,
                                        )
                                    }
                                }

                            _episodesPagingData.value = combinedFlow
                            _uiState.value =
                                _uiState.value.copy(episodesPagingData = _episodesPagingData.value)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load episodes")
                        }
                    }
                    launch {
                        try {
                            val userId = getCurrentUserId()
                            if (userId != null) {
                                val features =
                                    jellyfinRepository.getSpecialFeatures(item.id, userId)
                                _uiState.value = _uiState.value.copy(specialFeatures = features)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load special features")
                        }
                    }
                }

                is AfinityBoxSet -> {
                    loadBoxSetItems(item.id)
                }

                is AfinityMovie -> {
                    launch {
                        try {
                            val similarItems = jellyfinRepository.getSimilarItems(item.id)
                            _uiState.value = _uiState.value.copy(similarItems = similarItems)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load similar items")
                        }
                    }
                    launch {
                        try {
                            val userId = getCurrentUserId()
                            if (userId != null) {
                                val features = jellyfinRepository.getSpecialFeatures(itemId, userId)
                                _uiState.value = _uiState.value.copy(specialFeatures = features)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load special features")
                        }
                    }
                }

                is AfinityEpisode -> {
                    launch {
                        try {
                            val similarItems = jellyfinRepository.getSimilarItems(item.id)
                            _uiState.value = _uiState.value.copy(similarItems = similarItems)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load similar items")
                        }
                    }
                }
            }
        }
    }

    fun getTrailerUrl(item: AfinityItem): String? {
        return when (item) {
            is AfinityMovie -> item.trailer
            is AfinityShow -> item.trailer
            is AfinityVideo -> item.trailer
            else -> null
        }
    }

    private suspend fun getCurrentUserId(): UUID? {
        return try {
            jellyfinRepository.getCurrentUser()?.id
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            null
        }
    }

    private val _selectedEpisode = MutableStateFlow<AfinityEpisode?>(null)
    val selectedEpisode: StateFlow<AfinityEpisode?> = _selectedEpisode.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)

    private val _selectedEpisodeWatchlistStatus = MutableStateFlow(false)
    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> =
        _selectedEpisodeWatchlistStatus.asStateFlow()

    private val _selectedEpisodeDownloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> =
        _selectedEpisodeDownloadInfo.asStateFlow()

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true
                val fullEpisode =
                    try {
                        jellyfinRepository
                            .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                            ?.toAfinityEpisode(jellyfinRepository, null)
                    } catch (e: Exception) {
                        Timber.d(e, "Failed to load episode from online, trying database")
                        try {
                            val userId = authRepository.currentUser.value?.id
                            if (userId != null) {
                                databaseRepository.getEpisode(episode.id, userId)
                            } else null
                        } catch (dbError: Exception) {
                            Timber.e(dbError, "Failed to load episode from database")
                            null
                        }
                    }

                if (fullEpisode != null) {
                    _selectedEpisode.value = fullEpisode
                } else {
                    _selectedEpisode.value = episode
                }

                _selectedEpisodeWatchlistStatus.value = episode.liked

                try {
                    val episodeDownload = downloadRepository.getDownloadByItemId(episode.id)
                    _selectedEpisodeDownloadInfo.value = episodeDownload
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load episode download status")
                    _selectedEpisodeDownloadInfo.value = null
                }

                launch {
                    downloadRepository.getAllDownloadsFlow().collect { downloads ->
                        val currentEpisodeId = _selectedEpisode.value?.id
                        if (currentEpisodeId != null) {
                            val episodeDownload = downloads.find { it.itemId == currentEpisodeId }
                            _selectedEpisodeDownloadInfo.value = episodeDownload
                        }
                    }
                }

                _isLoadingEpisode.value = false
            } catch (e: Exception) {
                Timber.e(e, "Failed to load full episode details")
                _selectedEpisode.value = episode
                _selectedEpisodeWatchlistStatus.value = false
                _isLoadingEpisode.value = false
            }
        }
    }

    fun toggleEpisodeFavorite(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val success =
                    if (episode.favorite) {
                        userDataRepository.removeFromFavorites(episode.id)
                    } else {
                        userDataRepository.addToFavorites(episode.id)
                    }

                if (success) {
                    _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode favorite")
            }
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val isLiked = _selectedEpisodeWatchlistStatus.value

                _selectedEpisodeWatchlistStatus.value = !isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = !isLiked)

                val success = userDataRepository.setLike(itemId = episode.id, isLiked = !isLiked)

                if (!success) {
                    _selectedEpisodeWatchlistStatus.value = isLiked
                    _selectedEpisode.value = _selectedEpisode.value?.copy(liked = isLiked)
                    Timber.w("Failed to toggle like status")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode like status")
                val userData = userDataRepository.getUserData(episode.id)
                val isLiked = userData?.likes == true
                _selectedEpisodeWatchlistStatus.value = isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = isLiked)
            }
        }
    }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val isNowPlayed = !episode.played
                _episodeStatusUpdates.value += (episode.id to isNowPlayed)
                val updatedEpisode = episode.copy(played = isNowPlayed, playbackPositionTicks = 0)
                _selectedEpisode.value = updatedEpisode
                updateOptimisticCounters(episode, isNowPlayed)
                val success =
                    if (episode.played) {
                        userDataRepository.markUnwatched(episode.id)
                    } else {
                        userDataRepository.markWatched(episode.id)
                    }

                if (success) {
                    mediaRepository.refreshItemUserData(episode.id, FieldSets.REFRESH_USER_DATA)

                    val currentItem = _uiState.value.item
                    val routingSeriesId =
                        when (currentItem) {
                            is AfinitySeason -> currentItem.seriesId
                            is AfinityShow -> currentItem.id
                            else -> episode.seriesId
                        }
                    val routingSeasonId =
                        when (currentItem) {
                            is AfinitySeason -> currentItem.id
                            is AfinityShow ->
                                _uiState.value.seasons
                                    .find { s -> s.episodes.any { ep -> ep.id == episode.id } }
                                    ?.id ?: episode.seasonId
                            else -> episode.seasonId
                        }

                    playbackStateManager.notifyItemChanged(
                        episode.id,
                        routingSeriesId,
                        routingSeasonId,
                    )

                    if (updatedEpisode.played) {
                        mediaRepository.invalidateNextUpCache()
                    }
                } else {
                    val revertedStatus = !isNowPlayed
                    _episodeStatusUpdates.value += (episode.id to revertedStatus)
                    _selectedEpisode.value = episode
                    Timber.w("Failed to toggle watched status")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
                _selectedEpisode.value = episode
            }
        }
    }

    private fun updateOptimisticCounters(episode: AfinityEpisode, isNowPlayed: Boolean) {
        val change = if (isNowPlayed) -1 else 1

        val updatedSeasons =
            _uiState.value.seasons.map { season ->
                if (season.id == episode.seasonId) {
                    val currentCount = season.unplayedItemCount ?: 0
                    val newCount = (currentCount + change).coerceAtLeast(0)
                    season.copy(unplayedItemCount = newCount, played = newCount == 0)
                } else {
                    season
                }
            }

        val updatedItem =
            when (val currentItem = _uiState.value.item) {
                is AfinityShow if currentItem.id == episode.seriesId -> {
                    val currentCount = currentItem.unplayedItemCount ?: 0
                    val newCount = (currentCount + change).coerceAtLeast(0)
                    currentItem.copy(unplayedItemCount = newCount, played = newCount == 0)
                }

                is AfinitySeason if currentItem.id == episode.seasonId -> {
                    val currentCount = currentItem.unplayedItemCount ?: 0
                    val newCount = (currentCount + change).coerceAtLeast(0)
                    currentItem.copy(unplayedItemCount = newCount, played = newCount == 0)
                }

                else -> currentItem
            }

        _uiState.value = _uiState.value.copy(seasons = updatedSeasons, item = updatedItem)
    }

    fun clearSelectedEpisode() {
        _selectedEpisode.value = null
        _selectedEpisodeWatchlistStatus.value = false
        _selectedEpisodeDownloadInfo.value = null
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                val currentItem = _uiState.value.item ?: return@launch

                val optimisticItem =
                    when (currentItem) {
                        is AfinityMovie -> currentItem.copy(favorite = !currentItem.favorite)
                        is AfinityShow -> currentItem.copy(favorite = !currentItem.favorite)
                        is AfinityEpisode -> currentItem.copy(favorite = !currentItem.favorite)
                        is AfinityBoxSet -> currentItem.copy(favorite = !currentItem.favorite)
                        is AfinitySeason -> currentItem.copy(favorite = !currentItem.favorite)
                        else -> currentItem
                    }
                _uiState.value = _uiState.value.copy(item = optimisticItem)

                launch {
                    val success =
                        if (currentItem.favorite) {
                            userDataRepository.removeFromFavorites(currentItem.id)
                        } else {
                            userDataRepository.addToFavorites(currentItem.id)
                        }

                    if (!success) {
                        _uiState.value = _uiState.value.copy(item = currentItem)
                        Timber.w("Failed to toggle favorite status, reverted UI")
                    } else {
                        playbackStateManager.notifyItemChanged(
                            currentItem.id,
                            (currentItem as? AfinityEpisode)?.seriesId,
                            (currentItem as? AfinityEpisode)?.seasonId,
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite status")
            }
        }
    }

    fun toggleWatched() {
        viewModelScope.launch {
            try {
                val currentItem = _uiState.value.item ?: return@launch

                val optimisticItem =
                    when (currentItem) {
                        is AfinityMovie ->
                            currentItem.copy(
                                played = !currentItem.played,
                                playbackPositionTicks = 0,
                            )

                        is AfinityShow -> {
                            val newPlayed = !currentItem.played
                            val updatedSeasons =
                                _uiState.value.seasons.map {
                                    it.copy(
                                        played = newPlayed,
                                        unplayedItemCount =
                                            if (newPlayed) 0 else it.unplayedItemCount,
                                    )
                                }
                            _uiState.value = _uiState.value.copy(seasons = updatedSeasons)

                            currentItem.copy(
                                played = newPlayed,
                                unplayedItemCount =
                                    if (newPlayed) 0 else currentItem.unplayedItemCount,
                            )
                        }

                        is AfinityEpisode ->
                            currentItem.copy(
                                played = !currentItem.played,
                                playbackPositionTicks = 0,
                            )

                        is AfinityBoxSet -> {
                            val newPlayed = !currentItem.played
                            currentItem.copy(
                                played = newPlayed,
                                unplayedItemCount =
                                    if (newPlayed) 0
                                    else
                                        (currentItem.unplayedItemCount
                                            ?: _uiState.value.boxSetItems.size),
                            )
                        }

                        is AfinitySeason -> {
                            val newPlayed = !currentItem.played
                            _seasonWatchStatusOverride.value = newPlayed
                            _episodeStatusUpdates.value = emptyMap()

                            currentItem.copy(
                                played = newPlayed,
                                unplayedItemCount =
                                    if (newPlayed) 0 else currentItem.unplayedItemCount,
                            )
                        }

                        is AfinityVideo ->
                            currentItem.copy(
                                played = !currentItem.played,
                                playbackPositionTicks = 0,
                            )

                        else -> currentItem
                    }
                val optimisticBoxSetItems =
                    if (currentItem is AfinityBoxSet) {
                        _uiState.value.boxSetItems.map { child ->
                            when (child) {
                                is AfinityMovie ->
                                    child.copy(
                                        played = !currentItem.played,
                                        playbackPositionTicks =
                                            if (!currentItem.played) child.runtimeTicks else 0,
                                    )
                                is AfinityShow -> child.copy(played = !currentItem.played)
                                is AfinitySeason -> child.copy(played = !currentItem.played)
                                is AfinityEpisode ->
                                    child.copy(
                                        played = !currentItem.played,
                                        playbackPositionTicks = 0,
                                    )

                                else -> child
                            }
                        }
                    } else {
                        _uiState.value.boxSetItems
                    }

                _uiState.value =
                    _uiState.value.copy(item = optimisticItem, boxSetItems = optimisticBoxSetItems)

                launch {
                    val success =
                        if (currentItem.played) {
                            userDataRepository.markUnwatched(currentItem.id)
                        } else {
                            userDataRepository.markWatched(currentItem.id)
                        }

                    if (success) {
                        val refreshed =
                            mediaRepository.refreshItemUserData(
                                currentItem.id,
                                FieldSets.REFRESH_USER_DATA,
                            )
                        playbackStateManager.notifyItemChanged(
                            currentItem.id,
                            (currentItem as? AfinityEpisode)?.seriesId,
                            (currentItem as? AfinityEpisode)?.seasonId,
                        )

                        if (refreshed is AfinityEpisode && refreshed.played) {
                            mediaRepository.invalidateNextUpCache()
                        }
                    } else {
                        _uiState.value =
                            _uiState.value.copy(
                                item = currentItem,
                                boxSetItems = _uiState.value.boxSetItems,
                            )
                        Timber.w("Failed to toggle watched status, reverted UI")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling watched status")
            }
        }
    }

    fun toggleWatchlist() {
        viewModelScope.launch {
            try {
                val currentItem = _uiState.value.item ?: return@launch

                val optimisticItem =
                    when (currentItem) {
                        is AfinityMovie -> currentItem.copy(liked = !currentItem.liked)
                        is AfinityShow -> currentItem.copy(liked = !currentItem.liked)
                        is AfinityEpisode -> currentItem.copy(liked = !currentItem.liked)
                        is AfinitySeason -> currentItem.copy(liked = !currentItem.liked)
                        is AfinityBoxSet -> currentItem.copy(liked = !currentItem.liked)
                        else -> currentItem
                    }
                _uiState.value = _uiState.value.copy(item = optimisticItem)

                launch {
                    val success =
                        userDataRepository.setLike(
                            itemId = currentItem.id,
                            isLiked = !currentItem.liked,
                        )

                    if (!success) {
                        _uiState.value = _uiState.value.copy(item = currentItem)
                        Timber.w("Failed to toggle like status, reverted UI")
                    } else {
                        playbackStateManager.notifyItemChanged(
                            currentItem.id,
                            (currentItem as? AfinityEpisode)?.seriesId,
                            (currentItem as? AfinityEpisode)?.seasonId,
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling like status")
            }
        }
    }

    fun onPlayClick(item: AfinityItem, selection: PlaybackSelection? = null) {
        // TODO: Implement playback with selection
        Timber.d("Play clicked for item: ${item.name}")
        if (selection != null) {
            Timber.d(
                "Selected - MediaSource: ${selection.mediaSourceId}, Audio: ${selection.audioStreamIndex}, Subtitle: ${selection.subtitleStreamIndex}"
            )
        }
    }

    fun getBaseUrl(): String {
        return jellyfinRepository.getBaseUrl()
    }

    fun onDownloadClick() {
        viewModelScope.launch {
            try {
                val item = _selectedEpisode.value ?: _uiState.value.item ?: return@launch
                val sources =
                    item.sources.filter {
                        it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE
                    }

                if (sources.isEmpty()) {
                    Timber.w("No remote sources available for download for item: ${item.name}")
                    return@launch
                }

                if (sources.size == 1) {
                    Timber.d("Only one source available, starting download immediately")
                    val result = downloadRepository.startDownload(item.id, sources.first().id)
                    result
                        .onSuccess { Timber.i("Download started successfully for: ${item.name}") }
                        .onFailure { error -> Timber.e(error, "Failed to start download") }
                } else {
                    _uiState.value = _uiState.value.copy(showQualityDialog = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error preparing download")
            }
        }
    }

    fun onQualitySelected(sourceId: String) {
        viewModelScope.launch {
            try {
                val item = _selectedEpisode.value ?: _uiState.value.item ?: return@launch

                _uiState.value = _uiState.value.copy(showQualityDialog = false)

                val result = downloadRepository.startDownload(item.id, sourceId)
                result
                    .onSuccess { Timber.i("Download started successfully for: ${item.name}") }
                    .onFailure { error -> Timber.e(error, "Failed to start download") }
            } catch (e: Exception) {
                Timber.e(e, "Error starting download")
            }
        }
    }

    fun dismissQualityDialog() {
        _uiState.value = _uiState.value.copy(showQualityDialog = false)
    }

    fun pauseDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo =
                    _uiState.value.downloadInfo
                        ?: _selectedEpisodeDownloadInfo.value
                        ?: return@launch
                val result = downloadRepository.pauseDownload(downloadInfo.id)
                result.onFailure { error -> Timber.e(error, "Failed to pause download") }
            } catch (e: Exception) {
                Timber.e(e, "Error pausing download")
            }
        }
    }

    fun resumeDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo =
                    _uiState.value.downloadInfo
                        ?: _selectedEpisodeDownloadInfo.value
                        ?: return@launch
                val result = downloadRepository.resumeDownload(downloadInfo.id)
                result.onFailure { error -> Timber.e(error, "Failed to resume download") }
            } catch (e: Exception) {
                Timber.e(e, "Error resuming download")
            }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo =
                    _uiState.value.downloadInfo
                        ?: _selectedEpisodeDownloadInfo.value
                        ?: return@launch
                val result = downloadRepository.cancelDownload(downloadInfo.id)
                result
                    .onSuccess { Timber.i("Download cancelled successfully") }
                    .onFailure { error -> Timber.e(error, "Failed to cancel download") }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling download")
            }
        }
    }

    private fun getNextEpisodeOffline(show: AfinityShow): AfinityEpisode? {
        val allEpisodes =
            show.seasons
                .sortedBy { it.indexNumber }
                .flatMap { season -> season.episodes.sortedBy { it.indexNumber } }

        val inProgressEpisode =
            allEpisodes.firstOrNull { episode ->
                episode.playbackPositionTicks > 0 && !episode.played
            }

        if (inProgressEpisode != null) {
            return inProgressEpisode
        }

        return allEpisodes.firstOrNull { !it.played }
    }

    private fun getNextEpisodeForSeasonOffline(season: AfinitySeason): AfinityEpisode? {
        val episodes = season.episodes.sortedBy { it.indexNumber }

        val inProgressEpisode =
            episodes.firstOrNull { episode -> episode.playbackPositionTicks > 0 && !episode.played }

        if (inProgressEpisode != null) {
            return inProgressEpisode
        }

        return episodes.firstOrNull { !it.played }
    }
}

data class ItemDetailUiState(
    val item: AfinityItem? = null,
    val seasons: List<AfinitySeason> = emptyList(),
    val boxSetItems: List<AfinityItem> = emptyList(),
    val containingBoxSets: List<AfinityBoxSet> = emptyList(),
    val similarItems: List<AfinityItem> = emptyList(),
    val specialFeatures: List<AfinityItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val nextEpisode: AfinityEpisode? = null,
    val episodesPagingData: Flow<PagingData<AfinityEpisode>>? = null,
    val showQualityDialog: Boolean = false,
    val downloadInfo: DownloadInfo? = null,
    val tmdbReviews: List<TmdbReview> = emptyList(),
    val isLoadingReviews: Boolean = false,
    val mdbRatings: List<MdbListRating> = emptyList(),
    val isRatingsFromCache: Boolean = false,
)
