package com.makd.afinity.ui.item

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.data.models.extensions.logoImageUrl
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.item.components.BoxSetDetailContent
import com.makd.afinity.ui.item.components.DirectorSection
import com.makd.afinity.ui.item.components.MovieDetailContent
import com.makd.afinity.ui.item.components.OverviewSection
import com.makd.afinity.ui.item.components.SeasonsSection
import com.makd.afinity.ui.item.components.TaglineSection
import com.makd.afinity.ui.item.components.WriterSection
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.HeroSection
import com.makd.afinity.ui.item.components.shared.MediaSourceOption
import com.makd.afinity.ui.item.components.shared.MetadataRow
import com.makd.afinity.ui.item.components.shared.NextUpSection
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.PlaybackSelectionButton
import com.makd.afinity.ui.item.components.shared.SimilarItemsSection
import com.makd.afinity.ui.item.components.shared.SpecialFeaturesSection
import com.makd.afinity.ui.item.components.shared.VideoQualitySelection
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
    val nextEpisode = uiState.nextEpisode

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
                    similarItems = uiState.similarItems,
                    nextEpisode = nextEpisode,
                    baseUrl = viewModel.getBaseUrl(),
                    specialFeatures = uiState.specialFeatures,
                    isInWatchlist = uiState.isInWatchlist,
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
    }
}

@Composable
private fun ItemDetailContent(
    item: AfinityItem,
    seasons: List<AfinitySeason>,
    boxSetItems: List<AfinityItem>,
    similarItems: List<AfinityItem>,
    nextEpisode: AfinityEpisode?,
    baseUrl: String,
    specialFeatures: List<AfinityItem>,
    isInWatchlist: Boolean,
    onPlayClick: (AfinityItem, PlaybackSelection?) -> Unit,
    onSeasonClick: (AfinitySeason) -> Unit,
    onBoxSetItemClick: (AfinityItem) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    viewModel: ItemDetailViewModel
) {
    val context = LocalContext.current
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
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val overlayOffset = if (isLandscape) (-200).dp else (-110).dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = overlayOffset)
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (item.images.logo != null) {
                    val logoAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally
                    val logoContentAlignment = if (isLandscape) Alignment.CenterStart else Alignment.Center

                    OptimizedAsyncImage(
                        imageUrl = item.images.logoImageUrlWithTransparency,
                        contentDescription = "${item.name} logo",
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

                if (item.images.logo == null) {
                    Text(
                        text = item.name,
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

                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                        var episodeToPlay by remember { mutableStateOf<AfinityEpisode?>(null) }
                                        var isLoadingEpisode by remember { mutableStateOf(false) }

                                        LaunchedEffect(item.id) {
                                            isLoadingEpisode = true
                                            episodeToPlay = viewModel.getEpisodeToPlay(item.id)
                                            isLoadingEpisode = false
                                        }

                                        when {
                                            isLoadingEpisode -> {
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
                                            episodeToPlay != null -> {
                                                val episode = episodeToPlay!!
                                                val (buttonText, buttonIcon) = when {
                                                    episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                        "Rewatch" to Icons.Default.Replay
                                                    }
                                                    episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                        "Resume Playback" to Icons.Default.PlayArrow
                                                    }
                                                    else -> {
                                                        "Play" to Icons.Default.PlayArrow
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
                                                        val finalSelection = selection.copy(
                                                            mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull()?.id ?: "",
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
                                            else -> {
                                                Button(
                                                    onClick = { },
                                                    enabled = false,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(56.dp),
                                                    shape = RoundedCornerShape(28.dp)
                                                ) {
                                                    Text("No Episodes Available")
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        val (buttonText, buttonIcon) = when {
                                            item.playbackPositionTicks > 0 && item.playbackPositionTicks >= item.runtimeTicks -> {
                                                "Rewatch" to Icons.Default.Replay
                                            }
                                            item.playbackPositionTicks > 0 && item.runtimeTicks > 0 -> {
                                                "Resume Playback" to Icons.Default.PlayArrow
                                            }
                                            else -> {
                                                "Watch Now" to Icons.Default.PlayArrow
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
                                onClick = { viewModel.toggleWatchlist() }
                            ) {
                                Icon(
                                    imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                                    tint = if (isInWatchlist) Color(0xFFFF9800) else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.onPlayTrailerClick(context, item)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MovieCreation,
                                    contentDescription = "Play Trailer",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleFavorite() }
                            ) {
                                Icon(
                                    imageVector = if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (item.favorite) "Remove from Favorites" else "Add to Favorites",
                                    tint = if (item.favorite) Color.Red else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleWatched() }
                            ) {
                                Icon(
                                    imageVector = if (item.played) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                    contentDescription = if (item.played) "Mark as Unwatched" else "Mark as Watched",
                                    tint = if (item.played) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { /* TODO: Download */ }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                } else {
                    if (item !is AfinityBoxSet && item.canPlay) {
                        when (item) {
                            is AfinityShow -> {
                                var episodeToPlay by remember { mutableStateOf<AfinityEpisode?>(null) }
                                var isLoadingEpisode by remember { mutableStateOf(false) }

                                LaunchedEffect(item.id) {
                                    isLoadingEpisode = true
                                    episodeToPlay = viewModel.getEpisodeToPlay(item.id)
                                    isLoadingEpisode = false
                                }

                                when {
                                    isLoadingEpisode -> {
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
                                    episodeToPlay != null -> {
                                        val episode = episodeToPlay!!
                                        val (buttonText, buttonIcon) = when {
                                            episode.playbackPositionTicks > 0 && episode.playbackPositionTicks >= episode.runtimeTicks -> {
                                                "Rewatch" to Icons.Default.Replay
                                            }
                                            episode.playbackPositionTicks > 0 && episode.runtimeTicks > 0 -> {
                                                "Resume Playback" to Icons.Default.PlayArrow
                                            }
                                            else -> {
                                                "Play" to Icons.Default.PlayArrow
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
                                                val finalSelection = selection.copy(
                                                    mediaSourceId = selectedMediaSource?.id ?: episode.sources.firstOrNull()?.id ?: "",
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
                                    else -> {
                                        Button(
                                            onClick = { },
                                            enabled = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = RoundedCornerShape(28.dp)
                                        ) {
                                            Text("No Episodes Available")
                                        }
                                    }
                                }
                            }
                            else -> {
                                val (buttonText, buttonIcon) = when {
                                    item.playbackPositionTicks > 0 && item.playbackPositionTicks >= item.runtimeTicks -> {
                                        "Rewatch" to Icons.Default.Replay
                                    }
                                    item.playbackPositionTicks > 0 && item.runtimeTicks > 0 -> {
                                        "Resume Playback" to Icons.Default.PlayArrow
                                    }
                                    else -> {
                                        "Watch Now" to Icons.Default.PlayArrow
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
                                onClick = { viewModel.toggleWatchlist() }
                            ) {
                                Icon(
                                    imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
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
                                onClick = {
                                    viewModel.onPlayTrailerClick(context, item)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MovieCreation,
                                    contentDescription = "Play Trailer",
                                    tint = MaterialTheme.colorScheme.onBackground,
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
                                    imageVector = if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
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
                                    imageVector = if (item.played) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
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
                            IconButton(
                                onClick = { /* TODO: Download */ }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
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
                    is AfinityMovie -> MovieDetailContent(
                        item = item,
                        baseUrl = baseUrl,
                        specialFeatures = specialFeatures,
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

        if (seasons.isNotEmpty()) {
            SeasonsSection(
                seasons = seasons,
                onSeasonClick = onSeasonClick
            )
        }
    }
}