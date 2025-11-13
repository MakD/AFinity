package com.makd.afinity.ui.item

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.extensions.toAfinityBoxSet
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.models.media.toAfinityMovie
import com.makd.afinity.data.models.media.toAfinitySeason
import com.makd.afinity.data.models.media.toAfinityShow
import com.makd.afinity.data.paging.EpisodesPagingSource
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.utils.IntentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val watchlistRepository: WatchlistRepository,
    private val downloadRepository: DownloadRepository,
    private val databaseRepository: DatabaseRepository,
    private val offlineModeManager: OfflineModeManager,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: UUID = UUID.fromString(
        savedStateHandle.get<String>("itemId") ?: throw IllegalArgumentException("itemId is required")
    )

    private val _episodesPagingData = MutableStateFlow<Flow<PagingData<AfinityEpisode>>?>(null)

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val _selectedMediaSource = MutableStateFlow<MediaSourceOption?>(null)
    val selectedMediaSource = _selectedMediaSource.asStateFlow()

    fun selectMediaSource(source: MediaSourceOption) {
        _selectedMediaSource.value = source
    }

    init {
        loadItem()
        observeDownloadStatus()

        playbackStateManager.setOnItemUpdatedCallback { updatedItemId ->
            if (updatedItemId == itemId) {
                refreshFromCacheImmediate()
            }
        }
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
                Timber.e(e, "Failed to observe download status")
            }
        }
    }

    private fun refreshFromCacheImmediate() {
        viewModelScope.launch {
            try {
                val cachedItem = jellyfinRepository.getItemById(itemId)
                if (cachedItem != null) {
                    _uiState.value = _uiState.value.copy(item = cachedItem)
                    Timber.d("UI updated immediately from cache: ${cachedItem.name}")
                    if (cachedItem is AfinityShow) {
                        launch {
                            try {
                                val nextEpisode = jellyfinRepository.getEpisodeToPlay(cachedItem.id)
                                _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to get next episode in background")
                            }
                        }
                    }
                }

                launch {
                    syncWithServerInBackground()
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh from cache")
            }
        }
    }

    private suspend fun syncWithServerInBackground() {
        try {
            val serverItem = jellyfinRepository.getItem(itemId)?.let { baseItemDto ->
                when (baseItemDto.type) {
                    org.jellyfin.sdk.model.api.BaseItemKind.MOVIE ->
                        baseItemDto.toAfinityMovie(jellyfinRepository, null)
                    org.jellyfin.sdk.model.api.BaseItemKind.SERIES ->
                        baseItemDto.toAfinityShow(jellyfinRepository)
                    org.jellyfin.sdk.model.api.BaseItemKind.EPISODE ->
                        baseItemDto.toAfinityEpisode(jellyfinRepository, null)
                    org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET ->
                        baseItemDto.toAfinityBoxSet(jellyfinRepository.getBaseUrl())
                    else -> null
                }
            }

            if (serverItem != null) {
                val currentItem = _uiState.value.item
                if (currentItem == null || hasSignificantChanges(currentItem, serverItem)) {
                    _uiState.value = _uiState.value.copy(item = serverItem)
                    Timber.d("UI updated from server sync: ${serverItem.name}")
                }
            }

        } catch (e: Exception) {
            Timber.w(e, "Background server sync failed (non-critical)")
        }
    }

    private fun hasSignificantChanges(cached: AfinityItem, server: AfinityItem): Boolean {
        return cached.playbackPositionTicks != server.playbackPositionTicks ||
                cached.played != server.played ||
                cached.favorite != server.favorite
    }

    fun refreshManually() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                loadItem()
            } finally {
                // Let loadItem handle the loading state
            }
        }
    }

    fun refreshFromCache() {
        viewModelScope.launch {
            try {
                val freshItem = jellyfinRepository.getItemById(itemId)
                if (freshItem != null) {
                    _uiState.value = _uiState.value.copy(item = freshItem)

                    when (freshItem) {
                        is AfinityShow -> {
                            viewModelScope.launch {
                                try {
                                    val nextEpisode = jellyfinRepository.getEpisodeToPlay(freshItem.id)
                                    _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to load next episode")
                                }
                            }
                        }
                        is AfinityMovie -> {
                        }
                        is AfinityEpisode -> {
                        }
                    }

                    Timber.d("ItemDetail refreshed from cache: ${freshItem.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh item from cache")
            }
        }
    }

    fun loadItem() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val isOffline = offlineModeManager.isCurrentlyOffline()

                val item = if (isOffline) {
                    Timber.d("Device is offline, loading item from database")
                    loadItemFromDatabase()
                } else {
                    jellyfinRepository.getItem(itemId, fields = FieldSets.ITEM_DETAIL)?.let { baseItemDto ->
                        Timber.d("MediaSources count: ${baseItemDto.mediaSources?.size ?: 0}")

                        when (baseItemDto.type) {
                            org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> {
                                baseItemDto.toAfinityMovie(jellyfinRepository, null)
                            }
                            org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
                                baseItemDto.toAfinityShow(jellyfinRepository)
                            }
                            org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
                                baseItemDto.toAfinityEpisode(jellyfinRepository, null)
                            }
                            org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET -> {
                                baseItemDto.toAfinityBoxSet(jellyfinRepository.getBaseUrl())
                            }
                            org.jellyfin.sdk.model.api.BaseItemKind.SEASON -> {
                                baseItemDto.toAfinitySeason(jellyfinRepository.getBaseUrl())
                            }
                            else -> null
                        }
                    }
                }

                if (item == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (isOffline) "Item not available offline" else "Item not found"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(item = item, isLoading = false)

                if (!isOffline) {
                    loadAdditionalDetails(item)
                } else {
                    when (item) {
                        is AfinityShow -> {
                            if (item.seasons.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(seasons = item.seasons)
                                Timber.d("Loaded ${item.seasons.size} seasons from database for show: ${item.name}")

                                val nextEpisode = getNextEpisodeOffline(item)
                                _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                                Timber.d("Next episode for show: ${nextEpisode?.name ?: "none"}")
                            }
                        }
                        is AfinitySeason -> {
                            if (item.episodes.isNotEmpty()) {
                                Timber.d("Setting up ${item.episodes.size} episodes from database for season: ${item.name}")
                                _episodesPagingData.value = kotlinx.coroutines.flow.flowOf(
                                    androidx.paging.PagingData.from(item.episodes.toList())
                                )
                                _uiState.value = _uiState.value.copy(
                                    episodesPagingData = _episodesPagingData.value
                                )

                                val nextEpisode = getNextEpisodeForSeasonOffline(item)
                                _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                                Timber.d("Next episode for season: ${nextEpisode?.name ?: "none"}")
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to load item: $itemId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load item: ${e.message}"
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
                Timber.d("Loaded season from database: ${season.name} with ${season.episodes.size} episodes")
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
            launch {
                try {
                    val isInWatchlist = watchlistRepository.isInWatchlist(item.id)
                    _uiState.value = _uiState.value.copy(isInWatchlist = isInWatchlist)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load watchlist status")
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
                            val seasons = if (item is AfinityShow && item.seasons.isNotEmpty()) {
                                item.seasons
                            } else {
                                jellyfinRepository.getSeasons(item.id)
                            }
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
                            val episodeToPlay = jellyfinRepository.getEpisodeToPlayForSeason(item.id, item.seriesId)
                            _uiState.value = _uiState.value.copy(nextEpisode = episodeToPlay)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load episode to play for season")
                        }
                    }
                    launch {
                        try {
                            val isCurrentlyOffline = offlineModeManager.isCurrentlyOffline()
                            if (isCurrentlyOffline && item is AfinitySeason && item.episodes.isNotEmpty()) {
                                Timber.d("Using ${item.episodes.size} episodes from database for season: ${item.name}")
                                _episodesPagingData.value = kotlinx.coroutines.flow.flowOf(
                                    androidx.paging.PagingData.from(item.episodes.toList())
                                )
                            } else {
                                _episodesPagingData.value = Pager(
                                    config = PagingConfig(
                                        pageSize = 50,
                                        enablePlaceholders = false,
                                        initialLoadSize = 50
                                    )
                                ) {
                                    EpisodesPagingSource(
                                        mediaRepository = mediaRepository,
                                        seasonId = item.id,
                                        seriesId = item.seriesId
                                    )
                                }.flow.cachedIn(viewModelScope)
                            }

                            _uiState.value = _uiState.value.copy(
                                episodesPagingData = _episodesPagingData.value
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load episodes")
                        }
                    }
                    launch {
                        try {
                            val userId = getCurrentUserId()
                            if (userId != null) {
                                val features = jellyfinRepository.getSpecialFeatures(item.id, userId)
                                _uiState.value = _uiState.value.copy(specialFeatures = features)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load special features")
                        }
                    }
                }
                is AfinityBoxSet -> {
                    launch {
                        try {
                            val response = jellyfinRepository.getItems(
                                parentId = item.id,
                                includeItemTypes = listOf("MOVIE", "SERIES"),
                                limit = 100
                            )
                            val items = response.items?.mapNotNull { baseItem ->
                                baseItem.toAfinityItem(jellyfinRepository.getBaseUrl())
                            } ?: emptyList()
                            _uiState.value = _uiState.value.copy(boxSetItems = items)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to load boxset items")
                        }
                    }
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
    val isLoadingEpisode: StateFlow<Boolean> = _isLoadingEpisode.asStateFlow()

    private val _selectedEpisodeWatchlistStatus = MutableStateFlow(false)
    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> = _selectedEpisodeWatchlistStatus.asStateFlow()

    private val _selectedEpisodeDownloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> = _selectedEpisodeDownloadInfo.asStateFlow()

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true
                val fullEpisode = try {
                    jellyfinRepository.getItem(
                        episode.id,
                        fields = FieldSets.ITEM_DETAIL
                    )?.toAfinityEpisode(jellyfinRepository, null)
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

                try {
                    val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load episode watchlist status")
                    _selectedEpisodeWatchlistStatus.value = false
                }

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
                val success = if (episode.favorite) {
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
                val isInWatchlist = _selectedEpisodeWatchlistStatus.value

                _selectedEpisodeWatchlistStatus.value = !isInWatchlist

                val success = if (isInWatchlist) {
                    watchlistRepository.removeFromWatchlist(episode.id)
                } else {
                    watchlistRepository.addToWatchlist(episode.id, "EPISODE")
                }

                if (!success) {
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                    Timber.w("Failed to toggle watchlist status")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watchlist")
                val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                _selectedEpisodeWatchlistStatus.value = isInWatchlist
            }
        }
    }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val success = if (episode.played) {
                    userDataRepository.markUnwatched(episode.id)
                } else {
                    userDataRepository.markWatched(episode.id)
                }

                if (success) {
                    _selectedEpisode.value = episode.copy(
                        played = !episode.played,
                        playbackPositionTicks = if (!episode.played) episode.runtimeTicks else 0
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
            }
        }
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

                val optimisticItem = when (currentItem) {
                    is AfinityMovie -> currentItem.copy(favorite = !currentItem.favorite)
                    is AfinityShow -> currentItem.copy(favorite = !currentItem.favorite)
                    is AfinityEpisode -> currentItem.copy(favorite = !currentItem.favorite)
                    is AfinityBoxSet -> currentItem.copy(favorite = !currentItem.favorite)
                    is AfinitySeason -> currentItem.copy(favorite = !currentItem.favorite)
                    else -> currentItem
                }
                _uiState.value = _uiState.value.copy(item = optimisticItem)

                launch {
                    val success = if (currentItem.favorite) {
                        userDataRepository.removeFromFavorites(currentItem.id)
                    } else {
                        userDataRepository.addToFavorites(currentItem.id)
                    }

                    if (!success) {
                        _uiState.value = _uiState.value.copy(item = currentItem)
                        Timber.w("Failed to toggle favorite status, reverted UI")
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

                val optimisticItem = when (currentItem) {
                    is AfinityMovie -> currentItem.copy(
                        played = !currentItem.played,
                        playbackPositionTicks = if (!currentItem.played) currentItem.runtimeTicks else 0
                    )
                    is AfinityShow -> currentItem.copy(played = !currentItem.played)
                    is AfinityEpisode -> currentItem.copy(
                        played = !currentItem.played,
                        playbackPositionTicks = if (!currentItem.played) currentItem.runtimeTicks else 0
                    )
                    is AfinityBoxSet -> currentItem.copy(played = !currentItem.played)
                    is AfinitySeason -> currentItem.copy(played = !currentItem.played)
                    else -> currentItem
                }
                _uiState.value = _uiState.value.copy(item = optimisticItem)

                launch {
                    val success = if (currentItem.played) {
                        userDataRepository.markUnwatched(currentItem.id)
                    } else {
                        userDataRepository.markWatched(currentItem.id)
                    }

                    if (!success) {
                        _uiState.value = _uiState.value.copy(item = currentItem)
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
                val isCurrentlyInWatchlist = _uiState.value.isInWatchlist

                _uiState.value = _uiState.value.copy(isInWatchlist = !isCurrentlyInWatchlist)

                launch {
                    val itemType = when (currentItem) {
                        is AfinityMovie -> "MOVIE"
                        is AfinityShow -> "SERIES"
                        is AfinitySeason -> "SEASON"
                        is AfinityEpisode -> "EPISODE"
                        else -> "UNKNOWN"
                    }

                    val success = if (isCurrentlyInWatchlist) {
                        watchlistRepository.removeFromWatchlist(currentItem.id)
                    } else {
                        watchlistRepository.addToWatchlist(currentItem.id, itemType)
                    }

                    if (!success) {
                        _uiState.value = _uiState.value.copy(isInWatchlist = isCurrentlyInWatchlist)
                        Timber.w("Failed to toggle watchlist status, reverted UI")
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Error toggling watchlist status")
            }
        }
    }

    fun onPlayClick(item: AfinityItem, selection: PlaybackSelection? = null) {
        // TODO: Implement playback with selection
        Timber.d("Play clicked for item: ${item.name}")
        if (selection != null) {
            Timber.d("Selected - MediaSource: ${selection.mediaSourceId}, Audio: ${selection.audioStreamIndex}, Subtitle: ${selection.subtitleStreamIndex}")
        }
    }

    fun onPlayTrailerClick(context: Context, item: AfinityItem) {
        Timber.d("Play trailer clicked: ${item.name}")
        val trailerUrl = when (item) {
            is AfinityMovie -> item.trailer
            is AfinityShow -> item.trailer
            is AfinityVideo -> item.trailer
            else -> null
        }
        IntentUtils.openYouTubeUrl(context, trailerUrl)
    }

    fun getBaseUrl(): String {
        return jellyfinRepository.getBaseUrl()
    }

    suspend fun getEpisodeToPlay(seriesId: UUID): AfinityEpisode? {
        return jellyfinRepository.getEpisodeToPlay(seriesId)
    }

    suspend fun getEpisodeToPlayForSeason(seasonId: UUID, seriesId: UUID): AfinityEpisode? {
        return jellyfinRepository.getEpisodeToPlayForSeason(seasonId, seriesId)
    }

    fun onDownloadClick() {
        viewModelScope.launch {
            try {
                val item = _selectedEpisode.value ?: _uiState.value.item ?: return@launch
                val sources = item.sources.filter { it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE }

                if (sources.isEmpty()) {
                    Timber.w("No remote sources available for download for item: ${item.name}")
                    return@launch
                }

                _uiState.value = _uiState.value.copy(showQualityDialog = true)
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
                result.onSuccess {
                    Timber.i("Download started successfully for: ${item.name}")
                }.onFailure { error ->
                    Timber.e(error, "Failed to start download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting download")
            }
        }
    }

    fun dismissQualityDialog() {
        _uiState.value = _uiState.value.copy(showQualityDialog = false)
    }

    fun isItemDownloaded(): Boolean {
        val item = _uiState.value.item ?: return false
        return item.sources.any { it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL }
    }

    fun pauseDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo = _uiState.value.downloadInfo ?: return@launch
                val result = downloadRepository.pauseDownload(downloadInfo.id)
                result.onFailure { error ->
                    Timber.e(error, "Failed to pause download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error pausing download")
            }
        }
    }

    fun resumeDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo = _uiState.value.downloadInfo ?: return@launch
                val result = downloadRepository.resumeDownload(downloadInfo.id)
                result.onFailure { error ->
                    Timber.e(error, "Failed to resume download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error resuming download")
            }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo = _uiState.value.downloadInfo ?: return@launch
                val result = downloadRepository.cancelDownload(downloadInfo.id)
                result.onSuccess {
                    Timber.i("Download cancelled successfully")
                }.onFailure { error ->
                    Timber.e(error, "Failed to cancel download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling download")
            }
        }
    }

    private fun getNextEpisodeOffline(show: AfinityShow): AfinityEpisode? {
        val allEpisodes = show.seasons
            .sortedBy { it.indexNumber }
            .flatMap { season ->
                season.episodes.sortedBy { it.indexNumber }
            }

        val inProgressEpisode = allEpisodes.firstOrNull { episode ->
            episode.playbackPositionTicks > 0 && !episode.played
        }

        if (inProgressEpisode != null) {
            return inProgressEpisode
        }

        return allEpisodes.firstOrNull { !it.played }
    }


    private fun getNextEpisodeForSeasonOffline(season: AfinitySeason): AfinityEpisode? {
        val episodes = season.episodes.sortedBy { it.indexNumber }

        val inProgressEpisode = episodes.firstOrNull { episode ->
            episode.playbackPositionTicks > 0 && !episode.played
        }

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
    val similarItems: List<AfinityItem> = emptyList(),
    val specialFeatures: List<AfinityItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val nextEpisode: AfinityEpisode? = null,
    val isInWatchlist: Boolean = false,
    val episodesPagingData: Flow<PagingData<AfinityEpisode>>? = null,
    val showQualityDialog: Boolean = false,
    val downloadInfo: DownloadInfo? = null
)