package com.makd.afinity.ui.settings.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.repository.CacheSection
import com.makd.afinity.data.repository.CacheUsage
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinitySlider
import com.makd.afinity.ui.components.AfinitySwitch
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.DownloadListItemRow
import com.makd.afinity.ui.components.EmptyState
import com.makd.afinity.ui.downloads.DownloadCatalogEntry
import com.makd.afinity.ui.downloads.DownloadCatalogRef
import com.makd.afinity.ui.downloads.DownloadCategory
import com.makd.afinity.ui.downloads.DownloadsViewModel
import com.makd.afinity.ui.downloads.absChildrenOf
import com.makd.afinity.ui.downloads.components.DownloadStorageStrip
import com.makd.afinity.ui.downloads.components.downloadCatalogSections
import com.makd.afinity.ui.downloads.components.downloadCategoryLabel
import com.makd.afinity.ui.downloads.jellyfinChildrenOf
import java.util.UUID
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToAbsItem: (libraryItemId: String) -> Unit = {},
    onStorageSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerOffset = LocalPlayerOffset.current

    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    var selectedEntry by remember { mutableStateOf<DownloadCatalogEntry?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var pendingEntryDelete by remember { mutableStateOf<DownloadCatalogEntry?>(null) }

    var selectedKeyList by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selectedKeys = remember(selectedKeyList) { selectedKeyList.toSet() }
    var selectionMode by rememberSaveable { mutableStateOf(false) }

    val selectedEntries =
        remember(catalog, selectedKeys) { catalog.filter { it.key in selectedKeys } }
    val selectedBytes = remember(selectedEntries) { selectedEntries.sumOf { it.sizeBytes } }

    fun exitSelection() {
        selectionMode = false
        selectedKeyList = emptyList()
    }

    LaunchedEffect(catalog) {
        if (selectionMode) {
            val stillPresent = catalog.map { it.key }.toSet()
            selectedKeyList = selectedKeyList.filter { it in stillPresent }
        }
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    selectedEntry?.let { entry ->
        val jellyfinChildren =
            remember(entry.key, uiState.completedDownloads) {
                jellyfinChildrenOf(entry, uiState.completedDownloads)
            }
        val absChildren =
            remember(entry.key, uiState.absCompletedDownloads) {
                absChildrenOf(entry, uiState.absCompletedDownloads)
            }

        DownloadGroupSheet(
            entry = entry,
            jellyfinChildren = jellyfinChildren,
            absChildren = absChildren,
            volumeStats = uiState.volumeStorageStats,
            formatSize = viewModel::formatStorageSize,
            onDeleteAll = { pendingEntryDelete = entry },
            onDeleteChild = viewModel::deleteDownload,
            onDeleteAbsChild = viewModel::deleteAbsDownload,
            onOpenAbsItem = { libraryItemId ->
                selectedEntry = null
                onNavigateToAbsItem(libraryItemId)
            },
            onDismiss = { selectedEntry = null },
        )
    }

    if (pendingBulkDelete) {
        DeleteDownloadsDialog(
            title =
                pluralStringResource(
                    R.plurals.downloads_delete_selected_fmt,
                    selectedEntries.size,
                    selectedEntries.size,
                ),
            message =
                stringResource(
                    R.string.downloads_delete_selected_message_fmt,
                    viewModel.formatStorageSize(selectedBytes),
                ),
            onConfirm = {
                viewModel.deleteCatalogEntries(selectedEntries)
                pendingBulkDelete = false
                exitSelection()
            },
            onDismiss = { pendingBulkDelete = false },
        )
    }

    pendingEntryDelete?.let { entry ->
        DeleteDownloadsDialog(
            title = entry.title,
            message =
                stringResource(
                    R.string.downloads_delete_selected_message_fmt,
                    viewModel.formatStorageSize(entry.sizeBytes),
                ),
            onConfirm = {
                viewModel.deleteCatalogEntry(entry)
                pendingEntryDelete = null
                selectedEntry = null
            },
            onDismiss = { pendingEntryDelete = null },
        )
    }

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
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text =
                                stringResource(
                                    R.string.downloads_selected_fmt,
                                    selectedEntries.size,
                                ),
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription =
                                    stringResource(R.string.cd_downloads_exit_selection),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { selectedKeyList = catalog.map { it.key } },
                            enabled = selectedEntries.size < catalog.size,
                        ) {
                            Text(stringResource(R.string.downloads_select_all))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                )
            } else {
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
                    actions = {
                        IconButton(onClick = onStorageSettingsClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.cd_downloads_settings),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                DownloadSelectionBar(
                    count = selectedEntries.size,
                    totalBytes = selectedBytes,
                    formatSize = viewModel::formatStorageSize,
                    onDelete = { pendingBulkDelete = true },
                )
            }
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
        val deviceStats =
            if (uiState.volumeStorageStats.size > 1)
                aggregateDeviceStats(uiState.volumeStorageStats)
            else uiState.deviceStorageStats

        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = customPadding.calculateTopPadding(),
                    start = customPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = customPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = customPadding.calculateBottomPadding() + 32.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "storage_strip", span = { GridItemSpan(maxLineSpan) }) {
                DownloadStorageStrip(
                    downloadsBytes = uiState.totalStorageUsedAllServers,
                    deviceUsedBytes = deviceStats?.usedBytes ?: 0L,
                    deviceTotalBytes = deviceStats?.totalBytes ?: 0L,
                    freeBytes = deviceStats?.freeBytes ?: 0L,
                    formatSize = viewModel::formatStorageSize,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (availableCategories.size > 1) {
                item(key = "category_chips", span = { GridItemSpan(maxLineSpan) }) {
                    DownloadFilterChips(
                        categories = availableCategories,
                        selected = categoryFilter,
                        onSelect = viewModel::setCategoryFilter,
                    )
                }
            }

            val allActiveCount = uiState.activeDownloads.size + uiState.absActiveDownloads.size
            if (allActiveCount > 0) {
                item(key = "active_header", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        title =
                            stringResource(R.string.active_downloads_header_fmt, allActiveCount),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                items(
                    uiState.activeDownloads.reversed(),
                    key = { "jf_active_${it.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { download ->
                    ActiveDownloadCard(
                        download = download,
                        speedBps = uiState.downloadSpeeds[download.id],
                        onPause = viewModel::pauseDownload,
                        onResume = viewModel::resumeDownload,
                        onCancel = viewModel::cancelDownload,
                        formatSize = viewModel::formatStorageSize,
                    )
                }

                items(
                    uiState.absActiveDownloads.reversed(),
                    key = { "abs_active_${it.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { download ->
                    AbsActiveDownloadCard(
                        download = download,
                        speedBps = uiState.absDownloadSpeeds[download.id],
                        onCancel = viewModel::cancelAbsDownload,
                        formatSize = viewModel::formatStorageSize,
                    )
                }
            }

            val allCompletedCount = catalog.size
            if (allCompletedCount > 0) {
                downloadCatalogSections(
                    catalog = catalog,
                    formatSize = viewModel::formatStorageSize,
                    selectionMode = selectionMode,
                    selectedKeys = selectedKeys,
                    onEntryClick = { entry ->
                        if (selectionMode) {
                            selectedKeyList =
                                if (entry.key in selectedKeys) selectedKeyList - entry.key
                                else selectedKeyList + entry.key
                        } else {
                            selectedEntry = entry
                        }
                    },
                    onEntryLongClick = { entry ->
                        if (!selectionMode) {
                            selectionMode = true
                            selectedKeyList = listOf(entry.key)
                        }
                    },
                )
            }

            if (allActiveCount == 0 && allCompletedCount == 0) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyDownloadsState() }
            }
        }
    }
}

@Composable
private fun DeleteDownloadsDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun DownloadSelectionBar(
    count: Int,
    totalBytes: Long,
    formatSize: (Long) -> String,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.downloads_frees_up),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatSize(totalBytes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Button(
                onClick = onDelete,
                enabled = count > 0,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.downloads_delete_selected_fmt,
                            count,
                            count,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DownloadFilterChips(
    categories: List<DownloadCategory>,
    selected: DownloadCategory?,
    onSelect: (DownloadCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(downloadCategoryLabel(category)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadGroupSheet(
    entry: DownloadCatalogEntry,
    jellyfinChildren: List<DownloadInfo>,
    absChildren: List<AbsDownloadInfo>,
    volumeStats: List<DownloadsViewModel.VolumeStorageStats>,
    formatSize: (Long) -> String,
    onDeleteAll: () -> Unit,
    onDeleteChild: (UUID) -> Unit,
    onDeleteAbsChild: (UUID) -> Unit,
    onOpenAbsItem: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val childCount = jellyfinChildren.size + absChildren.size

    LaunchedEffect(entry.key, childCount) {
        if (entry.isGroup && childCount == 0) onDismiss()
    }

    val absLibraryItemId =
        when (val ref = entry.ref) {
            is DownloadCatalogRef.AbsBook -> ref.libraryItemId
            is DownloadCatalogRef.AbsPodcast -> ref.libraryItemId
            else -> null
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AsyncImage(
                    imageUrl = entry.imageUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.width(84.dp)
                            .aspectRatio(
                                if (entry.category == DownloadCategory.VIDEO) 2f / 3f else 1f
                            )
                            .clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text =
                            if (entry.isGroup) {
                                stringResource(
                                    R.string.downloads_group_summary_fmt,
                                    childCount,
                                    formatSize(entry.sizeBytes),
                                )
                            } else {
                                formatSize(entry.sizeBytes)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDeleteAll,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            if (entry.isGroup) stringResource(R.string.downloads_delete_all)
                            else stringResource(R.string.action_delete)
                    )
                }

                absLibraryItemId?.let { libraryItemId ->
                    OutlinedButton(onClick = { onOpenAbsItem(libraryItemId) }) {
                        Text(stringResource(R.string.downloads_open_item))
                    }
                }
            }

            if (entry.isGroup) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                val unavailableVolumeIds =
                    remember(volumeStats) {
                        volumeStats.filter { !it.isAvailable }.map { it.volumeId }.toSet()
                    }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (entry.category == DownloadCategory.MUSIC) {
                        items(jellyfinChildren, key = { "sheet_track_${it.id}" }) { download ->
                            MusicTrackRow(
                                download = download,
                                onDelete = onDeleteChild,
                                formatSize = formatSize,
                            )
                        }
                    } else {
                        items(jellyfinChildren, key = { "sheet_ep_${it.id}" }) { download ->
                            CompletedDownloadRow(
                                download = download,
                                volumeLabel =
                                    if (volumeStats.size > 1)
                                        volumeStats
                                            .firstOrNull { it.volumeId == download.storageVolumeId }
                                            ?.displayName
                                    else null,
                                isVolumeAvailable =
                                    download.storageVolumeId !in unavailableVolumeIds,
                                onDelete = onDeleteChild,
                                formatSize = formatSize,
                            )
                        }
                    }

                    items(absChildren, key = { "sheet_abs_${it.id}" }) { download ->
                        AbsCompletedDownloadRow(
                            download = download,
                            onClick = { onOpenAbsItem(download.libraryItemId) },
                            onDelete = onDeleteAbsChild,
                            formatSize = formatSize,
                        )
                    }
                }
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

    val runtimeMinutes = ceil((download.runtimeTicks ?: 0L) / 600_000_000.0).toInt()
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
                AfinitySwitch(checked = isCacheEnabled, onCheckedChange = onCacheEnabledChange)
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

                    AfinitySlider(
                        value = cacheSizeMb,
                        onValueChange = onCacheSizeChange,
                        valueRange = 256f..2048f,
                        steps = 6,
                        modifier = Modifier.height(24.dp),
                    )
                }
            }
        }
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

@Composable
fun VideoCacheSettingsCard(
    cacheSizeMb: Float,
    onCacheSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.pref_video_caching_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.pref_video_caching_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.pref_image_cache_restart_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.pref_image_cache_max_disk),
                    style =
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
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

            AfinitySlider(
                value = cacheSizeMb,
                onValueChange = onCacheSizeChange,
                valueRange = 256f..4096f,
                steps = 6,
                modifier = Modifier.height(24.dp),
            )
        }
    }
}

@Composable
internal fun CacheSectionPicker(
    usage: CacheUsage?,
    selected: Set<CacheSection>,
    enabled: Boolean,
    onToggle: (CacheSection) -> Unit,
    context: android.content.Context,
) {
    Column {
        CacheSection.entries.forEach { section ->
            val bytes = section.stores.sumOf { usage?.bytes?.get(it) ?: 0L }
            val entries = section.kinds.sumOf { usage?.entries?.get(it) ?: 0 }
            if (bytes <= 0L && entries <= 0) return@forEach

            val value =
                if (bytes > 0L) {
                    android.text.format.Formatter.formatShortFileSize(context, bytes)
                } else {
                    pluralStringResource(R.plurals.cache_entries_fmt, entries, entries)
                }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(enabled = enabled) { onToggle(section) }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = section in selected,
                    onCheckedChange = { onToggle(section) },
                    enabled = enabled,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(section.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun CacheSection.labelRes(): Int =
    when (this) {
        CacheSection.IMAGES -> R.string.cache_kind_images
        CacheSection.VIDEO -> R.string.cache_store_video
        CacheSection.NETWORK -> R.string.cache_store_network
        CacheSection.PLAYER -> R.string.cache_store_player
        CacheSection.JELLYFIN_METADATA -> R.string.cache_section_jellyfin_metadata
        CacheSection.JELLYSEERR -> R.string.cache_section_jellyseerr
        CacheSection.AUDIOBOOKSHELF -> R.string.cache_section_audiobookshelf
    }
