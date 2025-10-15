package com.makd.afinity.ui.item

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.extensions.toAfinityBoxSet
import com.makd.afinity.data.models.extensions.toAfinityItem
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
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.utils.IntentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: UUID = UUID.fromString(
        savedStateHandle.get<String>("itemId") ?: throw IllegalArgumentException("itemId is required")
    )

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val _selectedMediaSource = MutableStateFlow<MediaSourceOption?>(null)
    val selectedMediaSource = _selectedMediaSource.asStateFlow()

    fun selectMediaSource(source: MediaSourceOption) {
        _selectedMediaSource.value = source
    }

    init {
        loadItem()

        playbackStateManager.setOnItemUpdatedCallback { updatedItemId ->
            if (updatedItemId == itemId) {
                refreshFromCacheImmediate()
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

                val item = jellyfinRepository.getItem(itemId, fields = FieldSets.ITEM_DETAIL)?.let { baseItemDto ->
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
                        else -> null
                    }
                }

                if (item == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Item not found"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(item = item, isLoading = false)
                loadAdditionalDetails(item)

            } catch (e: Exception) {
                Timber.e(e, "Failed to load item: $itemId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load item: ${e.message}"
                )
            }
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
                            val seasons = jellyfinRepository.getSeasons(item.id)
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

    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                val currentItem = _uiState.value.item ?: return@launch

                val optimisticItem = when (currentItem) {
                    is AfinityMovie -> currentItem.copy(favorite = !currentItem.favorite)
                    is AfinityShow -> currentItem.copy(favorite = !currentItem.favorite)
                    is AfinityEpisode -> currentItem.copy(favorite = !currentItem.favorite)
                    is AfinityBoxSet -> currentItem.copy(favorite = !currentItem.favorite)
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
)