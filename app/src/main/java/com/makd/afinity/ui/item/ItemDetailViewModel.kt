package com.makd.afinity.ui.item

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.makd.afinity.R
import com.makd.afinity.data.database.entities.ItemMetadataCacheEntity
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackEvent
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
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
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.delegates.ItemDownloadDelegate
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@OptIn(FlowPreview::class)
@HiltViewModel
class ItemDetailViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val sessionManager: SessionManager,
    private val downloadRepository: DownloadRepository,
    private val databaseRepository: DatabaseRepository,
    private val offlineModeManager: OfflineModeManager,
    private val authRepository: AuthRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val mediaChangeManager: MediaChangeManager,
    private val serverRepository: ServerRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val tmdbApiService: TmdbApiService,
    private val itemDownloadDelegate: ItemDownloadDelegate,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: UUID =
        UUID.fromString(
            savedStateHandle.get<String>("itemId")
                ?: throw IllegalArgumentException("itemId is required")
        )

    private val itemType: String? = savedStateHandle.get<String>("itemType")
    private val seriesId: UUID? =
        savedStateHandle.get<String>("seriesId")?.let {
            try {
                UUID.fromString(it)
            } catch (_: Exception) {
                null
            }
        }

    private val _episodesPagingData = MutableStateFlow<Flow<PagingData<AfinityEpisode>>?>(null)
    private val _episodeStatusUpdates = MutableStateFlow<Map<UUID, Boolean>>(emptyMap())
    private var bulkDownloadJob: Job? = null
    private val _seasonWatchStatusOverride = MutableStateFlow<Boolean?>(null)

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    val canDownload: StateFlow<Boolean> =
        preferencesRepository
            .getDownloadWifiOnlyFlow()
            .combine(networkMonitor.isOnWifiFlow) { wifiOnly, onWifi -> !wifiOnly || onWifi }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _selectedMediaSource = MutableStateFlow<MediaSourceOption?>(null)
    val selectedMediaSource = _selectedMediaSource.asStateFlow()
    private val backgroundSyncTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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

        viewModelScope.launch {
            backgroundSyncTrigger.debounce(500L).collect {
                Timber.d("Debounced background sync triggered for item: $itemId")
                syncWithServerInBackground()
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
                }
            }
        }

        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                val currentItem = _uiState.value.item ?: return@collect

                val isRelated =
                    event.itemId == currentItem.id ||
                        event.seriesId == currentItem.id ||
                        event.seasonId == currentItem.id ||
                        event.itemId == _uiState.value.nextEpisode?.id

                if (isRelated) {
                    Timber.d("Item Detail invalidated by socket event. Re-fetching.")

                    refreshFromCacheImmediate(skipNetworkSync = true)

                    backgroundSyncTrigger.tryEmit(Unit)
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
    }

    private fun observeDownloadStatus() {
        viewModelScope.launch {
            try {
                combine(
                        _uiState.map { it.item }.distinctUntilChanged(),
                        downloadRepository.getAllDownloadsFlow(),
                    ) { item, downloads ->
                        computeDownloadInfo(item, downloads)
                    }
                    .collect { downloadInfo ->
                        _uiState.value = _uiState.value.copy(downloadInfo = downloadInfo)
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to observe download status")
            }
        }
    }

    private fun computeDownloadInfo(
        item: AfinityItem?,
        downloads: List<DownloadInfo>,
    ): DownloadInfo? {
        return when (item) {
            is AfinityShow -> {
                val seriesDownloads = downloads.filter { it.seriesId == item.id.toString() }
                aggregateDownloadInfo(seriesDownloads, item.id)
            }
            is AfinitySeason -> {
                val seriesDownloads = downloads.filter {
                    it.seriesId == item.seriesId.toString() && it.seasonNumber == item.indexNumber
                }
                aggregateDownloadInfo(seriesDownloads, item.id)
            }
            else -> downloads.find { it.itemId == itemId }
        }
    }

    private fun aggregateDownloadInfo(
        downloads: List<DownloadInfo>,
        parentId: UUID,
    ): DownloadInfo? {
        if (downloads.isEmpty()) return null
        val status =
            when {
                downloads.any { it.status == DownloadStatus.DOWNLOADING } ->
                    DownloadStatus.DOWNLOADING
                downloads.any { it.status == DownloadStatus.QUEUED } -> DownloadStatus.QUEUED
                downloads.all { it.status == DownloadStatus.COMPLETED } -> DownloadStatus.COMPLETED
                downloads.any { it.status == DownloadStatus.FAILED } -> DownloadStatus.FAILED
                else -> DownloadStatus.PAUSED
            }
        val first = downloads.first()
        return DownloadInfo(
            id = parentId,
            itemId = parentId,
            itemName = first.seriesName ?: first.itemName,
            itemType = first.itemType,
            sourceId = "",
            sourceName = "",
            status = status,
            progress = downloads.map { it.progress }.average().toFloat(),
            bytesDownloaded = downloads.sumOf { it.bytesDownloaded },
            totalBytes = downloads.sumOf { it.totalBytes },
            filePath = null,
            error = null,
            createdAt = downloads.minOf { it.createdAt },
            updatedAt = downloads.maxOf { it.updatedAt },
            serverId = first.serverId,
            userId = first.userId,
        )
    }

    private fun refreshFromCacheImmediate(skipNetworkSync: Boolean = false) {
        viewModelScope.launch {
            try {
                val cachedItem = mediaRepository.getItemById(itemId)
                if (cachedItem != null) {
                    _uiState.update { state ->
                        val currentItem = state.item
                        val mergedItem =
                            if (currentItem != null) patchUserData(currentItem, cachedItem)
                            else cachedItem
                        state.copy(item = mergedItem)
                    }

                    when (cachedItem) {
                        is AfinityShow -> {
                            launch {
                                try {
                                    val nextEpisode =
                                        mediaRepository.getEpisodeToPlay(cachedItem.id)
                                    _uiState.update { it.copy(nextEpisode = nextEpisode) }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to get next episode")
                                }
                            }
                            launch {
                                try {
                                    val seasons = mediaRepository.getSeasons(cachedItem.id)
                                    _uiState.update { it.copy(seasons = seasons) }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to get seasons")
                                }
                            }
                        }
                        is AfinitySeason -> {
                            launch {
                                try {
                                    val nextEpisode =
                                        mediaRepository.getEpisodeToPlayForSeason(
                                            cachedItem.id,
                                            cachedItem.seriesId,
                                        )
                                    _uiState.update { it.copy(nextEpisode = nextEpisode) }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to get next episode for season")
                                }
                            }
                        }
                        is AfinityBoxSet -> loadBoxSetItems(cachedItem.id)
                    }
                }

                if (!skipNetworkSync) {
                    launch { syncWithServerInBackground() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh from cache")
            }
        }
    }

    private fun updateSimilarItemsOnSync(syncedItemId: UUID) {
        viewModelScope.launch {
            try {
                val targetId =
                    when (val syncedItem = mediaRepository.getItemById(syncedItemId)) {
                        is AfinityEpisode -> syncedItem.seriesId
                        is AfinitySeason -> syncedItem.seriesId
                        else -> syncedItemId
                    }
                val index = _uiState.value.similarItems.indexOfFirst { it.id == targetId }
                if (index == -1) return@launch
                val updatedItem = mediaRepository.getItemById(targetId) ?: return@launch
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
                mediaRepository.getItem(itemId, fields = FieldSets.ITEM_DETAIL)?.let { baseItemDto
                    ->
                    when (baseItemDto.type) {
                        BaseItemKind.MOVIE ->
                            baseItemDto.toAfinityMovie(mediaRepository.getBaseUrl(), null)
                        BaseItemKind.SERIES ->
                            baseItemDto.toAfinityShow(mediaRepository.getBaseUrl())
                        BaseItemKind.EPISODE ->
                            baseItemDto.toAfinityEpisode(mediaRepository.getBaseUrl(), null)
                        BaseItemKind.BOX_SET ->
                            baseItemDto.toAfinityBoxSet(mediaRepository.getBaseUrl())
                        BaseItemKind.SEASON -> {
                            val season = baseItemDto.toAfinitySeason(mediaRepository.getBaseUrl())
                            if (season.runtimeTicks == 0L) {
                                try {
                                    val series =
                                        mediaRepository.getItem(
                                            season.seriesId,
                                            fields = FieldSets.REFRESH_USER_DATA,
                                        )
                                    season.copy(runtimeTicks = series?.runTimeTicks ?: 0L)
                                } catch (_: Exception) {
                                    season
                                }
                            } else season
                        }
                        else -> null
                    }
                }

            if (serverItem != null) {
                _uiState.update { state ->
                    val currentItem = state.item
                    val mergedItem =
                        if (currentItem != null) patchUserData(currentItem, serverItem)
                        else serverItem
                    state.copy(item = mergedItem)
                }

                when (serverItem) {
                    is AfinityShow -> {
                        try {
                            val nextEpisode = mediaRepository.getEpisodeToPlay(serverItem.id)
                            _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to refresh next episode in background sync")
                        }
                        try {
                            val seasons = mediaRepository.getSeasons(serverItem.id)
                            _uiState.value = _uiState.value.copy(seasons = seasons)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to refresh seasons in background sync")
                        }
                    }
                    is AfinitySeason -> {
                        try {
                            val nextEpisode =
                                mediaRepository.getEpisodeToPlayForSeason(
                                    serverItem.id,
                                    serverItem.seriesId,
                                )
                            _uiState.value = _uiState.value.copy(nextEpisode = nextEpisode)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to refresh next episode in background sync")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Background server sync failed (non-critical)")
        }
    }

    private fun loadBoxSetItems(boxSetId: UUID) {
        viewModelScope.launch {
            try {
                val response =
                    mediaRepository.getItems(
                        parentId = boxSetId,
                        includeItemTypes = listOf("MOVIE", "SERIES", "SEASON", "EPISODE"),
                        limit = 100,
                        sortBy = SortBy.RELEASE_DATE,
                        fields = FieldSets.MINIMAL,
                    )
                val items =
                    response.items.mapNotNull { baseItem ->
                        val item = baseItem.toAfinityItem(mediaRepository.getBaseUrl())
                        if (item is AfinitySeason && item.runtimeTicks == 0L) {
                            try {
                                val series =
                                    mediaRepository.getItem(
                                        item.seriesId,
                                        fields = FieldSets.MINIMAL,
                                    )
                                item.copy(runtimeTicks = series?.runTimeTicks ?: 0L)
                            } catch (_: Exception) {
                                item
                            }
                        } else item
                    }
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
                if (!isOffline) {
                    launchParallelFetches()
                }

                val item =
                    if (isOffline) {
                        loadItemFromDatabase()
                    } else {
                        mediaRepository.getItem(itemId, fields = FieldSets.ITEM_DETAIL)?.let {
                            baseItemDto ->
                            when (baseItemDto.type) {
                                BaseItemKind.MOVIE ->
                                    baseItemDto.toAfinityMovie(mediaRepository.getBaseUrl(), null)
                                BaseItemKind.SERIES ->
                                    baseItemDto.toAfinityShow(mediaRepository.getBaseUrl())
                                BaseItemKind.EPISODE ->
                                    baseItemDto.toAfinityEpisode(mediaRepository.getBaseUrl(), null)
                                BaseItemKind.BOX_SET ->
                                    baseItemDto.toAfinityBoxSet(mediaRepository.getBaseUrl())
                                BaseItemKind.SEASON -> {
                                    val season =
                                        baseItemDto.toAfinitySeason(mediaRepository.getBaseUrl())
                                    if (season.runtimeTicks == 0L) {
                                        try {
                                            val series =
                                                mediaRepository.getItem(
                                                    season.seriesId,
                                                    fields = FieldSets.REFRESH_USER_DATA,
                                                )
                                            season.copy(runtimeTicks = series?.runTimeTicks ?: 0L)
                                        } catch (_: Exception) {
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
                    if (item is AfinityMovie || item is AfinityShow) {
                        launch { loadReviewsAndRatings(item) }
                    }
                    if (item is AfinityBoxSet) {
                        loadBoxSetItems(item.id)
                    }
                    if (item is AfinityMovie && (item.partCount ?: 0) > 1) {
                        launch {
                            try {
                                val parts = mediaRepository.getAdditionalParts(item.id)
                                if (parts.isNotEmpty()) {
                                    _uiState.update { it.copy(movieParts = parts) }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to fetch movie parts")
                            }
                        }
                    }
                } else {
                    if (item is AfinityMovie || item is AfinityShow) {
                        val offlineSession = sessionManager.currentSession.value
                        val cachedMetadata =
                            if (offlineSession != null) {
                                databaseRepository.getItemMetadata(
                                    item.id,
                                    offlineSession.serverId,
                                    offlineSession.userId.toString(),
                                )
                            } else null
                        if (cachedMetadata != null) {
                            _uiState.value =
                                _uiState.value.copy(
                                    tmdbReviews = cachedMetadata.tmdbReviews,
                                    mdbRatings = cachedMetadata.mdbRatings,
                                    isRatingsFromCache = true,
                                )
                        }
                    }
                    when (item) {
                        is AfinityShow -> {
                            if (item.seasons.isNotEmpty()) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        seasons = item.seasons,
                                        nextEpisode = getNextEpisodeOffline(item),
                                    )
                            }
                        }
                        is AfinitySeason -> {
                            if (item.episodes.isNotEmpty()) {
                                val pagingData =
                                    kotlinx.coroutines.flow.flowOf(
                                        PagingData.from(item.episodes.toList())
                                    )
                                _episodesPagingData.value = pagingData
                                _uiState.value =
                                    _uiState.value.copy(
                                        episodesPagingData = pagingData,
                                        nextEpisode = getNextEpisodeForSeasonOffline(item),
                                    )
                            } else {
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
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error =
                            context.getString(R.string.error_failed_load_item_fmt, e.message ?: ""),
                    )
            }
        }
    }

    private fun launchParallelFetches() {
        when (itemType?.uppercase()) {
            "SERIES" -> {
                fetchNextUp()
                viewModelScope.launch {
                    try {
                        val similar = mediaRepository.getSimilarItems(itemId)
                        _uiState.update { it.copy(similarItems = similar) }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get similar items")
                    }
                }
                viewModelScope.launch {
                    try {
                        val seasons = mediaRepository.getSeasons(itemId)
                        _uiState.update { it.copy(seasons = seasons) }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get seasons")
                    }
                }
                viewModelScope.launch {
                    try {
                        getCurrentUserId()?.let { id ->
                            val features = mediaRepository.getSpecialFeatures(itemId, id)
                            _uiState.update { it.copy(specialFeatures = features) }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get special features")
                    }
                }
            }
            "SEASON" -> {
                fetchNextUp()
                if (seriesId != null) {
                    viewModelScope.launch {
                        try {
                            val basePagerFlow =
                                Pager(
                                        config =
                                            PagingConfig(
                                                pageSize = 50,
                                                enablePlaceholders = false,
                                                initialLoadSize = 50,
                                            )
                                    ) {
                                        EpisodesPagingSource(mediaRepository, itemId, seriesId)
                                    }
                                    .flow
                                    .cachedIn(viewModelScope)
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
                            _uiState.update {
                                it.copy(episodesPagingData = _episodesPagingData.value)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to get episodes flow")
                        }
                    }
                }
                viewModelScope.launch {
                    try {
                        getCurrentUserId()?.let { id ->
                            val features = mediaRepository.getSpecialFeatures(itemId, id)
                            _uiState.update { it.copy(specialFeatures = features) }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get special features")
                    }
                }
            }
            else -> {
                viewModelScope.launch {
                    try {
                        val similar = mediaRepository.getSimilarItems(itemId)
                        _uiState.update { it.copy(similarItems = similar) }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get similar items")
                    }
                }
                viewModelScope.launch {
                    try {
                        getCurrentUserId()?.let { id ->
                            val features = mediaRepository.getSpecialFeatures(itemId, id)
                            _uiState.update { it.copy(specialFeatures = features) }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get special features")
                    }
                }
            }
        }
    }

    private fun fetchNextUp() {
        viewModelScope.launch {
            try {
                val nextEp =
                    when (itemType?.uppercase()) {
                        "SERIES" -> mediaRepository.getEpisodeToPlay(itemId)
                        "SEASON" -> {
                            if (seriesId != null)
                                mediaRepository.getEpisodeToPlayForSeason(itemId, seriesId)
                            else null
                        }
                        else -> null
                    }
                if (nextEp != null) {
                    _uiState.update { it.copy(nextEpisode = nextEp) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch parallel next episode")
            }
        }
    }

    private suspend fun loadItemFromDatabase(): AfinityItem? {
        return try {
            val userId = authRepository.currentUser.value?.id ?: return null
            databaseRepository.getMovie(itemId, userId)
                ?: databaseRepository.getShow(itemId, userId)
                ?: databaseRepository.getSeason(itemId, userId)
                ?: databaseRepository.getEpisode(itemId, userId)
        } catch (_: Exception) {
            null
        }
    }

    private fun patchUserData(currentItem: AfinityItem, updatedItem: AfinityItem): AfinityItem {
        return when (currentItem) {
            is AfinityMovie ->
                currentItem.copy(
                    played = updatedItem.played,
                    playbackPositionTicks = updatedItem.playbackPositionTicks,
                    favorite = updatedItem.favorite,
                )
            is AfinityEpisode ->
                currentItem.copy(
                    played = updatedItem.played,
                    playbackPositionTicks = updatedItem.playbackPositionTicks,
                    favorite = updatedItem.favorite,
                )
            is AfinityShow ->
                currentItem.copy(
                    played = updatedItem.played,
                    favorite = updatedItem.favorite,
                    unplayedItemCount =
                        (updatedItem as? AfinityShow)?.unplayedItemCount
                            ?: currentItem.unplayedItemCount,
                )
            is AfinitySeason ->
                currentItem.copy(
                    played = updatedItem.played,
                    favorite = updatedItem.favorite,
                    unplayedItemCount =
                        (updatedItem as? AfinitySeason)?.unplayedItemCount
                            ?: currentItem.unplayedItemCount,
                )
            is AfinityBoxSet ->
                currentItem.copy(
                    played = updatedItem.played,
                    favorite = updatedItem.favorite,
                    unplayedItemCount =
                        (updatedItem as? AfinityBoxSet)?.unplayedItemCount
                            ?: currentItem.unplayedItemCount,
                )
            is AfinityVideo ->
                currentItem.copy(
                    played = updatedItem.played,
                    playbackPositionTicks = updatedItem.playbackPositionTicks,
                    favorite = updatedItem.favorite,
                )
            else -> updatedItem
        }
    }

    private suspend fun loadReviewsAndRatings(item: AfinityItem) {
        val userId = authRepository.currentUser.value?.id
        try {
            _uiState.update { it.copy(isLoadingReviews = true) }
            val session = sessionManager.currentSession.value
            val cachedMetadata =
                if (session != null) {
                    databaseRepository.getItemMetadata(
                        item.id,
                        session.serverId,
                        session.userId.toString(),
                    )
                } else null

            val cacheAgeMs = System.currentTimeMillis() - (cachedMetadata?.lastUpdated ?: 0L)
            val isCacheValid = cacheAgeMs < 48 * 60 * 60 * 1000L

            if (
                cachedMetadata != null &&
                    isCacheValid &&
                    (cachedMetadata.tmdbReviews.isNotEmpty() ||
                        cachedMetadata.mdbRatings.isNotEmpty())
            ) {
                _uiState.update {
                    it.copy(
                        tmdbReviews = cachedMetadata.tmdbReviews,
                        mdbRatings = cachedMetadata.mdbRatings,
                        isRatingsFromCache = true,
                        isLoadingReviews = false,
                    )
                }
            } else {
                val tmdbId = item.providerIds?.get("Tmdb")
                var fetchedReviews = emptyList<TmdbReview>()
                var fetchedRatings = emptyList<MdbListRating>()

                if (tmdbId != null && userId != null) {
                    val serverId = session?.serverId ?: serverRepository.currentServer.value?.id
                    val tmdbKey = serverId?.let {
                        securePreferencesRepository.getTmdbApiKey(it, userId.toString())
                    }
                    kotlinx.coroutines.coroutineScope {
                        val reviewsDeferred = async {
                            try {
                                if (!tmdbKey.isNullOrBlank()) {
                                    when (item) {
                                        is AfinityMovie ->
                                            tmdbApiService.getMovieReviews(tmdbId, tmdbKey).results
                                        is AfinityShow ->
                                            tmdbApiService.getSeriesReviews(tmdbId, tmdbKey).results
                                        else -> emptyList()
                                    }
                                } else emptyList()
                            } catch (e: Exception) {
                                Timber.w(
                                    e,
                                    "Failed to fetch TMDB reviews during network transition",
                                )
                                emptyList()
                            }
                        }

                        val ratingsDeferred = async {
                            try {
                                val ratings =
                                    mediaRepository.getMdbListRatings(tmdbId, item is AfinityMovie)
                                ratings.filter {
                                    it.source.lowercase() !in listOf("imdb", "tomatoes") &&
                                        it.value != null
                                }
                            } catch (e: Exception) {
                                Timber.w(
                                    e,
                                    "Failed to fetch MDBList ratings during network transition",
                                )
                                emptyList()
                            }
                        }

                        fetchedReviews = reviewsDeferred.await()
                        fetchedRatings = ratingsDeferred.await()
                    }
                }

                _uiState.update {
                    it.copy(
                        tmdbReviews = fetchedReviews,
                        mdbRatings = fetchedRatings,
                        isRatingsFromCache = false,
                        isLoadingReviews = false,
                    )
                }

                if (
                    (fetchedReviews.isNotEmpty() || fetchedRatings.isNotEmpty()) && session != null
                ) {
                    databaseRepository.insertItemMetadata(
                        ItemMetadataCacheEntity(
                            itemId = item.id,
                            serverId = session.serverId,
                            userId = session.userId.toString(),
                            tmdbReviews = fetchedReviews,
                            mdbRatings = fetchedRatings,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load reviews/ratings")
            _uiState.update { it.copy(isLoadingReviews = false) }
        }
    }

    fun getTrailerUrl(item: AfinityItem): String? =
        when (item) {
            is AfinityMovie -> item.trailer
            is AfinityShow -> item.trailer
            is AfinityVideo -> item.trailer
            else -> null
        }

    private fun getCurrentUserId(): UUID? = sessionManager.currentSession.value?.userId

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
                        mediaRepository
                            .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                            ?.toAfinityEpisode(mediaRepository.getBaseUrl(), null)
                    } catch (_: Exception) {
                        try {
                            authRepository.currentUser.value?.id?.let {
                                databaseRepository.getEpisode(episode.id, it)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                _selectedEpisode.value = fullEpisode ?: episode
                _selectedEpisodeWatchlistStatus.value = episode.liked

                try {
                    _selectedEpisodeDownloadInfo.value =
                        downloadRepository.getDownloadByItemId(episode.id)
                } catch (_: Exception) {
                    _selectedEpisodeDownloadInfo.value = null
                }

                launch {
                    downloadRepository.getAllDownloadsFlow().collect { downloads ->
                        _selectedEpisode.value?.id?.let { id ->
                            _selectedEpisodeDownloadInfo.value = downloads.find { it.itemId == id }
                        }
                    }
                }
                _isLoadingEpisode.value = false
            } catch (_: Exception) {
                _selectedEpisode.value = episode
                _selectedEpisodeWatchlistStatus.value = false
                _isLoadingEpisode.value = false
            }
        }
    }

    fun clearSelectedEpisode() {
        _selectedEpisode.value = null
        _selectedEpisodeWatchlistStatus.value = false
        _selectedEpisodeDownloadInfo.value = null
    }

    fun onDownloadClick() {
        val selectedEpisode = _selectedEpisode.value
        val currentItem = _uiState.value.item
        when {
            selectedEpisode != null ||
                (currentItem !is AfinityShow && currentItem !is AfinitySeason) -> {
                itemDownloadDelegate.onDownloadClick(
                    viewModelScope,
                    selectedEpisode ?: currentItem,
                ) {
                    _uiState.value = _uiState.value.copy(showQualityDialog = true)
                }
            }
            currentItem is AfinitySeason -> {
                bulkDownloadJob = viewModelScope.launch {
                    downloadRepository
                        .startSeasonDownload(currentItem.id, currentItem.seriesId)
                        .onSuccess { count ->
                            Timber.i("Queued $count episodes for season ${currentItem.name}")
                        }
                        .onFailure { Timber.e(it, "Failed to start season download") }
                }
            }
            currentItem is AfinityShow -> {
                bulkDownloadJob = viewModelScope.launch {
                    downloadRepository
                        .startSeriesDownload(currentItem.id)
                        .onSuccess { count ->
                            Timber.i("Queued $count episodes for series ${currentItem.name}")
                        }
                        .onFailure { Timber.e(it, "Failed to start series download") }
                }
            }
        }
    }

    fun onQualitySelected(sourceId: String) {
        itemDownloadDelegate.onQualitySelected(
            viewModelScope,
            _selectedEpisode.value ?: _uiState.value.item,
            sourceId,
        ) {
            dismissQualityDialog()
        }
    }

    fun pauseDownload() =
        itemDownloadDelegate.pauseDownload(
            viewModelScope,
            _uiState.value.downloadInfo ?: _selectedEpisodeDownloadInfo.value,
        )

    fun resumeDownload() =
        itemDownloadDelegate.resumeDownload(
            viewModelScope,
            _uiState.value.downloadInfo ?: _selectedEpisodeDownloadInfo.value,
        )

    fun cancelDownload() {
        val selectedEpisode = _selectedEpisode.value
        val currentItem = _uiState.value.item
        when {
            selectedEpisode != null -> {
                itemDownloadDelegate.cancelDownload(
                    viewModelScope,
                    _selectedEpisodeDownloadInfo.value,
                )
            }
            currentItem is AfinityShow -> {
                bulkDownloadJob?.cancel()
                bulkDownloadJob = null
                viewModelScope.launch {
                    downloadRepository.cancelAllSeriesDownloads(currentItem.id).onFailure {
                        Timber.e(it, "Failed to cancel series downloads")
                    }
                }
            }
            currentItem is AfinitySeason -> {
                bulkDownloadJob?.cancel()
                bulkDownloadJob = null
                viewModelScope.launch {
                    downloadRepository
                        .cancelAllSeasonDownloads(currentItem.seriesId, currentItem.indexNumber)
                        .onFailure { Timber.e(it, "Failed to cancel season downloads") }
                }
            }
            else -> {
                itemDownloadDelegate.cancelDownload(viewModelScope, _uiState.value.downloadInfo)
            }
        }
    }

    fun toggleFavorite() {
        val currentItem = _uiState.value.item ?: return
        val optimisticItem =
            when (currentItem) {
                is AfinityMovie -> currentItem.copy(favorite = !currentItem.favorite)
                is AfinityShow -> currentItem.copy(favorite = !currentItem.favorite)
                is AfinityEpisode -> currentItem.copy(favorite = !currentItem.favorite)
                is AfinityBoxSet -> currentItem.copy(favorite = !currentItem.favorite)
                is AfinitySeason -> currentItem.copy(favorite = !currentItem.favorite)
                else -> currentItem
            }
        itemUserDataDelegate.toggleFavorite(
            scope = viewModelScope,
            item = currentItem,
            updateOptimisticUI = { _uiState.value = _uiState.value.copy(item = optimisticItem) },
            revertUI = { _uiState.value = _uiState.value.copy(item = currentItem) },
        )
    }

    fun toggleWatchlist() {
        val currentItem = _uiState.value.item ?: return
        val optimisticItem =
            when (currentItem) {
                is AfinityMovie -> currentItem.copy(liked = !currentItem.liked)
                is AfinityShow -> currentItem.copy(liked = !currentItem.liked)
                is AfinityEpisode -> currentItem.copy(liked = !currentItem.liked)
                is AfinitySeason -> currentItem.copy(liked = !currentItem.liked)
                is AfinityBoxSet -> currentItem.copy(liked = !currentItem.liked)
                else -> currentItem
            }
        itemUserDataDelegate.toggleWatchlist(
            scope = viewModelScope,
            item = currentItem,
            updateOptimisticUI = { _uiState.value = _uiState.value.copy(item = optimisticItem) },
            revertUI = { _uiState.value = _uiState.value.copy(item = currentItem) },
        )
    }

    fun toggleEpisodeFavorite(episode: AfinityEpisode) {
        itemUserDataDelegate.toggleEpisodeFavorite(viewModelScope, episode) {
            _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        val isLiked = _selectedEpisodeWatchlistStatus.value
        itemUserDataDelegate.toggleWatchlist(
            scope = viewModelScope,
            item = episode,
            updateOptimisticUI = {
                _selectedEpisodeWatchlistStatus.value = !isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = !isLiked)
            },
            revertUI = {
                _selectedEpisodeWatchlistStatus.value = isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = isLiked)
            },
        )
    }

    fun toggleWatched() {
        viewModelScope.launch {
            try {
                val currentItem = _uiState.value.item ?: return@launch

                launch {
                    val success =
                        if (currentItem.played) {
                            userDataRepository.markUnwatched(currentItem.id)
                        } else {
                            userDataRepository.markWatched(currentItem.id)
                        }

                    if (success) {
                        mediaChangeManager.notifyItemChanged(
                            currentItem.id,
                            (currentItem as? AfinityEpisode)?.seriesId,
                            (currentItem as? AfinityEpisode)?.seasonId,
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling watched status")
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
                val success =
                    if (episode.played) userDataRepository.markUnwatched(episode.id)
                    else userDataRepository.markWatched(episode.id)

                if (success) {
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
                    mediaChangeManager.notifyItemChanged(
                        episode.id,
                        routingSeriesId,
                        routingSeasonId,
                    )
                } else {
                    val revertedStatus = !isNowPlayed
                    _episodeStatusUpdates.value += (episode.id to revertedStatus)
                    _selectedEpisode.value = episode
                }
            } catch (_: Exception) {
                _selectedEpisode.value = episode
            }
        }
    }

    private fun updateOptimisticCounters(episode: AfinityEpisode, isNowPlayed: Boolean) {
        val change = if (isNowPlayed) -1 else 1
        val updatedSeasons =
            _uiState.value.seasons.map { season ->
                if (season.id == episode.seasonId) {
                    val newCount = ((season.unplayedItemCount ?: 0) + change).coerceAtLeast(0)
                    season.copy(unplayedItemCount = newCount, played = newCount == 0)
                } else season
            }
        val updatedItem =
            when (val currentItem = _uiState.value.item) {
                is AfinityShow ->
                    if (currentItem.id == episode.seriesId) {
                        val newCount =
                            ((currentItem.unplayedItemCount ?: 0) + change).coerceAtLeast(0)
                        currentItem.copy(unplayedItemCount = newCount, played = newCount == 0)
                    } else currentItem
                is AfinitySeason ->
                    if (currentItem.id == episode.seasonId) {
                        val newCount =
                            ((currentItem.unplayedItemCount ?: 0) + change).coerceAtLeast(0)
                        currentItem.copy(unplayedItemCount = newCount, played = newCount == 0)
                    } else currentItem
                else -> currentItem
            }
        _uiState.value = _uiState.value.copy(seasons = updatedSeasons, item = updatedItem)
    }

    fun dismissQualityDialog() {
        _uiState.value = _uiState.value.copy(showQualityDialog = false)
    }

    fun getBaseUrl(): String = mediaRepository.getBaseUrl()

    private fun getNextEpisodeOffline(show: AfinityShow): AfinityEpisode? {
        val allEpisodes =
            show.seasons
                .sortedBy { it.indexNumber }
                .flatMap { season -> season.episodes.sortedBy { it.indexNumber } }
        return allEpisodes.firstOrNull { it.playbackPositionTicks > 0 && !it.played }
            ?: allEpisodes.firstOrNull { !it.played }
    }

    private fun getNextEpisodeForSeasonOffline(season: AfinitySeason): AfinityEpisode? {
        val episodes = season.episodes.sortedBy { it.indexNumber }
        return episodes.firstOrNull { it.playbackPositionTicks > 0 && !it.played }
            ?: episodes.firstOrNull { !it.played }
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
    val movieParts: List<AfinityItem> = emptyList(),
)
