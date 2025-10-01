package com.makd.afinity.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.media.*
import com.makd.afinity.data.models.extensions.*
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.PlaybackSelection

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
                _uiState.value = _uiState.value.copy(isLoading = false)
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
                            loadNextEpisode(freshItem.id)
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

                val item = jellyfinRepository.getItem(itemId)?.let { baseItemDto ->
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

                _uiState.value = _uiState.value.copy(
                    item = item,
                    isLoading = false
                )

                when (item) {
                    is AfinityShow -> {
                        loadNextEpisode(item.id)
                        loadSimilarItems(itemId)
                        loadSeasonsForShow(item.id)
                        loadSpecialFeatures(itemId)
                    }
                    is AfinityBoxSet -> loadBoxSetItems(item.id)
                    is AfinityMovie -> {
                        loadSimilarItems(item.id)
                        loadSpecialFeatures(itemId)
                    }
                    is AfinityEpisode -> loadSimilarItems(item.id)
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

    private suspend fun loadSpecialFeatures(itemId: UUID) {
        try {
            val userId = getCurrentUserId() ?: return
            Timber.d("Loading special features for item: $itemId, user: $userId")
            val specialFeatures = jellyfinRepository.getSpecialFeatures(itemId, userId)
            Timber.d("Found ${specialFeatures.size} special features")
            specialFeatures.forEach { feature ->
                Timber.d("Special feature: ${feature.name} (${feature.javaClass.simpleName})")
            }
            _uiState.value = _uiState.value.copy(specialFeatures = specialFeatures)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load special features for: $itemId")
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

    private fun loadNextEpisode(seriesId: UUID) {
        viewModelScope.launch {
            try {
                val nextEpisode = jellyfinRepository.getEpisodeToPlay(seriesId)
                _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load next episode for series: $seriesId")
            }
        }
    }

    private suspend fun loadSeasonsForShow(showId: UUID) {
        try {
            val seasons = jellyfinRepository.getSeasons(showId)
            _uiState.value = _uiState.value.copy(seasons = seasons)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load seasons for show: $showId")
        }
    }

    private suspend fun loadBoxSetItems(boxSetId: UUID) {
        try {
            Timber.d("Loading items for boxset: $boxSetId")

            val response = jellyfinRepository.getItems(
                parentId = boxSetId,
                includeItemTypes = listOf("MOVIE", "SERIES"),
                limit = 100
            )

            val items = response.items?.mapNotNull { baseItem ->
                baseItem.toAfinityItem(jellyfinRepository.getBaseUrl())
            } ?: emptyList()

            Timber.d("Loaded ${items.size} items for boxset")
            _uiState.value = _uiState.value.copy(boxSetItems = items)

        } catch (e: Exception) {
            Timber.e(e, "Failed to load items for boxset: $boxSetId")
        }
    }

    private suspend fun loadSimilarItems(itemId: UUID) {
        try {
            val similarItems = jellyfinRepository.getSimilarItems(itemId)
            _uiState.value = _uiState.value.copy(similarItems = similarItems)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load similar items for: $itemId")
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

    fun onPlayClick(item: AfinityItem, selection: PlaybackSelection? = null) {
        // TODO: Implement playback with selection
        Timber.d("Play clicked for item: ${item.name}")
        if (selection != null) {
            Timber.d("Selected - MediaSource: ${selection.mediaSourceId}, Audio: ${selection.audioStreamIndex}, Subtitle: ${selection.subtitleStreamIndex}")
        }
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
)