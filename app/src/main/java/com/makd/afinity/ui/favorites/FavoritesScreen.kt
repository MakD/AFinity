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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.EpisodeOverlayHandler
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaRowSection
import com.makd.afinity.ui.components.SectionRowHeader
import com.makd.afinity.ui.home.components.MusicAlbumRowSection
import com.makd.afinity.ui.home.components.MusicPlaylistRowSection
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.music.library.MusicArtistsRow
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onViewAllClick: (FavoritesCategory) -> Unit = {},
    navController: NavController,
    viewModel: FavoritesViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
    onMenuClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeWatchlistStatus by
        viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by
        viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                onMenuClick = onMenuClick,
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
                userName = mainUiState.userName,
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
                            uiState.channels.isNotEmpty() ||
                            uiState.favoriteAlbums.isNotEmpty() ||
                            uiState.favoriteArtists.isNotEmpty() ||
                            uiState.favoriteTracks.isNotEmpty() ||
                            uiState.favoritePlaylists.isNotEmpty()

                    if (!hasAnyFavorites) {
                        FullScreenEmpty(
                            title = stringResource(R.string.favorites_empty_title),
                            message = stringResource(R.string.favorites_empty_message),
                            actionText = "Browse Media",
                            onActionClick = {
                                navController.navigate(Destination.HOME.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    top = 16.dp,
                                    end = 16.dp,
                                    bottom = 16.dp + playerOffset,
                                ),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            if (uiState.boxSets.isNotEmpty()) {
                                item {
                                    MediaRowSection(
                                        title = stringResource(R.string.section_boxset),
                                        items = uiState.boxSets,
                                        onItemClick = onItemClick,
                                        cardWidth = portraitWidth,
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.BOXSETS)
                                        },
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
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.MOVIES)
                                        },
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
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.SHOWS)
                                        },
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
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.SEASONS)
                                        },
                                    )
                                }
                            }
                            if (uiState.episodes.isNotEmpty()) {
                                item {
                                    MediaRowSection(
                                        title = stringResource(R.string.section_episodes),
                                        items = uiState.episodes,
                                        onItemClick = { episode ->
                                            viewModel.selectEpisode(episode as AfinityEpisode)
                                        },
                                        cardWidth = landscapeWidth,
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.EPISODES)
                                        },
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
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.CHANNELS)
                                        },
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
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.PEOPLE)
                                        },
                                    )
                                }
                            }
                            if (uiState.favoriteAlbums.isNotEmpty()) {
                                item {
                                    MusicAlbumRowSection(
                                        title = "Favorite Albums",
                                        albums = uiState.favoriteAlbums,
                                        horizontalPadding = 0.dp,
                                        onAlbumClick = { album ->
                                            navController.navigate(
                                                Destination.createMusicAlbumRoute(
                                                    album.id.toString()
                                                )
                                            )
                                        },
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.ALBUMS)
                                        },
                                    )
                                }
                            }
                            if (uiState.favoriteArtists.isNotEmpty()) {
                                item {
                                    MusicArtistsRow(
                                        title = "Favorite Artists",
                                        artists = uiState.favoriteArtists,
                                        horizontalPadding = 0.dp,
                                        onArtistClick = { artist ->
                                            navController.navigate(
                                                Destination.createMusicArtistRoute(
                                                    artist.id.toString()
                                                )
                                            )
                                        },
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.ARTISTS)
                                        },
                                    )
                                }
                            }
                            if (uiState.favoriteTracks.isNotEmpty()) {
                                item {
                                    FavoriteTracksSection(
                                        tracks = uiState.favoriteTracks,
                                        currentTrackId = playbackState.currentTrack?.id,
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.TRACKS)
                                        },
                                        onTrackClick = { track ->
                                            startMusicService(context)
                                            playerViewModel.playQueue(
                                                uiState.favoriteTracks,
                                                uiState.favoriteTracks.indexOf(track),
                                            )
                                        },
                                        onInstantMix = { track ->
                                            startMusicService(context)
                                            playerViewModel.playInstantMix(track.id)
                                        },
                                        onAddNext = { track ->
                                            playerViewModel.addNext(listOf(track))
                                        },
                                        onAddLast = { track ->
                                            playerViewModel.addLast(listOf(track))
                                        },
                                        onFavorite = { track ->
                                            viewModel.toggleTrackFavorite(track)
                                        },
                                    )
                                }
                            }
                            if (uiState.favoritePlaylists.isNotEmpty()) {
                                item {
                                    MusicPlaylistRowSection(
                                        title =
                                            stringResource(
                                                R.string.section_favorite_playlists
                                            ),
                                        playlists = uiState.favoritePlaylists,
                                        horizontalPadding = 0.dp,
                                        onPlaylistClick = { playlist ->
                                            navController.navigate(
                                                Destination.createMusicPlaylistRoute(
                                                    playlist.id.toString()
                                                )
                                            )
                                        },
                                        onViewAllClick = {
                                            onViewAllClick(FavoritesCategory.PLAYLISTS)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    EpisodeOverlayHandler(
        selectedEpisode = selectedEpisode,
        watchlistStatus = selectedEpisodeWatchlistStatus,
        downloadInfo = selectedEpisodeDownloadInfo,
        canDownload = canDownload,
        onClearSelection = { viewModel.clearSelectedEpisode() },
        onToggleFavorite = { episode -> viewModel.toggleEpisodeFavorite(episode) },
        onToggleWatchlist = { episode -> viewModel.toggleEpisodeWatchlist(episode) },
        onToggleWatched = { episode -> viewModel.toggleEpisodeWatched(episode) },
        onNavigateToSeries = { seriesId ->
            navController.navigate(
                Destination.createItemDetailRoute(itemId = seriesId, itemType = "Series")
            )
        },
    )
}

@Composable
private fun FavoritePeopleSection(
    title: String,
    people: List<AfinityPersonDetail>,
    onPersonClick: (String) -> Unit,
    cardWidth: Dp,
    onViewAllClick: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionRowHeader(title = title, onViewAllClick = onViewAllClick)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(people, key = { it.id.toString() }) { person ->
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
internal fun FavoritePersonCard(person: AfinityPersonDetail, onClick: () -> Unit, cardWidth: Dp) {
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
    onViewAllClick: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionRowHeader(title = title, onViewAllClick = onViewAllClick)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(channels, key = { it.id.toString() }) { channel ->
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
internal fun FavoriteChannelCard(channel: AfinityChannel, onClick: () -> Unit, cardWidth: Dp) {
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
                        targetWidth = cardWidth,
                        targetHeight = cardWidth * 9f / 16f,
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

@Composable
private fun FavoriteTracksSection(
    tracks: List<com.makd.afinity.data.models.music.AfinityTrack>,
    currentTrackId: java.util.UUID?,
    onTrackClick: (com.makd.afinity.data.models.music.AfinityTrack) -> Unit,
    onInstantMix: (com.makd.afinity.data.models.music.AfinityTrack) -> Unit,
    onAddNext: (com.makd.afinity.data.models.music.AfinityTrack) -> Unit,
    onAddLast: (com.makd.afinity.data.models.music.AfinityTrack) -> Unit,
    onFavorite: (com.makd.afinity.data.models.music.AfinityTrack) -> Unit,
    onViewAllClick: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionRowHeader(title = "Favorite Songs", onViewAllClick = onViewAllClick)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                FavoriteTrackCard(
                    track = track,
                    isPlaying = track.id == currentTrackId,
                    onClick = { onTrackClick(track) },
                    onInstantMix = { onInstantMix(track) },
                    onAddNext = { onAddNext(track) },
                    onAddLast = { onAddLast(track) },
                    onFavorite = { onFavorite(track) },
                )
            }
        }
    }
}

@Composable
internal fun FavoriteTrackCard(
    track: com.makd.afinity.data.models.music.AfinityTrack,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onInstantMix: () -> Unit,
    onAddNext: () -> Unit,
    onAddLast: () -> Unit,
    onFavorite: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val cardWidth = 140.dp

    Column(
        modifier =
            Modifier.width(cardWidth)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            AsyncImage(
                imageUrl = track.images.primary?.toString(),
                contentDescription = track.name,
                targetWidth = cardWidth,
                targetHeight = cardWidth,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color =
                    if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = track.artist ?: track.artists.firstOrNull() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dots_vertical),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (track.favorite) "Remove from Favorites"
                                    else "Add to Favorites"
                                )
                            },
                            onClick = {
                                showMenu = false
                                onFavorite()
                            },
                            leadingIcon = {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (track.favorite) R.drawable.ic_favorite_filled
                                            else R.drawable.ic_favorite
                                        ),
                                    contentDescription = null,
                                    tint =
                                        if (track.favorite) Color.Red
                                        else androidx.compose.material3.LocalContentColor.current,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Play Instant Mix") },
                            onClick = {
                                showMenu = false
                                onInstantMix()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrows_shuffle),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            onClick = {
                                showMenu = false
                                onAddNext()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_skip_forward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            onClick = {
                                showMenu = false
                                onAddLast()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_playlist_alt),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
