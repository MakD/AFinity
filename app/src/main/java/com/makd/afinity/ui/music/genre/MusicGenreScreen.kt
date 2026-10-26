package com.makd.afinity.ui.music.genre

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailCoverSize
import com.makd.afinity.ui.components.DetailCoverTitle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.backdropChainOf
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.music.components.MusicAlbumCard
import com.makd.afinity.ui.music.components.MusicArtistCard
import com.makd.afinity.ui.music.components.MusicDetailActionRow
import com.makd.afinity.ui.music.components.MusicTrackRow
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import java.util.UUID

@Composable
fun MusicGenreScreen(
    onMusicAlbumClick: (String) -> Unit,
    onMusicArtistClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    viewModel: MusicGenreViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val isOffline by playerViewModel.isOffline.collectAsStateWithLifecycle()
    val isDownloadAllowedByServer by
        viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val canDownloadOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isLandscape = isLandscapeWindow()
    val relatedCardWidth = CardDimensions.musicDetailCardWidth(splitPane = isLandscape)
    val listState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(listState)
    var showAllTracks by remember { mutableStateOf(false) }
    var radioSeed by remember { mutableStateOf<RadioSeed?>(null) }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FullScreenLoading()
        }
        return
    }

    val actionRow: @Composable () -> Unit = {
        MusicDetailActionRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            playbackEnabled = uiState.tracks.isNotEmpty(),
            onShuffle = {
                startMusicService(context)
                playerViewModel.playQueue(uiState.tracks.shuffled(), 0)
            },
            onPlay = {
                startMusicService(context)
                playerViewModel.playQueue(uiState.tracks, 0)
            },
        ) {
            val genreId = viewModel.genreId
            if (genreId != null) {
                IconButton(
                    enabled = !isOffline,
                    onClick = {
                        startMusicService(context)
                        playerViewModel.playInstantMix(genreId)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_compass),
                        contentDescription = stringResource(R.string.cd_music_instant_mix),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
                val firstTrack = uiState.tracks.firstOrNull()
                if (firstTrack != null) {
                    IconButton(
                        enabled = !isOffline,
                        onClick = {
                            radioSeed =
                                RadioSeed(
                                    trackId = firstTrack.id,
                                    albumId = firstTrack.albumId,
                                    sourceTracks = uiState.tracks,
                                )
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_radio),
                            contentDescription = stringResource(R.string.cd_music_start_radio),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }

    val genreHeader: @Composable ColumnScope.() -> Unit = {
        GenreHeroImage(
            imageUrl = viewModel.genreImageUrl,
            contentDescription = viewModel.genreName,
            size = DetailCoverSize,
        )

        DetailCoverTitle(title = viewModel.genreName)

        actionRow()
    }

    val genreItems: LazyListScope.() -> Unit = {
        if (uiState.artists.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.music_tab_artists),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = DetailGutter, top = 24.dp, bottom = 12.dp),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DetailGutter),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.artists, key = { it.id }) { artist ->
                        MusicArtistCard(
                            name = artist.name,
                            imageUrl = artist.images.primary?.toString(),
                            blurHash = artist.images.primaryImageBlurHash,
                            size = relatedCardWidth,
                            onClick = { onMusicArtistClick(artist.id.toString()) },
                        )
                    }
                }
            }
        }

        if (uiState.albums.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.music_section_albums),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = DetailGutter, top = 32.dp, bottom = 16.dp),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DetailGutter),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.albums, key = { it.id }) { album ->
                        Box(modifier = Modifier.width(relatedCardWidth)) {
                            MusicAlbumCard(
                                album = album,
                                onClick = { onMusicAlbumClick(album.id.toString()) },
                            )
                        }
                    }
                }
            }
        }

        if (uiState.recentlyAdded.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.music_section_recently_added),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = DetailGutter, top = 32.dp, bottom = 16.dp),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DetailGutter),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.recentlyAdded, key = { it.id }) { album ->
                        Box(modifier = Modifier.width(relatedCardWidth)) {
                            MusicAlbumCard(
                                album = album,
                                onClick = { onMusicAlbumClick(album.id.toString()) },
                            )
                        }
                    }
                }
            }
        }

        if (uiState.tracks.isNotEmpty()) {
            item {
                GenreTracksSection(
                    uiState = uiState,
                    playbackState = playbackState,
                    isOffline = isOffline,
                    showAllTracks = showAllTracks,
                    onShowAllChange = { showAllTracks = it },
                    onPlay = { index ->
                        startMusicService(context)
                        playerViewModel.playQueue(uiState.tracks, index)
                    },
                    onInstantMix = { id ->
                        startMusicService(context)
                        playerViewModel.playInstantMix(id)
                    },
                    onRadio = { seed -> radioSeed = seed },
                    onAddNext = { track -> playerViewModel.addNext(listOf(track)) },
                    onAddLast = { track -> playerViewModel.addLast(listOf(track)) },
                    onDownload =
                        if (isDownloadAllowedByServer)
                            ({ track -> viewModel.downloadTrack(track.id) })
                        else null,
                    onCancelDownload = { track -> viewModel.cancelTrackDownload(track.id) },
                    isDownloadEnabled = canDownloadOnNetwork,
                )
            }
        }
    }

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
        if (isLandscape) {
            LandscapeDetailLayout(
                backdropStyle = BackdropStyle.BlurredCover,
                backdrop = backdropChainOf(viewModel.genreImageUrl),
                listState = listState,
                arrangement = LandscapeDetailArrangement.Split,
                pane = genreHeader,
                items = genreItems,
            )
        } else {
            PortraitDetailLayout(
                hero = PortraitHero.Cover(backdropChainOf(viewModel.genreImageUrl)),
                listState = listState,
                header = genreHeader,
                items = genreItems,
            )
        }
    }

    radioSeed?.let { seed ->
        RadioModeBottomSheet(
            seed = seed,
            onDismiss = { radioSeed = null },
            onSelectMode = { s, mode ->
                startMusicService(context)
                playerViewModel.startRadio(s, mode)
                radioSeed = null
            },
        )
    }
}

@Composable
private fun GenreTracksSection(
    uiState: MusicGenreUiState,
    playbackState: MusicPlaybackState,
    isOffline: Boolean,
    showAllTracks: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    onPlay: (Int) -> Unit,
    onInstantMix: (UUID) -> Unit,
    onRadio: (RadioSeed) -> Unit,
    onAddNext: (AfinityTrack) -> Unit,
    onAddLast: (AfinityTrack) -> Unit,
    onDownload: ((AfinityTrack) -> Unit)? = null,
    onCancelDownload: ((AfinityTrack) -> Unit)? = null,
    isDownloadEnabled: Boolean = true,
) {
    val fixedTracks = uiState.tracks.take(5)
    val extraTracks = uiState.tracks.drop(5)
    val hasMore = extraTracks.isNotEmpty()

    val fadeOutModifier =
        if (hasMore && !showAllTracks) {
            Modifier.graphicsLayer { alpha = 0.99f }
                .drawWithCache {
                    val gradient =
                        Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height - 80.dp.toPx(),
                            endY = size.height,
                        )
                    onDrawWithContent {
                        drawContent()
                        drawRect(gradient, blendMode = BlendMode.DstIn)
                    }
                }
        } else Modifier

    Text(
        text = stringResource(R.string.music_section_popular),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = DetailGutter, top = 24.dp, bottom = 12.dp),
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = fadeOutModifier) {
            fixedTracks.forEachIndexed { index, track ->
                MusicTrackRow(
                    track = track,
                    isPlaying = track.id == playbackState.currentTrack?.id,
                    trackNumber = index + 1,
                    showAlbumArt = true,
                    onClick = { onPlay(index) },
                    onInstantMix = if (isOffline) null else ({ onInstantMix(track.id) }),
                    onStartRadio =
                        if (isOffline) null
                        else
                            ({
                                onRadio(
                                    RadioSeed(
                                        trackId = track.id,
                                        albumId = track.albumId,
                                        sourceTracks = uiState.tracks,
                                    )
                                )
                            }),
                    onAddNext = { onAddNext(track) },
                    onAddLast = { onAddLast(track) },
                    onDownload = onDownload?.let { dl -> { dl(track) } },
                    onCancelDownload = onCancelDownload?.let { c -> { c(track) } },
                    isDownloadEnabled = isDownloadEnabled,
                    downloadInfo = uiState.trackDownloadInfos[track.id],
                )
            }
            AnimatedVisibility(
                visible = showAllTracks,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    extraTracks.forEachIndexed { i, track ->
                        MusicTrackRow(
                            track = track,
                            isPlaying = track.id == playbackState.currentTrack?.id,
                            trackNumber = fixedTracks.size + i + 1,
                            showAlbumArt = true,
                            onClick = { onPlay(fixedTracks.size + i) },
                            onInstantMix = if (isOffline) null else ({ onInstantMix(track.id) }),
                            onStartRadio =
                                if (isOffline) null
                                else
                                    ({
                                        onRadio(
                                            RadioSeed(
                                                trackId = track.id,
                                                albumId = track.albumId,
                                                sourceTracks = uiState.tracks,
                                            )
                                        )
                                    }),
                            onAddNext = { onAddNext(track) },
                            onAddLast = { onAddLast(track) },
                            onDownload = onDownload?.let { dl -> { dl(track) } },
                            onCancelDownload = onCancelDownload?.let { c -> { c(track) } },
                            isDownloadEnabled = isDownloadEnabled,
                            downloadInfo = uiState.trackDownloadInfos[track.id],
                        )
                    }
                }
            }
        }
    }

    if (hasMore) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedButton(
                onClick = { onShowAllChange(!showAllTracks) },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
            ) {
                Crossfade(targetState = showAllTracks, label = "see_more_label") { expanded ->
                    Text(
                        text =
                            stringResource(
                                if (expanded) R.string.action_see_less else R.string.action_see_more
                            ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreHeroImage(
    imageUrl: String?,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (imageUrl != null) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            blurHash = null,
            targetWidth = size,
            targetHeight = size,
            modifier =
                modifier
                    .size(size)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = Color.Black,
                    )
                    .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier =
                modifier
                    .size(size)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(12.dp),
                        spotColor = Color.Black,
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}
