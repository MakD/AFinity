package com.makd.afinity.ui.playlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.download.PlaylistDownloadFilter
import com.makd.afinity.data.models.media.PlaylistEntry
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.audiobookshelf.player.util.rememberDominantColor
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.EmptyState
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.MusicDetailActionRow
import com.makd.afinity.ui.music.components.MusicHeroBackground
import com.makd.afinity.ui.music.components.MusicHomeTopAppBar
import com.makd.afinity.ui.music.components.MusicTrackRow
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

@UnstableApi
@Composable
fun PlaylistScreen(
    navController: NavController,
    viewModel: PlaylistViewModel = hiltViewModel(),
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
    val playerOffset = LocalPlayerOffset.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)
    val isLandscape = isLandscapeWindow()

    var addToPlaylistTrackIds by remember { mutableStateOf<List<UUID>>(emptyList()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var radioSeed by remember { mutableStateOf<RadioSeed?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var playChoiceShuffle by remember { mutableStateOf<Boolean?>(null) }
    var showDownloadChoice by remember { mutableStateOf(false) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromKey = from.key as? String
            val toKey = to.key as? String
            if (fromKey != null && toKey != null) {
                draggedKey = fromKey
                viewModel.moveEntry(fromKey, toKey)
            }
        }

    LaunchedEffect(uiState.deleted) { if (uiState.deleted) navController.popBackStack() }

    LaunchedEffect(Unit) {
        viewModel.videoPlaybackRequests.collect { request ->
            PlayerLauncher.launch(
                context = context,
                itemId = request.itemId,
                mediaSourceId = request.mediaSourceId,
                startPositionMs = request.startPositionMs,
                playlistId = request.playlistId,
                shuffle = request.shuffle,
            )
        }
    }

    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.downloadMessages.collect { message ->
            val text =
                when (message) {
                    is PlaylistDownloadMessage.PartiallyStarted ->
                        resources.getQuantityString(
                            R.plurals.playlist_download_partial_fmt,
                            message.expected,
                            message.started,
                            message.expected,
                        )
                    is PlaylistDownloadMessage.Failed ->
                        message.reason?.let {
                            resources.getString(R.string.playlist_download_failed_fmt, it)
                        } ?: resources.getString(R.string.playlist_download_failed)
                }
            snackbarHostState.showSnackbar(text)
        }
    }

    fun playMusic(shuffle: Boolean) {
        val tracks = uiState.tracks
        if (tracks.isEmpty()) return
        startMusicService(context)
        playerViewModel.playQueue(if (shuffle) tracks.shuffled() else tracks, 0)
    }

    fun playVideos(shuffle: Boolean) {
        val videos = uiState.entries.filterIsInstance<PlaylistEntry.Video>()
        if (videos.isEmpty()) return
        viewModel.playVideoEntry(videos.first().item, shuffle = shuffle)
    }

    val onPlayRequest: (Boolean) -> Unit = { shuffle ->
        when {
            uiState.isMixed -> playChoiceShuffle = shuffle
            uiState.tracks.isNotEmpty() -> playMusic(shuffle)
            else -> playVideos(shuffle)
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FullScreenLoading()
        }
        return
    }

    val coverUrl = uiState.playlist?.images?.primary?.toString()
    val defaultColor = MaterialTheme.colorScheme.surfaceVariant
    val dominantColor = rememberDominantColor(url = coverUrl, defaultColor = defaultColor)
    val animatedDominantColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(600),
            label = "playlistGradient",
        )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isLandscape) {
            MusicHeroBackground(coverUrl)

            Row(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.statusBarsPadding().height(64.dp))

                    AsyncImage(
                        imageUrl = coverUrl,
                        contentDescription = uiState.playlist?.name,
                        blurHash = uiState.playlist?.images?.primaryImageBlurHash,
                        targetWidth = 400.dp,
                        targetHeight = 400.dp,
                        modifier =
                            Modifier.size(200.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    spotColor = Color.Black,
                                )
                                .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text =
                            uiState.playlist?.name
                                ?: stringResource(R.string.music_fallback_playlist),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )

                    PlaylistCountLabel(
                        count = uiState.playlist?.songCount ?: uiState.entries.size,
                        hiddenItemCount = uiState.hiddenItemCount,
                        audioOnly = uiState.audioOnly,
                    )

                    if (uiState.artistEntries.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        PlaylistArtistsRow(
                            entries = uiState.artistEntries,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(bottom = playerOffset + 16.dp),
                ) {
                    item { Spacer(modifier = Modifier.statusBarsPadding().height(64.dp)) }

                    item {
                        MusicDetailActionRow(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            playbackEnabled = uiState.entries.isNotEmpty(),
                            onShuffle = { onPlayRequest(true) },
                            onPlay = { onPlayRequest(false) },
                        ) {
                            IconButton(onClick = { viewModel.toggleFavorite() }) {
                                Icon(
                                    painter =
                                        if (uiState.playlist?.favorite == true)
                                            painterResource(R.drawable.ic_favorite_filled)
                                        else painterResource(R.drawable.ic_favorite),
                                    contentDescription =
                                        if (uiState.playlist?.favorite == true)
                                            stringResource(R.string.cd_favorite_remove)
                                        else stringResource(R.string.cd_favorite_add),
                                    tint =
                                        if (uiState.playlist?.favorite == true) Color.Red
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription =
                                        stringResource(R.string.cd_music_delete_playlist),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            uiState.playlist?.let { playlist ->
                                IconButton(
                                    enabled = !isOffline,
                                    onClick = {
                                        startMusicService(context)
                                        playerViewModel.playInstantMix(playlist.id)
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_compass),
                                        contentDescription =
                                            stringResource(R.string.cd_music_instant_mix),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                IconButton(
                                    enabled = !isOffline && uiState.tracks.isNotEmpty(),
                                    onClick = {
                                        val firstTrack =
                                            uiState.tracks.firstOrNull() ?: return@IconButton
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
                                        contentDescription =
                                            stringResource(R.string.cd_music_start_radio),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                            if (isDownloadAllowedByServer) {
                                DownloadProgressIndicator(
                                    downloadInfo = uiState.playlistDownloadInfo,
                                    onDownloadClick = {
                                        if (uiState.isMixed) showDownloadChoice = true
                                        else viewModel.downloadPlaylist()
                                    },
                                    onPauseClick = {},
                                    onResumeClick = { viewModel.downloadPlaylist() },
                                    onCancelClick = { viewModel.cancelPlaylistDownload() },
                                    canDownload = canDownloadOnNetwork,
                                    iconSize = 26.dp,
                                )
                            }
                        }
                    }

                    if (uiState.entries.isEmpty()) {
                        item {
                            PlaylistEmptyState(
                                hiddenItemCount = uiState.hiddenItemCount,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            )
                        }
                    }

                    itemsIndexed(
                        uiState.entries,
                        key = { _, entry -> entry.playlistItemId ?: entry.id.toString() },
                    ) { _, entry ->
                        val entryKey = entry.playlistItemId ?: entry.id.toString()
                        ReorderableItem(reorderState, key = entryKey) { isDragging ->
                            val dragModifier =
                                Modifier.longPressDraggableHandle(
                                    enabled = uiState.canReorder,
                                    onDragStopped = {
                                        draggedKey?.let { viewModel.commitEntryMove(it) }
                                        draggedKey = null
                                    },
                                )
                            when (entry) {
                                is PlaylistEntry.Video ->
                                    PlaylistVideoRow(
                                        item = entry.item,
                                        onClick = { viewModel.playVideoEntry(entry.item) },
                                        onRemoveFromPlaylist = { viewModel.removeEntry(entry) },
                                        modifier = dragModifier,
                                    )
                                is PlaylistEntry.Audio -> {
                                    val track = entry.track
                                    MusicTrackRow(
                                        track = track,
                                        isPlaying = track.id == playbackState.currentTrack?.id,
                                        showAlbumArt = true,
                                        onClick = {
                                            startMusicService(context)
                                            playerViewModel.playQueue(
                                                uiState.tracks,
                                                uiState.tracks
                                                    .indexOfFirst { it.id == track.id }
                                                    .coerceAtLeast(0),
                                            )
                                        },
                                        onInstantMix = {
                                            startMusicService(context)
                                            playerViewModel.playInstantMix(track.id)
                                        },
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
                                        onAddToPlaylist = {
                                            addToPlaylistTrackIds = listOf(track.id)
                                            addToPlaylistViewModel.reset()
                                            showAddToPlaylist = true
                                        },
                                        onRemoveFromPlaylist = { viewModel.removeEntry(entry) },
                                        onDownload =
                                            if (isDownloadAllowedByServer)
                                                ({
                                                    viewModel.downloadTrack(track.id)
                                                })
                                            else null,
                                        isDownloadEnabled = canDownloadOnNetwork,
                                        onCancelDownload = {
                                            viewModel.cancelTrackDownload(track.id)
                                        },
                                        downloadInfo = uiState.trackDownloadInfos[track.id],
                                        modifier = dragModifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
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
                item { Spacer(modifier = Modifier.statusBarsPadding().height(80.dp)) }

                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            imageUrl = coverUrl,
                            contentDescription = uiState.playlist?.name,
                            blurHash = uiState.playlist?.images?.primaryImageBlurHash,
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
                            Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 28.dp,
                                bottom = 12.dp,
                            )
                    ) {
                        Text(
                            text =
                                uiState.playlist?.name
                                    ?: stringResource(R.string.music_fallback_playlist),
                            style =
                                MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        PlaylistCountLabel(
                            count = uiState.playlist?.songCount ?: uiState.entries.size,
                            hiddenItemCount = uiState.hiddenItemCount,
                            audioOnly = uiState.audioOnly,
                        )
                    }
                }

                if (uiState.artistEntries.isNotEmpty()) {
                    item {
                        PlaylistArtistsRow(
                            entries = uiState.artistEntries,
                            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                        )
                    }
                }

                item {
                    MusicDetailActionRow(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        playbackEnabled = uiState.entries.isNotEmpty(),
                        onShuffle = { onPlayRequest(true) },
                        onPlay = { onPlayRequest(false) },
                    ) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                painter =
                                    if (uiState.playlist?.favorite == true)
                                        painterResource(R.drawable.ic_favorite_filled)
                                    else painterResource(R.drawable.ic_favorite),
                                contentDescription =
                                    if (uiState.playlist?.favorite == true)
                                        stringResource(R.string.cd_favorite_remove)
                                    else stringResource(R.string.cd_favorite_add),
                                tint =
                                    if (uiState.playlist?.favorite == true) Color.Red
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription =
                                    stringResource(R.string.cd_music_delete_playlist),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        uiState.playlist?.let { playlist ->
                            IconButton(
                                enabled = !isOffline,
                                onClick = {
                                    startMusicService(context)
                                    playerViewModel.playInstantMix(playlist.id)
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_compass),
                                    contentDescription =
                                        stringResource(R.string.cd_music_instant_mix),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            IconButton(
                                enabled = !isOffline && uiState.tracks.isNotEmpty(),
                                onClick = {
                                    val firstTrack =
                                        uiState.tracks.firstOrNull() ?: return@IconButton
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
                                    contentDescription =
                                        stringResource(R.string.cd_music_start_radio),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        if (isDownloadAllowedByServer) {
                            DownloadProgressIndicator(
                                downloadInfo = uiState.playlistDownloadInfo,
                                onDownloadClick = {
                                    if (uiState.isMixed) showDownloadChoice = true
                                    else viewModel.downloadPlaylist()
                                },
                                onPauseClick = {},
                                onResumeClick = { viewModel.downloadPlaylist() },
                                onCancelClick = { viewModel.cancelPlaylistDownload() },
                                canDownload = canDownloadOnNetwork,
                                iconSize = 26.dp,
                            )
                        }
                    }
                }

                if (uiState.entries.isEmpty()) {
                    item {
                        PlaylistEmptyState(
                            hiddenItemCount = uiState.hiddenItemCount,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                }

                itemsIndexed(
                    uiState.entries,
                    key = { _, entry -> entry.playlistItemId ?: entry.id.toString() },
                ) { _, entry ->
                    val entryKey = entry.playlistItemId ?: entry.id.toString()
                    ReorderableItem(reorderState, key = entryKey) { isDragging ->
                        val dragModifier =
                            Modifier.longPressDraggableHandle(
                                enabled = uiState.canReorder,
                                onDragStopped = {
                                    draggedKey?.let { viewModel.commitEntryMove(it) }
                                    draggedKey = null
                                },
                            )
                        when (entry) {
                            is PlaylistEntry.Video ->
                                PlaylistVideoRow(
                                    item = entry.item,
                                    onClick = { viewModel.playVideoEntry(entry.item) },
                                    onRemoveFromPlaylist = { viewModel.removeEntry(entry) },
                                    modifier = dragModifier,
                                )
                            is PlaylistEntry.Audio -> {
                                val track = entry.track
                                MusicTrackRow(
                                    track = track,
                                    isPlaying = track.id == playbackState.currentTrack?.id,
                                    showAlbumArt = true,
                                    onClick = {
                                        startMusicService(context)
                                        playerViewModel.playQueue(
                                            uiState.tracks,
                                            uiState.tracks
                                                .indexOfFirst { it.id == track.id }
                                                .coerceAtLeast(0),
                                        )
                                    },
                                    onInstantMix = {
                                        startMusicService(context)
                                        playerViewModel.playInstantMix(track.id)
                                    },
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
                                    onAddToPlaylist = {
                                        addToPlaylistTrackIds = listOf(track.id)
                                        addToPlaylistViewModel.reset()
                                        showAddToPlaylist = true
                                    },
                                    onRemoveFromPlaylist = { viewModel.removeEntry(entry) },
                                    onDownload =
                                        if (isDownloadAllowedByServer)
                                            ({
                                                viewModel.downloadTrack(track.id)
                                            })
                                        else null,
                                    isDownloadEnabled = canDownloadOnNetwork,
                                    onCancelDownload = { viewModel.cancelTrackDownload(track.id) },
                                    downloadInfo = uiState.trackDownloadInfos[track.id],
                                    modifier = dragModifier,
                                )
                            }
                        }
                    }
                }
            }
        }

        MusicHomeTopAppBar(navController, isLandscape, topBarOpacity)

        SnackbarHost(
            hostState = snackbarHostState,
            snackbar = { AFinitySnackbar(it) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
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
                if (
                    result is AddToPlaylistResult.Added &&
                        result.playlistName == uiState.playlist?.name
                ) {
                    viewModel.reload()
                }
                val message =
                    when (result) {
                        is AddToPlaylistResult.Added ->
                            addedToPlaylistFmt.format(result.playlistName)
                        is AddToPlaylistResult.Created ->
                            createdPlaylistFmt.format(result.playlistName)
                        is AddToPlaylistResult.Error -> result.message
                        else -> null
                    }
                message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
            },
        )
    }

    playChoiceShuffle?.let { shuffle ->
        AlertDialog(
            onDismissRequest = { playChoiceShuffle = null },
            title = { Text(stringResource(R.string.playlist_play_choice_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.playlist_play_choice_message,
                        pluralStringResource(
                            R.plurals.playlist_choice_songs,
                            uiState.audioCount,
                            uiState.audioCount,
                        ),
                        pluralStringResource(
                            R.plurals.playlist_choice_videos,
                            uiState.videoCount,
                            uiState.videoCount,
                        ),
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playChoiceShuffle = null
                        playMusic(shuffle)
                    }
                ) {
                    Text(stringResource(R.string.playlist_play_music))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        playChoiceShuffle = null
                        playVideos(shuffle)
                    }
                ) {
                    Text(stringResource(R.string.playlist_play_videos))
                }
            },
        )
    }

    if (showDownloadChoice) {
        AlertDialog(
            onDismissRequest = { showDownloadChoice = false },
            title = { Text(stringResource(R.string.playlist_download_choice_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.playlist_play_choice_message,
                            pluralStringResource(
                                R.plurals.playlist_choice_songs,
                                uiState.audioCount,
                                uiState.audioCount,
                            ),
                            pluralStringResource(
                                R.plurals.playlist_choice_videos,
                                uiState.videoCount,
                                uiState.videoCount,
                            ),
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadChoiceRow(
                        iconRes = R.drawable.ic_music,
                        label = stringResource(R.string.playlist_download_songs_only),
                        count = uiState.audioCount,
                        onClick = {
                            showDownloadChoice = false
                            viewModel.downloadPlaylist(PlaylistDownloadFilter.AUDIO)
                        },
                    )
                    DownloadChoiceRow(
                        iconRes = R.drawable.ic_music_video,
                        label = stringResource(R.string.playlist_download_videos_only),
                        count = uiState.videoCount,
                        onClick = {
                            showDownloadChoice = false
                            viewModel.downloadPlaylist(PlaylistDownloadFilter.VIDEO)
                        },
                    )
                    DownloadChoiceRow(
                        iconRes = R.drawable.ic_download,
                        label = stringResource(R.string.playlist_download_everything),
                        count = uiState.audioCount + uiState.videoCount,
                        onClick = {
                            showDownloadChoice = false
                            viewModel.downloadPlaylist(PlaylistDownloadFilter.ALL)
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDownloadChoice = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.music_delete_playlist_title)) },
            text = {
                Text(
                    "Are you sure you want to delete \"${uiState.playlist?.name ?: "this playlist"}\"? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deletePlaylist()
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistArtistsRow(
    entries: List<PlaylistArtistEntry>,
    modifier: Modifier = Modifier,
) {
    val display = entries.take(3)
    val overflow = entries.size - display.size
    val containerSize = 32.dp
    val gapWidth = 2.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-12).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            display.forEachIndexed { index, entry ->
                Box(
                    modifier =
                        Modifier.zIndex((display.size - index).toFloat())
                            .size(containerSize)
                            .background(
                                color = MaterialTheme.colorScheme.background,
                                shape = CircleShape,
                            )
                            .padding(gapWidth)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        imageUrl = entry.imageUrl,
                        contentDescription = entry.name,
                        blurHash = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        targetWidth = containerSize,
                        targetHeight = containerSize,
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        val label =
            when {
                entries.size == 1 -> entries[0].name
                entries.size == 2 -> "${entries[0].name} & ${entries[1].name}"
                overflow == 0 -> "${entries[0].name}, ${entries[1].name} & ${entries[2].name}"
                else -> "${entries[0].name}, ${entries[1].name} and $overflow more"
            }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DownloadChoiceRow(iconRes: Int, label: String, count: Int, onClick: () -> Unit) {
    if (count <= 0) return
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistCountLabel(count: Int, hiddenItemCount: Int, audioOnly: Boolean) {
    if (count <= 0 && hiddenItemCount <= 0) return
    val songs =
        if (audioOnly) pluralStringResource(R.plurals.music_playlist_song_count, count, count)
        else pluralStringResource(R.plurals.playlist_item_count, count, count)
    val label =
        if (hiddenItemCount > 0) {
            val hidden =
                pluralStringResource(
                    R.plurals.music_playlist_hidden_videos,
                    hiddenItemCount,
                    hiddenItemCount,
                )
            "$songs · $hidden"
        } else {
            songs
        }
    Spacer(Modifier.height(4.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlaylistEmptyState(hiddenItemCount: Int, modifier: Modifier = Modifier) {
    val videoOnly = hiddenItemCount > 0
    EmptyState(
        icon =
            painterResource(
                if (videoOnly) R.drawable.ic_music_video else R.drawable.ic_music_playlist
            ),
        title =
            stringResource(
                if (videoOnly) R.string.music_playlist_video_only_title
                else R.string.music_playlist_empty_title
            ),
        message =
            if (videoOnly)
                pluralStringResource(
                    R.plurals.music_playlist_video_only_message,
                    hiddenItemCount,
                    hiddenItemCount,
                )
            else stringResource(R.string.music_playlist_empty_message),
        modifier = modifier,
        badgeSize = 96.dp,
        iconSize = 44.dp,
    )
}
