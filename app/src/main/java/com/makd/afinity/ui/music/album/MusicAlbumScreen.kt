package com.makd.afinity.ui.music.album

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.audiobookshelf.player.util.rememberDominantColor
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.MusicTrackRow
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MusicAlbumScreen(
    navController: NavController,
    viewModel: MusicAlbumViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playerOffset = LocalPlayerOffset.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addToPlaylistTrackIds by remember { mutableStateOf<List<UUID>>(emptyList()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FullScreenLoading()
        }
        return
    }

    val coverUrl =
        uiState.album?.images?.primary?.toString()
            ?: uiState.tracks.firstOrNull()?.images?.primary?.toString()

    val defaultColor = MaterialTheme.colorScheme.surfaceVariant
    val dominantColor = rememberDominantColor(url = coverUrl, defaultColor = defaultColor)
    val animatedDominantColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(600),
            label = "albumGradient",
        )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(450.dp)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    animatedDominantColor.copy(alpha = 0.65f),
                                    MaterialTheme.colorScheme.background,
                                )
                        )
                    )
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = playerOffset + 16.dp),
        ) {
            item { Spacer(modifier = Modifier.statusBarsPadding().height(24.dp)) }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        imageUrl = coverUrl,
                        contentDescription = uiState.album?.name,
                        blurHash = uiState.album?.images?.primaryImageBlurHash,
                        targetWidth = 400.dp,
                        targetHeight = 400.dp,
                        modifier =
                            Modifier.size(260.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    spotColor = Color.Black,
                                )
                                .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            item {
                Column(
                    modifier =
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)
                ) {
                    Text(
                        text =
                            uiState.album?.name ?: uiState.tracks.firstOrNull()?.album ?: "Album",
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val artist = uiState.album?.artist ?: uiState.tracks.firstOrNull()?.artist
                    val artistId = uiState.album?.artistId
                    if (artist != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier =
                                Modifier.padding(top = 10.dp)
                                    .then(
                                        if (artistId != null)
                                            Modifier.clickable(
                                                indication = null,
                                                interactionSource =
                                                    remember { MutableInteractionSource() },
                                            ) {
                                                navController.navigate(
                                                    com.makd.afinity.navigation.Destination
                                                        .createMusicArtistRoute(artistId.toString())
                                                )
                                            }
                                        else Modifier
                                    ),
                        ) {
                            ArtistPhotoCard(imageUrl = uiState.artistImageUrl)
                            Column {
                                Text(
                                    text = artist,
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                val year = uiState.album?.productionYear
                                if (year != null) {
                                    Text(
                                        text = year.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                painter =
                                    if (uiState.album?.favorite == true)
                                        painterResource(R.drawable.ic_favorite_filled)
                                    else painterResource(R.drawable.ic_favorite),
                                contentDescription =
                                    if (uiState.album?.favorite == true) "Remove from favorites"
                                    else "Add to favorites",
                                tint =
                                    if (uiState.album?.favorite == true) Color.Red
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        uiState.album?.let { album ->
                            IconButton(
                                onClick = {
                                    startMusicService(context)
                                    playerViewModel.playInstantMix(album.id)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_compass),
                                    contentDescription = "Instant Mix",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        DownloadProgressIndicator(
                            downloadInfo = uiState.albumDownloadInfo,
                            onDownloadClick = { viewModel.downloadAlbum() },
                            onPauseClick = {},
                            onResumeClick = { viewModel.downloadAlbum() },
                            onCancelClick = { viewModel.cancelAlbumDownload() },
                        )
                        IconButton(onClick = { /* TODO: Context menu */ }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_dots_vertical),
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                startMusicService(context)
                                val shuffled = uiState.tracks.shuffled()
                                playerViewModel.playQueue(shuffled, 0)
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrows_shuffle),
                                contentDescription = "Shuffle",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                startMusicService(context)
                                playerViewModel.playQueue(uiState.tracks, 0)
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_play_filled),
                                contentDescription = "Play",
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }

            val discGroups = uiState.tracks.groupBy { it.discNumber ?: 1 }.toSortedMap()
            val isMultiDisc = discGroups.size > 1

            discGroups.forEach { (disc, tracks) ->
                if (isMultiDisc) {
                    item(key = "disc_$disc") {
                        Text(
                            text = "Disc $disc",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
                        )
                    }
                }

                itemsIndexed(tracks, key = { _, track -> track.id }) { _, track ->
                    MusicTrackRow(
                        track = track,
                        isPlaying = track.id == playbackState.currentTrack?.id,
                        trackNumber = track.indexNumber,
                        showAlbumArt = false,
                        onClick = {
                            val index = uiState.tracks.indexOf(track)
                            startMusicService(context)
                            playerViewModel.playQueue(uiState.tracks, index)
                        },
                        onInstantMix = {
                            startMusicService(context)
                            playerViewModel.playInstantMix(track.id)
                        },
                        onStartRadio =
                            track.artistId?.let { artistId ->
                                {
                                    startMusicService(context)
                                    playerViewModel.playArtistRadio(artistId)
                                }
                            },
                        onAddNext = { playerViewModel.addNext(listOf(track)) },
                        onAddLast = { playerViewModel.addLast(listOf(track)) },
                        onFavorite = { viewModel.toggleTrackFavorite(track.id) },
                        onAddToPlaylist = {
                            addToPlaylistTrackIds = listOf(track.id)
                            addToPlaylistViewModel.reset()
                            showAddToPlaylist = true
                        },
                        onDownload = { viewModel.downloadTrack(track.id) },
                    )
                }
            }
        }

        AfinityTopAppBar(
            title = {
                IconButton(
                    onClick = {
                        navController.navigate(Destination.HOME.route) {
                            popUpTo(Destination.HOME.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_home),
                            contentDescription = "Go to Home",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            backgroundOpacity = { topBarOpacity },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            trackIds = addToPlaylistTrackIds,
            viewModel = addToPlaylistViewModel,
            onDismiss = { showAddToPlaylist = false },
            onResult = { result ->
                showAddToPlaylist = false
                val message =
                    when (result) {
                        is AddToPlaylistResult.Added -> "Added to \"${result.playlistName}\""
                        is AddToPlaylistResult.Created -> "Created \"${result.playlistName}\""
                        is AddToPlaylistResult.Error -> result.message
                        else -> null
                    }
                message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
            },
        )
    }
}

@Composable
private fun ArtistPhotoCard(imageUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(width = 44.dp, height = 44.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier.size(width = 28.dp, height = 36.dp)
                    .offset(x = 10.dp, y = (-2).dp)
                    .rotate(12f)
                    .background(
                        color = Color(0xFF757575),
                        shape = RoundedCornerShape(4.dp),
                    )
        )

        Box(
            modifier =
                Modifier.size(width = 34.dp, height = 44.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(2.dp)
                    .background(
                        color = Color(0xFFC4C4C4),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                imageUrl = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 34.dp,
                targetHeight = 44.dp,
            )
        }
    }
}
