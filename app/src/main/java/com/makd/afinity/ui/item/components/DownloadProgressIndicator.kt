package com.makd.afinity.ui.item.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus

@Composable
fun DownloadProgressIndicator(
    downloadInfo: DownloadInfo?,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    canDownload: Boolean = true,
    isLandscape: Boolean = false,
    isUnavailable: Boolean = false,
    onDownloadLongClick: (() -> Unit)? = null,
    iconSize: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    val isStartDownloadState = downloadInfo?.status == null ||
        downloadInfo.status == DownloadStatus.FAILED ||
        downloadInfo.status == DownloadStatus.CANCELLED
    val enabled = canDownload || !isStartDownloadState

    val onClick: () -> Unit = {
        when (downloadInfo?.status) {
            null,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED -> onDownloadClick()
            DownloadStatus.DOWNLOADING,
            DownloadStatus.QUEUED -> onCancelClick()
            DownloadStatus.PAUSED -> onResumeClick()
            DownloadStatus.COMPLETED -> onCancelClick()
        }
    }

    // A long-press to choose a storage location only makes sense from the start-download state.
    val longClick: (() -> Unit)? =
        onDownloadLongClick?.takeIf { isStartDownloadState }

    val content: @Composable () -> Unit = {
        DownloadIndicatorContent(
            downloadInfo = downloadInfo,
            enabled = enabled,
            isUnavailable = isUnavailable,
            iconSize = iconSize,
        )
    }

    if (longClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier =
                modifier
                    .size(48.dp)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false, radius = 24.dp),
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = longClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    } else {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
    }
}

@Composable
private fun DownloadIndicatorContent(
    downloadInfo: DownloadInfo?,
    enabled: Boolean,
    isUnavailable: Boolean = false,
    iconSize: Dp = 28.dp,
) {
    if (isUnavailable && downloadInfo?.status == DownloadStatus.COMPLETED) {
        Icon(
            painter = painterResource(id = R.drawable.ic_cloud_off),
            contentDescription = stringResource(R.string.download_unavailable_indicator),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        return
    }
    when (downloadInfo?.status) {
        null,
        DownloadStatus.FAILED,
        DownloadStatus.CANCELLED -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_download),
                contentDescription = stringResource(R.string.action_download),
                tint =
                    if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(iconSize),
            )
        }

        DownloadStatus.DOWNLOADING -> {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { downloadInfo.progress },
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 2.dp,
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_cancel),
                    contentDescription = stringResource(R.string.cd_cancel_download),
                    modifier = Modifier.size(iconSize * 0.57f),
                    tint = Color.Red,
                )
            }
        }

        DownloadStatus.QUEUED -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_hourglass_empty),
                contentDescription = stringResource(R.string.cd_queued_tap_cancel),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize),
            )
        }

        DownloadStatus.PAUSED -> {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { downloadInfo.progress },
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 2.dp,
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_cloud_pause),
                    contentDescription = stringResource(R.string.cd_resume_download),
                    modifier = Modifier.size(iconSize * 0.57f),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DownloadStatus.COMPLETED -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = stringResource(R.string.cd_delete_download),
                tint = Color.Red,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}