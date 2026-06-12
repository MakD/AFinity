@file:UnstableApi

package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.storage.StorageVolumeInfo
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.item.components.QualitySelectionDialog
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.util.rememberItemDownloadDelegate
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
    onNavigateToSeries: (seriesId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadDelegate = rememberItemDownloadDelegate()
    var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var volumes by remember { mutableStateOf<List<StorageVolumeInfo>>(emptyList()) }
    var selectedVolumeId by remember { mutableStateOf<String?>(null) }

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
            onDownloadClick = {
                downloadDelegate.onDownloadClick(scope, episode) {
                    volumes = emptyList()
                    selectedVolumeId = null
                    showQualityDialog = true
                }
            },
            onPauseDownload = { downloadDelegate.pauseDownload(scope, downloadInfo) },
            onResumeDownload = { downloadDelegate.resumeDownload(scope, downloadInfo) },
            onCancelDownload = { downloadDelegate.cancelDownload(scope, downloadInfo) },
            onDownloadLongClick = {
                downloadDelegate.onDownloadLongClick(scope, episode) {
                    loadedVolumes,
                    defaultVolumeId ->
                    volumes = loadedVolumes
                    selectedVolumeId = defaultVolumeId
                    showQualityDialog = true
                }
            },
            onGoToSeries = {
                onClearSelection()
                pendingNavigationSeriesId = episode.seriesId?.toString()
            },
        )
    }

    if (showQualityDialog) {
        val remoteSources =
            selectedEpisode?.sources?.filter { it.type == AfinitySourceType.REMOTE } ?: emptyList()
        if (remoteSources.isNotEmpty()) {
            QualitySelectionDialog(
                sources = remoteSources,
                onSourceSelected = {},
                onDismiss = { showQualityDialog = false },
                volumes = volumes,
                selectedVolumeId = selectedVolumeId,
                onVolumeSelected = { selectedVolumeId = it },
                onConfirm = { source, volumeId ->
                    downloadDelegate.onQualitySelected(
                        scope = scope,
                        item = selectedEpisode,
                        sourceId = source.id,
                        volumeId = volumeId,
                        hideQualityDialog = { showQualityDialog = false },
                    )
                },
            )
        }
    }

    LaunchedEffect(selectedEpisode, pendingNavigationSeriesId) {
        if (selectedEpisode == null && pendingNavigationSeriesId != null) {
            delay(300)
            onNavigateToSeries(pendingNavigationSeriesId!!)
            pendingNavigationSeriesId = null
        }
    }
}