package com.makd.afinity.ui.item

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.PagingData
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showLogoImageUrl
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.item.components.BoxSetDetailContent
import com.makd.afinity.ui.item.components.DirectorSection
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.item.components.MovieDetailContent
import com.makd.afinity.ui.item.components.OverviewSection
import com.makd.afinity.ui.item.components.QualitySelectionDialog
import com.makd.afinity.ui.item.components.SeasonDetailContent
import com.makd.afinity.ui.item.components.SeasonsSection
import com.makd.afinity.ui.item.components.TaglineSection
import com.makd.afinity.ui.item.components.WriterSection
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.HeroSection
import com.makd.afinity.ui.item.components.shared.InCollectionsSection
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.MetadataRow
import com.makd.afinity.ui.item.components.shared.NextUpSection
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.PlaybackSelectionButton
import com.makd.afinity.ui.item.components.shared.SimilarItemsSection
import com.makd.afinity.ui.item.components.shared.SpecialFeaturesSection
import com.makd.afinity.ui.item.components.shared.VideoQualitySelection
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

@Composable
fun ItemDetailScreen(
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val nextEpisode = uiState.nextEpisode
    val context = LocalContext.current
    val selectedEpisodeWatchlistStatus by viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Something went wrong",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Data will refresh automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    onPlayClick = { item, selection -> onPlayClick(item, selection) },
                    onSeasonClick = { season ->
                        val route = Destination.createEpisodeListRoute(
                            season.id.toString(),
                            season.name
                        )
                        navController.navigate(route)
                    },
                    onBoxSetItemClick = { item ->
                        val route = Destination.createItemDetailRoute(item.id.toString())
                        navController.navigate(route)
                    },
                    onSpecialFeatureClick = { specialFeature ->
                        val route = Destination.createItemDetailRoute(specialFeature.id.toString())
                        navController.navigate(route)
                    },
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
        selectedEpisode?.let { episode ->
            EpisodeDetailOverlay(
                episode = episode,
                isLoading = isLoadingEpisode,
                isInWatchlist = selectedEpisodeWatchlistStatus,
                downloadInfo = selectedEpisodeDownloadInfo,
                onDismiss = { viewModel.clearSelectedEpisode() },
                onPlayClick = { episodeToPlay, selection ->
                    viewModel.clearSelectedEpisode()

                    com.makd.afinity.ui.player.PlayerLauncher.launch(
                        context = context,
                        itemId = episodeToPlay.id,
                        mediaSourceId = selection.mediaSourceId,
                        audioStreamIndex = selection.audioStreamIndex,
                        subtitleStreamIndex = selection.subtitleStreamIndex,
                        startPositionMs = selection.startPositionMs
                    )
                },
                onToggleFavorite = {
                    viewModel.toggleEpisodeFavorite(episode)
                },
                onToggleWatchlist = {
                    viewModel.toggleEpisodeWatchlist(episode)
                },
                onToggleWatched = {
                    viewModel.toggleEpisodeWatched(episode)
                },
                onDownloadClick = {
                    viewModel.onDownloadClick()
                },
                onPauseDownload = {
                    viewModel.pauseDownload()
                },
                onResumeDownload = {
                    viewModel.resumeDownload()
                },
                onCancelDownload = {
                    viewModel.cancelDownload()
                }
            )
        }

        if (uiState.showQualityDialog) {
            val currentItem = selectedEpisode ?: uiState.item
            val remoteSources = currentItem?.sources?.filter {
                it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE
            } ?: emptyList()

            if (remoteSources.isNotEmpty()) {
                QualitySelectionDialog(
                    sources = remoteSources,
                    onSourceSelected = { source ->
                        viewModel.onQualitySelected(source.id)
                    },
                    onDismiss = { viewModel.dismissQualityDialog() }
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
    downloadInfo: com.makd.afinity.data.models.download.DownloadInfo?,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel
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
            onPlayClick = onPlayClick,
            onSeasonClick = onSeasonClick,
            onBoxSetItemClick = onBoxSetItemClick,
            onSpecialFeatureClick = onSpecialFeatureClick,
            navController = navController,
            viewModel = viewModel,
            context = context
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
            onPlayClick = onPlayClick,
            onSeasonClick = onSeasonClick,
            onBoxSetItemClick = onBoxSetItemClick,
            onSpecialFeatureClick = onSpecialFeatureClick,
            navController = navController,
            viewModel = viewModel,
            context = context
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
    downloadInfo: com.makd.afinity.data.models.download.DownloadInfo?,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel,
    context: android.content.Context
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val displayCutoutLeft = WindowInsets.displayCutout.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val backdropUrl = if (item is AfinitySeason) {
            item.images.backdropImageUrl ?: item.images.showBackdropImageUrl ?: item.images.primaryImageUrl
        } else {
            item.images.backdropImageUrl ?: item.images.primaryImageUrl
        }

        if (backdropUrl != null) {
            OptimizedAsyncImage(
                imageUrl = backdropUrl,
                contentDescription = "${item.name} backdrop",
                targetWidth = 1920.dp,
                targetHeight = 1080.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(0.dp),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Image(
            painter = painterResource(id = R.drawable.mask),
            contentDescription = "Mask overlay",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = with(density) { statusBarHeight.toDp() + 16.dp },
                start = with(density) { displayCutoutLeft.toDp() + 16.dp },
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val logoToDisplay = if (item is AfinitySeason) {
                        item.images.showLogo
                    } else {
                        item.images.logo
                    }

                    val logoUrlToDisplay = if (item is AfinitySeason) {
                        item.images.showLogoImageUrl?.let { url ->
                            if (url.contains("?")) "$url&format=png" else "$url?format=png"
                        }
                    } else {
                        item.images.logoImageUrlWithTransparency
                    }

                    val logoNameToDisplay = if (item is AfinitySeason) {
                        item.seriesName
                    } else {
                        item.name
                    }

                    if (logoToDisplay != null) {
                        OptimizedAsyncImage(
                            imageUrl = logoUrlToDisplay,
                            contentDescription = "$logoNameToDisplay logo",
                            targetWidth = 300.dp,
                            targetHeight = 150.dp,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(150.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart
                        )
                    } else {
                        Text(
                            text = logoNameToDisplay,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    MetadataRow(item = item)

                    val mediaSourceOptions = remember(item) {
                        item.sources.mapIndexed { index, source ->
                            val videoStream = source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }

                            val resolution = when {
                                (videoStream?.height ?: 0) > 2160 -> "8K"
                                (videoStream?.height ?: 0) > 1080 -> "4K"
                                (videoStream?.height ?: 0) > 720 -> "1080p"
                                (videoStream?.height ?: 0) > 480 -> "720p"
                                else -> "SD"
                            }

                            val displayName = when {
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
                                isDefault = index == 0
                            )
                        }
                    }

                    val selectedMediaSource by viewModel.selectedMediaSource.collectAsStateWithLifecycle()

                    LaunchedEffect(mediaSourceOptions) {
                        if (selectedMediaSource == null && mediaSourceOptions.isNotEmpty()) {
                            viewModel.selectMediaSource(mediaSourceOptions.first())
                        }
                    }

                    val hasTrailer = when (item) {
                        is AfinityMovie -> item.trailer != null
                        is AfinityShow -> item.trailer != null
                        is AfinityVideo -> item.trailer != null
                        else -> false
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item !is AfinityBoxSet && item.canPlay) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .widthIn(max = 200.dp)
                            ) {
                                when (item) {
                                    is AfinityShow -> {
                                        when {
                                            nextEpisode == null -> {
                                                Button(
                                                    onClick = { },
                                                    enabled = false,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(56.dp),
                                                    shape = RoundedCornerShape(28.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Loading...")
                                                }
                                            }
                                            else -> {
                                                val episode = nextEpisode!!
                                                val (buttonText, buttonIcon) = when {
                                                    episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                        "Rewatch" to painterResource(id = R.drawable.replay)

                                                    }
                                                    episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                        "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                    else -> {
                                                        "Play" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                }

                                                PlaybackSelectionButton(
                                                    item = episode,
                                                    buttonText = buttonText,
                                                    buttonIcon = buttonIcon,
                                                    onPlayClick = { selection ->
                                                        if (episode.sources.isEmpty()) {
                                                            Timber.w("Episode ${episode.name} has no media sources")
                                                            return@PlaybackSelectionButton
                                                        }

                                                        val mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull {
                                                            it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                                                        }?.id ?: episode.sources.firstOrNull()?.id ?: ""

                                                        val finalSelection = selection.copy(
                                                            mediaSourceId = mediaSourceId,
                                                            startPositionMs = if (episode.playbackPositionTicks > 0) {
                                                                episode.playbackPositionTicks / 10000
                                                            } else {
                                                                0L
                                                            }
                                                        )
                                                        com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                            context = navController.context,
                                                            itemId = episode.id,
                                                            mediaSourceId = finalSelection.mediaSourceId,
                                                            audioStreamIndex = finalSelection.audioStreamIndex,
                                                            subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                            startPositionMs = finalSelection.startPositionMs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    is AfinitySeason -> {
                                        when {
                                            nextEpisode == null -> {
                                                Button(
                                                    onClick = { },
                                                    enabled = false,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(56.dp),
                                                    shape = RoundedCornerShape(28.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Loading...")
                                                }
                                            }
                                            else -> {
                                                val episode = nextEpisode!!
                                                val (buttonText, buttonIcon) = when {
                                                    episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                        "Rewatch" to painterResource(id = R.drawable.replay)
                                                    }
                                                    episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                        "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                    else -> {
                                                        "Play" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                }

                                                PlaybackSelectionButton(
                                                    item = episode,
                                                    buttonText = buttonText,
                                                    buttonIcon = buttonIcon,
                                                    onPlayClick = { selection ->
                                                        if (episode.sources.isEmpty()) {
                                                            Timber.w("Episode ${episode.name} has no media sources")
                                                            return@PlaybackSelectionButton
                                                        }

                                                        val mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull {
                                                            it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                                                        }?.id ?: episode.sources.firstOrNull()?.id ?: ""

                                                        val finalSelection = selection.copy(
                                                            mediaSourceId = mediaSourceId,
                                                            startPositionMs = if (episode.playbackPositionTicks > 0) {
                                                                episode.playbackPositionTicks / 10000
                                                            } else {
                                                                0L
                                                            }
                                                        )
                                                        com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                            context = navController.context,
                                                            itemId = episode.id,
                                                            mediaSourceId = finalSelection.mediaSourceId,
                                                            audioStreamIndex = finalSelection.audioStreamIndex,
                                                            subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                            startPositionMs = finalSelection.startPositionMs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        val (buttonText, buttonIcon) = when {
                                            item.playbackPositionTicks > 0 && item.playbackPositionTicks >= item.runtimeTicks -> {
                                                "Rewatch" to painterResource(id = R.drawable.replay)
                                            }
                                            item.playbackPositionTicks > 0 && item.runtimeTicks > 0 -> {
                                                "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                            }
                                            else -> {
                                                "Watch Now" to painterResource(id = R.drawable.play_arrow)
                                            }
                                        }

                                        PlaybackSelectionButton(
                                            item = item,
                                            buttonText = buttonText,
                                            buttonIcon = buttonIcon,
                                            onPlayClick = { selection ->
                                                val finalSelection = selection.copy(
                                                    mediaSourceId = selectedMediaSource?.id ?: selection.mediaSourceId
                                                )
                                                com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                    context = navController.context,
                                                    itemId = item.id,
                                                    mediaSourceId = finalSelection.mediaSourceId,
                                                    audioStreamIndex = finalSelection.audioStreamIndex,
                                                    subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                    startPositionMs = finalSelection.startPositionMs
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = {
                                    if (hasTrailer) {
                                        viewModel.onPlayTrailerClick(context, item)
                                    }
                                },
                                enabled = hasTrailer
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.video),
                                    contentDescription = "Play Trailer",
                                    tint = if (hasTrailer) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleWatchlist() }
                            ) {
                                Icon(
                                    painter = if (isInWatchlist) painterResource(id = R.drawable.bookmark) else painterResource(id = R.drawable.bookmark_filled),
                                    contentDescription = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                                    tint = if (isInWatchlist) Color(0xFFFF9800) else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleFavorite() }
                            ) {
                                Icon(
                                    painter = if (item.favorite) painterResource(id = R.drawable.favorite_filled) else painterResource(id = R.drawable.favorite),
                                    contentDescription = if (item.favorite) "Remove from Favorites" else "Add to Favorites",
                                    tint = if (item.favorite) Color.Red else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleWatched() }
                            ) {
                                Icon(
                                    painter = if (item.played) painterResource(id = R.drawable.circle_check) else painterResource(id = R.drawable.circle_check_outline),
                                    contentDescription = if (item.played) "Mark as Unwatched" else "Mark as Watched",
                                    tint = if (item.played) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            DownloadProgressIndicator(
                                downloadInfo = downloadInfo,
                                onDownloadClick = { viewModel.onDownloadClick() },
                                onPauseClick = { viewModel.pauseDownload() },
                                onResumeClick = { viewModel.resumeDownload() },
                                onCancelClick = { viewModel.cancelDownload() },
                                isLandscape = true
                            )
                        }
                    }

                    VideoQualitySelection(
                        mediaSourceOptions = mediaSourceOptions,
                        selectedSource = selectedMediaSource,
                        onSourceSelected = viewModel::selectMediaSource
                    )

                    when (item) {
                        is AfinityShow -> SeriesDetailContent(
                            item = item,
                            seasons = seasons,
                            nextEpisode = nextEpisode,
                            specialFeatures = specialFeatures,
                            containingBoxSets = containingBoxSets,
                            onSeasonClick = onSeasonClick,
                            onEpisodeClick = { clickedEpisode ->
                                if (clickedEpisode.sources.isEmpty()) {
                                    Timber.w("Episode ${clickedEpisode.name} has no media sources")
                                    return@SeriesDetailContent
                                }
                                val mediaSourceId = clickedEpisode.sources.firstOrNull()?.id ?: ""
                                val startPositionMs = if (clickedEpisode.playbackPositionTicks > 0) {
                                    clickedEpisode.playbackPositionTicks / 10000
                                } else {
                                    0L
                                }
                                com.makd.afinity.ui.player.PlayerLauncher.launch(
                                    context = navController.context,
                                    itemId = clickedEpisode.id,
                                    mediaSourceId = mediaSourceId,
                                    audioStreamIndex = null,
                                    subtitleStreamIndex = null,
                                    startPositionMs = startPositionMs
                                )
                            },
                            onSpecialFeatureClick = { specialFeature ->
                                val route = Destination.createItemDetailRoute(specialFeature.id.toString())
                                navController.navigate(route)
                            },
                            navController = navController
                        )

                        is AfinitySeason -> SeasonDetailContent(
                            season = item,
                            episodesPagingData = episodesPagingData,
                            specialFeatures = specialFeatures,
                            onEpisodeClick = { episode ->
                                viewModel.selectEpisode(episode)
                            },
                            onSpecialFeatureClick = onSpecialFeatureClick,
                            navController = navController
                        )

                        is AfinityMovie -> MovieDetailContent(
                            item = item,
                            baseUrl = baseUrl,
                            specialFeatures = specialFeatures,
                            containingBoxSets = containingBoxSets,
                            onSpecialFeatureClick = onSpecialFeatureClick,
                            onPlayClick = { movie, selection ->
                                onPlayClick(movie, selection)
                            },
                            navController = navController
                        )

                        is AfinityBoxSet -> BoxSetDetailContent(
                            item = item,
                            boxSetItems = boxSetItems,
                            onItemClick = onBoxSetItemClick
                        )

                        else -> {
                            TaglineSection(item = item)
                            OverviewSection(item = item)
                            DirectorSection(item = item)
                            WriterSection(item = item)
                            ExternalLinksSection(item = item)
                            CastSection(
                                item = item,
                                onPersonClick = { personId ->
                                    val route = Destination.createPersonRoute(personId.toString())
                                    navController.navigate(route)
                                }
                            )
                        }
                    }

                    if (item !is AfinityBoxSet && similarItems.isNotEmpty()) {
                        SimilarItemsSection(
                            items = similarItems,
                            onItemClick = { similarItem ->
                                val route = Destination.createItemDetailRoute(similarItem.id.toString())
                                navController.navigate(route)
                            }
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
    downloadInfo: com.makd.afinity.data.models.download.DownloadInfo?,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel,
    context: android.content.Context
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            HeroSection(
                item = item,
                onPlayClick = onPlayClick
            )
        }

        item {
            val overlayOffset = (-110).dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = overlayOffset)
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val logoToDisplay = if (item is AfinitySeason) {
                    item.images.showLogo
                } else {
                    item.images.logo
                }

                val logoUrlToDisplay = if (item is AfinitySeason) {
                    item.images.showLogoImageUrl?.let { url ->
                        if (url.contains("?")) "$url&format=png" else "$url?format=png"
                    }
                } else {
                    item.images.logoImageUrlWithTransparency
                }

                val logoNameToDisplay = if (item is AfinitySeason) {
                    item.seriesName
                } else {
                    item.name
                }

                if (logoToDisplay != null) {
                    val logoAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally
                    val logoContentAlignment = if (isLandscape) Alignment.CenterStart else Alignment.Center

                    OptimizedAsyncImage(
                        imageUrl = logoUrlToDisplay,
                        contentDescription = "$logoNameToDisplay logo",
                        targetWidth = 240.dp,
                        targetHeight = 120.dp,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(120.dp)
                            .align(logoAlignment),
                        contentScale = ContentScale.Fit,
                        alignment = logoContentAlignment
                    )
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }

                if (logoToDisplay == null) {
                    Text(
                        text = logoNameToDisplay,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                MetadataRow(item = item)

                val mediaSourceOptions = remember(item) {
                    item.sources.mapIndexed { index, source ->
                        val videoStream = source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }

                        val resolution = when {
                            (videoStream?.height ?: 0) > 2160 -> "8K"
                            (videoStream?.height ?: 0) > 1080 -> "4K"
                            (videoStream?.height ?: 0) > 720 -> "1080p"
                            (videoStream?.height ?: 0) > 480 -> "720p"
                            else -> "SD"
                        }

                        val displayName = when {
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
                            isDefault = index == 0
                        )
                    }
                }

                val selectedMediaSource by viewModel.selectedMediaSource.collectAsStateWithLifecycle()

                LaunchedEffect(mediaSourceOptions) {
                    if (selectedMediaSource == null && mediaSourceOptions.isNotEmpty()) {
                        viewModel.selectMediaSource(mediaSourceOptions.first())
                    }
                }

                val hasTrailer = when (item) {
                    is AfinityMovie -> item.trailer != null
                    is AfinityShow -> item.trailer != null
                    is AfinityVideo -> item.trailer != null
                    else -> false
                }

                if (isLandscape) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item !is AfinityBoxSet && item.canPlay) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .widthIn(max = 200.dp)
                            ) {
                                when (item) {
                                    is AfinityShow -> {

                                        when {
                                            nextEpisode == null -> {
                                                Button(
                                                    onClick = { },
                                                    enabled = false,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(56.dp),
                                                    shape = RoundedCornerShape(28.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Loading...")
                                                }
                                            }
                                            else -> {
                                                val episode = nextEpisode!!
                                                val (buttonText, buttonIcon) = when {
                                                    episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                        "Rewatch" to painterResource(id = R.drawable.replay)
                                                    }
                                                    episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                        "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                    else -> {
                                                        "Play" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                }

                                                PlaybackSelectionButton(
                                                    item = episode,
                                                    buttonText = buttonText,
                                                    buttonIcon = buttonIcon,
                                                    onPlayClick = { selection ->
                                                        if (episode.sources.isEmpty()) {
                                                            Timber.w("Episode ${episode.name} has no media sources")
                                                            return@PlaybackSelectionButton
                                                        }

                                                        val mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull {
                                                            it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                                                        }?.id ?: episode.sources.firstOrNull()?.id ?: ""

                                                        val finalSelection = selection.copy(
                                                            mediaSourceId = mediaSourceId,
                                                            startPositionMs = if (episode.playbackPositionTicks > 0) {
                                                                episode.playbackPositionTicks / 10000
                                                            } else {
                                                                0L
                                                            }
                                                        )
                                                        com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                            context = navController.context,
                                                            itemId = episode.id,
                                                            mediaSourceId = finalSelection.mediaSourceId,
                                                            audioStreamIndex = finalSelection.audioStreamIndex,
                                                            subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                            startPositionMs = finalSelection.startPositionMs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    is AfinitySeason -> {

                                        when {
                                            nextEpisode == null -> {
                                                Button(
                                                    onClick = { },
                                                    enabled = false,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(56.dp),
                                                    shape = RoundedCornerShape(28.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Loading...")
                                                }
                                            }
                                            else -> {
                                                val episode = nextEpisode!!
                                                val (buttonText, buttonIcon) = when {
                                                    episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                        "Rewatch" to painterResource(id = R.drawable.replay)
                                                    }
                                                    episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                        "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                    else -> {
                                                        "Play" to painterResource(id = R.drawable.play_arrow)
                                                    }
                                                }

                                                PlaybackSelectionButton(
                                                    item = episode,
                                                    buttonText = buttonText,
                                                    buttonIcon = buttonIcon,
                                                    onPlayClick = { selection ->
                                                        if (episode.sources.isEmpty()) {
                                                            Timber.w("Episode ${episode.name} has no media sources")
                                                            return@PlaybackSelectionButton
                                                        }

                                                        val mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull {
                                                            it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                                                        }?.id ?: episode.sources.firstOrNull()?.id ?: ""

                                                        val finalSelection = selection.copy(
                                                            mediaSourceId = mediaSourceId,
                                                            startPositionMs = if (episode.playbackPositionTicks > 0) {
                                                                episode.playbackPositionTicks / 10000
                                                            } else {
                                                                0L
                                                            }
                                                        )
                                                        com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                            context = navController.context,
                                                            itemId = episode.id,
                                                            mediaSourceId = finalSelection.mediaSourceId,
                                                            audioStreamIndex = finalSelection.audioStreamIndex,
                                                            subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                            startPositionMs = finalSelection.startPositionMs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        val (buttonText, buttonIcon) = when {
                                            item.playbackPositionTicks > 0 && item.playbackPositionTicks >= item.runtimeTicks -> {
                                                "Rewatch" to painterResource(id = R.drawable.replay)
                                            }
                                            item.playbackPositionTicks > 0 && item.runtimeTicks > 0 -> {
                                                "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                            }
                                            else -> {
                                                "Watch Now" to painterResource(id = R.drawable.play_arrow)
                                            }
                                        }

                                        PlaybackSelectionButton(
                                            item = item,
                                            buttonText = buttonText,
                                            buttonIcon = buttonIcon,
                                            onPlayClick = { selection ->
                                                val finalSelection = selection.copy(
                                                    mediaSourceId = selectedMediaSource?.id ?: selection.mediaSourceId
                                                )
                                                com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                    context = navController.context,
                                                    itemId = item.id,
                                                    mediaSourceId = finalSelection.mediaSourceId,
                                                    audioStreamIndex = finalSelection.audioStreamIndex,
                                                    subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                    startPositionMs = finalSelection.startPositionMs
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val hasTrailer = when (item) {
                                is AfinityMovie -> item.trailer != null
                                is AfinityShow -> item.trailer != null
                                is AfinityVideo -> item.trailer != null
                                else -> false
                            }

                            IconButton(
                                onClick = {
                                    if (hasTrailer) {
                                        viewModel.onPlayTrailerClick(context, item)
                                    }
                                },
                                enabled = hasTrailer
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.video),
                                    contentDescription = "Play Trailer",
                                    tint = if (hasTrailer) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleWatchlist() }
                            ) {
                                Icon(
                                    painter = if (isInWatchlist) painterResource(id = R.drawable.bookmark_filled) else painterResource(id = R.drawable.bookmark),
                                    contentDescription = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                                    tint = if (isInWatchlist) Color(0xFFFF9800) else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleFavorite() }
                            ) {
                                Icon(
                                    painter = if (item.favorite) painterResource(id = R.drawable.favorite_filled) else painterResource(id = R.drawable.favorite),
                                    contentDescription = if (item.favorite) "Remove from Favorites" else "Add to Favorites",
                                    tint = if (item.favorite) Color.Red else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleWatched() }
                            ) {
                                Icon(
                                    painter = if (item.played) painterResource(id = R.drawable.circle_check) else painterResource(id = R.drawable.circle_check_outline),
                                    contentDescription = if (item.played) "Mark as Unwatched" else "Mark as Watched",
                                    tint = if (item.played) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            DownloadProgressIndicator(
                                downloadInfo = downloadInfo,
                                onDownloadClick = { viewModel.onDownloadClick() },
                                onPauseClick = { viewModel.pauseDownload() },
                                onResumeClick = { viewModel.resumeDownload() },
                                onCancelClick = { viewModel.cancelDownload() },
                                isLandscape = true
                            )
                        }
                    }
                } else {
                    if (item !is AfinityBoxSet && item.canPlay) {
                        when (item) {
                            is AfinityShow -> {

                                when {
                                    nextEpisode == null -> {
                                        Button(
                                            onClick = { },
                                            enabled = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = RoundedCornerShape(28.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Loading...")
                                        }
                                    }
                                    else -> {
                                        val episode = nextEpisode!!
                                        val (buttonText, buttonIcon) = when {
                                            episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                "Rewatch" to painterResource(id = R.drawable.replay)
                                            }
                                            episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                            }
                                            else -> {
                                                "Play" to painterResource(id = R.drawable.play_arrow)
                                            }
                                        }

                                        PlaybackSelectionButton(
                                            item = episode,
                                            buttonText = buttonText,
                                            buttonIcon = buttonIcon,
                                            onPlayClick = { selection ->
                                                if (episode.sources.isEmpty()) {
                                                    Timber.w("Episode ${episode.name} has no media sources")
                                                    return@PlaybackSelectionButton
                                                }
                                                val mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull {
                                                    it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                                                }?.id ?: episode.sources.firstOrNull()?.id ?: ""

                                                val finalSelection = selection.copy(
                                                    mediaSourceId = mediaSourceId,
                                                    startPositionMs = if (episode.playbackPositionTicks > 0) {
                                                        episode.playbackPositionTicks / 10000
                                                    } else {
                                                        0L
                                                    }
                                                )
                                                com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                    context = navController.context,
                                                    itemId = episode.id,
                                                    mediaSourceId = finalSelection.mediaSourceId,
                                                    audioStreamIndex = finalSelection.audioStreamIndex,
                                                    subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                    startPositionMs = finalSelection.startPositionMs
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            is AfinitySeason -> {

                                when {
                                    nextEpisode == null -> {
                                        Button(
                                            onClick = { },
                                            enabled = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = RoundedCornerShape(28.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Loading...")
                                        }
                                    }
                                    else -> {
                                        val episode = nextEpisode!!
                                        val (buttonText, buttonIcon) = when {
                                            episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                "Rewatch" to painterResource(id = R.drawable.replay)
                                            }
                                            episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                            }
                                            else -> {
                                                "Play" to painterResource(id = R.drawable.play_arrow)
                                            }
                                        }

                                        PlaybackSelectionButton(
                                            item = episode,
                                            buttonText = buttonText,
                                            buttonIcon = buttonIcon,
                                            onPlayClick = { selection ->
                                                if (episode.sources.isEmpty()) {
                                                    Timber.w("Episode ${episode.name} has no media sources")
                                                    return@PlaybackSelectionButton
                                                }
                                                val mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull {
                                                    it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                                                }?.id ?: episode.sources.firstOrNull()?.id ?: ""

                                                val finalSelection = selection.copy(
                                                    mediaSourceId = mediaSourceId,
                                                    startPositionMs = if (episode.playbackPositionTicks > 0) {
                                                        episode.playbackPositionTicks / 10000
                                                    } else {
                                                        0L
                                                    }
                                                )
                                                com.makd.afinity.ui.player.PlayerLauncher.launch(
                                                    context = navController.context,
                                                    itemId = episode.id,
                                                    mediaSourceId = finalSelection.mediaSourceId,
                                                    audioStreamIndex = finalSelection.audioStreamIndex,
                                                    subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                                    startPositionMs = finalSelection.startPositionMs
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            else -> {
                                val (buttonText, buttonIcon) = when {
                                    item.playbackPositionTicks > 0 && item.playbackPositionTicks >= item.runtimeTicks -> {
                                        "Rewatch" to painterResource(id = R.drawable.replay)
                                    }
                                    item.playbackPositionTicks > 0 && item.runtimeTicks > 0 -> {
                                        "Resume Playback" to painterResource(id = R.drawable.play_arrow)
                                    }
                                    else -> {
                                        "Watch Now" to painterResource(id = R.drawable.play_arrow)
                                    }
                                }

                                PlaybackSelectionButton(
                                    item = item,
                                    buttonText = buttonText,
                                    buttonIcon = buttonIcon,
                                    onPlayClick = { selection ->
                                        val finalSelection = selection.copy(
                                            mediaSourceId = selectedMediaSource?.id ?: selection.mediaSourceId
                                        )
                                        com.makd.afinity.ui.player.PlayerLauncher.launch(
                                            context = navController.context,
                                            itemId = item.id,
                                            mediaSourceId = finalSelection.mediaSourceId,
                                            audioStreamIndex = finalSelection.audioStreamIndex,
                                            subtitleStreamIndex = finalSelection.subtitleStreamIndex,
                                            startPositionMs = finalSelection.startPositionMs
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = {
                                    if (hasTrailer) {
                                        viewModel.onPlayTrailerClick(context, item)
                                    }
                                },
                                enabled = hasTrailer
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.video),
                                    contentDescription = "Play Trailer",
                                    tint = if (hasTrailer) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleWatchlist() }
                            ) {
                                Icon(
                                    painter = if (isInWatchlist) painterResource(id = R.drawable.bookmark_filled) else painterResource(id = R.drawable.bookmark),
                                    contentDescription = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                                    tint = if (isInWatchlist) Color(0xFFFF9800) else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleFavorite() }
                            ) {
                                Icon(
                                    painter = if (item.favorite) painterResource(id = R.drawable.favorite_filled) else painterResource(id = R.drawable.favorite),
                                    contentDescription = if (item.favorite) "Remove from Favorites" else "Add to Favorites",
                                    tint = if (item.favorite) Color.Red else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleWatched() }
                            ) {
                                Icon(
                                    painter = if (item.played) painterResource(id = R.drawable.circle_check) else painterResource(id = R.drawable.circle_check_outline),
                                    contentDescription = if (item.played) "Mark as Unwatched" else "Mark as Watched",
                                    tint = if (item.played) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DownloadProgressIndicator(
                                downloadInfo = downloadInfo,
                                onDownloadClick = { viewModel.onDownloadClick() },
                                onPauseClick = { viewModel.pauseDownload() },
                                onResumeClick = { viewModel.resumeDownload() },
                                onCancelClick = { viewModel.cancelDownload() },
                                isLandscape = false
                            )
                        }
                    }
                }

                VideoQualitySelection(
                    mediaSourceOptions = mediaSourceOptions,
                    selectedSource = selectedMediaSource,
                    onSourceSelected = viewModel::selectMediaSource
                )

                when (item) {
                    is AfinityShow -> SeriesDetailContent(
                        item = item,
                        seasons = seasons,
                        nextEpisode = nextEpisode,
                        specialFeatures = specialFeatures,
                        containingBoxSets = containingBoxSets,
                        onSeasonClick = onSeasonClick,
                        onEpisodeClick = { clickedEpisode ->
                            if (clickedEpisode.sources.isEmpty()) {
                                Timber.w("Episode ${clickedEpisode.name} has no media sources")
                                return@SeriesDetailContent
                            }
                            val mediaSourceId = clickedEpisode.sources.firstOrNull()?.id ?: ""
                            val startPositionMs = if (clickedEpisode.playbackPositionTicks > 0) {
                                clickedEpisode.playbackPositionTicks / 10000
                            } else {
                                0L
                            }
                            com.makd.afinity.ui.player.PlayerLauncher.launch(
                                context = navController.context,
                                itemId = clickedEpisode.id,
                                mediaSourceId = mediaSourceId,
                                audioStreamIndex = null,
                                subtitleStreamIndex = null,
                                startPositionMs = startPositionMs
                            )
                        },
                        onSpecialFeatureClick = { specialFeature ->
                            val route = Destination.createItemDetailRoute(specialFeature.id.toString())
                            navController.navigate(route)
                        },
                        navController = navController
                    )

                    is AfinitySeason -> SeasonDetailContent(
                        season = item,
                        episodesPagingData = episodesPagingData,
                        specialFeatures = specialFeatures,
                        onEpisodeClick = { episode ->
                            viewModel.selectEpisode(episode)
                        },
                        onSpecialFeatureClick = onSpecialFeatureClick,
                        navController = navController
                    )

                    is AfinityMovie -> MovieDetailContent(
                        item = item,
                        baseUrl = baseUrl,
                        specialFeatures = specialFeatures,
                        containingBoxSets = containingBoxSets,
                        onSpecialFeatureClick = onSpecialFeatureClick,
                        onPlayClick = { movie, selection ->
                            onPlayClick(movie, selection)
                        },
                        navController = navController
                    )

                    is AfinityBoxSet -> BoxSetDetailContent(
                        item = item,
                        boxSetItems = boxSetItems,
                        onItemClick = onBoxSetItemClick
                    )

                    else -> {
                        TaglineSection(item = item)
                        OverviewSection(item = item)
                        DirectorSection(item = item)
                        WriterSection(item = item)
                        ExternalLinksSection(item = item)
                        CastSection(
                            item = item,
                            onPersonClick = { personId ->
                                val route = Destination.createPersonRoute(personId.toString())
                                navController.navigate(route)
                            }
                        )
                    }
                }

                if (item !is AfinityBoxSet && similarItems.isNotEmpty()) {
                    SimilarItemsSection(
                        items = similarItems,
                        onItemClick = { similarItem ->
                            val route = Destination.createItemDetailRoute(similarItem.id.toString())
                            navController.navigate(route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesDetailContent(
    item: AfinityShow,
    seasons: List<AfinitySeason>,
    nextEpisode: AfinityEpisode?,
    specialFeatures: List<AfinityItem>,
    containingBoxSets: List<AfinityBoxSet>,
    onSeasonClick: (AfinitySeason) -> Unit,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TaglineSection(item = item)

        OverviewSection(item = item)

        if (nextEpisode != null) {
            NextUpSection(
                episode = nextEpisode,
                onEpisodeClick = onEpisodeClick
            )
        }

        DirectorSection(item = item)

        WriterSection(item = item)

        ExternalLinksSection(item = item)

        SpecialFeaturesSection(
            specialFeatures = specialFeatures,
            onItemClick = onSpecialFeatureClick
        )

        CastSection(
            item = item,
            onPersonClick = { personId ->
                val route = Destination.createPersonRoute(personId.toString())
                navController.navigate(route)
            }
        )

        InCollectionsSection(
            boxSets = containingBoxSets,
            onBoxSetClick = { boxSet ->
                val route = Destination.createItemDetailRoute(boxSet.id.toString())
                navController.navigate(route)
            }
        )

        if (seasons.isNotEmpty()) {
            SeasonsSection(
                seasons = seasons,
                onSeasonClick = onSeasonClick,
                navController = navController
            )
        }
    }
}