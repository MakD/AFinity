package com.makd.afinity.ui.settings.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.DownloadListItemRow
import com.makd.afinity.ui.components.EmptyState
import com.makd.afinity.ui.downloads.DownloadsViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToAbsItem: (libraryItemId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOffline by
        viewModel.offlineModeManager.isOffline.collectAsStateWithLifecycle(initialValue = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val playerOffset = LocalPlayerOffset.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (uiState.pendingUnavailableDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUnavailableDelete,
            title = { Text(stringResource(R.string.download_unavailable_delete_title)) },
            text = { Text(stringResource(R.string.download_unavailable_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemoveUnavailableDelete) {
                    Text(stringResource(R.string.download_unavailable_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUnavailableDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pref_downloads_and_storage),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, snackbar = { AFinitySnackbar(it) })
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val customPadding =
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = max(innerPadding.calculateBottomPadding(), playerOffset),
            )
        val absBooks =
            remember(uiState.absCompletedDownloads) {
                uiState.absCompletedDownloads.filter { it.episodeId == null }
            }
        val absPodcastGroups =
            remember(uiState.absCompletedDownloads) {
                uiState.absCompletedDownloads
                    .filter { it.episodeId != null }
                    .groupBy { it.libraryItemId }
            }
        val absUniqueItemCount = absBooks.size + absPodcastGroups.size

        val musicDownloads =
            remember(uiState.completedDownloads) {
                uiState.completedDownloads.filter { it.itemType == "Audio" }
            }
        val videoDownloads =
            remember(uiState.completedDownloads) {
                uiState.completedDownloads.filter { it.itemType != "Audio" }
            }
        val musicAlbumGroups =
            remember(musicDownloads) {
                musicDownloads.filter { it.seriesId != null }.groupBy { it.seriesId.orEmpty() }
            }
        val standaloneMusicTracks =
            remember(musicDownloads) { musicDownloads.filter { it.seriesId == null } }
        val musicUniqueItemCount = musicAlbumGroups.size + standaloneMusicTracks.size

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = customPadding.calculateTopPadding(),
                    start = customPadding.calculateStartPadding(layoutDirection),
                    end = customPadding.calculateEndPadding(layoutDirection),
                    bottom = customPadding.calculateBottomPadding() + 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                StatusHub(
                    totalStorageUsed = uiState.totalStorageUsed,
                    totalStorageUsedAllServers = uiState.totalStorageUsedAllServers,
                    downloadCount =
                        uiState.activeDownloads.size +
                            videoDownloads.size +
                            uiState.absActiveDownloads.size +
                            absUniqueItemCount +
                            musicUniqueItemCount,
                    isOffline = isOffline,
                    wifiOnly = uiState.downloadOverWifiOnly,
                    maxConcurrentDownloads = uiState.maxConcurrentDownloads,
                    deviceStats =
                        if (uiState.volumeStorageStats.size > 1)
                            aggregateDeviceStats(uiState.volumeStorageStats)
                        else uiState.deviceStorageStats,
                    onWifiOnlyChanged = viewModel::setDownloadOverWifiOnly,
                    onMaxDownloadsChanged = viewModel::setMaxConcurrentDownloads,
                    formatSize = viewModel::formatStorageSize,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (uiState.volumeStorageStats.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(
                        title = stringResource(R.string.section_storage_locations),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                item {
                    StorageLocationsCard(
                        stats = uiState.volumeStorageStats,
                        defaultVolumeId = uiState.defaultStorageVolumeId,
                        onSetDefault = viewModel::setDefaultStorageVolume,
                        formatSize = viewModel::formatStorageSize,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(
                    title = stringResource(R.string.section_storage_cache),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            item {
                ImageCacheSettingsCard(
                    isCacheEnabled = uiState.isImageCacheEnabled,
                    cacheSizeMb = uiState.imageCacheSizeMb.toFloat(),
                    onCacheEnabledChange = viewModel::setImageCacheEnabled,
                    onCacheSizeChange = { viewModel.setImageCacheSizeMb(it.toInt()) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            val allActiveCount = uiState.activeDownloads.size + uiState.absActiveDownloads.size
            if (allActiveCount > 0) {
                item {
                    SectionHeader(
                        title =
                            stringResource(R.string.active_downloads_header_fmt, allActiveCount),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                items(uiState.activeDownloads.reversed(), key = { "jf_active_${it.id}" }) { download
                    ->
                    ActiveDownloadCard(
                        download = download,
                        speedBps = uiState.downloadSpeeds[download.id],
                        onPause = viewModel::pauseDownload,
                        onResume = viewModel::resumeDownload,
                        onCancel = viewModel::cancelDownload,
                        formatSize = viewModel::formatStorageSize,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                items(uiState.absActiveDownloads.reversed(), key = { "abs_active_${it.id}" }) {
                    download ->
                    AbsActiveDownloadCard(
                        download = download,
                        speedBps = uiState.absDownloadSpeeds[download.id],
                        onCancel = viewModel::cancelAbsDownload,
                        formatSize = viewModel::formatStorageSize,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            val allCompletedCount = videoDownloads.size + absUniqueItemCount + musicUniqueItemCount
            if (allCompletedCount > 0) {
                item {
                    SectionHeader(
                        title =
                            stringResource(
                                R.string.completed_downloads_header_fmt,
                                allCompletedCount,
                            ),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }

                if (videoDownloads.isNotEmpty()) {
                    if (absUniqueItemCount > 0 || musicUniqueItemCount > 0) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.section_videos),
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(videoDownloads, key = { "jf_completed_${it.id}" }) { download ->
                        val unavailableVolumeIds =
                            uiState.volumeStorageStats
                                .filter { !it.isAvailable }
                                .map { it.volumeId }
                                .toSet()
                        val isVolumeAvailable = download.storageVolumeId !in unavailableVolumeIds
                        CompletedDownloadRow(
                            download = download,
                            volumeLabel =
                                if (uiState.volumeStorageStats.size > 1)
                                    uiState.volumeStorageStats
                                        .firstOrNull { it.volumeId == download.storageVolumeId }
                                        ?.displayName
                                else null,
                            isVolumeAvailable = isVolumeAvailable,
                            onDelete = viewModel::deleteDownload,
                            formatSize = viewModel::formatStorageSize,
                        )
                    }
                }

                if (absBooks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.section_download_audiobooks),
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                        )
                    }
                    items(absBooks, key = { "abs_completed_book_${it.id}" }) { download ->
                        AbsCompletedDownloadRow(
                            download = download,
                            onClick = { onNavigateToAbsItem(download.libraryItemId) },
                            onDelete = viewModel::deleteAbsDownload,
                            formatSize = viewModel::formatStorageSize,
                        )
                    }
                }

                if (absPodcastGroups.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.section_download_podcasts),
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                        )
                    }
                    absPodcastGroups.forEach { (libraryItemId, episodes) ->
                        item(key = "abs_podcast_$libraryItemId") {
                            AbsPodcastGroupRow(
                                libraryItemId = libraryItemId,
                                episodes = episodes,
                                onClick = { onNavigateToAbsItem(libraryItemId) },
                                onDelete = { viewModel.deleteAbsPodcast(libraryItemId) },
                                formatSize = viewModel::formatStorageSize,
                            )
                        }
                    }
                }

                if (musicAlbumGroups.isNotEmpty() || standaloneMusicTracks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.section_download_music),
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                        )
                    }
                    musicAlbumGroups.forEach { (albumSeriesId, tracks) ->
                        item(key = "music_album_$albumSeriesId") {
                            MusicAlbumGroupRow(
                                tracks = tracks,
                                onDelete = { viewModel.deleteMusicAlbum(albumSeriesId) },
                                formatSize = viewModel::formatStorageSize,
                            )
                        }
                    }
                    items(standaloneMusicTracks, key = { "music_track_${it.id}" }) { download ->
                        MusicTrackRow(
                            download = download,
                            onDelete = viewModel::deleteDownload,
                            formatSize = viewModel::formatStorageSize,
                        )
                    }
                }
            }

            if (allActiveCount == 0 && allCompletedCount == 0) {
                item { EmptyDownloadsState() }
            }
        }
    }
}

@Composable
fun StatusHub(
    totalStorageUsed: Long,
    totalStorageUsedAllServers: Long,
    downloadCount: Int,
    isOffline: Boolean,
    wifiOnly: Boolean,
    maxConcurrentDownloads: Int,
    deviceStats: DownloadsViewModel.DeviceStorageStats?,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onMaxDownloadsChanged: (Int) -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeWidth = 6.dp,
                    )

                    val progress = deviceStats?.usagePercentage ?: 0f

                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color =
                            if (progress > 0.9f) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round,
                    )

                    Icon(
                        painter = painterResource(id = R.drawable.ic_database),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_this_server),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatSize(totalStorageUsed),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                letterSpacing = (-1).sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (totalStorageUsedAllServers > totalStorageUsed) {
                        Text(
                            text =
                                stringResource(
                                    R.string.storage_all_servers_fmt,
                                    formatSize(totalStorageUsedAllServers),
                                ),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val freeSpaceText = deviceStats?.freeBytes?.let { formatSize(it) } ?: "..."
                    Text(
                        text =
                            stringResource(
                                R.string.storage_free_on_device_fmt,
                                freeSpaceText,
                            ),
                        style =
                            MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color =
                            if ((deviceStats?.usagePercentage ?: 0f) > 0.9f)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.pref_max_concurrent_downloads),
                    style =
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(36.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (maxConcurrentDownloads > 1)
                                    onMaxDownloadsChanged(maxConcurrentDownloads - 1)
                            },
                            enabled = maxConcurrentDownloads > 1,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_remove),
                                contentDescription = stringResource(R.string.cd_decrease_limit),
                                tint =
                                    if (maxConcurrentDownloads > 1)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.5f
                                        ),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "$maxConcurrentDownloads",
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(36.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (maxConcurrentDownloads < 3)
                                    onMaxDownloadsChanged(maxConcurrentDownloads + 1)
                            },
                            enabled = maxConcurrentDownloads < 3,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = stringResource(R.string.cd_increase_limit),
                                tint =
                                    if (maxConcurrentDownloads < 3)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.5f
                                        ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.pref_download_wifi_only_title),
                    style =
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChanged,
                    modifier = Modifier.scale(0.8f),
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(
    download: DownloadInfo,
    speedBps: Long?,
    onPause: (UUID) -> Unit,
    onResume: (UUID) -> Unit,
    onCancel: (UUID) -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val isEpisode = download.itemType.equals("Episode", ignoreCase = true)
    val imageRatio = if (isEpisode) 16f / 9f else 2f / 3f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val imgWidth = if (isEpisode) 120.dp else 80.dp
            AsyncImage(
                imageUrl = download.imageUrl,
                contentDescription = download.itemName,
                modifier =
                    Modifier.width(imgWidth)
                        .aspectRatio(imageRatio)
                        .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                targetWidth = imgWidth,
                targetHeight = imgWidth / imageRatio,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.itemName,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val subtitle =
                            if (isEpisode && !download.seriesName.isNullOrBlank()) {
                                download.seriesName
                            } else if (!isEpisode && !download.releaseYear.isNullOrBlank()) {
                                download.releaseYear
                            } else {
                                download.sourceName
                            }

                        Text(
                            text = subtitle ?: download.sourceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (
                            download.status == DownloadStatus.DOWNLOADING ||
                                download.status == DownloadStatus.QUEUED
                        ) {
                            IconButton(
                                onClick = { onPause(download.id) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(id = R.drawable.ic_player_pause_filled),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else if (
                            download.status == DownloadStatus.PAUSED ||
                                download.status == DownloadStatus.FAILED
                        ) {
                            IconButton(
                                onClick = { onResume(download.id) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(id = R.drawable.ic_player_play_filled),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        IconButton(
                            onClick = { onCancel(download.id) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cancel),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color =
                        if (download.status == DownloadStatus.FAILED)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val statusText =
                        when (download.status) {
                            DownloadStatus.QUEUED ->
                                stringResource(R.string.download_status_queued).uppercase()
                            DownloadStatus.DOWNLOADING ->
                                stringResource(R.string.download_status_downloading).uppercase()
                            DownloadStatus.PAUSED ->
                                stringResource(R.string.download_status_paused).uppercase()
                            DownloadStatus.FAILED ->
                                stringResource(
                                        R.string.download_status_failed_fmt,
                                        download.error ?: "",
                                    )
                                    .uppercase()
                            else -> ""
                        }

                    Text(
                        text = statusText,
                        style =
                            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color =
                            if (download.status == DownloadStatus.FAILED)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    val sizeText =
                        "${formatSize(download.bytesDownloaded)} / ${formatSize(download.totalBytes)}"
                    val speedText =
                        if (
                            download.status == DownloadStatus.DOWNLOADING &&
                                speedBps != null &&
                                speedBps > 0
                        ) {
                            " • ${formatSize(speedBps)}/s"
                        } else {
                            ""
                        }

                    Text(
                        text = sizeText + speedText,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadRow(
    download: DownloadInfo,
    volumeLabel: String? = null,
    isVolumeAvailable: Boolean = true,
    onDelete: (UUID) -> Unit,
    formatSize: (Long) -> String,
) {
    val isEpisode = download.itemType.equals("Episode", ignoreCase = true)

    val runtimeMinutes = (download.runtimeTicks ?: 0L) / 10000000 / 60
    val runtimeStr = if (runtimeMinutes > 0) "${runtimeMinutes}m • " else ""

    val subtitleText = buildString {
        if (isEpisode) {
            if (!download.seriesName.isNullOrBlank()) append("${download.seriesName} • ")
            if (download.seasonNumber != null && download.episodeNumber != null) {
                append(
                    "S${download.seasonNumber.toString().padStart(2, '0')}:E${download.episodeNumber.toString().padStart(2, '0')} • "
                )
            }
        } else {
            if (!download.releaseYear.isNullOrBlank()) append("${download.releaseYear} • ")
        }
        append(runtimeStr)
        append(formatSize(download.totalBytes))
    }

    DownloadListItemRow(
        imageUrl =
            if (isEpisode) download.seriesImageUrl ?: download.imageUrl else download.imageUrl,
        title = download.itemName,
        aspectRatio = 2f / 3f,
        imageAlpha = if (isVolumeAvailable) 1f else 0.4f,
        onDelete = { onDelete(download.id) },
    ) {
        Column {
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isVolumeAvailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_folder),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text =
                            stringResource(
                                R.string.download_unavailable_label,
                                volumeLabel ?: stringResource(R.string.storage_unavailable_volume),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (volumeLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_folder),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = volumeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
fun StorageLocationsCard(
    stats: List<DownloadsViewModel.VolumeStorageStats>,
    defaultVolumeId: String,
    onSetDefault: (String) -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            stats.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                VolumeStorageRow(
                    stats = item,
                    isDefault = item.isAvailable && item.volumeId == defaultVolumeId,
                    onSetDefault = { if (item.isAvailable) onSetDefault(item.volumeId) },
                    formatSize = formatSize,
                )
            }
        }
    }
}

@Composable
private fun VolumeStorageRow(
    stats: DownloadsViewModel.VolumeStorageStats,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    formatSize: (Long) -> String,
) {
    val device = stats.device
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = stats.isAvailable, onClick = onSetDefault)
                .background(
                    if (isDefault) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    else Color.Transparent
                )
                .padding(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeWidth = 5.dp,
            )
            if (device != null) {
                val progress = device.usagePercentage
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color =
                        if (progress > 0.9f) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    strokeWidth = 5.dp,
                    strokeCap = StrokeCap.Round,
                )
            }
            Icon(
                painter =
                    painterResource(
                        id = if (stats.isRemovable) R.drawable.ic_folder else R.drawable.ic_database
                    ),
                contentDescription = null,
                tint =
                    if (stats.isAvailable) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stats.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color =
                        if (stats.isAvailable) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (!stats.isAvailable) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = stringResource(R.string.storage_unavailable_badge),
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                } else if (isDefault) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = stringResource(R.string.storage_default_badge),
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.storage_set_as_default),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text =
                    if (device != null)
                        stringResource(
                            R.string.storage_usage_combined_fmt,
                            formatSize(stats.usedThisServer),
                            formatSize(device.freeBytes),
                            formatSize(device.totalBytes),
                        )
                    else
                        stringResource(
                            R.string.storage_unavailable_used_fmt,
                            formatSize(stats.usedThisServer),
                        ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun aggregateDeviceStats(
    stats: List<DownloadsViewModel.VolumeStorageStats>
): DownloadsViewModel.DeviceStorageStats? {
    val devices = stats.mapNotNull { it.device }
    if (devices.isEmpty()) return null
    val totalBytes = devices.sumOf { it.totalBytes }
    val freeBytes = devices.sumOf { it.freeBytes }
    val usedBytes = totalBytes - freeBytes
    return DownloadsViewModel.DeviceStorageStats(
        totalBytes = totalBytes,
        freeBytes = freeBytes,
        usedBytes = usedBytes,
        usagePercentage = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f,
    )
}

@Composable
fun EmptyDownloadsState() {
    EmptyState(
        icon = painterResource(id = R.drawable.ic_download),
        title = stringResource(R.string.empty_downloads_title),
        message = stringResource(R.string.empty_downloads_message),
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        iconSize = 48.dp,
        badgeAlpha = 0.5f,
    )
}

@Composable
fun AbsActiveDownloadCard(
    download: AbsDownloadInfo,
    speedBps: Long?,
    onCancel: (UUID) -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                imageUrl = download.coverUrl,
                contentDescription = download.title,
                modifier = Modifier.width(64.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                targetWidth = 64.dp,
                targetHeight = 64.dp,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = download.title,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!download.authorName.isNullOrBlank()) {
                            Text(
                                text = download.authorName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    IconButton(
                        onClick = { onCancel(download.id) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cancel),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color =
                        if (download.status == AbsDownloadStatus.FAILED)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val statusText =
                        when (download.status) {
                            AbsDownloadStatus.QUEUED -> "QUEUED"
                            AbsDownloadStatus.DOWNLOADING ->
                                "TRACK ${download.tracksDownloaded}/${download.tracksTotal}"
                            AbsDownloadStatus.FAILED -> "FAILED"
                            else -> ""
                        }
                    Text(
                        text = statusText,
                        style =
                            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color =
                            if (download.status == AbsDownloadStatus.FAILED)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    val speedText =
                        if (
                            download.status == AbsDownloadStatus.DOWNLOADING &&
                                speedBps != null &&
                                speedBps > 0
                        ) {
                            " • ${formatSize(speedBps)}/s"
                        } else {
                            ""
                        }

                    Text(
                        text = formatSize(download.bytesDownloaded) + speedText,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun AbsCompletedDownloadRow(
    download: AbsDownloadInfo,
    onClick: () -> Unit = {},
    onDelete: (UUID) -> Unit,
    formatSize: (Long) -> String,
) {
    val durationMinutes = (download.duration / 60).toInt()
    val durationStr =
        when {
            durationMinutes >= 60 -> "${durationMinutes / 60}h ${durationMinutes % 60}m"
            durationMinutes > 0 -> "${durationMinutes}m"
            else -> ""
        }
    val subtitleText = buildString {
        if (!download.authorName.isNullOrBlank()) append("${download.authorName} • ")
        if (durationStr.isNotEmpty()) append("$durationStr • ")
        append(formatSize(download.bytesDownloaded))
    }

    DownloadListItemRow(
        imageUrl = download.coverUrl,
        title = download.title,
        onClick = onClick,
        onDelete = { onDelete(download.id) },
    ) {
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AbsPodcastGroupRow(
    libraryItemId: String,
    episodes: List<AbsDownloadInfo>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    formatSize: (Long) -> String,
) {
    val first = episodes.first()
    val podcastName = first.authorName?.takeIf { it.isNotBlank() } ?: first.title
    val totalBytes = episodes.sumOf { it.bytesDownloaded }
    val count = episodes.size
    val subtitleText =
        "$count episode${if (count > 1) "s" else ""} \u00B7 ${formatSize(totalBytes)}"

    DownloadListItemRow(
        imageUrl = first.coverUrl,
        title = podcastName,
        onClick = onClick,
        onDelete = onDelete,
    ) {
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ImageCacheSettingsCard(
    isCacheEnabled: Boolean,
    cacheSizeMb: Float,
    onCacheEnabledChange: (Boolean) -> Unit,
    onCacheSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = stringResource(R.string.pref_image_caching_title),
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.pref_image_caching_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(
                    checked = isCacheEnabled,
                    onCheckedChange = onCacheEnabledChange,
                    modifier = Modifier.scale(0.8f),
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                )
            }

            Text(
                text = stringResource(R.string.pref_image_cache_restart_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )

            AnimatedVisibility(
                visible = isCacheEnabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.pref_image_cache_max_disk),
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            val formattedSize =
                                if (cacheSizeMb >= 1024f) {
                                    String.format(
                                            LocalLocale.current.platformLocale,
                                            "%.1f GB",
                                            cacheSizeMb / 1024f,
                                        )
                                        .replace(".0", "")
                                        .replace(",0", "")
                                } else {
                                    "${cacheSizeMb.toInt()} MB"
                                }

                            Text(
                                text = formattedSize,
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = cacheSizeMb,
                        onValueChange = onCacheSizeChange,
                        valueRange = 256f..2048f,
                        steps = 6,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor =
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                activeTickColor =
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                inactiveTickColor =
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun MusicAlbumGroupRow(
    tracks: List<DownloadInfo>,
    onDelete: () -> Unit,
    formatSize: (Long) -> String,
) {
    val first = tracks.first()
    val albumName = first.seriesName?.takeIf { it.isNotBlank() } ?: first.itemName
    val totalBytes = tracks.sumOf { it.totalBytes }
    val count = tracks.size
    val subtitleText = "$count track${if (count > 1) "s" else ""} · ${formatSize(totalBytes)}"

    DownloadListItemRow(imageUrl = first.imageUrl, title = albumName, onDelete = onDelete) {
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MusicTrackRow(
    download: DownloadInfo,
    onDelete: (UUID) -> Unit,
    formatSize: (Long) -> String,
) {
    val subtitleText = buildString {
        if (!download.seriesName.isNullOrBlank()) append("${download.seriesName} · ")
        append(formatSize(download.totalBytes))
    }

    DownloadListItemRow(
        imageUrl = download.imageUrl,
        title = download.itemName,
        onDelete = { onDelete(download.id) },
    ) {
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
