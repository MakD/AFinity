package com.makd.afinity.ui.audiobookshelf.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.makd.afinity.ui.audiobookshelf.player.components.ChapterSelector
import com.makd.afinity.ui.audiobookshelf.player.components.PlaybackSpeedSelector
import com.makd.afinity.ui.audiobookshelf.player.components.PlayerControls
import com.makd.afinity.ui.audiobookshelf.player.components.SleepTimerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfPlayerScreen(
    onNavigateBack: () -> Unit,
    viewModel: AudiobookshelfPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()


    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val coverUrl = playbackState.coverUrl

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = "Minimize player"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (coverUrl != null) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = playbackState.displayTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = playbackState.displayTitle.ifEmpty { "Unknown Title" },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (playbackState.displayAuthor != null) {
                        Text(
                            text = playbackState.displayAuthor!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (playbackState.currentChapter != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playbackState.currentChapter!!.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    PlayerControls(
                        currentTime = playbackState.currentTime,
                        duration = playbackState.duration,
                        isPlaying = playbackState.isPlaying,
                        isBuffering = playbackState.isBuffering,
                        onPlayPauseClick = viewModel::togglePlayPause,
                        onSkipForward = viewModel::skipForward,
                        onSkipBackward = viewModel::skipBackward,
                        onSeek = viewModel::seekTo,
                        onPreviousChapter = if (playbackState.chapters.isNotEmpty() &&
                            playbackState.currentChapterIndex > 0
                        ) {
                            { viewModel.seekToChapter(playbackState.currentChapterIndex - 1) }
                        } else null,
                        onNextChapter = if (playbackState.chapters.isNotEmpty() &&
                            playbackState.currentChapterIndex < playbackState.chapters.lastIndex
                        ) {
                            { viewModel.seekToChapter(playbackState.currentChapterIndex + 1) }
                        } else null
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (playbackState.chapters.isNotEmpty()) {
                            IconButton(onClick = viewModel::showChapterSelector) {
                                Icon(
                                    imageVector = Icons.Filled.List,
                                    contentDescription = "Chapters"
                                )
                            }
                        }

                        TextButton(onClick = viewModel::showSpeedSelector) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${playbackState.playbackSpeed}x")
                        }

                        IconButton(onClick = viewModel::showSleepTimerDialog) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (playbackState.sleepTimerEndTime != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (uiState.showChapterSelector) {
            ChapterSelector(
                chapters = playbackState.chapters,
                currentChapterIndex = playbackState.currentChapterIndex,
                onChapterSelected = viewModel::seekToChapter,
                onDismiss = viewModel::dismissChapterSelector
            )
        }

        if (uiState.showSpeedSelector) {
            PlaybackSpeedSelector(
                currentSpeed = playbackState.playbackSpeed,
                onSpeedSelected = viewModel::setPlaybackSpeed,
                onDismiss = viewModel::dismissSpeedSelector
            )
        }

        if (uiState.showSleepTimerDialog) {
            SleepTimerDialog(
                currentTimerEndTime = playbackState.sleepTimerEndTime,
                onTimerSelected = viewModel::setSleepTimer,
                onCancelTimer = viewModel::cancelSleepTimer,
                onDismiss = viewModel::dismissSleepTimerDialog
            )
        }
    }
}
