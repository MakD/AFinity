package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.DeleteSummary
import com.makd.afinity.ui.admin.refresh.RefreshMetadataDialog
import com.makd.afinity.ui.components.DeleteItemDialog
import com.makd.afinity.ui.item.DeleteOutcome
import java.util.UUID

sealed interface MusicAdminRequest {
    data class Refresh(val itemId: UUID, val name: String, val includesChildren: Boolean) :
        MusicAdminRequest

    data class Delete(val itemId: UUID, val name: String, val kind: MusicDeleteKind) :
        MusicAdminRequest
}

enum class MusicDeleteKind {
    TRACK,
    ALBUM,
    ARTIST,
}

@Composable
fun MusicAdminDialogs(
    request: MusicAdminRequest?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onDeleted: (MusicAdminRequest.Delete) -> Unit = {},
    viewModel: MusicAdminViewModel = hiltViewModel(),
) {
    when (request) {
        null -> Unit
        is MusicAdminRequest.Refresh -> {
            val queuedMessage = stringResource(R.string.admin_refresh_queued)
            RefreshMetadataDialog(
                itemId = request.itemId.toString(),
                itemName = request.name,
                onDismiss = onDismiss,
                includesChildren = request.includesChildren,
                onQueued = { onMessage(queuedMessage) },
            )
        }
        is MusicAdminRequest.Delete ->
            MusicDeleteDialog(
                request = request,
                viewModel = viewModel,
                onDismiss = onDismiss,
                onMessage = onMessage,
                onDeleted = onDeleted,
            )
    }
}

@Composable
private fun MusicDeleteDialog(
    request: MusicAdminRequest.Delete,
    viewModel: MusicAdminViewModel,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onDeleted: (MusicAdminRequest.Delete) -> Unit,
) {
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    var deleting by remember(request.itemId) { mutableStateOf(false) }
    var errorRes by remember(request.itemId) { mutableStateOf<Int?>(null) }
    var summary by remember(request.itemId) { mutableStateOf<DeleteSummary?>(null) }

    if (request.kind != MusicDeleteKind.TRACK) {
        LaunchedEffect(request.itemId) { summary = viewModel.deleteSummary(request.itemId) }
    }

    DeleteItemDialog(
        targetName = request.name,
        message =
            stringResource(
                if (request.kind == MusicDeleteKind.TRACK) R.string.admin_delete_message_item
                else R.string.admin_delete_message_children
            ),
        deleting = deleting,
        errorRes = errorRes,
        details =
            summary?.let { loaded -> @Composable { DeleteSummaryBlock(request.kind, loaded) } },
        onConfirm = {
            deleting = true
            errorRes = null
            viewModel.delete(request.itemId) { outcome ->
                deleting = false
                when (outcome) {
                    DeleteOutcome.DELETED -> {
                        currentOnMessage(context.getString(R.string.admin_delete_success))
                        currentOnDeleted(request)
                        currentOnDismiss()
                    }
                    DeleteOutcome.STILL_RUNNING -> {
                        currentOnMessage(context.getString(R.string.admin_delete_still_running))
                        currentOnDismiss()
                    }
                    DeleteOutcome.NOT_ALLOWED -> errorRes = R.string.admin_delete_not_allowed
                    DeleteOutcome.FAILED -> errorRes = R.string.admin_delete_failed
                }
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun DeleteSummaryBlock(kind: MusicDeleteKind, summary: DeleteSummary) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text =
                if (kind == MusicDeleteKind.ARTIST) {
                    stringResource(
                        R.string.music_admin_delete_counts,
                        summary.childCount,
                        summary.recursiveItemCount,
                    )
                } else {
                    stringResource(
                        R.string.music_admin_delete_track_count,
                        summary.recursiveItemCount,
                    )
                },
            style = MaterialTheme.typography.titleSmall,
        )
        summary.path?.let { path ->
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
