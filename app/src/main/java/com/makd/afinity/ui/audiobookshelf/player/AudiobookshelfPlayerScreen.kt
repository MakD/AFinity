package com.makd.afinity.ui.audiobookshelf.player

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackState
import com.makd.afinity.ui.audiobookshelf.player.components.ChapterSelector
import com.makd.afinity.ui.audiobookshelf.player.components.PlaybackSpeedSelector
import com.makd.afinity.ui.audiobookshelf.player.components.PlayerControls
import com.makd.afinity.ui.audiobookshelf.player.components.SleepTimerDialog
import com.makd.afinity.ui.audiobookshelf.player.util.rememberDominantColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfPlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: AudiobookshelfPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultColor = MaterialTheme.colorScheme.surface
    val dominantColor = rememberDominantColor(playbackState.coverUrl, defaultColor)
    val animatedColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(durationMillis = 800),
            label = "color",
        )

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

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
                LandscapePlayerContent(
                    playbackState = playbackState,
                    viewModel = viewModel,
                    animatedColor = animatedColor,
                    onNavigateBack = onNavigateBack,
                    paddingValues = paddingValues,
                )
            } else {
                PortraitPlayerContent(
                    playbackState = playbackState,
                    viewModel = viewModel,
                    animatedColor = animatedColor,
                    onNavigateBack = onNavigateBack,
                    paddingValues = paddingValues,
                )
            }
        }
        if (uiState.showChapterSelector) {
            ChapterSelector(
                chapters = playbackState.chapters,
                currentChapterIndex = playbackState.currentChapterIndex,
                onChapterSelected = viewModel::seekToChapter,
                onDismiss = viewModel::dismissChapterSelector,
            )
        }

        if (uiState.showSpeedSelector) {
            PlaybackSpeedSelector(
                currentSpeed = playbackState.playbackSpeed,
                onSpeedSelected = viewModel::setPlaybackSpeed,
                onDismiss = viewModel::dismissSpeedSelector,
            )
        }

        if (uiState.showSleepTimerDialog) {
            SleepTimerDialog(
                currentTimerEndTime = playbackState.sleepTimerEndTime,
                onTimerSelected = viewModel::setSleepTimer,
                onCancelTimer = viewModel::cancelSleepTimer,
                onDismiss = viewModel::dismissSleepTimerDialog,
            )
        }
    }
}

@Composable
fun PortraitPlayerContent(
    playbackState: AudiobookshelfPlaybackState,
    viewModel: AudiobookshelfPlayerViewModel,
    animatedColor: Color,
    onNavigateBack: () -> Unit,
    paddingValues: PaddingValues,
) {
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
                    painterResource(id = R.drawable.ic_keyboard_arrow_down),
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

            IconButton(onClick = { /* Option Menu */ }) {
                Icon(
                    painterResource(id = R.drawable.ic_options),
                    contentDescription = "Options",
                    tint = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(32.dp),
                            spotColor = Color.Black,
                        ),
                shape = RoundedCornerShape(32.dp),
                color = Color.Transparent,
            ) {
                if (playbackState.coverUrl != null) {
                    AsyncImage(
                        model = playbackState.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = playbackState.displayTitle.ifEmpty { "Unknown Title" },
                style =
                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = playbackState.displayAuthor ?: "Unknown Author",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (playbackState.currentChapter != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playbackState.currentChapter!!.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = animatedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PlayerControls(
            currentTime = playbackState.currentTime,
            duration = playbackState.duration,
            isPlaying = playbackState.isPlaying,
            isBuffering = playbackState.isBuffering,
            onPlayPauseClick = viewModel::togglePlayPause,
            onSkipForward = viewModel::skipForward,
            onSkipBackward = viewModel::skipBackward,
            onSeek = viewModel::seekTo,
            onPreviousChapter =
                if (playbackState.chapters.isNotEmpty() && playbackState.currentChapterIndex > 0) {
                    { viewModel.seekToChapter(playbackState.currentChapterIndex - 1) }
                } else null,
            onNextChapter =
                if (
                    playbackState.chapters.isNotEmpty() &&
                        playbackState.currentChapterIndex < playbackState.chapters.lastIndex
                ) {
                    { viewModel.seekToChapter(playbackState.currentChapterIndex + 1) }
                } else null,
            accentColor = animatedColor,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth(0.5f)
                    .padding(bottom = 50.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::showSpeedSelector) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(id = R.drawable.ic_speed),
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                    if (playbackState.playbackSpeed != 1.0f) {
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .background(
                                        Color.White,
                                        androidx.compose.foundation.shape.CircleShape,
                                    )
                                    .align(Alignment.TopEnd)
                        )
                    }
                }
            }

            IconButton(onClick = viewModel::showSleepTimerDialog) {
                Icon(
                    painter =
                        if (playbackState.sleepTimerEndTime != null)
                            painterResource(id = R.drawable.ic_moon_filled)
                        else painterResource(id = R.drawable.ic_moon),
                    contentDescription = null,
                    tint =
                        if (playbackState.sleepTimerEndTime != null) animatedColor
                        else Color.White.copy(alpha = 0.8f),
                )
            }

            IconButton(onClick = viewModel::showChapterSelector) {
                Icon(
                    painterResource(id = R.drawable.ic_playlist_alt),
                    null,
                    tint = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun LandscapePlayerContent(
    playbackState: AudiobookshelfPlaybackState,
    viewModel: AudiobookshelfPlayerViewModel,
    animatedColor: Color,
    onNavigateBack: () -> Unit,
    paddingValues: PaddingValues,
) {
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
            Surface(
                modifier =
                    Modifier.aspectRatio(1f)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = Color.Black,
                        ),
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
            ) {
                if (playbackState.coverUrl != null) {
                    AsyncImage(
                        model = playbackState.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                        painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        "Minimize",
                        tint = Color.White,
                    )
                }
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                )
                IconButton(onClick = {}) {
                    Icon(painterResource(id = R.drawable.ic_options), "Options", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = playbackState.displayTitle.ifEmpty { "Unknown Title" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playbackState.displayAuthor ?: "Unknown Author",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (playbackState.currentChapter != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playbackState.currentChapter!!.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = animatedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PlayerControls(
                currentTime = playbackState.currentTime,
                duration = playbackState.duration,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onPlayPauseClick = viewModel::togglePlayPause,
                onSkipForward = viewModel::skipForward,
                onSkipBackward = viewModel::skipBackward,
                onSeek = viewModel::seekTo,
                onPreviousChapter =
                    if (
                        playbackState.chapters.isNotEmpty() && playbackState.currentChapterIndex > 0
                    ) {
                        { viewModel.seekToChapter(playbackState.currentChapterIndex - 1) }
                    } else null,
                onNextChapter =
                    if (
                        playbackState.chapters.isNotEmpty() &&
                            playbackState.currentChapterIndex < playbackState.chapters.lastIndex
                    ) {
                        { viewModel.seekToChapter(playbackState.currentChapterIndex + 1) }
                    } else null,
                accentColor = animatedColor,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier =
                    Modifier.fillMaxWidth(0.5f)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = viewModel::showSpeedSelector) {
                    Icon(
                        painterResource(id = R.drawable.ic_speed),
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }
                IconButton(onClick = viewModel::showSleepTimerDialog) {
                    Icon(
                        if (playbackState.sleepTimerEndTime != null)
                            painterResource(id = R.drawable.ic_moon_filled)
                        else painterResource(id = R.drawable.ic_moon),
                        null,
                        tint =
                            if (playbackState.sleepTimerEndTime != null) animatedColor
                            else Color.White.copy(alpha = 0.8f),
                    )
                }
                IconButton(onClick = viewModel::showChapterSelector) {
                    Icon(
                        painterResource(id = R.drawable.ic_playlist_alt),
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
