package com.makd.afinity.ui.settings.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.repository.CacheSection
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.downloads.DownloadsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()
    val isClearingCache by viewModel.isClearingCache.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerOffset = LocalPlayerOffset.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showClearCacheDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshCacheUsage() }

    val cacheUsageSubtitle =
        cacheUsage?.let { usage ->
            if (usage.isEmpty) {
                stringResource(R.string.pref_clear_cache_empty)
            } else {
                pluralStringResource(
                    R.plurals.pref_clear_cache_usage_total_fmt,
                    usage.metadataEntries,
                    android.text.format.Formatter.formatShortFileSize(context, usage.totalBytes),
                    usage.metadataEntries,
                )
            }
        } ?: stringResource(R.string.pref_clear_cache_summary)

    val cacheClearedMessage = stringResource(R.string.pref_clear_cache_done)

    var selectedSections by remember { mutableStateOf(CacheSection.entries.toSet()) }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { if (!isClearingCache) showClearCacheDialog = false },
            title = { Text(text = stringResource(R.string.pref_clear_cache_confirm)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.pref_clear_cache_message))
                    CacheSectionPicker(
                        usage = cacheUsage,
                        selected = selectedSections,
                        enabled = !isClearingCache,
                        onToggle = { section ->
                            selectedSections =
                                if (section in selectedSections) selectedSections - section
                                else selectedSections + section
                        },
                        context = context,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCachedData(selectedSections) {
                            showClearCacheDialog = false
                            scope.launch { snackbarHostState.showSnackbar(cacheClearedMessage) }
                        }
                    },
                    enabled = !isClearingCache && selectedSections.isNotEmpty(),
                ) {
                    Text(text = stringResource(R.string.pref_clear_cache))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearCacheDialog = false },
                    enabled = !isClearingCache,
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.section_storage_cache),
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
                top = innerPadding.calculateTopPadding() + 16.dp,
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = max(innerPadding.calculateBottomPadding(), playerOffset) + 32.dp,
            )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = customPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup(title = stringResource(R.string.section_downloads)) {
                    SettingsSwitchItem(
                        icon = painterResource(id = R.drawable.ic_wifi),
                        title = stringResource(R.string.pref_download_wifi_only_title),
                        checked = uiState.downloadOverWifiOnly,
                        onCheckedChange = viewModel::setDownloadOverWifiOnly,
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_download_arrow),
                        title = stringResource(R.string.pref_max_concurrent_downloads),
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.setMaxConcurrentDownloads(
                                            uiState.maxConcurrentDownloads - 1
                                        )
                                    },
                                    enabled = uiState.maxConcurrentDownloads > 1,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_remove),
                                        contentDescription =
                                            stringResource(R.string.cd_decrease_limit),
                                    )
                                }
                                Text(
                                    text = "${uiState.maxConcurrentDownloads}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                IconButton(
                                    onClick = {
                                        viewModel.setMaxConcurrentDownloads(
                                            uiState.maxConcurrentDownloads + 1
                                        )
                                    },
                                    enabled = uiState.maxConcurrentDownloads < 3,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_add),
                                        contentDescription =
                                            stringResource(R.string.cd_increase_limit),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            if (uiState.volumeStorageStats.size > 1) {
                item {
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

            item {
                VideoCacheSettingsCard(
                    cacheSizeMb = uiState.videoCacheSizeMb.toFloat(),
                    onCacheSizeChange = { viewModel.setVideoCacheSizeMb(it.toInt()) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                SettingsGroup(modifier = Modifier.fillMaxWidth()) {
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_database_off),
                        title = stringResource(R.string.pref_clear_cache),
                        subtitle = cacheUsageSubtitle,
                        onClick = { showClearCacheDialog = true },
                    )
                }
            }
        }
    }
}
