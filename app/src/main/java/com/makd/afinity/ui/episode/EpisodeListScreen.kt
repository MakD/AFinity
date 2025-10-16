package com.makd.afinity.ui.episode

/*import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.navigation.Destination
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun EpisodeListScreen(
    onBackClick: () -> Unit,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: EpisodeListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val episodesPagingDataFlow by viewModel.episodesPagingData.collectAsStateWithLifecycle()
    val lazyEpisodeItems = episodesPagingDataFlow.collectAsLazyPagingItems()
    val coroutineScope = rememberCoroutineScope()

    uiState.season?.let { season ->
        EpisodeListContent(
            season = season,
            lazyEpisodeItems = lazyEpisodeItems,
            specialFeatures = uiState.specialFeatures,
            isLoading = uiState.isLoading,
            onBackClick = onBackClick,
            onEpisodeClick = { episode ->
                coroutineScope.launch {
                    try {
                        Timber.d("Episode clicked: ${episode.name}, loading full details...")

                        val fullEpisode = viewModel.getFullEpisodeDetails(episode.id)

                        if (fullEpisode == null) {
                            Timber.e("Failed to load full episode details for: ${episode.name}")
                            return@launch
                        }

                        if (fullEpisode.sources.isEmpty()) {
                            Timber.w("Episode ${fullEpisode.name} has no media sources")
                            return@launch
                        }

                        val mediaSourceId = fullEpisode.sources.firstOrNull()?.id ?: ""
                        val startPositionMs = if (fullEpisode.playbackPositionTicks > 0) {
                            fullEpisode.playbackPositionTicks / 10000
                        } else {
                            0L
                        }

                        com.makd.afinity.ui.player.PlayerLauncher.launch(
                            context = navController.context,
                            itemId = fullEpisode.id,
                            mediaSourceId = mediaSourceId,
                            audioStreamIndex = null,
                            subtitleStreamIndex = null,
                            startPositionMs = startPositionMs
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load episode for playback: ${episode.name}")
                    }
                }
            },
            onSpecialFeatureClick = { specialFeature ->
                val route = Destination.createItemDetailRoute(specialFeature.id.toString())
                navController.navigate(route)
            },
            navController = navController
        )
    } ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val errorMessage = uiState.error
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}*/