@file:OptIn(UnstableApi::class)

package com.makd.afinity.ui.item

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.R
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.models.common.EpisodeLayout
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.external.ExternalTitles
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.mdblist.MdbListRatingBadges
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.AfinityVideoPlaylist
import com.makd.afinity.data.models.tmdb.TmdbRegionProviders
import com.makd.afinity.data.models.tmdb.TmdbReview
import com.makd.afinity.data.models.wikidata.WikidataAwards
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.admin.refresh.RefreshMetadataDialog
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DeleteItemDialog
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.ImageHeroKind
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.item.components.QualitySelectionDialog
import com.makd.afinity.ui.item.components.StorageLocationDialog
import com.makd.afinity.ui.item.components.VersionPickerDialog
import com.makd.afinity.ui.item.components.boxSetDetailItems
import com.makd.afinity.ui.item.components.movieDetailItems
import com.makd.afinity.ui.item.components.seasonDetailItems
import com.makd.afinity.ui.item.components.seriesDetailItems
import com.makd.afinity.ui.item.components.shared.ActionButtonsRow
import com.makd.afinity.ui.item.components.shared.AdminAction
import com.makd.afinity.ui.item.components.shared.LocalDetailLayout
import com.makd.afinity.ui.item.components.shared.MediaLogoHeader
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.MetadataRow
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.PrimaryPlaybackButton
import com.makd.afinity.ui.item.components.shared.RatingChipsRow
import com.makd.afinity.ui.item.components.shared.SimilarItemsSection
import com.makd.afinity.ui.item.components.shared.VideoQualitySelection
import com.makd.afinity.ui.item.components.shared.WhereToWatchStrip
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.item.components.shared.rememberItemBackdropChain
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.utils.IntentUtils
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import com.makd.afinity.util.rememberPreferencesRepository
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber

@Composable
fun ItemDetailScreen(
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    onEditMetadataClick: (String) -> Unit,
    onEditImagesClick: (String) -> Unit,
    onIdentifyClick: (itemId: String, itemType: String) -> Unit,
    onItemDeleted: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    onShowSnackbar: (String) -> Unit = {},
    viewModel: ItemDetailViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val nextEpisode = uiState.nextEpisode
    val context = LocalContext.current
    val selectedEpisodeWatchlistStatus by
        viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by
        viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()
    val isDownloadAllowedByServer by
        viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val canDownloadOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val mayDeleteMedia by viewModel.mayDeleteMedia.collectAsStateWithLifecycle()
    val deletableItemIds by viewModel.deletableItemIds.collectAsStateWithLifecycle()
    var showEpisodeRefreshDialog by remember { mutableStateOf(false) }
    var showEpisodeDeleteDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferencesRepository = rememberPreferencesRepository()
    val detailLayoutFlow =
        remember(preferencesRepository) { preferencesRepository.getDetailLayoutFlow() }
    val detailLayout by
        detailLayoutFlow.collectAsStateWithLifecycle<DetailLayout?>(initialValue = null)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pendingPlayItem by remember { mutableStateOf<AfinityItem?>(null) }
    var pendingPlaySelection by remember { mutableStateOf<PlaybackSelection?>(null) }
    var showVersionPickerForPlay by remember { mutableStateOf(false) }
    var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }
    var pendingNavigationPersonId by remember { mutableStateOf<String?>(null) }

    fun interceptPlayClick(item: AfinityItem, selection: PlaybackSelection?) {
        val remoteSources =
            item.sources.filter {
                it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE
            }
        if (remoteSources.size > 1 && item !is AfinityMovie) {
            pendingPlayItem = item
            pendingPlaySelection = selection
            showVersionPickerForPlay = true
        } else {
            onPlayClick(item, selection)
        }
    }

    val relatedRows =
        remember(uiState.relatedRows, uiState.similarItems) {
            val seen = uiState.similarItems.mapTo(HashSet()) { it.id }
            uiState.relatedRows.mapNotNull { row ->
                val fresh = row.items.filter { seen.add(it.id) }
                if (fresh.isEmpty()) null else row.copy(items = fresh)
            }
        }

    val missingCollectionParts =
        remember(uiState.collectionParts, uiState.boxSetItems) {
            val parts = uiState.collectionParts
            if (parts == null || uiState.boxSetItems.isEmpty()) return@remember null
            val owned =
                uiState.boxSetItems.mapNotNullTo(HashSet()) {
                    it.providerIds?.get("Tmdb")?.toIntOrNull()
                }
            val missing =
                parts.items
                    .filterNot {
                        it.id in owned || it.mediaInfo?.status == MediaStatus.AVAILABLE.value
                    }
                    .sortedWith(
                        compareBy<SearchResultItem> { it.releaseDate.isNullOrBlank() }
                            .thenBy { it.releaseDate }
                    )
            if (missing.isEmpty()) null else parts.copy(items = missing)
        }

    Box(modifier = modifier.fillMaxSize()) {
        val resolvedLayout = detailLayout
        when {
            uiState.isLoading -> {
                FullScreenLoading()
            }
            uiState.error != null -> {
                FullScreenError(
                    message = uiState.error!!,
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = { viewModel.loadItem() },
                )
            }
            uiState.item != null && resolvedLayout != null -> {
                ItemDetailContent(
                    detailLayout = resolvedLayout,
                    item = uiState.item!!,
                    hasPlayableItems = uiState.hasPlayableItems,
                    seasons = uiState.seasons,
                    boxSetItems = uiState.boxSetItems,
                    containingBoxSets = uiState.containingBoxSets,
                    similarItems = uiState.similarItems,
                    relatedRows = relatedRows,
                    missingCollectionParts = missingCollectionParts,
                    nextEpisode = nextEpisode,
                    baseUrl = viewModel.getBaseUrl(),
                    specialFeatures = uiState.specialFeatures,
                    isInWatchlist = uiState.item?.liked == true,
                    episodesPagingData = uiState.episodesPagingData,
                    downloadInfo = uiState.downloadInfo,
                    tmdbReviews = uiState.tmdbReviews,
                    mdbRatings = uiState.mdbRatings,
                    mdbRatingBadges = uiState.mdbRatingBadges,
                    omdbAwards = uiState.omdbAwards,
                    wikidataAwards = uiState.wikidataAwards,
                    isRatingsFromCache = uiState.isRatingsFromCache,
                    movieParts = uiState.movieParts,
                    onPlayClick = { item, selection -> interceptPlayClick(item, selection) },
                    onBoxSetItemClick = { item ->
                        if (item is AfinityEpisode) {
                            viewModel.selectEpisode(item)
                        } else {
                            onItemClick(item)
                        }
                    },
                    onSpecialFeatureClick = { specialFeature ->
                        val mediaSourceId = specialFeature.sources.firstOrNull()?.id
                        if (mediaSourceId != null) {
                            val startPos =
                                if (specialFeature.playbackPositionTicks > 0)
                                    specialFeature.playbackPositionTicks / 10000
                                else 0L
                            PlayerLauncher.launch(
                                context = context,
                                itemId = specialFeature.id,
                                mediaSourceId = mediaSourceId,
                                audioStreamIndex = null,
                                subtitleStreamIndex = null,
                                startPositionMs = startPos,
                            )
                        } else {
                            Timber.w(
                                "Special feature has no playable source: name=${specialFeature.name}, type=${specialFeature::class.simpleName}"
                            )
                        }
                    },
                    onItemClick = onItemClick,
                    onSeriesClick = onSeriesClick,
                    onSeasonClick = onSeasonClick,
                    onPersonClick = onPersonClick,
                    onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                    onEditMetadataClick = onEditMetadataClick,
                    onEditImagesClick = onEditImagesClick,
                    onIdentifyClick = onIdentifyClick,
                    onItemDeleted = onItemDeleted,
                    onHomeClick = onHomeClick,
                    onShowSnackbar = onShowSnackbar,
                    viewModel = viewModel,
                    widthSizeClass = widthSizeClass,
                )
            }
        }

        selectedEpisode?.let { episode ->
            LaunchedEffect(episode.id, mayDeleteMedia) {
                if (mayDeleteMedia) viewModel.checkCanDelete(episode.id)
            }

            EpisodeDetailOverlay(
                episode = episode,
                isInWatchlist = selectedEpisodeWatchlistStatus,
                downloadInfo = selectedEpisodeDownloadInfo,
                onDismiss = { viewModel.clearSelectedEpisode() },
                onPlayClick = { episodeToPlay, selection ->
                    viewModel.clearSelectedEpisode()
                    interceptPlayClick(episodeToPlay, selection)
                },
                onToggleFavorite = { viewModel.toggleEpisodeFavorite(episode) },
                onToggleWatchlist = { viewModel.toggleEpisodeWatchlist(episode) },
                onToggleWatched = { viewModel.toggleEpisodeWatched(episode) },
                onDownloadClick = { viewModel.onDownloadClick() },
                onPauseDownload = { viewModel.pauseDownload() },
                onResumeDownload = { viewModel.resumeDownload() },
                onCancelDownload = { viewModel.cancelDownload() },
                onDownloadLongClick = { viewModel.onDownloadLongClick() },
                isDownloadAllowedByServer = isDownloadAllowedByServer,
                canDownloadOnNetwork = canDownloadOnNetwork,
                onGoToSeries =
                    if (uiState.item !is AfinityShow && uiState.item !is AfinitySeason) {
                        {
                            viewModel.clearSelectedEpisode()
                            pendingNavigationSeriesId = episode.seriesId.toString()
                        }
                    } else null,
                isAdmin = isAdmin,
                canDelete = episode.id in deletableItemIds,
                onAdminAction = { action ->
                    when (action) {
                        AdminAction.EditMetadata -> onEditMetadataClick(episode.id.toString())
                        AdminAction.EditImages -> onEditImagesClick(episode.id.toString())
                        AdminAction.Refresh -> showEpisodeRefreshDialog = true
                        AdminAction.Identify -> Unit
                        AdminAction.Delete -> showEpisodeDeleteDialog = true
                    }
                },
                onPersonClick = { personId ->
                    viewModel.clearSelectedEpisode()
                    pendingNavigationPersonId = personId.toString()
                },
            )

            if (showEpisodeRefreshDialog) {
                val refreshQueuedMessage = stringResource(R.string.admin_refresh_queued)
                RefreshMetadataDialog(
                    itemId = episode.id.toString(),
                    itemName = episode.name,
                    onDismiss = { showEpisodeRefreshDialog = false },
                    onQueued = { onShowSnackbar(refreshQueuedMessage) },
                )
            }

            if (showEpisodeDeleteDialog) {
                DeleteConfirmationDialog(
                    targetId = episode.id,
                    targetName = episode.name,
                    isMainItem = false,
                    scope = DeleteScope.ITEM,
                    viewModel = viewModel,
                    onItemDeleted = onItemDeleted,
                    onMessage = onShowSnackbar,
                    onDismiss = { showEpisodeDeleteDialog = false },
                )
            }
        }

        LaunchedEffect(selectedEpisode, pendingNavigationSeriesId) {
            if (selectedEpisode == null && pendingNavigationSeriesId != null) {
                kotlinx.coroutines.delay(300.milliseconds)
                onSeriesClick(pendingNavigationSeriesId!!)
                pendingNavigationSeriesId = null
            }
        }

        LaunchedEffect(selectedEpisode, pendingNavigationPersonId) {
            if (selectedEpisode == null && pendingNavigationPersonId != null) {
                kotlinx.coroutines.delay(300.milliseconds)
                onPersonClick(pendingNavigationPersonId!!)
                pendingNavigationPersonId = null
            }
        }

        if (uiState.showQualityDialog) {
            val currentItem = selectedEpisode ?: uiState.item
            val remoteSources =
                currentItem?.sources?.filter {
                    it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE
                } ?: emptyList()

            if (remoteSources.isNotEmpty()) {
                QualitySelectionDialog(
                    sources = remoteSources,
                    onSourceSelected = { source -> viewModel.onQualitySelected(source.id) },
                    onDismiss = { viewModel.dismissQualityDialog() },
                    volumes = uiState.availableVolumes,
                    selectedVolumeId = uiState.selectedVolumeId,
                    onVolumeSelected = { viewModel.onVolumeSelected(it) },
                    initialQualityBitrate = uiState.defaultVideoDownloadQuality,
                    onConfirm = { source, _, quality ->
                        viewModel.onQualitySelected(source.id, quality)
                    },
                )
            }
        }

        if (uiState.showLocationDialog) {
            StorageLocationDialog(
                volumes = uiState.availableVolumes,
                selectedVolumeId = uiState.selectedVolumeId,
                onVolumeSelected = { viewModel.onVolumeSelected(it) },
                onConfirm = { quality -> viewModel.onLocationConfirmed(quality) },
                onDismiss = { viewModel.dismissLocationDialog() },
                initialQualityBitrate = uiState.defaultVideoDownloadQuality,
            )
        }

        if (showVersionPickerForPlay) {
            val item = pendingPlayItem
            if (item != null) {
                val remoteSources =
                    item.sources.filter {
                        it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE
                    }
                VersionPickerDialog(
                    sources = remoteSources,
                    selectedSourceId = pendingPlaySelection?.mediaSourceId,
                    onVersionSelected = { source ->
                        showVersionPickerForPlay = false
                        val finalSelection =
                            pendingPlaySelection?.copy(mediaSourceId = source.id)
                                ?: PlaybackSelection(
                                    mediaSourceId = source.id,
                                    audioStreamIndex = null,
                                    subtitleStreamIndex = null,
                                    videoStreamIndex = null,
                                )
                        onPlayClick(item, finalSelection)
                        pendingPlayItem = null
                        pendingPlaySelection = null
                    },
                    onDismiss = {
                        showVersionPickerForPlay = false
                        pendingPlayItem = null
                        pendingPlaySelection = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ItemDetailContent(
    detailLayout: DetailLayout,
    item: AfinityItem,
    hasPlayableItems: Boolean,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    relatedRows: List<RelatedRow>,
    missingCollectionParts: ExternalTitles?,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    isInWatchlist: Boolean,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    downloadInfo: DownloadInfo?,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating>,
    mdbRatingBadges: MdbListRatingBadges,
    omdbAwards: String?,
    wikidataAwards: WikidataAwards?,
    isRatingsFromCache: Boolean,
    movieParts: List<AfinityItem>,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    onEditMetadataClick: (String) -> Unit,
    onEditImagesClick: (String) -> Unit,
    onIdentifyClick: (itemId: String, itemType: String) -> Unit,
    onItemDeleted: () -> Unit,
    onHomeClick: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    viewModel: ItemDetailViewModel,
    widthSizeClass: WindowWidthSizeClass,
) {
    val context = LocalContext.current
    val isLandscape = isLandscapeWindow()

    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = {},
                onHomeClick = onHomeClick,
                backgroundOpacity = backgroundOpacity,
            )
        },
        mode = AfinityScreenMode.Hero,
        scrollOpacity = { topBarOpacity },
    ) {
        CompositionLocalProvider(LocalDetailLayout provides detailLayout) {
            if (isLandscape) {
                LandscapeItemDetailContent(
                    item = item,
                    hasPlayableItems = hasPlayableItems,
                    seasons = seasons,
                    boxSetItems = boxSetItems,
                    containingBoxSets = containingBoxSets,
                    similarItems = similarItems,
                    relatedRows = relatedRows,
                    missingCollectionParts = missingCollectionParts,
                    nextEpisode = nextEpisode,
                    baseUrl = baseUrl,
                    specialFeatures = specialFeatures,
                    isInWatchlist = isInWatchlist,
                    episodesPagingData = episodesPagingData,
                    downloadInfo = downloadInfo,
                    tmdbReviews = tmdbReviews,
                    mdbRatings = mdbRatings,
                    mdbRatingBadges = mdbRatingBadges,
                    omdbAwards = omdbAwards,
                    wikidataAwards = wikidataAwards,
                    isRatingsFromCache = isRatingsFromCache,
                    movieParts = movieParts,
                    onPlayClick = onPlayClick,
                    onBoxSetItemClick = onBoxSetItemClick,
                    onSpecialFeatureClick = onSpecialFeatureClick,
                    onItemClick = onItemClick,
                    onSeriesClick = onSeriesClick,
                    onSeasonClick = onSeasonClick,
                    onPersonClick = onPersonClick,
                    onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                    onEditMetadataClick = onEditMetadataClick,
                    onEditImagesClick = onEditImagesClick,
                    onIdentifyClick = onIdentifyClick,
                    onItemDeleted = onItemDeleted,
                    onShowSnackbar = onShowSnackbar,
                    viewModel = viewModel,
                    context = context,
                    widthSizeClass = widthSizeClass,
                    detailLayout = detailLayout,
                    lazyListState = lazyListState,
                )
            } else {
                PortraitItemDetailContent(
                    item = item,
                    hasPlayableItems = hasPlayableItems,
                    seasons = seasons,
                    boxSetItems = boxSetItems,
                    containingBoxSets = containingBoxSets,
                    similarItems = similarItems,
                    relatedRows = relatedRows,
                    missingCollectionParts = missingCollectionParts,
                    nextEpisode = nextEpisode,
                    baseUrl = baseUrl,
                    specialFeatures = specialFeatures,
                    isInWatchlist = isInWatchlist,
                    episodesPagingData = episodesPagingData,
                    downloadInfo = downloadInfo,
                    tmdbReviews = tmdbReviews,
                    mdbRatings = mdbRatings,
                    mdbRatingBadges = mdbRatingBadges,
                    omdbAwards = omdbAwards,
                    wikidataAwards = wikidataAwards,
                    isRatingsFromCache = isRatingsFromCache,
                    movieParts = movieParts,
                    onPlayClick = onPlayClick,
                    onBoxSetItemClick = onBoxSetItemClick,
                    onSpecialFeatureClick = onSpecialFeatureClick,
                    onItemClick = onItemClick,
                    onSeriesClick = onSeriesClick,
                    onSeasonClick = onSeasonClick,
                    onPersonClick = onPersonClick,
                    onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                    onEditMetadataClick = onEditMetadataClick,
                    onEditImagesClick = onEditImagesClick,
                    onIdentifyClick = onIdentifyClick,
                    onItemDeleted = onItemDeleted,
                    onShowSnackbar = onShowSnackbar,
                    viewModel = viewModel,
                    context = context,
                    widthSizeClass = widthSizeClass,
                    detailLayout = detailLayout,
                    lazyListState = lazyListState,
                )
            }
        }
    }
}

@Composable
private fun LandscapeItemDetailContent(
    item: AfinityItem,
    hasPlayableItems: Boolean,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    relatedRows: List<RelatedRow>,
    missingCollectionParts: ExternalTitles?,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    isInWatchlist: Boolean,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    downloadInfo: DownloadInfo?,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating>,
    mdbRatingBadges: MdbListRatingBadges,
    omdbAwards: String?,
    wikidataAwards: WikidataAwards?,
    isRatingsFromCache: Boolean,
    movieParts: List<AfinityItem>,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    onEditMetadataClick: (String) -> Unit,
    onEditImagesClick: (String) -> Unit,
    onIdentifyClick: (itemId: String, itemType: String) -> Unit,
    onItemDeleted: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    viewModel: ItemDetailViewModel,
    context: Context,
    widthSizeClass: WindowWidthSizeClass,
    detailLayout: DetailLayout,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val preferencesRepository = rememberPreferencesRepository()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDownloadAllowedByServer by
        viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val canDownloadOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val mayDeleteMedia by viewModel.mayDeleteMedia.collectAsStateWithLifecycle()
    val deletableItemIds by viewModel.deletableItemIds.collectAsStateWithLifecycle()
    LaunchedEffect(item.id, mayDeleteMedia) {
        if (mayDeleteMedia) viewModel.checkCanDelete(item.id)
    }
    var showRefreshDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val mediaSourceOptions = rememberMediaSourceOptions(item)
    val selectedMediaSource by viewModel.selectedMediaSource.collectAsStateWithLifecycle()
    val episodeLayoutFlow =
        remember(preferencesRepository) { preferencesRepository.getEpisodeLayoutFlow() }
    val episodeLayoutPreference by
        episodeLayoutFlow.collectAsState(initial = EpisodeLayout.HORIZONTAL)
    val episodeLayout =
        if (detailLayout == DetailLayout.MODERN) EpisodeLayout.VERTICAL else episodeLayoutPreference
    val isModernSeason = detailLayout == DetailLayout.MODERN && item is AfinitySeason
    val lazyEpisodeItems = episodesPagingData?.collectAsLazyPagingItems()

    LaunchedEffect(mediaSourceOptions) {
        if (selectedMediaSource == null && mediaSourceOptions.isNotEmpty()) {
            viewModel.selectMediaSource(mediaSourceOptions.first())
        }
    }

    LandscapeDetailLayout(
        backdropStyle = BackdropStyle.Cinematic,
        backdrop = rememberItemBackdropChain(item),
        listState = lazyListState,
        arrangement = LandscapeDetailArrangement.Single,
        backdropDescription = stringResource(R.string.cd_backdrop_fmt, item.name),
        items = {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MediaLogoHeader(item = item, isLandscape = true)

                    if (isModernSeason) {
                        ModernSeasonTitle(
                            season = item,
                            isLandscape = true,
                            onSeriesClick = { onSeriesClick(item.seriesId.toString()) },
                        )
                    }

                    MetadataRow(
                        item = item,
                        boxSetItems = boxSetItems,
                        selectedSourceId = selectedMediaSource?.id,
                    )

                    if (detailLayout == DetailLayout.MODERN && LocalShowRatings.current) {
                        RatingChipsRow(
                            item = item,
                            mdbRatings = mdbRatings,
                            mdbRatingBadges = mdbRatingBadges,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (
                            item !is AfinityBoxSet && item !is AfinityVideoPlaylist && item.canPlay
                        ) {
                            Box(modifier = Modifier.widthIn(max = 200.dp)) {
                                PrimaryPlaybackButton(
                                    item = item,
                                    nextEpisode = nextEpisode,
                                    selectedMediaSource = selectedMediaSource,
                                    isLoading =
                                        viewModel.uiState
                                            .collectAsStateWithLifecycle()
                                            .value
                                            .isLoading,
                                    onPlayRequested = { targetPlayItem, selection ->
                                        handlePlayRequest(
                                            item,
                                            targetPlayItem,
                                            selection,
                                            context,
                                            onPlayClick,
                                        )
                                    },
                                )
                            }
                        }

                        ActionButtonsRow(
                            item = item,
                            isInWatchlist = isInWatchlist,
                            hasTrailer = hasTrailer(item),
                            downloadInfo = downloadInfo,
                            hasPlayableItems = hasPlayableItems,
                            onPlayTrailer = { playTrailer(item, context, viewModel) },
                            onToggleWatchlist = { viewModel.toggleWatchlist() },
                            onShufflePlay = { shufflePlay(item, nextEpisode, context) },
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onToggleWatched = { viewModel.toggleWatched() },
                            onDownloadClick = { viewModel.onDownloadClick() },
                            onPauseDownload = { viewModel.pauseDownload() },
                            onResumeDownload = { viewModel.resumeDownload() },
                            onCancelDownload = { viewModel.cancelDownload() },
                            isDownloadAllowedByServer = isDownloadAllowedByServer,
                            canDownloadOnNetwork = canDownloadOnNetwork,
                            isLandscape = true,
                            downloadUnavailable = uiState.downloadUnavailable,
                            isAdmin = isAdmin,
                            canIdentify = identifyItemType(item) != null,
                            canDelete = item.id in deletableItemIds,
                            selectedSourceId = selectedMediaSource?.id,
                            onDownloadLongClick = { viewModel.onDownloadLongClick() },
                            onAddToPlaylist =
                                if (
                                    item is AfinityMovie ||
                                        item is AfinityEpisode ||
                                        item is AfinityShow ||
                                        item is AfinitySeason
                                )
                                    ({ showAddToPlaylist = true })
                                else null,
                            onAdminAction = { action ->
                                when (action) {
                                    AdminAction.EditMetadata ->
                                        onEditMetadataClick(item.id.toString())
                                    AdminAction.Identify ->
                                        identifyItemType(item)?.let { itemType ->
                                            onIdentifyClick(item.id.toString(), itemType)
                                        }
                                    AdminAction.EditImages -> onEditImagesClick(item.id.toString())
                                    AdminAction.Refresh -> showRefreshDialog = true
                                    AdminAction.Delete -> showDeleteDialog = true
                                }
                            },
                            modifier = Modifier.weight(2f),
                        )

                        if (showRefreshDialog) {
                            val refreshQueuedMessage = stringResource(R.string.admin_refresh_queued)
                            RefreshMetadataDialog(
                                itemId = item.id.toString(),
                                itemName = item.name,
                                includesChildren = refreshIncludesChildren(item),
                                onDismiss = { showRefreshDialog = false },
                                onQueued = { onShowSnackbar(refreshQueuedMessage) },
                            )
                        }

                        if (showAddToPlaylist) {
                            AddToPlaylistDialog(
                                trackIds = listOf(item.id),
                                viewModel = hiltViewModel(),
                                mediaType = MediaType.VIDEO,
                                onDismiss = { showAddToPlaylist = false },
                                onResult = {},
                            )
                        }

                        if (showDeleteDialog) {
                            DeleteConfirmationDialog(
                                targetId = item.id,
                                targetName = item.name,
                                isMainItem = true,
                                scope = deleteScope(item),
                                viewModel = viewModel,
                                onItemDeleted = onItemDeleted,
                                onMessage = onShowSnackbar,
                                onDismiss = { showDeleteDialog = false },
                            )
                        }
                    }

                    val providers = uiState.watchProviders
                    if (detailLayout == DetailLayout.MODERN && providers != null) {
                        WhereToWatchStrip(providers = providers)
                    }

                    VideoQualitySelection(
                        mediaSourceOptions = mediaSourceOptions,
                        selectedSource = selectedMediaSource,
                        onSourceSelected = viewModel::selectMediaSource,
                    )
                }
            }

            typeSpecificItems(
                item = item,
                seasons = seasons,
                boxSetItems = boxSetItems,
                containingBoxSets = containingBoxSets,
                similarItems = similarItems,
                relatedRows = relatedRows,
                missingCollectionParts = missingCollectionParts,
                nextEpisode = nextEpisode,
                baseUrl = baseUrl,
                specialFeatures = specialFeatures,
                lazyEpisodeItems = lazyEpisodeItems,
                episodeLayout = episodeLayout,
                tmdbReviews = tmdbReviews,
                mdbRatings = mdbRatings,
                mdbRatingBadges = mdbRatingBadges,
                omdbAwards = omdbAwards,
                wikidataAwards = wikidataAwards,
                isRatingsFromCache = isRatingsFromCache,
                movieParts = movieParts,
                selectedSourceId = selectedMediaSource?.id,
                onPlayClick = onPlayClick,
                onBoxSetItemClick = onBoxSetItemClick,
                onSpecialFeatureClick = onSpecialFeatureClick,
                onItemClick = onItemClick,
                onSeasonClick = onSeasonClick,
                onPersonClick = onPersonClick,
                onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                viewModel = viewModel,
                context = context,
                widthSizeClass = widthSizeClass,
                horizontalPadding = 0.dp,
                detailLayout = detailLayout,
                watchProviders = uiState.watchProviders,
            )
        },
    )
}

@Composable
private fun PortraitItemDetailContent(
    item: AfinityItem,
    hasPlayableItems: Boolean,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    relatedRows: List<RelatedRow>,
    missingCollectionParts: ExternalTitles?,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    isInWatchlist: Boolean,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    downloadInfo: DownloadInfo?,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating>,
    mdbRatingBadges: MdbListRatingBadges,
    omdbAwards: String?,
    wikidataAwards: WikidataAwards?,
    isRatingsFromCache: Boolean,
    movieParts: List<AfinityItem>,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    onEditMetadataClick: (String) -> Unit,
    onEditImagesClick: (String) -> Unit,
    onIdentifyClick: (itemId: String, itemType: String) -> Unit,
    onItemDeleted: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    viewModel: ItemDetailViewModel,
    context: Context,
    widthSizeClass: WindowWidthSizeClass,
    detailLayout: DetailLayout,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val preferencesRepository = rememberPreferencesRepository()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDownloadAllowedByServer by
        viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val canDownloadOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val mayDeleteMedia by viewModel.mayDeleteMedia.collectAsStateWithLifecycle()
    val deletableItemIds by viewModel.deletableItemIds.collectAsStateWithLifecycle()
    LaunchedEffect(item.id, mayDeleteMedia) {
        if (mayDeleteMedia) viewModel.checkCanDelete(item.id)
    }
    var showRefreshDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val mediaSourceOptions = rememberMediaSourceOptions(item)
    val selectedMediaSource by viewModel.selectedMediaSource.collectAsStateWithLifecycle()
    val episodeLayoutFlow =
        remember(preferencesRepository) { preferencesRepository.getEpisodeLayoutFlow() }
    val episodeLayoutPreference by
        episodeLayoutFlow.collectAsState(initial = EpisodeLayout.HORIZONTAL)
    val episodeLayout =
        if (detailLayout == DetailLayout.MODERN) EpisodeLayout.VERTICAL else episodeLayoutPreference
    val isModernSeason = detailLayout == DetailLayout.MODERN && item is AfinitySeason
    val lazyEpisodeItems = episodesPagingData?.collectAsLazyPagingItems()

    LaunchedEffect(mediaSourceOptions) {
        if (selectedMediaSource == null && mediaSourceOptions.isNotEmpty()) {
            viewModel.selectMediaSource(mediaSourceOptions.first())
        }
    }

    PortraitDetailLayout(
        hero =
            PortraitHero.Image(
                kind = ImageHeroKind.Backdrop,
                backdrop = rememberItemBackdropChain(item),
            ),
        listState = lazyListState,
        header = {
            MediaLogoHeader(item = item, isLandscape = false)

            if (isModernSeason) {
                ModernSeasonTitle(
                    season = item,
                    isLandscape = false,
                    onSeriesClick = { onSeriesClick(item.seriesId.toString()) },
                )
            }

            MetadataRow(
                item = item,
                boxSetItems = boxSetItems,
                selectedSourceId = selectedMediaSource?.id,
            )

            if (detailLayout == DetailLayout.MODERN && LocalShowRatings.current) {
                RatingChipsRow(
                    item = item,
                    mdbRatings = mdbRatings,
                    mdbRatingBadges = mdbRatingBadges,
                )
            }

            if (item !is AfinityBoxSet && item !is AfinityVideoPlaylist && item.canPlay) {
                PrimaryPlaybackButton(
                    item = item,
                    nextEpisode = nextEpisode,
                    selectedMediaSource = selectedMediaSource,
                    isLoading = viewModel.uiState.collectAsStateWithLifecycle().value.isLoading,
                    onPlayRequested = { targetPlayItem, selection ->
                        handlePlayRequest(item, targetPlayItem, selection, context, onPlayClick)
                    },
                )
            }

            ActionButtonsRow(
                item = item,
                isInWatchlist = isInWatchlist,
                hasTrailer = hasTrailer(item),
                downloadInfo = downloadInfo,
                hasPlayableItems = hasPlayableItems,
                onPlayTrailer = { playTrailer(item, context, viewModel) },
                onToggleWatchlist = { viewModel.toggleWatchlist() },
                onShufflePlay = { shufflePlay(item, nextEpisode, context) },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onToggleWatched = { viewModel.toggleWatched() },
                onDownloadClick = { viewModel.onDownloadClick() },
                onPauseDownload = { viewModel.pauseDownload() },
                onResumeDownload = { viewModel.resumeDownload() },
                onCancelDownload = { viewModel.cancelDownload() },
                isDownloadAllowedByServer = isDownloadAllowedByServer,
                canDownloadOnNetwork = canDownloadOnNetwork,
                isLandscape = false,
                downloadUnavailable = uiState.downloadUnavailable,
                isAdmin = isAdmin,
                canIdentify = identifyItemType(item) != null,
                canDelete = item.id in deletableItemIds,
                selectedSourceId = selectedMediaSource?.id,
                onDownloadLongClick = { viewModel.onDownloadLongClick() },
                onAddToPlaylist =
                    if (
                        item is AfinityMovie ||
                            item is AfinityEpisode ||
                            item is AfinityShow ||
                            item is AfinitySeason
                    )
                        ({ showAddToPlaylist = true })
                    else null,
                onAdminAction = { action ->
                    when (action) {
                        AdminAction.EditMetadata -> onEditMetadataClick(item.id.toString())
                        AdminAction.Identify ->
                            identifyItemType(item)?.let { itemType ->
                                onIdentifyClick(item.id.toString(), itemType)
                            }
                        AdminAction.EditImages -> onEditImagesClick(item.id.toString())
                        AdminAction.Refresh -> showRefreshDialog = true
                        AdminAction.Delete -> showDeleteDialog = true
                    }
                },
            )

            if (showRefreshDialog) {
                val refreshQueuedMessage = stringResource(R.string.admin_refresh_queued)
                RefreshMetadataDialog(
                    itemId = item.id.toString(),
                    itemName = item.name,
                    includesChildren = refreshIncludesChildren(item),
                    onDismiss = { showRefreshDialog = false },
                    onQueued = { onShowSnackbar(refreshQueuedMessage) },
                )
            }

            if (showAddToPlaylist) {
                AddToPlaylistDialog(
                    trackIds = listOf(item.id),
                    viewModel = hiltViewModel(),
                    mediaType = MediaType.VIDEO,
                    onDismiss = { showAddToPlaylist = false },
                    onResult = {},
                )
            }

            if (showDeleteDialog) {
                DeleteConfirmationDialog(
                    targetId = item.id,
                    targetName = item.name,
                    isMainItem = true,
                    scope = deleteScope(item),
                    viewModel = viewModel,
                    onItemDeleted = onItemDeleted,
                    onMessage = onShowSnackbar,
                    onDismiss = { showDeleteDialog = false },
                )
            }

            val providers = uiState.watchProviders
            if (detailLayout == DetailLayout.MODERN && providers != null) {
                WhereToWatchStrip(providers = providers)
            }

            VideoQualitySelection(
                mediaSourceOptions = mediaSourceOptions,
                selectedSource = selectedMediaSource,
                onSourceSelected = viewModel::selectMediaSource,
            )
        },
        items = {
            typeSpecificItems(
                item = item,
                seasons = seasons,
                boxSetItems = boxSetItems,
                containingBoxSets = containingBoxSets,
                similarItems = similarItems,
                relatedRows = relatedRows,
                missingCollectionParts = missingCollectionParts,
                nextEpisode = nextEpisode,
                baseUrl = baseUrl,
                specialFeatures = specialFeatures,
                lazyEpisodeItems = lazyEpisodeItems,
                episodeLayout = episodeLayout,
                tmdbReviews = tmdbReviews,
                mdbRatings = mdbRatings,
                mdbRatingBadges = mdbRatingBadges,
                omdbAwards = omdbAwards,
                wikidataAwards = wikidataAwards,
                isRatingsFromCache = isRatingsFromCache,
                movieParts = movieParts,
                selectedSourceId = selectedMediaSource?.id,
                onPlayClick = onPlayClick,
                onBoxSetItemClick = onBoxSetItemClick,
                onSpecialFeatureClick = onSpecialFeatureClick,
                onItemClick = onItemClick,
                onSeasonClick = onSeasonClick,
                onPersonClick = onPersonClick,
                onNavigateToSeerrMedia = onNavigateToSeerrMedia,
                viewModel = viewModel,
                context = context,
                widthSizeClass = widthSizeClass,
                horizontalPadding = DetailGutter,
                detailLayout = detailLayout,
                watchProviders = uiState.watchProviders,
            )
        },
    )
}

@Composable
private fun ModernSeasonTitle(
    season: AfinitySeason,
    isLandscape: Boolean,
    onSeriesClick: () -> Unit,
) {
    Column(
        horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = season.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier.clickable(role = Role.Button, onClick = onSeriesClick)
                    .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.cd_go_to_series),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun LazyListScope.typeSpecificItems(
    item: AfinityItem,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    relatedRows: List<RelatedRow>,
    missingCollectionParts: ExternalTitles?,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    lazyEpisodeItems: LazyPagingItems<AfinityEpisode>?,
    episodeLayout: EpisodeLayout,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating>,
    mdbRatingBadges: MdbListRatingBadges,
    omdbAwards: String?,
    wikidataAwards: WikidataAwards?,
    isRatingsFromCache: Boolean,
    movieParts: List<AfinityItem>,
    selectedSourceId: String?,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onPersonClick: (String) -> Unit,
    onNavigateToSeerrMedia: (SearchResultItem) -> Unit,
    viewModel: ItemDetailViewModel,
    context: Context,
    widthSizeClass: WindowWidthSizeClass,
    horizontalPadding: Dp,
    detailLayout: DetailLayout,
    watchProviders: TmdbRegionProviders?,
) {
    when (item) {
        is AfinityShow ->
            seriesDetailItems(
                item = item,
                watchProviders = watchProviders,
                seasons = seasons,
                nextEpisode = nextEpisode,
                specialFeatures = specialFeatures,
                containingBoxSets = containingBoxSets,
                tmdbReviews = tmdbReviews,
                mdbRatings = mdbRatings,
                mdbRatingBadges = mdbRatingBadges,
                omdbAwards = omdbAwards,
                wikidataAwards = wikidataAwards,
                isRatingsFromCache = isRatingsFromCache,
                onEpisodeClick = { ep ->
                    val mediaSourceId = ep.sources.firstOrNull()?.id ?: return@seriesDetailItems
                    val startPos =
                        if (ep.playbackPositionTicks > 0) ep.playbackPositionTicks / 10000 else 0L
                    PlayerLauncher.launch(
                        context,
                        ep.id,
                        mediaSourceId,
                        null,
                        null,
                        startPos,
                    )
                },
                onEpisodeMoreClick = { ep -> viewModel.selectEpisode(ep) },
                onSpecialFeatureClick = { sf ->
                    val mediaSourceId = sf.sources.firstOrNull()?.id
                    if (mediaSourceId != null) {
                        val startPos =
                            if (sf.playbackPositionTicks > 0) sf.playbackPositionTicks / 10000
                            else 0L
                        PlayerLauncher.launch(
                            context = context,
                            itemId = sf.id,
                            mediaSourceId = mediaSourceId,
                            audioStreamIndex = null,
                            subtitleStreamIndex = null,
                            startPositionMs = startPos,
                        )
                    } else {
                        Timber.w(
                            "Special feature (series) has no playable source: name=${sf.name}, type=${sf::class.simpleName}"
                        )
                    }
                },
                onItemClick = onItemClick,
                onSeasonClick = onSeasonClick,
                onPersonClick = onPersonClick,
                widthSizeClass = widthSizeClass,
                horizontalPadding = horizontalPadding,
                detailLayout = detailLayout,
            )
        is AfinitySeason ->
            seasonDetailItems(
                season = item,
                lazyEpisodeItems = lazyEpisodeItems,
                episodeLayout = episodeLayout,
                specialFeatures = specialFeatures,
                containingBoxSets = containingBoxSets,
                tmdbReviews = tmdbReviews,
                mdbRatings = mdbRatings,
                mdbRatingBadges = mdbRatingBadges,
                wikidataAwards = wikidataAwards,
                isRatingsFromCache = isRatingsFromCache,
                onEpisodeClick = { ep -> viewModel.selectEpisode(ep) },
                onSpecialFeatureClick = onSpecialFeatureClick,
                onItemClick = onItemClick,
                onPersonClick = onPersonClick,
                widthSizeClass = widthSizeClass,
                horizontalPadding = horizontalPadding,
                detailLayout = detailLayout,
            )
        is AfinityMovie ->
            movieDetailItems(
                item = item,
                watchProviders = watchProviders,
                baseUrl = baseUrl,
                specialFeatures = specialFeatures,
                containingBoxSets = containingBoxSets,
                tmdbReviews = tmdbReviews,
                mdbRatings = mdbRatings,
                mdbRatingBadges = mdbRatingBadges,
                omdbAwards = omdbAwards,
                wikidataAwards = wikidataAwards,
                isRatingsFromCache = isRatingsFromCache,
                parts = movieParts,
                onSpecialFeatureClick = onSpecialFeatureClick,
                onPlayClick = { movie, sel -> onPlayClick(movie, sel) },
                onPartClick = { part -> onPlayClick(part, null) },
                onItemClick = onItemClick,
                onPersonClick = onPersonClick,
                widthSizeClass = widthSizeClass,
                horizontalPadding = horizontalPadding,
                detailLayout = detailLayout,
                selectedSourceId = selectedSourceId,
            )
        is AfinityBoxSet ->
            boxSetDetailItems(
                item = item,
                boxSetItems = boxSetItems,
                onItemClick = onBoxSetItemClick,
                missingParts = missingCollectionParts,
                onMissingPartClick = onNavigateToSeerrMedia,
                widthSizeClass = widthSizeClass,
                horizontalPadding = horizontalPadding,
                detailLayout = detailLayout,
            )

        else -> Unit
    }

    if (item !is AfinityBoxSet && item !is AfinityVideoPlaylist && similarItems.isNotEmpty()) {
        detailItem("similar", horizontalPadding) {
            SimilarItemsSection(
                items = similarItems,
                onItemClick = onItemClick,
                widthSizeClass = widthSizeClass,
            )
        }
    }

    relatedRows.forEach { row ->
        detailItem("related_${row.kind.name}", horizontalPadding) {
            SimilarItemsSection(
                items = row.items,
                onItemClick = onItemClick,
                widthSizeClass = widthSizeClass,
                title =
                    when (row.kind) {
                        RelatedRow.Kind.STARRING ->
                            stringResource(R.string.related_starring_fmt, row.subject)

                        RelatedRow.Kind.GENRE ->
                            stringResource(R.string.related_more_in_genre_fmt, row.subject)
                    },
            )
        }
    }
}

@Composable
private fun rememberMediaSourceOptions(item: AfinityItem): List<MediaSourceOption> {
    val unknownCodec = stringResource(R.string.track_unknown)
    return remember(item, unknownCodec) {
        item.sources.mapIndexed { index, source ->
            val videoStream = source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
            val resolution =
                when {
                    (videoStream?.height ?: 0) > 2160 -> "8K"
                    (videoStream?.height ?: 0) > 1080 -> "4K"
                    (videoStream?.height ?: 0) > 720 -> "1080p"
                    (videoStream?.height ?: 0) > 480 -> "720p"
                    else -> "SD"
                }
            val displayName =
                when {
                    source.name.isNotBlank() && source.name != "Default" -> source.name
                    else -> {
                        val codec = videoStream?.codec?.uppercase() ?: unknownCodec
                        "$resolution $codec"
                    }
                }
            MediaSourceOption(
                id = source.id,
                name = displayName,
                quality = resolution,
                codec = videoStream?.codec?.uppercase() ?: unknownCodec,
                size = source.size,
                isDefault = index == 0,
            )
        }
    }
}

private fun identifyItemType(item: AfinityItem): String? =
    when (item) {
        is AfinityMovie -> "Movie"
        is AfinityShow -> "Series"
        is AfinityBoxSet -> "BoxSet"
        else -> null
    }

private fun refreshIncludesChildren(item: AfinityItem): Boolean =
    item is AfinityShow || item is AfinitySeason || item is AfinityBoxSet

private fun hasTrailer(item: AfinityItem): Boolean =
    (item as? AfinityMovie)?.trailer != null ||
        (item as? AfinityShow)?.trailer != null ||
        (item as? AfinityVideo)?.trailer != null

private fun playTrailer(item: AfinityItem, context: Context, viewModel: ItemDetailViewModel) {
    viewModel.getTrailerUrl(item)?.let { IntentUtils.openYouTubeUrl(context, it) }
}

private fun handlePlayRequest(
    item: AfinityItem,
    targetPlayItem: AfinityItem,
    selection: PlaybackSelection,
    context: Context,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
) {
    if (item is AfinityShow || item is AfinitySeason) {
        PlayerLauncher.launch(
            context = context,
            itemId = targetPlayItem.id,
            mediaSourceId = selection.mediaSourceId,
            audioStreamIndex = selection.audioStreamIndex,
            subtitleStreamIndex = selection.subtitleStreamIndex,
            startPositionMs = selection.startPositionMs,
            seasonId = if (item is AfinitySeason) item.id else null,
        )
    } else {
        onPlayClick(targetPlayItem, selection)
    }
}

private fun shufflePlay(item: AfinityItem, nextEpisode: AfinityEpisode?, context: Context) {
    val episode =
        when (item) {
            is AfinityShow,
            is AfinitySeason -> nextEpisode
            else -> null
        }
    episode?.let { ep ->
        val mediaSourceId = ep.sources.firstOrNull()?.id ?: return
        PlayerLauncher.launch(
            context = context,
            itemId = ep.id,
            mediaSourceId = mediaSourceId,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            startPositionMs = 0L,
            seasonId = if (item is AfinitySeason) item.id else null,
            shuffle = true,
        )
    }
}

private enum class DeleteScope {
    ITEM,
    WITH_CHILDREN,
    CONTAINER_ONLY,
}

private fun deleteScope(item: AfinityItem): DeleteScope =
    when (item) {
        is AfinityShow,
        is AfinitySeason -> DeleteScope.WITH_CHILDREN

        is AfinityBoxSet,
        is AfinityVideoPlaylist -> DeleteScope.CONTAINER_ONLY

        else -> DeleteScope.ITEM
    }

@Composable
private fun DeleteConfirmationDialog(
    targetId: UUID,
    targetName: String,
    isMainItem: Boolean,
    scope: DeleteScope,
    viewModel: ItemDetailViewModel,
    onItemDeleted: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnItemDeleted by rememberUpdatedState(onItemDeleted)
    var deleting by remember(targetId) { mutableStateOf(false) }
    var errorRes by remember(targetId) { mutableStateOf<Int?>(null) }

    DeleteItemDialog(
        targetName = targetName,
        message =
            stringResource(
                when (scope) {
                    DeleteScope.ITEM -> R.string.admin_delete_message_item
                    DeleteScope.WITH_CHILDREN -> R.string.admin_delete_message_children
                    DeleteScope.CONTAINER_ONLY -> R.string.admin_delete_message_container
                }
            ),
        deleting = deleting,
        errorRes = errorRes,
        onConfirm = {
            deleting = true
            errorRes = null
            viewModel.deleteItem(targetId) { outcome ->
                deleting = false
                when (outcome) {
                    DeleteOutcome.DELETED -> {
                        onMessage(context.getString(R.string.admin_delete_success))
                        if (!isMainItem) {
                            viewModel.clearSelectedEpisode()
                        } else {
                            currentOnItemDeleted()
                        }
                        currentOnDismiss()
                    }

                    DeleteOutcome.STILL_RUNNING -> {
                        onMessage(context.getString(R.string.admin_delete_still_running))
                        currentOnDismiss()
                    }

                    DeleteOutcome.NOT_ALLOWED -> errorRes = R.string.admin_delete_not_allowed

                    DeleteOutcome.FAILED -> errorRes = R.string.admin_delete_failed
                }
            }
        },
        onDismiss = onDismiss,
    )
}
