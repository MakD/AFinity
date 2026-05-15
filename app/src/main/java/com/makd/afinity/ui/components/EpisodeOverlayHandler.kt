@file:UnstableApi

package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.player.PlayerLauncher
import kotlinx.coroutines.delay

@Composable
fun EpisodeOverlayHandler(
    selectedEpisode: AfinityEpisode?,
    watchlistStatus: Boolean,
    downloadInfo: DownloadInfo?,
    canDownload: Boolean,
    onClearSelection: () -> Unit,
    onToggleFavorite: (AfinityEpisode) -> Unit,
    onToggleWatchlist: (AfinityEpisode) -> Unit,
    onToggleWatched: (AfinityEpisode) -> Unit,
    onDownloadClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onNavigateToSeries: (seriesId: String) -> Unit,
) {
    val context = LocalContext.current
    var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }

    selectedEpisode?.let { episode ->
        EpisodeDetailOverlay(
            episode = episode,
            isInWatchlist = watchlistStatus,
            downloadInfo = downloadInfo,
            canDownload = canDownload,
            onDismiss = {
                onClearSelection()
                pendingNavigationSeriesId = null
            },
            onPlayClick = { episodeToPlay, selection ->
                onClearSelection()
                PlayerLauncher.launch(
                    context = context,
                    itemId = episodeToPlay.id,
                    mediaSourceId = selection.mediaSourceId,
                    audioStreamIndex = selection.audioStreamIndex,
                    subtitleStreamIndex = selection.subtitleStreamIndex,
                    startPositionMs = selection.startPositionMs,
                )
            },
            onToggleFavorite = { onToggleFavorite(episode) },
            onToggleWatchlist = { onToggleWatchlist(episode) },
            onToggleWatched = { onToggleWatched(episode) },
            onDownloadClick = onDownloadClick,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onCancelDownload = onCancelDownload,
            onGoToSeries = {
                onClearSelection()
                pendingNavigationSeriesId = episode.seriesId?.toString()
            },
        )
    }

    LaunchedEffect(selectedEpisode, pendingNavigationSeriesId) {
        if (selectedEpisode == null && pendingNavigationSeriesId != null) {
            delay(300)
            onNavigateToSeries(pendingNavigationSeriesId!!)
            pendingNavigationSeriesId = null
        }
    }
}