package com.makd.afinity.ui.music.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailActionRow
import com.makd.afinity.ui.components.DetailCoverImage
import com.makd.afinity.ui.components.DetailCoverTitle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.backdropChainOf
import com.makd.afinity.ui.components.detailListBottomPadding
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.item.components.MusicDownloadQualityDialog
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.OverviewSection
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.LastFmAttribution
import com.makd.afinity.ui.music.components.MusicTrackRow
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun MusicAlbumScreen(
    onMusicAlbumClick: (String) -> Unit,
    onMusicArtistClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    viewModel: MusicAlbumViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val isOffline by playerViewModel.isOffline.collectAsStateWithLifecycle()
    val isDownloadAllowedByServer by
        viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val canDownloadOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addToPlaylistTrackIds by remember { mutableStateOf<List<UUID>>(emptyList()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var radioSeed by remember { mutableStateOf<RadioSeed?>(null) }
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)
    val isLandscape = isLandscapeWindow()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FullScreenLoading()
        }
        return
    }

    val coverUrl =
        uiState.album?.images?.primary?.toString()
            ?: uiState.tracks.firstOrNull()?.images?.primary?.toString()

    val actionButtonsContent: @Composable () -> Unit = {
        DetailActionRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            playbackEnabled = uiState.tracks.isNotEmpty(),
            onShuffle = {
                startMusicService(context)
                val shuffled = uiState.tracks.shuffled()
                playerViewModel.playQueue(shuffled, 0)
            },
            onPlay = {
                startMusicService(context)
                playerViewModel.playQueue(uiState.tracks, 0)
            },
        ) {
            IconButton(onClick = { viewModel.toggleFavorite() }) {
                Icon(
                    painter =
                        if (uiState.album?.favorite == true)
                            painterResource(R.drawable.ic_favorite_filled)
                        else painterResource(R.drawable.ic_favorite),
                    contentDescription =
                        if (uiState.album?.favorite == true)
                            stringResource(R.string.cd_favorite_remove)
                        else stringResource(R.string.cd_favorite_add),
                    tint =
                        if (uiState.album?.favorite == true) Color.Red
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            uiState.album?.let { album ->
                IconButton(
                    enabled = !isOffline,
                    onClick = {
                        startMusicService(context)
                        playerViewModel.playInstantMix(album.id)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_compass),
                        contentDescription = stringResource(R.string.cd_music_instant_mix),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(
                    enabled = !isOffline,
                    onClick = {
                        val firstTrack = uiState.tracks.firstOrNull()
                        if (firstTrack != null) {
                            radioSeed =
                                RadioSeed(
                                    trackId = firstTrack.id,
                                    albumId = album.id,
                                    sourceTracks = uiState.tracks,
                                )
                        }
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
            if (isDownloadAllowedByServer) {
                var showDownloadQualityDialog by remember { mutableStateOf(false) }
                val defaultDownloadQuality by
                    viewModel.defaultMusicDownloadQuality.collectAsStateWithLifecycle()
                DownloadProgressIndicator(
                    downloadInfo = uiState.albumDownloadInfo,
                    onDownloadClick = { viewModel.downloadAlbum() },
                    onPauseClick = {},
                    onResumeClick = { viewModel.downloadAlbum() },
                    onCancelClick = { viewModel.cancelAlbumDownload() },
                    canDownload = canDownloadOnNetwork,
                    onDownloadLongClick = { showDownloadQualityDialog = true },
                    iconSize = 26.dp,
                )
                if (showDownloadQualityDialog) {
                    MusicDownloadQualityDialog(
                        initialBitrate = defaultDownloadQuality,
                        onConfirm = { quality -> viewModel.downloadAlbum(quality) },
                        onDismiss = { showDownloadQualityDialog = false },
                    )
                }
            }
        }
    }

    val albumInfoContent: @Composable () -> Unit = {
        val album = uiState.album
        val overview = album?.overview.orEmpty()
        val externalUrls = album?.externalUrls
        if (overview.isNotEmpty() || !externalUrls.isNullOrEmpty()) {
            Column(modifier = Modifier.padding(horizontal = DetailGutter, vertical = 4.dp)) {
                OverviewSection(overview = overview)
                ExternalLinksSection(externalUrls = externalUrls)
            }
        }
    }

    val relatedSectionsContent: @Composable () -> Unit = {
        Column {
            val artist = uiState.album?.artist ?: uiState.album?.artists?.firstOrNull()
            val artistId = uiState.album?.artistId

            if (artist != null) {
                AlbumRelatedSection(
                    title = stringResource(R.string.music_section_more_from_fmt, artist),
                    albums = uiState.moreFromArtist,
                    showArtist = false,
                    onViewAllClick = artistId?.let { { onMusicArtistClick(it.toString()) } },
                    onAlbumClick = { album -> onMusicAlbumClick(album.id.toString()) },
                )
            }

            AlbumRelatedSection(
                title = stringResource(R.string.music_section_more_like_this),
                albums = uiState.similarAlbums,
                onAlbumClick = { album -> onMusicAlbumClick(album.id.toString()) },
            )
        }
    }

    val albumHeader: @Composable ColumnScope.() -> Unit = {
        DetailCoverImage(
            imageUrl = coverUrl,
            contentDescription = uiState.album?.name,
            blurHash = uiState.album?.images?.primaryImageBlurHash,
        )

        DetailCoverTitle(
            title =
                uiState.album?.name
                    ?: uiState.tracks.firstOrNull()?.album
                    ?: stringResource(R.string.music_fallback_album)
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
                                    interactionSource = remember { MutableInteractionSource() },
                                ) {
                                    onMusicArtistClick(artistId.toString())
                                }
                            else Modifier
                        ),
            ) {
                ArtistPhotoCard(imageUrl = uiState.artistImageUrl, fallbackUrl = coverUrl)
                Column {
                    Text(
                        text = artist,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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

        if (!isLandscape) actionButtonsContent()
    }

    val albumItems: LazyListScope.() -> Unit = {
        if (isLandscape) {
            item(key = "actions") {
                Box(modifier = Modifier.padding(horizontal = DetailGutter)) {
                    actionButtonsContent()
                }
            }
        }

        item { albumInfoContent() }

        val discGroups = uiState.tracks.groupBy { it.discNumber ?: 1 }.toSortedMap()
        val isMultiDisc = discGroups.size > 1

        discGroups.forEach { (disc, tracks) ->
            if (isMultiDisc) {
                item(key = "disc_$disc") {
                    Text(
                        text = "Disc $disc",
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier =
                            Modifier.padding(start = DetailGutter, top = 24.dp, bottom = 8.dp),
                    )
                }
            }

            itemsIndexed(tracks, key = { _, track -> track.id }) { _, track ->
                MusicTrackRow(
                    track = track,
                    isPlaying = track.id == playbackState.currentTrack?.id,
                    trackNumber = track.indexNumber,
                    isHot = track.id in uiState.hotTrackIds,
                    showAlbumArt = false,
                    onClick = {
                        val index = uiState.tracks.indexOf(track)
                        startMusicService(context)
                        playerViewModel.playQueue(uiState.tracks, index)
                    },
                    onInstantMix =
                        if (isOffline) null
                        else
                            ({
                                startMusicService(context)
                                playerViewModel.playInstantMix(track.id)
                            }),
                    onStartRadio =
                        if (isOffline) null
                        else
                            ({
                                radioSeed =
                                    RadioSeed(
                                        trackId = track.id,
                                        albumId = track.albumId,
                                        sourceTracks = uiState.tracks,
                                    )
                            }),
                    onAddNext = { playerViewModel.addNext(listOf(track)) },
                    onAddLast = { playerViewModel.addLast(listOf(track)) },
                    onFavorite = { viewModel.toggleTrackFavorite(track.id) },
                    onAddToPlaylist =
                        if (isOffline) null
                        else
                            ({
                                addToPlaylistTrackIds = listOf(track.id)
                                addToPlaylistViewModel.reset()
                                showAddToPlaylist = true
                            }),
                    onDownload =
                        if (isDownloadAllowedByServer) ({ viewModel.downloadTrack(track.id) })
                        else null,
                    isDownloadEnabled = canDownloadOnNetwork,
                    onCancelDownload = { viewModel.cancelTrackDownload(track.id) },
                    downloadInfo = uiState.trackDownloadInfos[track.id],
                )
            }
        }

        if (uiState.hotTrackIds.isNotEmpty()) {
            item(key = "lastfm_attribution") {
                LastFmAttribution(artistName = uiState.album?.artist)
            }
        }

        item { relatedSectionsContent() }
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
                backdrop = backdropChainOf(coverUrl),
                listState = lazyListState,
                arrangement = LandscapeDetailArrangement.Split,
                pane = albumHeader,
                items = albumItems,
            )
        } else {
            PortraitDetailLayout(
                hero = PortraitHero.Cover(backdropChainOf(coverUrl)),
                listState = lazyListState,
                header = albumHeader,
                items = albumItems,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            snackbar = { AFinitySnackbar(it) },
            modifier =
                Modifier.align(Alignment.BottomCenter).padding(bottom = detailListBottomPadding()),
        )
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

    if (showAddToPlaylist) {
        val addedToPlaylistFmt = stringResource(R.string.music_snackbar_added_to_playlist_fmt)
        val createdPlaylistFmt = stringResource(R.string.music_snackbar_created_playlist_fmt)
        AddToPlaylistDialog(
            trackIds = addToPlaylistTrackIds,
            viewModel = addToPlaylistViewModel,
            onDismiss = { showAddToPlaylist = false },
            onResult = { result ->
                showAddToPlaylist = false
                val message =
                    when (result) {
                        is AddToPlaylistResult.Added ->
                            addedToPlaylistFmt.format(result.playlistName)
                        is AddToPlaylistResult.Created ->
                            createdPlaylistFmt.format(result.playlistName)
                        is AddToPlaylistResult.Error -> result.message
                    }
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }
}

@Composable
private fun ArtistPhotoCard(
    modifier: Modifier = Modifier,
    imageUrl: String?,
    fallbackUrl: String? = null,
) {
    var currentUrl by remember(imageUrl) { mutableStateOf(imageUrl) }

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
                imageUrl = currentUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 34.dp,
                targetHeight = 44.dp,
                onError = { if (currentUrl != fallbackUrl) currentUrl = fallbackUrl },
            )
        }
    }
}
