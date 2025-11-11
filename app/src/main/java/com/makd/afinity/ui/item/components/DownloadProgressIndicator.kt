package com.makd.afinity.ui.item.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus

@Composable
fun DownloadProgressIndicator(
    downloadInfo: DownloadInfo?,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    when {
        downloadInfo == null -> {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        downloadInfo.status == DownloadStatus.COMPLETED -> {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        else -> {
            if (isLandscape) {
                DownloadProgressCompact(
                    downloadInfo = downloadInfo,
                    onPauseClick = onPauseClick,
                    onResumeClick = onResumeClick,
                    onCancelClick = onCancelClick,
                    modifier = modifier
                )
            } else {
                DownloadProgressExpanded(
                    downloadInfo = downloadInfo,
                    onPauseClick = onPauseClick,
                    onResumeClick = onResumeClick,
                    onCancelClick = onCancelClick,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun DownloadProgressCompact(
    downloadInfo: DownloadInfo,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { downloadInfo.progress },
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
            Text(
                text = "${(downloadInfo.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.7
            )
        }

        when (downloadInfo.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                IconButton(onClick = onPauseClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.PAUSED -> {
                IconButton(onClick = onResumeClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            else -> {}
        }

        IconButton(onClick = onCancelClick, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "Cancel",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DownloadProgressExpanded(
    downloadInfo: DownloadInfo,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (downloadInfo.status) {
                            DownloadStatus.QUEUED -> "Queued for download"
                            DownloadStatus.DOWNLOADING -> "Downloading..."
                            DownloadStatus.PAUSED -> "Download paused"
                            DownloadStatus.FAILED -> "Download failed"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (downloadInfo.status == DownloadStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${formatBytes(downloadInfo.bytesDownloaded)} / ${formatBytes(downloadInfo.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (downloadInfo.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                            IconButton(onClick = onPauseClick) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause"
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(onClick = onResumeClick) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume"
                                )
                            }
                        }
                        DownloadStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }

                    IconButton(onClick = onCancelClick) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Cancel"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { downloadInfo.progress },
                modifier = Modifier
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
