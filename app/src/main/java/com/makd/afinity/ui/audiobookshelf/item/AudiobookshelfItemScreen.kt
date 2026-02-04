package com.makd.afinity.ui.audiobookshelf.item

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.ui.audiobookshelf.item.components.ChapterList
import com.makd.afinity.ui.audiobookshelf.item.components.EpisodeList
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeader
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeaderContent
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeroBackground

@Composable
fun AudiobookshelfItemScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, String?) -> Unit,
    viewModel: AudiobookshelfItemViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()

    val isPodcast = item?.mediaType?.lowercase() == "podcast"
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            item != null -> {
                if (isLandscape) {
                    val coverUrl =
                        if (config?.serverUrl != null && item?.media?.coverPath != null) {
                            "${config?.serverUrl}/api/items/${item?.id}/cover"
                        } else null

                    ItemHeroBackground(coverUrl = coverUrl)

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.displayCutout)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 24.dp)
                        ) {
                            ItemHeaderContent(
                                item = item!!,
                                progress = progress,
                                coverUrl = coverUrl,
                                onPlay = { onNavigateToPlayer(viewModel.itemId, null) }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            Spacer(modifier = Modifier.statusBarsPadding())

                            val showEpisodesHeader = isPodcast && uiState.episodes.isNotEmpty()
                            val showChaptersHeader = !isPodcast && uiState.chapters.isNotEmpty()

                            if (showEpisodesHeader || showChaptersHeader) {
                                Text(
                                    text = if (showEpisodesHeader) "EPISODES" else "CHAPTERS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 20.dp,
                                            end = 16.dp,
                                            top = 20.dp,
                                            bottom = 12.dp
                                        )
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = WindowInsets.navigationBars.asPaddingValues()
                            ) {
                                if (isPodcast && uiState.episodes.isNotEmpty()) {
                                    item {
                                        EpisodeList(
                                            episodes = uiState.episodes,
                                            onEpisodeClick = { /* Details */ },
                                            onEpisodePlay = { episode ->
                                                onNavigateToPlayer(viewModel.itemId, episode.id)
                                            },
                                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                                        )
                                    }
                                } else if (!isPodcast && uiState.chapters.isNotEmpty()) {
                                    item {
                                        ChapterList(
                                            chapters = uiState.chapters,
                                            currentPosition = progress?.currentTime,
                                            onChapterClick = { chapter ->
                                                onNavigateToPlayer(viewModel.itemId, null)
                                            },
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                } else {
                    Column(modifier = Modifier.fillMaxSize()) {

                        ItemHeader(
                            item = item!!,
                            progress = progress,
                            serverUrl = config?.serverUrl,
                            onPlay = {
                                onNavigateToPlayer(viewModel.itemId, null)
                            }
                        )

                        val showEpisodesHeader = isPodcast && uiState.episodes.isNotEmpty()
                        val showChaptersHeader = !isPodcast && uiState.chapters.isNotEmpty()

                        if (showEpisodesHeader || showChaptersHeader) {
                            Text(
                                text = if (showEpisodesHeader) "EPISODES" else "CHAPTERS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(
                                        start = 20.dp,
                                        end = 16.dp,
                                        top = 12.dp,
                                        bottom = 12.dp
                                    )
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = WindowInsets.navigationBars.asPaddingValues()
                        ) {
                            if (isPodcast && uiState.episodes.isNotEmpty()) {
                                item {
                                    EpisodeList(
                                        episodes = uiState.episodes,
                                        onEpisodeClick = { /* Details */ },
                                        onEpisodePlay = { episode ->
                                            onNavigateToPlayer(viewModel.itemId, episode.id)
                                        },
                                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                                    )
                                }
                            } else if (!isPodcast && uiState.chapters.isNotEmpty()) {
                                item {
                                    ChapterList(
                                        chapters = uiState.chapters,
                                        currentPosition = progress?.currentTime,
                                        onChapterClick = { chapter ->
                                            onNavigateToPlayer(viewModel.itemId, null)
                                        },
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                            } else {
                                item { Spacer(modifier = Modifier.padding(32.dp)) }
                            }
                        }
                    }
                }
            }

            uiState.error != null -> {
                Text(
                    text = "Failed to load item",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        AnimatedVisibility(
            visible = uiState.error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = uiState.error ?: "",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}