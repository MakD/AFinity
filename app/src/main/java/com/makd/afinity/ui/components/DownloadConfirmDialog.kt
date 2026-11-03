package com.makd.afinity.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.util.formatFileSize

enum class DownloadConfirmKind {
    DELETE,
    CANCEL,
}

data class DownloadConfirm(
    val kind: DownloadConfirmKind,
    val title: String,
    val count: Int,
    val bytes: Long,
)

fun DownloadInfo.singleDownloadConfirm(title: String): DownloadConfirm? =
    when (status) {
        DownloadStatus.COMPLETED ->
            DownloadConfirm(DownloadConfirmKind.DELETE, title, 1, totalBytes)
        DownloadStatus.DOWNLOADING,
        DownloadStatus.PAUSED ->
            DownloadConfirm(DownloadConfirmKind.CANCEL, title, 1, bytesDownloaded)
        else -> null
    }

fun DownloadInfo.matches(confirm: DownloadConfirm): Boolean =
    (status == DownloadStatus.COMPLETED) == (confirm.kind == DownloadConfirmKind.DELETE)

fun AbsDownloadInfo.singleDownloadConfirm(title: String): DownloadConfirm? =
    when {
        status == AbsDownloadStatus.COMPLETED ->
            DownloadConfirm(DownloadConfirmKind.DELETE, title, 1, totalBytes)
        status == AbsDownloadStatus.DOWNLOADING && episodeId == null ->
            DownloadConfirm(DownloadConfirmKind.CANCEL, title, 1, bytesDownloaded)
        else -> null
    }

fun AbsDownloadInfo.matches(confirm: DownloadConfirm): Boolean =
    (status == AbsDownloadStatus.COMPLETED) == (confirm.kind == DownloadConfirmKind.DELETE)

@Composable
fun DownloadConfirmDialog(confirm: DownloadConfirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val size = formatFileSize(context, confirm.bytes)
    val isDelete = confirm.kind == DownloadConfirmKind.DELETE
    val message =
        when {
            isDelete && confirm.count > 1 ->
                pluralStringResource(
                    R.plurals.download_confirm_delete_many_fmt,
                    confirm.count,
                    confirm.count,
                    size,
                )
            isDelete -> stringResource(R.string.download_confirm_delete_one_fmt, size)
            confirm.count > 1 ->
                pluralStringResource(
                    R.plurals.download_confirm_cancel_many_fmt,
                    confirm.count,
                    confirm.count,
                )
            confirm.bytes > 0 -> stringResource(R.string.download_confirm_cancel_one_fmt, size)
            else -> stringResource(R.string.download_confirm_cancel_one)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter =
                    painterResource(
                        id = if (isDelete) R.drawable.ic_delete else R.drawable.ic_cancel
                    ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = confirm.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                Text(
                    stringResource(
                        if (isDelete) R.string.action_delete else R.string.action_stop_download
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(
                        if (isDelete) R.string.action_cancel else R.string.action_keep_downloading
                    )
                )
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
