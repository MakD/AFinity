package com.makd.afinity.ui.episode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.episode.EpisodeListContent
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

    uiState.season?.let { season ->
        EpisodeListContent(
            season = season,
            lazyEpisodeItems = lazyEpisodeItems,
            specialFeatures = uiState.specialFeatures,
            isLoading = uiState.isLoading,
            onBackClick = onBackClick,
            onEpisodeClick = { episode ->
                if (episode.sources.isEmpty()) {
                    Timber.w("Episode ${episode.name} has no media sources")
                    return@EpisodeListContent
                }
                val mediaSourceId = episode.sources.firstOrNull()?.id ?: ""
                val startPositionMs = if (episode.playbackPositionTicks > 0) {
                    episode.playbackPositionTicks / 10000
                } else {
                    0L
                }
                val route = Destination.createPlayerRoute(
                    itemId = episode.id.toString(),
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    startPositionMs = startPositionMs
                )
                navController.navigate(route)
            },
            onSpecialFeatureClick = { specialFeature ->
                val route = Destination.createItemDetailRoute(specialFeature.id.toString())
                navController.navigate(route)
            }
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
}