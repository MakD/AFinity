@file:OptIn(UnstableApi::class)

package com.makd.afinity.ui.item

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.PagingData
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showLogoImageUrl
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.tmdb.TmdbReview
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.item.components.BoxSetDetailContent
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.item.components.MovieDetailContent
import com.makd.afinity.ui.item.components.QualitySelectionDialog
import com.makd.afinity.ui.item.components.SeasonDetailContent
import com.makd.afinity.ui.item.components.SeriesDetailContent
import com.makd.afinity.ui.item.components.VersionPickerDialog
import com.makd.afinity.ui.item.components.shared.ActionButtonsRow
import com.makd.afinity.ui.item.components.shared.HeroSection
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.MetadataRow
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.PrimaryPlaybackButton
import com.makd.afinity.ui.item.components.shared.SimilarItemsSection
import com.makd.afinity.ui.item.components.shared.VideoQualitySelection
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.utils.IntentUtils
import com.makd.afinity.ui.utils.verticalLayoutOffset
import com.makd.afinity.util.rememberPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

@Composable
fun ItemDetailScreen(
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
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
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()

    var pendingPlayItem by remember { mutableStateOf<AfinityItem?>(null) }
    var pendingPlaySelection by remember { mutableStateOf<PlaybackSelection?>(null) }
    var showVersionPickerForPlay by remember { mutableStateOf(false) }
    var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }

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
            viewModel.dismissTrailer()
            onPlayClick(item, selection)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_error_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.home_error_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            uiState.item != null -> {
                ItemDetailContent(
                    item = uiState.item!!,
                    seasons = uiState.seasons,
                    boxSetItems = uiState.boxSetItems,
                    containingBoxSets = uiState.containingBoxSets,
                    similarItems = uiState.similarItems,
                    nextEpisode = nextEpisode,
                    baseUrl = viewModel.getBaseUrl(),
                    specialFeatures = uiState.specialFeatures,
                    isInWatchlist = uiState.item?.liked == true,
                    episodesPagingData = uiState.episodesPagingData,
                    downloadInfo = uiState.downloadInfo,
                    tmdbReviews = uiState.tmdbReviews,
                    mdbRatings = uiState.mdbRatings,
                    isRatingsFromCache = uiState.isRatingsFromCache,
                    onPlayClick = { item, selection -> interceptPlayClick(item, selection) },
                    onBoxSetItemClick = { item ->
                        if (item is AfinityEpisode) {
                            viewModel.selectEpisode(item)
                        } else {
                            val route =
                                Destination.createItemDetailRoute(
                                    itemId = item.id.toString(),
                                    itemType =
                                        when (item) {
                                            is AfinityShow -> "Series"
                                            is AfinitySeason -> "Season"
                                            else -> null
                                        },
                                    seriesId = (item as? AfinitySeason)?.seriesId?.toString(),
                                )
                            navController.navigate(route)
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
                    navController = navController,
                    viewModel = viewModel,
                    widthSizeClass = widthSizeClass,
                )
            }
        }

        selectedEpisode?.let { episode ->
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
                canDownload = canDownload,
                onGoToSeries =
                    if (uiState.item !is AfinityShow && uiState.item !is AfinitySeason) {
                        {
                            viewModel.clearSelectedEpisode()
                            pendingNavigationSeriesId = episode.seriesId.toString()
                        }
                    } else null,
            )
        }

        LaunchedEffect(selectedEpisode, pendingNavigationSeriesId) {
            if (selectedEpisode == null && pendingNavigationSeriesId != null) {
                kotlinx.coroutines.delay(300)
                val route =
                    Destination.createItemDetailRoute(
                        itemId = pendingNavigationSeriesId!!,
                        itemType = "Series",
                    )
                navController.navigate(route)
                pendingNavigationSeriesId = null
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
                )
            }
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
                        viewModel.dismissTrailer()
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
    item: AfinityItem,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    isInWatchlist: Boolean,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    downloadInfo: DownloadInfo?,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating>,
    isRatingsFromCache: Boolean,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel,
    widthSizeClass: WindowWidthSizeClass,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeItemDetailContent(
            item = item,
            seasons = seasons,
            boxSetItems = boxSetItems,
            containingBoxSets = containingBoxSets,
            similarItems = similarItems,
            nextEpisode = nextEpisode,
            baseUrl = baseUrl,
            specialFeatures = specialFeatures,
            isInWatchlist = isInWatchlist,
            episodesPagingData = episodesPagingData,
            downloadInfo = downloadInfo,
            tmdbReviews = tmdbReviews,
            mdbRatings = mdbRatings,
            isRatingsFromCache = isRatingsFromCache,
            onPlayClick = onPlayClick,
            onBoxSetItemClick = onBoxSetItemClick,
            onSpecialFeatureClick = onSpecialFeatureClick,
            navController = navController,
            viewModel = viewModel,
            context = context,
            widthSizeClass = widthSizeClass,
        )
    } else {
        PortraitItemDetailContent(
            item = item,
            seasons = seasons,
            boxSetItems = boxSetItems,
            containingBoxSets = containingBoxSets,
            similarItems = similarItems,
            nextEpisode = nextEpisode,
            baseUrl = baseUrl,
            specialFeatures = specialFeatures,
            isInWatchlist = isInWatchlist,
            episodesPagingData = episodesPagingData,
            downloadInfo = downloadInfo,
            tmdbReviews = tmdbReviews,
            mdbRatings = mdbRatings,
            isRatingsFromCache = isRatingsFromCache,
            onPlayClick = onPlayClick,
            onBoxSetItemClick = onBoxSetItemClick,
            onSpecialFeatureClick = onSpecialFeatureClick,
            navController = navController,
            viewModel = viewModel,
            context = context,
            widthSizeClass = widthSizeClass,
        )
    }
}

@Composable
private fun LandscapeItemDetailContent(
    item: AfinityItem,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    isInWatchlist: Boolean,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    downloadInfo: DownloadInfo?,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating>,
    isRatingsFromCache: Boolean,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel,
    context: Context,
    widthSizeClass: WindowWidthSizeClass,
) {
    val preferencesRepository = rememberPreferencesRepository()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val displayCutoutLeft =
        WindowInsets.displayCutout.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val baseColorScheme = MaterialTheme.colorScheme

    val landscapeColorScheme =
        remember(baseColorScheme) {
            baseColorScheme.copy(
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                outline = Color.White.copy(alpha = 0.5f),
            )
        }

    MaterialTheme(colorScheme = landscapeColorScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            val backdropUrl =
                if (item is AfinitySeason) {
                    item.images.backdropImageUrl
                        ?: item.images.showBackdropImageUrl
                        ?: item.images.primaryImageUrl
                } else {
                    item.images.backdropImageUrl ?: item.images.primaryImageUrl
                }

            if (backdropUrl != null) {
                AsyncImage(
                    imageUrl = backdropUrl,
                    contentDescription = stringResource(R.string.cd_backdrop_fmt, item.name),
                    targetWidth = 1920.dp,
                    targetHeight = 1080.dp,
                    modifier = Modifier.fillMaxSize().blur(0.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                )
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            Image(
                painter = painterResource(id = R.drawable.mask),
                contentDescription = "Mask overlay",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = with(density) { statusBarHeight.toDp() + 16.dp },
                        start = with(density) { displayCutoutLeft.toDp() + 16.dp },
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MediaLogoHeader(item = item, isLandscape = true)

                        MetadataRow(
                            item = item,
                            boxSetItems = boxSetItems,
                            mdbRatings = mdbRatings,
                            isRatingsFromCache = isRatingsFromCache,
                        )

                        val mediaSourceOptions = rememberMediaSourceOptions(item)
                        val selectedMediaSource by
                            viewModel.selectedMediaSource.collectAsStateWithLifecycle()

                        LaunchedEffect(mediaSourceOptions) {
                            if (selectedMediaSource == null && mediaSourceOptions.isNotEmpty()) {
                                viewModel.selectMediaSource(mediaSourceOptions.first())
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (item !is AfinityBoxSet && item.canPlay) {
                                Box(modifier = Modifier.widthIn(max = 200.dp)) {
                                    PrimaryPlaybackButton(
                                        item = item,
                                        nextEpisode = nextEpisode,
                                        selectedMediaSource = selectedMediaSource,
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
                                onPlayTrailer = { playTrailer(item, context, viewModel) },
                                onToggleWatchlist = { viewModel.toggleWatchlist() },
                                onShufflePlay = { shufflePlay(item, nextEpisode, context) },
                                onToggleFavorite = { viewModel.toggleFavorite() },
                                onToggleWatched = { viewModel.toggleWatched() },
                                onDownloadClick = { viewModel.onDownloadClick() },
                                onPauseDownload = { viewModel.pauseDownload() },
                                onResumeDownload = { viewModel.resumeDownload() },
                                onCancelDownload = { viewModel.cancelDownload() },
                                canDownload = canDownload,
                                isLandscape = true,
                                modifier = Modifier.weight(2f),
                            )
                        }

                        VideoQualitySelection(
                            mediaSourceOptions = mediaSourceOptions,
                            selectedSource = selectedMediaSource,
                            onSourceSelected = viewModel::selectMediaSource,
                        )

                        TypeSpecificContent(
                            item = item,
                            seasons = seasons,
                            boxSetItems = boxSetItems,
                            containingBoxSets = containingBoxSets,
                            similarItems = similarItems,
                            nextEpisode = nextEpisode,
                            baseUrl = baseUrl,
                            specialFeatures = specialFeatures,
                            episodesPagingData = episodesPagingData,
                            tmdbReviews = tmdbReviews,
                            onPlayClick = onPlayClick,
                            onBoxSetItemClick = onBoxSetItemClick,
                            onSpecialFeatureClick = onSpecialFeatureClick,
                            navController = navController,
                            viewModel = viewModel,
                            preferencesRepository = preferencesRepository,
                            widthSizeClass = widthSizeClass,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitItemDetailContent(
    item: AfinityItem,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    isInWatchlist: Boolean,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    downloadInfo: DownloadInfo?,
    tmdbReviews: List<TmdbReview>,
    mdbRatings: List<MdbListRating>,
    isRatingsFromCache: Boolean,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel,
    context: Context,
    widthSizeClass: WindowWidthSizeClass,
) {
    val preferencesRepository = rememberPreferencesRepository()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()
    val localTrailerUrl by viewModel.localTrailerUrl.collectAsStateWithLifecycle()
    val isTrailerPlaying by viewModel.isTrailerPlaying.collectAsStateWithLifecycle()
    val isTrailerMuted by viewModel.isTrailerMuted.collectAsStateWithLifecycle()
    val autoPlayTrailers by viewModel.autoPlayTrailers.collectAsStateWithLifecycle()

    LaunchedEffect(localTrailerUrl, autoPlayTrailers) {
        if (localTrailerUrl != null && autoPlayTrailers) {
            delay(3000)
            viewModel.startTrailer()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
    ) {
        item {
            HeroSection(
                item = item,
                localTrailerUrl = localTrailerUrl,
                isTrailerPlaying = isTrailerPlaying,
                isTrailerMuted = isTrailerMuted,
                onDismissTrailer = viewModel::dismissTrailer,
                onToggleMute = viewModel::toggleTrailerMute,
            )
        }

        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalLayoutOffset((-110).dp)
                        .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MediaLogoHeader(item = item, isLandscape = false)

                MetadataRow(
                    item = item,
                    boxSetItems = boxSetItems,
                    mdbRatings = mdbRatings,
                    isRatingsFromCache = isRatingsFromCache,
                )

                val mediaSourceOptions = rememberMediaSourceOptions(item)
                val selectedMediaSource by
                    viewModel.selectedMediaSource.collectAsStateWithLifecycle()

                LaunchedEffect(mediaSourceOptions) {
                    if (selectedMediaSource == null && mediaSourceOptions.isNotEmpty()) {
                        viewModel.selectMediaSource(mediaSourceOptions.first())
                    }
                }

                if (item !is AfinityBoxSet && item.canPlay) {
                    PrimaryPlaybackButton(
                        item = item,
                        nextEpisode = nextEpisode,
                        selectedMediaSource = selectedMediaSource,
                        onPlayRequested = { targetPlayItem, selection ->
                            handlePlayRequest(item, targetPlayItem, selection, context, onPlayClick)
                        },
                    )
                }

                ActionButtonsRow(
                    item = item,
                    isInWatchlist = isInWatchlist,
                    hasTrailer = hasTrailer(item) || localTrailerUrl != null,
                    downloadInfo = downloadInfo,
                    onPlayTrailer = {
                        if (localTrailerUrl != null) viewModel.startTrailer()
                        else playTrailer(item, context, viewModel)
                    },
                    onToggleWatchlist = { viewModel.toggleWatchlist() },
                    onShufflePlay = { shufflePlay(item, nextEpisode, context) },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onToggleWatched = { viewModel.toggleWatched() },
                    onDownloadClick = { viewModel.onDownloadClick() },
                    onPauseDownload = { viewModel.pauseDownload() },
                    onResumeDownload = { viewModel.resumeDownload() },
                    onCancelDownload = { viewModel.cancelDownload() },
                    canDownload = canDownload,
                    isLandscape = false,
                )

                VideoQualitySelection(
                    mediaSourceOptions = mediaSourceOptions,
                    selectedSource = selectedMediaSource,
                    onSourceSelected = viewModel::selectMediaSource,
                )

                TypeSpecificContent(
                    item = item,
                    seasons = seasons,
                    boxSetItems = boxSetItems,
                    containingBoxSets = containingBoxSets,
                    similarItems = similarItems,
                    nextEpisode = nextEpisode,
                    baseUrl = baseUrl,
                    specialFeatures = specialFeatures,
                    episodesPagingData = episodesPagingData,
                    tmdbReviews = tmdbReviews,
                    onPlayClick = onPlayClick,
                    onBoxSetItemClick = onBoxSetItemClick,
                    onSpecialFeatureClick = onSpecialFeatureClick,
                    navController = navController,
                    viewModel = viewModel,
                    preferencesRepository = preferencesRepository,
                    widthSizeClass = widthSizeClass,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MediaLogoHeader(item: AfinityItem, isLandscape: Boolean) {
    val logoToDisplay = if (item is AfinitySeason) item.images.showLogo else item.images.logo
    val logoUrlToDisplay =
        if (item is AfinitySeason) {
            item.images.showLogoImageUrl?.let { url ->
                if (url.contains("?")) "$url&format=png" else "$url?format=png"
            }
        } else item.images.logoImageUrlWithTransparency
    val logoNameToDisplay = if (item is AfinitySeason) item.seriesName else item.name

    if (logoToDisplay != null) {
        AsyncImage(
            imageUrl = logoUrlToDisplay,
            contentDescription = stringResource(R.string.cd_logo_fmt, logoNameToDisplay),
            targetWidth = if (isLandscape) 300.dp else 240.dp,
            targetHeight = if (isLandscape) 150.dp else 120.dp,
            modifier =
                Modifier.fillMaxWidth(0.8f)
                    .height(if (isLandscape) 150.dp else 120.dp)
                    .align(if (isLandscape) Alignment.Start else Alignment.CenterHorizontally),
            contentScale = ContentScale.Fit,
            alignment = if (isLandscape) Alignment.CenterStart else Alignment.Center,
        )
    } else {
        Text(
            text = logoNameToDisplay,
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (!isLandscape) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TypeSpecificContent(
    item: AfinityItem,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    similarItems: List<AfinityItem>,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    episodesPagingData: Flow<PagingData<AfinityEpisode>>?,
    tmdbReviews: List<TmdbReview>,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel,
    preferencesRepository: com.makd.afinity.data.repository.PreferencesRepository,
    widthSizeClass: WindowWidthSizeClass,
) {
    when (item) {
        is AfinityShow ->
            SeriesDetailContent(
                item = item,
                seasons = seasons,
                nextEpisode = nextEpisode,
                specialFeatures = specialFeatures,
                containingBoxSets = containingBoxSets,
                tmdbReviews = tmdbReviews,
                onEpisodeClick = { ep ->
                    val mediaSourceId = ep.sources.firstOrNull()?.id ?: return@SeriesDetailContent
                    val startPos =
                        if (ep.playbackPositionTicks > 0) ep.playbackPositionTicks / 10000 else 0L
                    PlayerLauncher.launch(
                        navController.context,
                        ep.id,
                        mediaSourceId,
                        null,
                        null,
                        startPos,
                    )
                },
                onSpecialFeatureClick = { sf ->
                    val mediaSourceId = sf.sources.firstOrNull()?.id
                    if (mediaSourceId != null) {
                        val startPos =
                            if (sf.playbackPositionTicks > 0) sf.playbackPositionTicks / 10000
                            else 0L
                        PlayerLauncher.launch(
                            context = navController.context,
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
                navController = navController,
                widthSizeClass = widthSizeClass,
            )
        is AfinitySeason ->
            SeasonDetailContent(
                season = item,
                episodesPagingData = episodesPagingData,
                specialFeatures = specialFeatures,
                containingBoxSets = containingBoxSets,
                tmdbReviews = tmdbReviews,
                onEpisodeClick = { ep -> viewModel.selectEpisode(ep) },
                onSpecialFeatureClick = onSpecialFeatureClick,
                navController = navController,
                preferencesRepository = preferencesRepository,
                widthSizeClass = widthSizeClass,
            )
        is AfinityMovie ->
            MovieDetailContent(
                item = item,
                baseUrl = baseUrl,
                specialFeatures = specialFeatures,
                containingBoxSets = containingBoxSets,
                tmdbReviews = tmdbReviews,
                onSpecialFeatureClick = onSpecialFeatureClick,
                onPlayClick = { movie, sel -> onPlayClick(movie, sel) },
                navController = navController,
                widthSizeClass = widthSizeClass,
            )
        is AfinityBoxSet ->
            BoxSetDetailContent(
                item = item,
                boxSetItems = boxSetItems,
                onItemClick = onBoxSetItemClick,
                widthSizeClass = widthSizeClass,
            )
    }

    if (item !is AfinityBoxSet && similarItems.isNotEmpty()) {
        SimilarItemsSection(
            items = similarItems,
            onItemClick = { sim ->
                val route =
                    Destination.createItemDetailRoute(
                        itemId = sim.id.toString(),
                        itemType =
                            when (sim) {
                                is AfinityShow -> "Series"
                                is AfinitySeason -> "Season"
                                else -> null
                            },
                        seriesId = (sim as? AfinitySeason)?.seriesId?.toString(),
                    )
                navController.navigate(route)
            },
            widthSizeClass = widthSizeClass,
        )
    }
}

@Composable
private fun rememberMediaSourceOptions(item: AfinityItem): List<MediaSourceOption> {
    return remember(item) {
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
                        val codec = videoStream?.codec?.uppercase() ?: "Unknown"
                        "$resolution $codec"
                    }
                }
            MediaSourceOption(
                id = source.id,
                name = displayName,
                quality = resolution,
                codec = videoStream?.codec?.uppercase() ?: "Unknown",
                size = source.size,
                isDefault = index == 0,
            )
        }
    }
}

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
