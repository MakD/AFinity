package com.makd.afinity.ui.music.player

import android.content.res.Configuration
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.cast.framework.CastButtonFactory
import com.makd.afinity.R
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.ui.audiobookshelf.player.util.rememberDominantColor
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.player.components.MusicPlayerControls
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.MusicPlayerScreen(
    onNavigateBack: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val showLyrics by viewModel.showLyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val isMusicCasting by viewModel.isMusicCasting.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val radioState by viewModel.radioState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCastChooser by remember { mutableStateOf(false) }
    var radioSeed by remember { mutableStateOf<RadioSeed?>(null) }
    val context = LocalContext.current
    val mediaRouteButton = remember {
        androidx.mediarouter.app.MediaRouteButton(context).also { button ->
            CastButtonFactory.setUpMediaRouteButton(context, button)
            button.visibility = View.GONE
        }
    }
    LaunchedEffect(showCastChooser) {
        if (showCastChooser) {
            mediaRouteButton.performClick()
            showCastChooser = false
        }
    }
    AndroidView(factory = { mediaRouteButton }, modifier = Modifier)

    val coverUrl = playbackState.currentTrack?.images?.primary?.toString()
    val coverBlurHash = playbackState.currentTrack?.images?.primaryImageBlurHash
    val defaultColor = MaterialTheme.colorScheme.surface
    val dominantColor = rememberDominantColor(coverUrl, defaultColor)
    val animatedColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(durationMillis = 800),
            label = "color",
        )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    animatedColor.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                                    Color.Black.copy(alpha = 0.9f),
                                )
                        )
                    )
        ) {
            if (isLandscape) {
                MusicPlayerLandscape(
                    viewModel = viewModel,
                    coverUrl = coverUrl,
                    coverBlurHash = coverBlurHash,
                    animatedColor = animatedColor,
                    showLyrics = showLyrics,
                    lyrics = lyrics,
                    lyricsLoading = lyricsLoading,
                    onNavigateBack = onNavigateBack,
                    onOpenQueue = { showQueue = true },
                    onOpenSleepTimer = { showSleepTimer = true },
                    onAddToPlaylist =
                        if (isOffline) null
                        else
                            ({
                                addToPlaylistViewModel.reset()
                                showAddToPlaylist = true
                            }),
                    onInstantMix =
                        if (isOffline) null
                        else
                            ({
                                playbackState.currentTrack?.id?.let { viewModel.playInstantMix(it) }
                            }),
                    onStartRadio =
                        if (isOffline) null
                        else
                            ({
                                playbackState.currentTrack?.let { track ->
                                    radioSeed = RadioSeed(
                                        trackId = track.id,
                                        albumId = track.albumId,
                                        sourceTracks = queue,
                                    )
                                }
                            }),
                    isRadioActive = radioState.isActive,
                    onCastClick = { showCastChooser = true },
                    isMusicCasting = isMusicCasting,
                    paddingValues = paddingValues,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            } else {
                MusicPlayerPortrait(
                    viewModel = viewModel,
                    coverUrl = coverUrl,
                    coverBlurHash = coverBlurHash,
                    animatedColor = animatedColor,
                    showLyrics = showLyrics,
                    lyrics = lyrics,
                    lyricsLoading = lyricsLoading,
                    onNavigateBack = onNavigateBack,
                    onOpenQueue = { showQueue = true },
                    onOpenSleepTimer = { showSleepTimer = true },
                    onAddToPlaylist =
                        if (isOffline) null
                        else
                            ({
                                addToPlaylistViewModel.reset()
                                showAddToPlaylist = true
                            }),
                    onInstantMix =
                        if (isOffline) null
                        else
                            ({
                                playbackState.currentTrack?.id?.let { viewModel.playInstantMix(it) }
                            }),
                    onStartRadio =
                        if (isOffline) null
                        else
                            ({
                                playbackState.currentTrack?.let { track ->
                                    radioSeed = RadioSeed(
                                        trackId = track.id,
                                        albumId = track.albumId,
                                        sourceTracks = queue,
                                    )
                                }
                            }),
                    isRadioActive = radioState.isActive,
                    onCastClick = { showCastChooser = true },
                    isMusicCasting = isMusicCasting,
                    paddingValues = paddingValues,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        if (showQueue) {
            MusicQueueSheet(onDismiss = { showQueue = false }, viewModel = viewModel)
        }

        if (showSleepTimer) {
            MusicSleepTimerSheet(
                activeTimerEndMs = playbackState.sleepTimerEndMs,
                onSetTimer = viewModel::setSleepTimer,
                onCancel = viewModel::cancelSleepTimer,
                onDismiss = { showSleepTimer = false },
            )
        }

        radioSeed?.let { seed ->
            RadioModeBottomSheet(
                seed = seed,
                onDismiss = { radioSeed = null },
                onSelectMode = { s, mode ->
                    viewModel.startRadio(s, mode)
                    radioSeed = null
                },
            )
        }

        if (showAddToPlaylist) {
            val currentTrackId = playbackState.currentTrack?.id
            if (currentTrackId != null) {
                AddToPlaylistDialog(
                    trackIds = listOf(currentTrackId),
                    viewModel = addToPlaylistViewModel,
                    onDismiss = { showAddToPlaylist = false },
                    onResult = { result ->
                        showAddToPlaylist = false
                        val message =
                            when (result) {
                                is AddToPlaylistResult.Added ->
                                    "Added to \"${result.playlistName}\""
                                is AddToPlaylistResult.Created ->
                                    "Created \"${result.playlistName}\""
                                is AddToPlaylistResult.Error -> result.message
                                else -> null
                            }
                        message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MusicPlayerPortrait(
    viewModel: MusicPlayerViewModel,
    coverUrl: String?,
    coverBlurHash: String? = null,
    animatedColor: Color,
    showLyrics: Boolean,
    lyrics: List<com.makd.afinity.data.models.music.AfinityLyricLine>,
    lyricsLoading: Boolean,
    onNavigateBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onInstantMix: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    isRadioActive: Boolean = false,
    onCastClick: () -> Unit = {},
    isMusicCasting: Boolean = false,
    paddingValues: PaddingValues,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Minimize",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSleepTimer) {
                    Icon(
                        painter =
                            painterResource(
                                if (playbackState.sleepTimerEndMs != null) R.drawable.ic_moon_filled
                                else R.drawable.ic_moon
                            ),
                        contentDescription = "Sleep timer",
                        tint =
                            if (playbackState.sleepTimerEndMs != null) animatedColor
                            else Color.White,
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playlist_alt),
                        contentDescription = "Queue",
                        tint = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "lyricsToggle",
                modifier = Modifier.fillMaxSize(),
            ) { lyricsVisible ->
                if (lyricsVisible) {
                    MusicLyricsView(
                        lyrics = lyrics,
                        positionMs = playbackState.positionMs,
                        isLoading = lyricsLoading,
                        onSeek = viewModel::seekTo,
                        accentColor = animatedColor,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier =
                                Modifier.aspectRatio(1f)
                                    .sharedElement(
                                        sharedContentState =
                                            rememberSharedContentState(
                                                key = "music-cover-${coverUrl ?: "default"}"
                                            ),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                    .shadow(
                                        elevation = 24.dp,
                                        shape = RoundedCornerShape(32.dp),
                                        spotColor = Color.Black,
                                    ),
                            shape = RoundedCornerShape(32.dp),
                            color = Color.Transparent,
                        ) {
                            if (coverUrl != null || coverBlurHash != null) {
                                val coverSizeDp = LocalConfiguration.current.screenWidthDp.dp
                                com.makd.afinity.ui.components.AsyncImage(
                                    imageUrl = coverUrl,
                                    contentDescription = null,
                                    blurHash = coverBlurHash,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    targetWidth = coverSizeDp,
                                    targetHeight = coverSizeDp,
                                )
                            } else {
                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_music),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playbackState.currentTrack?.name ?: "Nothing playing",
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        playbackState.currentTrack?.let {
                            it.artist ?: it.artists.firstOrNull() ?: ""
                        } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val album = playbackState.currentTrack?.album
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = album ?: " ",
                    style = MaterialTheme.typography.labelLarge,
                    color = animatedColor,
                    maxLines = 1,
                    modifier =
                        Modifier.alpha(if (album != null) 1f else 0f)
                            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = onAddToPlaylist != null,
                    onClick = onAddToPlaylist ?: {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playlist),
                        contentDescription = "Add to playlist",
                        tint =
                            Color.White.copy(alpha = if (onAddToPlaylist != null) 0.8f else 0.3f),
                    )
                }
                IconButton(
                    enabled = onInstantMix != null,
                    onClick = onInstantMix ?: {},
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_compass),
                        contentDescription = "Instant Mix",
                        tint = Color.White.copy(alpha = if (onInstantMix != null) 0.8f else 0.3f),
                    )
                }
                IconButton(
                    enabled = onStartRadio != null,
                    onClick = onStartRadio ?: {},
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_radio),
                            contentDescription = "Start Radio",
                            tint =
                                if (isRadioActive) animatedColor
                                else Color.White.copy(alpha = if (onStartRadio != null) 0.8f else 0.3f),
                        )
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .align(Alignment.BottomCenter)
                                    .alpha(if (isRadioActive) 1f else 0f)
                                    .background(animatedColor, CircleShape)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        MusicPlayerControls(
            positionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            bufferedPositionMs = playbackState.bufferedPositionMs,
            isPlaying = playbackState.isPlaying,
            isBuffering = playbackState.isBuffering,
            onPlayPauseClick = viewModel::togglePlayPause,
            onPrevious = viewModel::skipPrevious,
            onNext = viewModel::skipNext,
            onSeek = viewModel::seekTo,
            onSeekBackward = viewModel::seekBackward,
            onSeekForward = viewModel::seekForward,
            accentColor = animatedColor,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                IconButton(onClick = viewModel::toggleCurrentTrackFavorite) {
                    Icon(
                        painter =
                            painterResource(
                                if (playbackState.currentTrack?.favorite == true)
                                    R.drawable.ic_favorite_filled
                                else R.drawable.ic_favorite
                            ),
                        contentDescription = "Favorite",
                        tint =
                            if (playbackState.currentTrack?.favorite == true) Color.Red
                            else Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            Row(
                modifier =
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrows_shuffle),
                            contentDescription = "Shuffle",
                            tint =
                                if (playbackState.shuffled) animatedColor
                                else Color.White.copy(alpha = 0.8f),
                        )
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .align(Alignment.BottomCenter)
                                    .alpha(if (playbackState.shuffled) 1f else 0f)
                                    .background(animatedColor, CircleShape)
                        )
                    }
                }
                IconButton(onClick = viewModel::cycleRepeatMode) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter =
                                when (playbackState.repeatMode) {
                                    RepeatMode.OFF -> painterResource(R.drawable.ic_repeat_off)
                                    RepeatMode.ALL -> painterResource(R.drawable.ic_repeat)
                                    RepeatMode.ONE -> painterResource(R.drawable.ic_repeat_once)
                                },
                            contentDescription = "Repeat",
                            tint =
                                if (playbackState.repeatMode != RepeatMode.OFF) animatedColor
                                else Color.White.copy(alpha = 0.8f),
                        )
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .align(Alignment.BottomCenter)
                                    .alpha(
                                        if (playbackState.repeatMode != RepeatMode.OFF) 1f else 0f
                                    )
                                    .background(animatedColor, CircleShape)
                        )
                    }
                }
                IconButton(onClick = viewModel::toggleLyrics) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_article),
                            contentDescription = "Lyrics",
                            tint =
                                if (showLyrics) animatedColor else Color.White.copy(alpha = 0.8f),
                        )
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .align(Alignment.BottomCenter)
                                    .alpha(if (showLyrics) 1f else 0f)
                                    .background(animatedColor, CircleShape)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = onCastClick) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cast_devices),
                            contentDescription = "Cast",
                            tint =
                                if (isMusicCasting) animatedColor
                                else Color.White.copy(alpha = 0.8f),
                        )
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .align(Alignment.BottomCenter)
                                    .alpha(if (isMusicCasting) 1f else 0f)
                                    .background(animatedColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MusicPlayerLandscape(
    viewModel: MusicPlayerViewModel,
    coverUrl: String?,
    coverBlurHash: String? = null,
    animatedColor: Color,
    showLyrics: Boolean,
    lyrics: List<com.makd.afinity.data.models.music.AfinityLyricLine>,
    lyricsLoading: Boolean,
    onNavigateBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onInstantMix: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    isRadioActive: Boolean = false,
    onCastClick: () -> Unit = {},
    isMusicCasting: Boolean = false,
    paddingValues: PaddingValues,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    Row(
        modifier =
            Modifier.fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(0.45f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "lyricsToggleLandscape",
                modifier = Modifier.fillMaxSize(),
            ) { lyricsVisible ->
                if (lyricsVisible) {
                    MusicLyricsView(
                        lyrics = lyrics,
                        positionMs = playbackState.positionMs,
                        isLoading = lyricsLoading,
                        onSeek = viewModel::seekTo,
                        accentColor = animatedColor,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier =
                                Modifier.aspectRatio(1f)
                                    .sharedElement(
                                        sharedContentState =
                                            rememberSharedContentState(
                                                key = "music-cover-${coverUrl ?: "default"}"
                                            ),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(24.dp),
                                        spotColor = Color.Black,
                                    ),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent,
                        ) {
                            if (coverUrl != null || coverBlurHash != null) {
                                val coverSizeDp =
                                    (LocalConfiguration.current.screenWidthDp * 0.45f).dp
                                com.makd.afinity.ui.components.AsyncImage(
                                    imageUrl = coverUrl,
                                    contentDescription = null,
                                    blurHash = coverBlurHash,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    targetWidth = coverSizeDp,
                                    targetHeight = coverSizeDp,
                                )
                            } else {
                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_music),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                        contentDescription = "Minimize",
                        tint = Color.White,
                    )
                }
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSleepTimer) {
                        Icon(
                            painter =
                                painterResource(
                                    if (playbackState.sleepTimerEndMs != null)
                                        R.drawable.ic_moon_filled
                                    else R.drawable.ic_moon
                                ),
                            contentDescription = "Sleep timer",
                            tint =
                                if (playbackState.sleepTimerEndMs != null) animatedColor
                                else Color.White,
                        )
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist_alt),
                            contentDescription = "Queue",
                            tint = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playbackState.currentTrack?.name ?: "Nothing playing",
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            playbackState.currentTrack?.let {
                                it.artist ?: it.artists.firstOrNull() ?: ""
                            } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val albumLandscape = playbackState.currentTrack?.album
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = albumLandscape ?: " ",
                        style = MaterialTheme.typography.labelLarge,
                        color = animatedColor,
                        maxLines = 1,
                        modifier =
                            Modifier.alpha(if (albumLandscape != null) 1f else 0f)
                                .basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        enabled = onAddToPlaylist != null,
                        onClick = onAddToPlaylist ?: {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist),
                            contentDescription = "Add to playlist",
                            tint =
                                Color.White.copy(
                                    alpha = if (onAddToPlaylist != null) 0.8f else 0.3f
                                ),
                        )
                    }
                    IconButton(
                        enabled = onInstantMix != null,
                        onClick = onInstantMix ?: {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_compass),
                            contentDescription = "Instant Mix",
                            tint =
                                Color.White.copy(alpha = if (onInstantMix != null) 0.8f else 0.3f),
                        )
                    }
                    IconButton(
                        enabled = onStartRadio != null,
                        onClick = onStartRadio ?: {},
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter = painterResource(R.drawable.ic_radio),
                                contentDescription = "Start Radio",
                                tint =
                                    if (isRadioActive) animatedColor
                                    else Color.White.copy(alpha = if (onStartRadio != null) 0.8f else 0.3f),
                            )
                            Box(
                                modifier =
                                    Modifier.size(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .alpha(if (isRadioActive) 1f else 0f)
                                        .background(animatedColor, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            MusicPlayerControls(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                bufferedPositionMs = playbackState.bufferedPositionMs,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onPlayPauseClick = viewModel::togglePlayPause,
                onPrevious = viewModel::skipPrevious,
                onNext = viewModel::skipNext,
                onSeek = viewModel::seekTo,
                onSeekBackward = viewModel::seekBackward,
                onSeekForward = viewModel::seekForward,
                accentColor = animatedColor,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    IconButton(onClick = viewModel::toggleCurrentTrackFavorite) {
                        Icon(
                            painter =
                                painterResource(
                                    if (playbackState.currentTrack?.favorite == true)
                                        R.drawable.ic_favorite_filled
                                    else R.drawable.ic_favorite
                                ),
                            contentDescription = "Favorite",
                            tint =
                                if (playbackState.currentTrack?.favorite == true) Color.Red
                                else Color.White.copy(alpha = 0.8f),
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier.clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrows_shuffle),
                                contentDescription = "Shuffle",
                                tint =
                                    if (playbackState.shuffled) animatedColor
                                    else Color.White.copy(alpha = 0.8f),
                            )
                            Box(
                                modifier =
                                    Modifier.size(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .alpha(if (playbackState.shuffled) 1f else 0f)
                                        .background(animatedColor, CircleShape)
                            )
                        }
                    }
                    IconButton(onClick = viewModel::cycleRepeatMode) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter =
                                    when (playbackState.repeatMode) {
                                        RepeatMode.OFF -> painterResource(R.drawable.ic_repeat_off)
                                        RepeatMode.ALL -> painterResource(R.drawable.ic_repeat)
                                        RepeatMode.ONE -> painterResource(R.drawable.ic_repeat_once)
                                    },
                                contentDescription = "Repeat",
                                tint =
                                    if (playbackState.repeatMode != RepeatMode.OFF) animatedColor
                                    else Color.White.copy(alpha = 0.8f),
                            )
                            Box(
                                modifier =
                                    Modifier.size(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .alpha(
                                            if (playbackState.repeatMode != RepeatMode.OFF) 1f
                                            else 0f
                                        )
                                        .background(animatedColor, CircleShape)
                            )
                        }
                    }
                    IconButton(onClick = viewModel::toggleLyrics) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_article),
                                contentDescription = "Lyrics",
                                tint =
                                    if (showLyrics) animatedColor
                                    else Color.White.copy(alpha = 0.8f),
                            )
                            Box(
                                modifier =
                                    Modifier.size(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .alpha(if (showLyrics) 1f else 0f)
                                        .background(animatedColor, CircleShape)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = onCastClick) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cast_devices),
                                contentDescription = "Cast",
                                tint =
                                    if (isMusicCasting) animatedColor
                                    else Color.White.copy(alpha = 0.8f),
                            )
                            Box(
                                modifier =
                                    Modifier.size(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .alpha(if (isMusicCasting) 1f else 0f)
                                        .background(animatedColor, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
