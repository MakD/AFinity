package com.makd.afinity.ui.settings.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.data.models.download.DownloadPriority
import com.makd.afinity.data.models.download.DownloadState
import com.makd.afinity.data.network.ConnectionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    navController: NavController,
    viewModel: DownloadSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showQueueManagement by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = Color.Red
                )
            },
            title = { Text("Clear All Downloads") },
            text = {
                Text(
                    "Are you sure you want to delete ALL downloaded files? " +
                            "This will free up ${uiState.storageUsed} of storage space."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllDownloads()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StorageStatisticsCard(
                    storageUsed = uiState.storageUsed,
                    downloadCount = uiState.completedDownloads.size,
                    onClearAll = { showClearAllDialog = true }
                )
            }
            item {
                NetworkStatusCard(
                    viewModel = viewModel
                )
            }

            if (uiState.activeDownloads.isNotEmpty() || uiState.queuedDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Active & Queued (${uiState.activeDownloads.size + uiState.queuedDownloads.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(uiState.activeDownloads) { download ->
                    ActiveDownloadCard(
                        download = download,
                        onCancel = { viewModel.cancelDownload(download.itemId) }
                    )
                }

                items(uiState.queuedDownloads) { queueItem ->
                    QueuedDownloadCard(
                        queueItem = queueItem,
                        onRemove = { viewModel.removeFromQueue(queueItem.itemId) },
                        onChangePriority = { priority ->
                            viewModel.changePriority(queueItem.itemId, priority)
                        }
                    )
                }
            }

            if (uiState.completedDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloaded (${uiState.completedDownloads.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(uiState.completedDownloads) { download ->
                    CompletedDownloadCard(
                        download = download,
                        onDelete = { viewModel.deleteDownload(download.itemId) },
                        onPlay = { /* TODO: Navigate to player */ }
                    )
                }
            }

            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            item {
                DownloadSettingsCard(
                    maxConcurrentDownloads = uiState.maxConcurrentDownloads,
                    wifiOnly = uiState.wifiOnly,
                    onMaxConcurrentChanged = { viewModel.setMaxConcurrentDownloads(it) },
                    onWifiOnlyChanged = { viewModel.setWifiOnly(it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NetworkStatusCard(
    viewModel: DownloadSettingsViewModel
) {
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (networkState.connectionType) {
                ConnectionType.WIFI, ConnectionType.ETHERNET -> MaterialTheme.colorScheme.primaryContainer
                ConnectionType.CELLULAR -> MaterialTheme.colorScheme.tertiaryContainer
                ConnectionType.NONE -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (networkState.connectionType) {
                        ConnectionType.WIFI -> "Connected to WiFi"
                        ConnectionType.ETHERNET -> "Connected via Ethernet"
                        ConnectionType.CELLULAR -> "On Mobile Data"
                        ConnectionType.NONE -> "No Connection"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = when (networkState.connectionType) {
                        ConnectionType.WIFI, ConnectionType.ETHERNET -> "Downloads active"
                        ConnectionType.CELLULAR -> "Downloads paused (WiFi-only enabled)"
                        ConnectionType.NONE -> "Downloads paused"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Icon(
                imageVector = when (networkState.connectionType) {
                    ConnectionType.WIFI -> Icons.Default.Wifi
                    ConnectionType.ETHERNET -> Icons.Default.Cable
                    ConnectionType.CELLULAR -> Icons.Default.SignalCellularAlt
                    ConnectionType.NONE -> Icons.Default.SignalWifiOff
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun StorageStatisticsCard(
    storageUsed: String,
    downloadCount: Int,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = storageUsed,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Used by $downloadCount downloads",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            if (downloadCount > 0) {
                Button(
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Downloads")
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    download: DownloadState.Downloading,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.itemName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${formatBytes(download.bytesDownloaded)} / ${formatBytes(download.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.Red
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { download.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "${download.progress}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QueuedDownloadCard(
    queueItem: com.makd.afinity.data.models.download.QueuedDownloadItem,
    onRemove: () -> Unit,
    onChangePriority: (DownloadPriority) -> Unit
) {
    var showPriorityMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = queueItem.itemName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Queued - Priority: ${queueItem.priority.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        IconButton(onClick = { showPriorityMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Priority")
                        }

                        DropdownMenu(
                            expanded = showPriorityMenu,
                            onDismissRequest = { showPriorityMenu = false }
                        ) {
                            DownloadPriority.entries.forEach { priority ->
                                DropdownMenuItem(
                                    text = { Text(priority.name) },
                                    onClick = {
                                        onChangePriority(priority)
                                        showPriorityMenu = false
                                    },
                                    leadingIcon = {
                                        if (priority == queueItem.priority) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.Red
                        )
                    }
                }
            }

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CompletedDownloadCard(
    download: DownloadState.Completed,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.itemName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = formatBytes(download.file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSettingsCard(
    maxConcurrentDownloads: Int,
    wifiOnly: Boolean,
    onMaxConcurrentChanged: (Int) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download over WiFi only",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Prevent mobile data usage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChanged
                )
            }

            HorizontalDivider()

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Maximum concurrent downloads",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Current: $maxConcurrentDownloads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Slider(
                    value = maxConcurrentDownloads.toFloat(),
                    onValueChange = { onMaxConcurrentChanged(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "10",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}