package com.makd.afinity.ui.music.artist

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.MusicAlbumCard
import com.makd.afinity.ui.music.components.MusicHeroBackground
import com.makd.afinity.ui.music.components.MusicTrackRow
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.utils.htmlToAnnotatedString
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MusicArtistScreen(
    navController: NavController,
    viewModel: MusicArtistViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val isOffline by playerViewModel.isOffline.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addToPlaylistTrackIds by remember { mutableStateOf<List<UUID>>(emptyList()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var radioSeed by remember { mutableStateOf<RadioSeed?>(null) }
    var showAllTracks by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val portraitListState = rememberLazyListState()
    val landscapeListState = rememberLazyListState()
    val lazyListState = if (isLandscape) landscapeListState else portraitListState
    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FullScreenLoading()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isLandscape) {
            MusicHeroBackground(
                coverUrl =
                    (uiState.artist?.images?.backdrop ?: uiState.artist?.images?.primary)
                        ?.toString()
            )

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
                        imageUrl = uiState.artist?.images?.primary?.toString(),
                        contentDescription = uiState.artist?.name,
                        blurHash = uiState.artist?.images?.primaryImageBlurHash,
                        targetWidth = 180.dp,
                        targetHeight = 180.dp,
                        modifier = Modifier.size(180.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.ic_person_placeholder),
                        error = painterResource(R.drawable.ic_person_placeholder),
                    )

                    Spacer(Modifier.height(16.dp))

                    val logoUrl = uiState.artist?.images?.logo?.toString()
                    if (logoUrl != null) {
                        AsyncImage(
                            imageUrl = logoUrl,
                            contentDescription = uiState.artist?.name,
                            blurHash = null,
                            modifier = Modifier.fillMaxWidth(0.75f).height(80.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            targetWidth = 240.dp,
                            targetHeight = 80.dp,
                        )
                    } else {
                        Text(
                            text = uiState.artist?.name ?: "",
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
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
                    item { Spacer(Modifier.statusBarsPadding().height(64.dp)) }

                    item {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
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
                                            if (uiState.artist?.favorite == true)
                                                painterResource(R.drawable.ic_favorite_filled)
                                            else painterResource(R.drawable.ic_favorite),
                                        contentDescription =
                                            if (uiState.artist?.favorite == true)
                                                stringResource(R.string.cd_favorite_remove)
                                            else stringResource(R.string.cd_favorite_add),
                                        tint =
                                            if (uiState.artist?.favorite == true) Color.Red
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                uiState.artist?.let { artist ->
                                    IconButton(
                                        enabled = !isOffline,
                                        onClick = {
                                            startMusicService(context)
                                            playerViewModel.playInstantMix(artist.id)
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_compass),
                                            contentDescription = stringResource(R.string.cd_music_instant_mix),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    IconButton(
                                        enabled = !isOffline && uiState.topTracks.isNotEmpty(),
                                        onClick = {
                                            val firstTrack =
                                                uiState.topTracks.firstOrNull() ?: return@IconButton
                                            radioSeed =
                                                RadioSeed(
                                                    trackId = firstTrack.id,
                                                    albumId = firstTrack.albumId,
                                                    sourceTracks = uiState.topTracks,
                                                )
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_radio),
                                            contentDescription = stringResource(R.string.cd_music_start_radio),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        if (uiState.topTracks.isNotEmpty()) {
                                            startMusicService(context)
                                            playerViewModel.playQueue(
                                                uiState.topTracks.shuffled(),
                                                0,
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrows_shuffle),
                                        contentDescription = stringResource(R.string.cd_music_shuffle),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                FloatingActionButton(
                                    onClick = {
                                        if (uiState.topTracks.isNotEmpty()) {
                                            startMusicService(context)
                                            playerViewModel.playQueue(uiState.topTracks, 0)
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_player_play_filled),
                                        contentDescription = stringResource(R.string.cd_play),
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }
                    }

                    val overview = uiState.artist?.overview
                    if (!overview.isNullOrBlank()) {
                        item {
                            ArtistOverviewSection(
                                overview = overview,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }

                    val genres = uiState.artist?.genres.orEmpty()
                    if (genres.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(genres) { genre ->
                                    androidx.compose.material3.SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                genre,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.topTracks.isNotEmpty()) {
                        item {
                            val fixedTracks = uiState.topTracks.take(5)
                            val extraTracks = uiState.topTracks.drop(5)
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
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier =
                                    Modifier.padding(
                                        start = 20.dp,
                                        top = 24.dp,
                                        bottom = 12.dp,
                                    ),
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = fadeOutModifier) {
                                    fixedTracks.forEachIndexed { index, track ->
                                        MusicTrackRow(
                                            track = track,
                                            isPlaying = track.id == playbackState.currentTrack?.id,
                                            trackNumber = index + 1,
                                            showAlbumArt = true,
                                            onClick = {
                                                startMusicService(context)
                                                playerViewModel.playQueue(
                                                    uiState.topTracks,
                                                    index,
                                                )
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
                                                                sourceTracks = uiState.topTracks,
                                                            )
                                                    }),
                                            onAddNext = { playerViewModel.addNext(listOf(track)) },
                                            onAddLast = { playerViewModel.addLast(listOf(track)) },
                                            onFavorite = {
                                                viewModel.toggleTrackFavorite(track.id)
                                            },
                                            onAddToPlaylist =
                                                if (isOffline) null
                                                else
                                                    ({
                                                        addToPlaylistTrackIds = listOf(track.id)
                                                        addToPlaylistViewModel.reset()
                                                        showAddToPlaylist = true
                                                    }),
                                            onDownload = { viewModel.downloadTrack(track.id) },
                                            isDownloaded =
                                                uiState.trackDownloadInfos[track.id]?.status ==
                                                    DownloadStatus.COMPLETED,
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
                                                    isPlaying =
                                                        track.id == playbackState.currentTrack?.id,
                                                    trackNumber = fixedTracks.size + i + 1,
                                                    showAlbumArt = true,
                                                    onClick = {
                                                        startMusicService(context)
                                                        playerViewModel.playQueue(
                                                            uiState.topTracks,
                                                            fixedTracks.size + i,
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
                                                                        sourceTracks =
                                                                            uiState.topTracks,
                                                                    )
                                                            }),
                                                    onAddNext = {
                                                        playerViewModel.addNext(listOf(track))
                                                    },
                                                    onAddLast = {
                                                        playerViewModel.addLast(listOf(track))
                                                    },
                                                    onFavorite = {
                                                        viewModel.toggleTrackFavorite(track.id)
                                                    },
                                                    onAddToPlaylist = {
                                                        addToPlaylistTrackIds = listOf(track.id)
                                                        addToPlaylistViewModel.reset()
                                                        showAddToPlaylist = true
                                                    },
                                                    onDownload = {
                                                        viewModel.downloadTrack(track.id)
                                                    },
                                                    isDownloaded =
                                                        uiState.trackDownloadInfos[track.id]
                                                            ?.status == DownloadStatus.COMPLETED,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (hasMore) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    OutlinedButton(
                                        onClick = { showAllTracks = !showAllTracks },
                                        shape = CircleShape,
                                        contentPadding =
                                            PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                                    ) {
                                        Crossfade(
                                            targetState = showAllTracks,
                                            label = "see_more_label",
                                        ) { expanded ->
                                            Text(
                                                text = stringResource(if (expanded) R.string.action_see_less else R.string.action_see_more),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.music_section_albums),
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier =
                                    Modifier.padding(start = 20.dp, top = 32.dp, bottom = 16.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(uiState.albums, key = { it.id }) { album ->
                                    Box(modifier = Modifier.width(140.dp)) {
                                        MusicAlbumCard(
                                            album = album,
                                            onClick = {
                                                navController.navigate(
                                                    Destination.createMusicAlbumRoute(
                                                        album.id.toString()
                                                    )
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.appearsOn.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.music_section_appears_on),
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier =
                                    Modifier.padding(start = 20.dp, top = 32.dp, bottom = 16.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(uiState.appearsOn, key = { it.id }) { album ->
                                    Box(modifier = Modifier.width(140.dp)) {
                                        MusicAlbumCard(
                                            album = album,
                                            onClick = {
                                                navController.navigate(
                                                    Destination.createMusicAlbumRoute(
                                                        album.id.toString()
                                                    )
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        } else {
            LazyColumn(
                state = portraitListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = playerOffset + 16.dp),
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.6f)) {
                        AsyncImage(
                            imageUrl =
                                (uiState.artist?.images?.backdrop
                                        ?: uiState.artist?.images?.primary)
                                    ?.toString(),
                            contentDescription = uiState.artist?.name,
                            blurHash =
                                uiState.artist?.images?.backdropImageBlurHash
                                    ?: uiState.artist?.images?.primaryImageBlurHash,
                            targetWidth = 800.dp,
                            targetHeight = 600.dp,
                            modifier =
                                Modifier.fillMaxSize()
                                    .graphicsLayer { alpha = 0.99f }
                                    .drawWithCache {
                                        val gradient =
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startY = size.height * 0.72f,
                                                endY = size.height,
                                            )
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(gradient, blendMode = BlendMode.DstIn)
                                        }
                                    },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                item(key = "artist_header") {
                    Column(
                        modifier = Modifier.artistContentOffset(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        val logoUrl = uiState.artist?.images?.logo?.toString()
                        if (logoUrl != null) {
                            val logoWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
                            AsyncImage(
                                imageUrl = logoUrl,
                                contentDescription = uiState.artist?.name,
                                blurHash = null,
                                modifier =
                                    Modifier.fillMaxWidth(0.8f)
                                        .height(120.dp)
                                        .align(Alignment.CenterHorizontally),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.Center,
                                targetWidth = logoWidth,
                                targetHeight = 120.dp,
                            )
                        } else {
                            Text(
                                text = uiState.artist?.name ?: "",
                                style =
                                    MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp,
                                    ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { viewModel.toggleFavorite() }) {
                                    Icon(
                                        painter =
                                            if (uiState.artist?.favorite == true)
                                                painterResource(R.drawable.ic_favorite_filled)
                                            else painterResource(R.drawable.ic_favorite),
                                        contentDescription =
                                            if (uiState.artist?.favorite == true)
                                                stringResource(R.string.cd_favorite_remove)
                                            else stringResource(R.string.cd_favorite_add),
                                        tint =
                                            if (uiState.artist?.favorite == true) Color.Red
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                uiState.artist?.let { artist ->
                                    IconButton(
                                        enabled = !isOffline,
                                        onClick = {
                                            startMusicService(context)
                                            playerViewModel.playInstantMix(artist.id)
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_compass),
                                            contentDescription = stringResource(R.string.cd_music_instant_mix),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                    IconButton(
                                        enabled = !isOffline && uiState.topTracks.isNotEmpty(),
                                        onClick = {
                                            val firstTrack =
                                                uiState.topTracks.firstOrNull() ?: return@IconButton
                                            radioSeed =
                                                RadioSeed(
                                                    trackId = firstTrack.id,
                                                    albumId = firstTrack.albumId,
                                                    sourceTracks = uiState.topTracks,
                                                )
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_radio),
                                            contentDescription = stringResource(R.string.cd_music_start_radio),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (uiState.topTracks.isNotEmpty()) {
                                            startMusicService(context)
                                            playerViewModel.playQueue(
                                                uiState.topTracks.shuffled(),
                                                0,
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrows_shuffle),
                                        contentDescription = stringResource(R.string.cd_music_shuffle),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                FloatingActionButton(
                                    onClick = {
                                        if (uiState.topTracks.isNotEmpty()) {
                                            startMusicService(context)
                                            playerViewModel.playQueue(uiState.topTracks, 0)
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_player_play_filled),
                                        contentDescription = stringResource(R.string.cd_play),
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }

                        val overview = uiState.artist?.overview
                        if (!overview.isNullOrBlank()) {
                            ArtistOverviewSection(
                                overview = overview,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }

                        val genres = uiState.artist?.genres.orEmpty()
                        if (genres.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(genres) { genre ->
                                    androidx.compose.material3.SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                genre,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.topTracks.isNotEmpty()) {
                    val fixedTracks = uiState.topTracks.take(5)
                    val extraTracks = uiState.topTracks.drop(5)
                    val hasMore = extraTracks.isNotEmpty()

                    item(key = "popular_header") {
                        Text(
                            text = "Popular",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier =
                                Modifier.padding(
                                    start = 20.dp,
                                    top = 24.dp,
                                    bottom = 12.dp,
                                ),
                        )
                    }

                    itemsIndexed(
                        fixedTracks,
                        key = { _, track -> "fixed_${track.id}" },
                    ) { index, track ->
                        val isLastFixedTrack = index == fixedTracks.lastIndex
                        val rowModifier =
                            if (isLastFixedTrack && hasMore && !showAllTracks) {
                                Modifier.graphicsLayer { alpha = 0.99f }
                                    .drawWithCache {
                                        val gradient =
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startY = size.height * 0.2f,
                                                endY = size.height,
                                            )
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(gradient, blendMode = BlendMode.DstIn)
                                        }
                                    }
                            } else Modifier

                        Box(modifier = rowModifier) {
                            MusicTrackRow(
                                track = track,
                                isPlaying = track.id == playbackState.currentTrack?.id,
                                trackNumber = index + 1,
                                showAlbumArt = true,
                                onClick = {
                                    startMusicService(context)
                                    playerViewModel.playQueue(uiState.topTracks, index)
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
                                                    sourceTracks = uiState.topTracks,
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
                                onDownload = { viewModel.downloadTrack(track.id) },
                                isDownloaded =
                                    uiState.trackDownloadInfos[track.id]?.status ==
                                        DownloadStatus.COMPLETED,
                            )
                        }
                    }

                    if (hasMore) {
                        item(key = "extra_tracks") {
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
                                            onClick = {
                                                startMusicService(context)
                                                playerViewModel.playQueue(
                                                    uiState.topTracks,
                                                    fixedTracks.size + i,
                                                )
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
                                                                sourceTracks = uiState.topTracks,
                                                            )
                                                    }),
                                            onAddNext = {
                                                playerViewModel.addNext(listOf(track))
                                            },
                                            onAddLast = {
                                                playerViewModel.addLast(listOf(track))
                                            },
                                            onFavorite = {
                                                viewModel.toggleTrackFavorite(track.id)
                                            },
                                            onAddToPlaylist =
                                                if (isOffline) null
                                                else
                                                    ({
                                                        addToPlaylistTrackIds = listOf(track.id)
                                                        addToPlaylistViewModel.reset()
                                                        showAddToPlaylist = true
                                                    }),
                                            onDownload = {
                                                viewModel.downloadTrack(track.id)
                                            },
                                            isDownloaded =
                                                uiState.trackDownloadInfos[track.id]?.status ==
                                                    DownloadStatus.COMPLETED,
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "see_more") {
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                OutlinedButton(
                                    onClick = { showAllTracks = !showAllTracks },
                                    shape = CircleShape,
                                    contentPadding =
                                        PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                                ) {
                                    Crossfade(
                                        targetState = showAllTracks,
                                        label = "see_more_label",
                                    ) { expanded ->
                                        Text(
                                            text = if (expanded) "See less" else "See more",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.albums.isNotEmpty()) {
                    item(key = "albums_section") {
                        Text(
                            text = "Albums",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 16.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(uiState.albums, key = { it.id }) { album ->
                                Box(modifier = Modifier.width(140.dp)) {
                                    MusicAlbumCard(
                                        album = album,
                                        onClick = {
                                            navController.navigate(
                                                Destination.createMusicAlbumRoute(
                                                    album.id.toString()
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.appearsOn.isNotEmpty()) {
                    item(key = "appears_on_section") {
                        Text(
                            text = "Appears On",
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 16.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(uiState.appearsOn, key = { it.id }) { album ->
                                Box(modifier = Modifier.width(140.dp)) {
                                    MusicAlbumCard(
                                        album = album,
                                        onClick = {
                                            navController.navigate(
                                                Destination.createMusicAlbumRoute(
                                                    album.id.toString()
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
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
                            contentDescription = stringResource(R.string.cd_music_go_to_home),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            backgroundOpacity = { if (isLandscape) 0f else topBarOpacity },
        )

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
private fun ArtistOverviewSection(overview: String, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var isEllipsized by remember { mutableStateOf(false) }

    val containsHtml =
        remember(overview) {
            overview.contains("<a ", ignoreCase = true) ||
                overview.contains("</a>", ignoreCase = true) ||
                overview.contains("<br", ignoreCase = true)
        }
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText =
        remember(overview, linkColor) {
            if (containsHtml) htmlToAnnotatedString(overview, linkColor) else null
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val textModifier = Modifier.animateContentSize()
        val style = MaterialTheme.typography.bodyMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        val maxLines = if (isExpanded) Int.MAX_VALUE else 4
        val overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
        val onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = { result ->
            if (!isExpanded) isEllipsized = result.hasVisualOverflow
        }

        if (containsHtml && annotatedText != null) {
            Text(
                text = annotatedText,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = overflow,
                modifier = textModifier,
                onTextLayout = onTextLayout,
            )
        } else {
            Text(
                text = overview,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = overflow,
                modifier = textModifier,
                onTextLayout = onTextLayout,
            )
        }

        if (isEllipsized || isExpanded) {
            Text(
                text = stringResource(if (isExpanded) R.string.action_show_less else R.string.action_show_more),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        isExpanded = !isExpanded
                    },
            )
        }
    }
}

private fun Modifier.artistContentOffset(yOffset: Dp = (-90).dp) =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val yOffsetPx = yOffset.roundToPx()
        layout(placeable.width, placeable.height + yOffsetPx) {
            placeable.placeRelative(0, yOffsetPx)
        }
    }
