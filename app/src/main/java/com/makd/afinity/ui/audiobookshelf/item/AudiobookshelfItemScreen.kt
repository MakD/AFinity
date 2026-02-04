package com.makd.afinity.ui.audiobookshelf.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.ui.audiobookshelf.item.components.ChapterList
import com.makd.afinity.ui.audiobookshelf.item.components.EpisodeList
import com.makd.afinity.ui.audiobookshelf.item.components.ItemHeader

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.media?.metadata?.title ?: "Item") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                item != null -> {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ItemHeader(
                            item = item!!,
                            progress = progress,
                            serverUrl = config?.serverUrl,
                            onPlay = {
                                onNavigateToPlayer(viewModel.itemId, null)
                            }
                        )

                        if (isPodcast && uiState.episodes.isNotEmpty()) {
                            EpisodeList(
                                episodes = uiState.episodes,
                                onEpisodeClick = { episode ->
                                    // Could show episode details
                                },
                                onEpisodePlay = { episode ->
                                    onNavigateToPlayer(viewModel.itemId, episode.id)
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        } else if (!isPodcast && uiState.chapters.isNotEmpty()) {
                            ChapterList(
                                chapters = uiState.chapters,
                                currentPosition = progress?.currentTime,
                                onChapterClick = { chapter ->
                                    // Could seek to chapter
                                    onNavigateToPlayer(viewModel.itemId, null)
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        Spacer(
                            modifier = Modifier.padding(bottom = 32.dp)
                        )
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

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
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
}
