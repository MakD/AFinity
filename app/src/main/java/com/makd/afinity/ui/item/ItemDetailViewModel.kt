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
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaChangeSource
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
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.mdblist.MdbListRatingsResult
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
import com.makd.afinity.data.repository.AppDataRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
    private val appDataRepository: AppDataRepository,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val sessionManager: SessionManager,
    private val downloadRepository: DownloadRepository,
    private val databaseRepository: DatabaseRepository,
    private val offlineModeManager: OfflineModeManager,
    private val authRepository: AuthRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
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
    private val pendingItemUpdates = mutableMapOf<UUID, AfinityItem>()
    private val pagingUpdateTrigger = MutableStateFlow(0)
    private var currentEpisodesPagingSource: EpisodesPagingSource? = null

    private fun applyUpdatesToPagingFlow(
        baseFlow: Flow<PagingData<AfinityEpisode>>
    ): Flow<PagingData<AfinityEpisode>> {
        return baseFlow
            .combine(pagingUpdateTrigger) { pagingData, _ ->
                pagingData.map { episode ->
                    pendingItemUpdates[episode.id] as? AfinityEpisode ?: episode
                }
            }
            .cachedIn(viewModelScope)
    }

    private var bulkDownloadJob: Job? = null
    private var itemLastLoadedAt = 0L

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    val isAdmin: StateFlow<Boolean> =
        sessionManager.currentSession
            .map { it?.isAdmin == true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

        loadItem()
        observeDownloadStatus()

        viewModelScope.launch {
            adminChangeBroadcaster.itemChanged
                .filter { it == itemId.toString() }
                .collect { forceReloadFromServer() }
        }

        viewModelScope.launch {
            playbackStateManager.playbackEvents.collect { event ->
                if (event is PlaybackEvent.Stopped && event.itemId == itemId) {
                    Timber.d("Playback stopped. Waiting for WebSocket to patch UI.")
                }
            }
        }

        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                val currentItem = _uiState.value.item ?: return@collect
                var targetItem = event.updatedItem ?: event.parentItem ?: event.seasonItem
                if (targetItem == null) {
                    try {
                        targetItem = mediaRepository.getItemById(event.itemId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to resolve item for detail patch: ${event.itemId}")
                    }
                }
                val trueSeriesId =
                    event.seriesId
                        ?: (targetItem as? AfinityEpisode)?.seriesId
                        ?: (targetItem as? AfinitySeason)?.seriesId
                val trueSeasonId = event.seasonId ?: (targetItem as? AfinityEpisode)?.seasonId
                val isInBoxSet = _uiState.value.boxSetItems.any { it.id == event.itemId }

                val isRelated =
                    event.itemId == currentItem.id ||
                        trueSeriesId == currentItem.id ||
                        trueSeasonId == currentItem.id ||
                        event.itemId == _uiState.value.nextEpisode?.id ||
                        isInBoxSet
                val similarDirectIdx =
                    _uiState.value.similarItems.indexOfFirst { it.id == event.itemId }
                if (similarDirectIdx != -1) {
                    val patchedSimilar =
                        targetItem
                            ?: if (event.userData?.played == true) {
                                when (val cur = _uiState.value.similarItems[similarDirectIdx]) {
                                    is AfinityMovie ->
                                        cur.copy(played = true, playbackPositionTicks = 0)
                                    is AfinityShow -> cur.copy(played = true)
                                    else -> null
                                }
                            } else null
                    patchedSimilar?.let { updated ->
                        _uiState.update { state ->
                            val newList = state.similarItems.toMutableList()
                            newList[similarDirectIdx] = updated
                            state.copy(similarItems = newList)
                        }
                    }
                } else if (trueSeriesId != null && trueSeriesId != event.itemId) {
                    val seriesSimilarIdx =
                        _uiState.value.similarItems.indexOfFirst { it.id == trueSeriesId }
                    if (seriesSimilarIdx != -1) {
                        val freshParent = event.parentItem
                        if (freshParent != null) {
                            _uiState.update { state ->
                                val newList = state.similarItems.toMutableList()
                                newList[seriesSimilarIdx] = freshParent
                                state.copy(similarItems = newList)
                            }
                        } else {
                            launch {
                                try {
                                    val fresh = mediaRepository.refreshItemUserData(trueSeriesId)
                                    if (fresh != null) {
                                        val idx =
                                            _uiState.value.similarItems.indexOfFirst {
                                                it.id == trueSeriesId
                                            }
                                        if (idx != -1) {
                                            _uiState.update { state ->
                                                val newList = state.similarItems.toMutableList()
                                                newList[idx] = fresh
                                                state.copy(similarItems = newList)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(
                                        e,
                                        "Failed to refresh series in similar items: $trueSeriesId",
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isRelated) return@collect

                targetItem?.let { item ->
                    if (item is AfinityEpisode) {
                        pendingItemUpdates[item.id] = item
                        pagingUpdateTrigger.value += 1
                        if (_selectedEpisode.value?.id == item.id) {
                            _selectedEpisode.value = item
                        }
                    }

                    if (item.id == currentItem.id) {
                        _uiState.update { it.copy(item = item) }
                        launch {
                            try {
                                val freshBoxSets =
                                    mediaRepository.getBoxSetsContaining(
                                        itemId = currentItem.id,
                                        fields = FieldSets.MEDIA_ITEM_CARDS,
                                    )
                                if (freshBoxSets != _uiState.value.containingBoxSets) {
                                    _uiState.update { it.copy(containingBoxSets = freshBoxSets) }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to refresh containing BoxSets")
                            }
                        }
                    }

                    val boxSetIdx = _uiState.value.boxSetItems.indexOfFirst { it.id == item.id }
                    if (boxSetIdx != -1) {
                        _uiState.update { state ->
                            val newList = state.boxSetItems.toMutableList()
                            newList[boxSetIdx] = item
                            state.copy(boxSetItems = newList)
                        }
                    }
                }

                if (
                    currentItem is AfinityShow ||
                        currentItem is AfinitySeason ||
                        (currentItem is AfinityBoxSet && isInBoxSet)
                ) {
                    if (
                        event.source == MediaChangeSource.WEBSOCKET ||
                            event.itemId == currentItem.id ||
                            isInBoxSet
                    ) {
                        launch {
                            try {
                                val nextEp =
                                    when (currentItem) {
                                        is AfinityShow ->
                                            mediaRepository.getEpisodeToPlay(currentItem.id)
                                        is AfinitySeason ->
                                            mediaRepository.getEpisodeToPlayForSeason(
                                                currentItem.id,
                                                currentItem.seriesId,
                                            )
                                        else -> null
                                    }
                                if (nextEp != null && nextEp != _uiState.value.nextEpisode) {
                                    _uiState.update { it.copy(nextEpisode = nextEp) }
                                }

                                if (currentItem is AfinityShow) {
                                    val freshSeasons = mediaRepository.getSeasons(currentItem.id)
                                    _uiState.update { it.copy(seasons = freshSeasons) }
                                }

                                val freshMainItem = mediaRepository.getItemById(currentItem.id)
                                if (freshMainItem != null && freshMainItem != _uiState.value.item) {
                                    _uiState.update { it.copy(item = freshMainItem) }

                                    if (currentItem is AfinityBoxSet && isInBoxSet) {
                                        mediaChangeManager.notifyItemChanged(
                                            currentItem.id,
                                            null,
                                            null,
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "Failed background patch for series/season/boxset counts",
                                )
                            }
                        }
                    }
                }
            }
        }
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
                    val resolvedItem =
                        if (cachedItem is AfinitySeason && cachedItem.runtimeTicks == 0L) {
                            val currentItem = _uiState.value.item
                            if (currentItem is AfinitySeason && currentItem.runtimeTicks > 0L) {
                                cachedItem.copy(runtimeTicks = currentItem.runtimeTicks)
                            } else cachedItem
                        } else cachedItem

                    if (resolvedItem != _uiState.value.item) {
                        _uiState.update { state -> state.copy(item = resolvedItem) }
                    }

                    when (resolvedItem) {
                        is AfinityShow -> {
                            launch {
                                try {
                                    val nextEpisode =
                                        mediaRepository.getEpisodeToPlay(resolvedItem.id)
                                    if (nextEpisode != _uiState.value.nextEpisode) {
                                        _uiState.update { it.copy(nextEpisode = nextEpisode) }
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to get next episode")
                                }
                            }
                            launch {
                                try {
                                    val seasons = mediaRepository.getSeasons(resolvedItem.id)
                                    if (seasons != _uiState.value.seasons) {
                                        _uiState.update { it.copy(seasons = seasons) }
                                    }
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
                                            resolvedItem.id,
                                            resolvedItem.seriesId,
                                        )
                                    if (nextEpisode != _uiState.value.nextEpisode) {
                                        _uiState.update { it.copy(nextEpisode = nextEpisode) }
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed to get next episode for season")
                                }
                            }
                        }
                        is AfinityBoxSet -> loadBoxSetItems(resolvedItem.id)
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
                if (serverItem != _uiState.value.item) {
                    _uiState.update { state -> state.copy(item = serverItem) }
                }

                when (serverItem) {
                    is AfinityShow -> {
                        try {
                            val nextEpisode = mediaRepository.getEpisodeToPlay(serverItem.id)
                            if (nextEpisode != _uiState.value.nextEpisode) {
                                _uiState.update { it.copy(nextEpisode = nextEpisode) }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to refresh next episode in background sync")
                        }
                        try {
                            val seasons = mediaRepository.getSeasons(serverItem.id)
                            if (seasons != _uiState.value.seasons) {
                                _uiState.update { it.copy(seasons = seasons) }
                            }
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
                            if (nextEpisode != _uiState.value.nextEpisode) {
                                _uiState.update { it.copy(nextEpisode = nextEpisode) }
                            }
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
                itemLastLoadedAt = System.currentTimeMillis()

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
                                    mdbRatingBadges = cachedMetadata.mdbRatingBadges,
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
                            if (_episodesPagingData.value == null) {
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
                                                .also { currentEpisodesPagingSource = it }
                                        }
                                        .flow
                                        .cachedIn(viewModelScope)

                                val patchedFlow = applyUpdatesToPagingFlow(basePagerFlow)
                                _episodesPagingData.value = patchedFlow
                                _uiState.update { it.copy(episodesPagingData = patchedFlow) }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to get episodes flow")
                        }
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
                        cachedMetadata.mdbRatings.isNotEmpty() ||
                        cachedMetadata.mdbRatingBadges.hasAny)
            ) {
                _uiState.update {
                    it.copy(
                        tmdbReviews = cachedMetadata.tmdbReviews,
                        mdbRatings = cachedMetadata.mdbRatings,
                        mdbRatingBadges = cachedMetadata.mdbRatingBadges,
                        isRatingsFromCache = true,
                        isLoadingReviews = false,
                    )
                }
            } else {
                val tmdbId = item.providerIds?.get("Tmdb")
                var fetchedReviews = emptyList<TmdbReview>()
                var fetchedRatings = emptyList<MdbListRating>()
                var fetchedRatingBadges = MdbListRatingBadges()

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
                                val ratingsResult =
                                    mediaRepository.getMdbListRatings(tmdbId, item is AfinityMovie)
                                ratingsResult.copy(
                                    ratings =
                                        ratingsResult.ratings.filter {
                                            it.source.lowercase() !in listOf("imdb", "tomatoes") &&
                                                it.value != null
                                        }
                                )
                            } catch (e: Exception) {
                                Timber.w(
                                    e,
                                    "Failed to fetch MDBList ratings during network transition",
                                )
                                MdbListRatingsResult()
                            }
                        }

                        fetchedReviews = reviewsDeferred.await()
                        val ratingsResult = ratingsDeferred.await()
                        fetchedRatings = ratingsResult.ratings
                        fetchedRatingBadges = ratingsResult.badges
                    }
                }

                _uiState.update {
                    it.copy(
                        tmdbReviews = fetchedReviews,
                        mdbRatings = fetchedRatings,
                        mdbRatingBadges = fetchedRatingBadges,
                        isRatingsFromCache = false,
                        isLoadingReviews = false,
                    )
                }

                if (
                    (fetchedReviews.isNotEmpty() ||
                        fetchedRatings.isNotEmpty() ||
                        fetchedRatingBadges.hasAny) && session != null
                ) {
                    databaseRepository.insertItemMetadata(
                        ItemMetadataCacheEntity(
                            itemId = item.id,
                            serverId = session.serverId,
                            userId = session.userId.toString(),
                            tmdbReviews = fetchedReviews,
                            mdbRatings = fetchedRatings,
                            mdbRatingBadges = fetchedRatingBadges,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load reviews/ratings")
            _uiState.update { it.copy(isLoadingReviews = false) }
        }
    }

    fun forceReloadFromServer() {
        loadItem()
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > itemLastLoadedAt) {
            refreshFromCacheImmediate(skipNetworkSync = false)
            itemLastLoadedAt = System.currentTimeMillis()
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
            val currentItem = _uiState.value.item ?: return@launch
            val isNowPlayed = !currentItem.played
            _uiState.update { it.copy(item = currentItem.withPlayed(isNowPlayed)) }

            try {
                val success =
                    if (currentItem.played) {
                        userDataRepository.markUnwatched(currentItem.id)
                    } else {
                        userDataRepository.markWatched(currentItem.id)
                    }

                if (success) {
                    mediaChangeManager.publishKnownChange(
                        updatedItem = currentItem.withPlayed(isNowPlayed),
                        source = MediaChangeSource.MANUAL,
                    )
                    if (
                        currentItem is AfinitySeason ||
                            currentItem is AfinityShow ||
                            currentItem is AfinityBoxSet
                    ) {
                        currentEpisodesPagingSource?.invalidate()
                        refreshFromCacheImmediate(skipNetworkSync = false)
                        if (currentItem is AfinityBoxSet) {
                            loadBoxSetItems(currentItem.id)
                        }
                    }
                } else {
                    _uiState.update { it.copy(item = currentItem) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling watched status")
                _uiState.update { it.copy(item = currentItem) }
            }
        }
    }

    private fun AfinityItem.withPlayed(played: Boolean): AfinityItem =
        when (this) {
            is AfinityMovie ->
                copy(
                    played = played,
                    playbackPositionTicks = if (played) 0L else playbackPositionTicks,
                )
            is AfinityShow -> copy(played = played)
            is AfinitySeason -> copy(played = played)
            is AfinityEpisode ->
                copy(
                    played = played,
                    playbackPositionTicks = if (played) 0L else playbackPositionTicks,
                )
            is AfinityBoxSet -> copy(played = played)
            else -> this
        }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            val isNowPlayed = !episode.played
            _selectedEpisode.value = episode.copy(played = isNowPlayed, playbackPositionTicks = 0)
            try {
                val success =
                    if (episode.played) {
                        userDataRepository.markUnwatched(episode.id)
                    } else {
                        userDataRepository.markWatched(episode.id)
                    }
                if (success) {
                    mediaChangeManager.notifyItemChanged(
                        episode.id,
                        episode.seriesId,
                        episode.seasonId,
                    )
                } else {
                    _selectedEpisode.value = episode
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
                _selectedEpisode.value = episode
            }
        }
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
    val mdbRatingBadges: MdbListRatingBadges = MdbListRatingBadges(),
    val isRatingsFromCache: Boolean = false,
    val movieParts: List<AfinityItem> = emptyList(),
) {
    val hasPlayableItems: Boolean
        get() =
            when (item) {
                is AfinityShow,
                is AfinitySeason -> nextEpisode != null && !nextEpisode.missing
                is AfinityEpisode -> !item.missing
                else -> true
            }
}
