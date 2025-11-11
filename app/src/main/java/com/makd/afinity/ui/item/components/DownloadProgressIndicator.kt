package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    IconButton(
        onClick = {
            when (downloadInfo?.status) {
                null, DownloadStatus.FAILED, DownloadStatus.CANCELLED -> onDownloadClick()
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> onCancelClick()
                DownloadStatus.PAUSED -> onResumeClick()
                DownloadStatus.COMPLETED -> onCancelClick()
            }
        },
        modifier = modifier
    ) {
        when (downloadInfo?.status) {
            null, DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
            DownloadStatus.DOWNLOADING -> {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { downloadInfo.progress },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Download",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Red
                    )
                }
            }
            DownloadStatus.QUEUED -> {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = "Queued - Tap to Cancel",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            DownloadStatus.PAUSED -> {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { downloadInfo.progress },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Resume Download",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            DownloadStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Download",
                    tint = Color.Red,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
