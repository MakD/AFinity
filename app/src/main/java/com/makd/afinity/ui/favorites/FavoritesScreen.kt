@file:UnstableApi

package com.makd.afinity.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaRowSection
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    navController: NavController,
    viewModel: FavoritesViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeWatchlistStatus by
        viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by
        viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()
    var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadFavorites() }

    val portraitWidth = widthSizeClass.portraitWidth
    val landscapeWidth = widthSizeClass.landscapeWidth

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.favorites_title),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> FullScreenLoading()

                uiState.error != null -> FullScreenError(message = uiState.error)

                else -> {
                    val hasAnyFavorites =
                        uiState.movies.isNotEmpty() ||
                            uiState.shows.isNotEmpty() ||
                            uiState.seasons.isNotEmpty() ||
                            uiState.episodes.isNotEmpty() ||
                            uiState.boxSets.isNotEmpty() ||
                            uiState.people.isNotEmpty() ||
                            uiState.channels.isNotEmpty()

                    if (!hasAnyFavorites) {
                        FullScreenEmpty(
                            title = stringResource(R.string.favorites_empty_title),
                            message = stringResource(R.string.favorites_empty_message),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            if (uiState.boxSets.isNotEmpty()) {
                                item {
                                    MediaRowSection(
                                        title = stringResource(R.string.section_boxset),
                                        items = uiState.boxSets,
                                        onItemClick = onItemClick,
                                        cardWidth = portraitWidth,
                                    )
                                }
                            }
                            if (uiState.movies.isNotEmpty()) {
                                item {
                                    MediaRowSection(
                                        title = stringResource(R.string.section_movies),
                                        items = uiState.movies,
                                        onItemClick = onItemClick,
                                        cardWidth = portraitWidth,
                                    )
                                }
                            }
                            if (uiState.shows.isNotEmpty()) {
                                item {
                                    MediaRowSection(
                                        title = stringResource(R.string.section_tv_shows),
                                        items = uiState.shows,
                                        onItemClick = onItemClick,
                                        cardWidth = portraitWidth,
                                    )
                                }
                            }
                            if (uiState.seasons.isNotEmpty()) {
                                item {
                                    MediaRowSection(
                                        title = stringResource(R.string.section_seasons),
                                        items = uiState.seasons,
                                        onItemClick = onItemClick,
                                        cardWidth = portraitWidth,
                                    )
                                }
                            }
                            if (uiState.episodes.isNotEmpty()) {
                                item {
                                    MediaRowSection(
                                        title = stringResource(R.string.section_episodes),
                                        items = uiState.episodes,
                                        onItemClick = { episode ->
                                            viewModel.selectEpisode(
                                                episode
                                                    as com.makd.afinity.data.models.media.AfinityEpisode
                                            )
                                        },
                                        cardWidth = landscapeWidth,
                                    )
                                }
                            }

                            if (uiState.channels.isNotEmpty()) {
                                item {
                                    FavoriteChannelsSection(
                                        title = stringResource(R.string.section_tv_channels),
                                        channels = uiState.channels,
                                        onChannelClick = { channel ->
                                            PlayerLauncher.launchLiveChannel(
                                                context,
                                                channel.id,
                                                channel.name,
                                            )
                                        },
                                        cardWidth = landscapeWidth,
                                    )
                                }
                            }
                            if (uiState.people.isNotEmpty()) {
                                item {
                                    FavoritePeopleSection(
                                        title = stringResource(R.string.section_people),
                                        people = uiState.people,
                                        onPersonClick = onPersonClick,
                                        cardWidth = portraitWidth,
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                com.makd.afinity.ui.player.PlayerLauncher.launch(
                    context = context,
                    itemId = episodeToPlay.id,
                    mediaSourceId = selection.mediaSourceId,
                    audioStreamIndex = selection.audioStreamIndex,
                    subtitleStreamIndex = selection.subtitleStreamIndex,
                    startPositionMs = selection.startPositionMs,
                )
            },
            onToggleFavorite = { viewModel.toggleEpisodeFavorite(episode) },
            onToggleWatchlist = { viewModel.toggleEpisodeWatchlist(episode) },
            onToggleWatched = { viewModel.toggleEpisodeWatched(episode) },
            onDownloadClick = { viewModel.onDownloadClick() },
            onPauseDownload = { viewModel.pauseDownload() },
            onResumeDownload = { viewModel.resumeDownload() },
            onCancelDownload = { viewModel.cancelDownload() },
            onGoToSeries = {
                viewModel.clearSelectedEpisode()
                pendingNavigationSeriesId = episode.seriesId?.toString()
            },
        )
    }
    LaunchedEffect(selectedEpisode, pendingNavigationSeriesId) {
        if (selectedEpisode == null && pendingNavigationSeriesId != null) {
            delay(300)
            val route =
                Destination.createItemDetailRoute(
                    itemId = pendingNavigationSeriesId!!,
                    itemType = "Series",
                )
            navController.navigate(route)
            pendingNavigationSeriesId = null
        }
    }
}

@Composable
private fun FavoritePeopleSection(
    title: String,
    people: List<AfinityPersonDetail>,
    onPersonClick: (String) -> Unit,
    cardWidth: Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(people) { person ->
                FavoritePersonCard(
                    person = person,
                    onClick = { onPersonClick(person.id.toString()) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
private fun FavoritePersonCard(person: AfinityPersonDetail, onClick: () -> Unit, cardWidth: Dp) {
    Column(
        modifier =
            Modifier.width(cardWidth)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AsyncImage(
            imageUrl = person.images.primaryImageUrl,
            blurHash = person.images.primaryBlurHash,
            contentDescription = person.name,
            targetWidth = cardWidth,
            targetHeight = cardWidth,
            modifier = Modifier.size(cardWidth).clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_person_heart),
            error = painterResource(id = R.drawable.ic_person_heart),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FavoriteChannelsSection(
    title: String,
    channels: List<AfinityChannel>,
    onChannelClick: (AfinityChannel) -> Unit,
    cardWidth: Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(channels) { channel ->
                FavoriteChannelCard(
                    channel = channel,
                    onClick = { onChannelClick(channel) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
private fun FavoriteChannelCard(channel: AfinityChannel, onClick: () -> Unit, cardWidth: Dp) {
    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val imageUrl = channel.images.primary ?: channel.images.thumb
                if (imageUrl != null) {
                    AsyncImage(
                        imageUrl = imageUrl.toString(),
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = channel.channelNumber ?: channel.name.take(3),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            channel.channelNumber?.let { number ->
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
