package com.makd.afinity.ui.music.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.DetailMenuAction

@Composable
fun musicAdminMenuActions(
    isAdmin: Boolean,
    canDelete: Boolean,
    @StringRes deleteLabel: Int?,
    onEditMetadata: () -> Unit,
    onEditImages: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onIdentify: (() -> Unit)? = null,
): List<DetailMenuAction> {
    if (!isAdmin) return emptyList()
    return buildList {
        add(
            DetailMenuAction(
                label = stringResource(R.string.admin_action_edit_metadata),
                iconRes = R.drawable.ic_edit_circle,
                onClick = onEditMetadata,
                dividerBefore = true,
            )
        )
        if (onIdentify != null) {
            add(
                DetailMenuAction(
                    label = stringResource(R.string.admin_action_identify),
                    iconRes = R.drawable.ic_search,
                    onClick = onIdentify,
                )
            )
        }
        add(
            DetailMenuAction(
                label = stringResource(R.string.admin_action_edit_images),
                iconRes = R.drawable.ic_photo_search,
                onClick = onEditImages,
            )
        )
        add(
            DetailMenuAction(
                label = stringResource(R.string.admin_action_refresh_metadata),
                iconRes = R.drawable.ic_refresh,
                onClick = onRefresh,
            )
        )
        if (canDelete && deleteLabel != null) {
            add(
                DetailMenuAction(
                    label = stringResource(deleteLabel),
                    iconRes = R.drawable.ic_delete,
                    onClick = onDelete,
                    destructive = true,
                )
            )
        }
    }
}

@Composable
fun rememberTrackAdminActions(
    adminViewModel: MusicAdminViewModel,
    onEditMetadata: (String) -> Unit,
    onEditImages: (String) -> Unit,
    onEditLyrics: (String) -> Unit,
    onRequest: (MusicAdminRequest) -> Unit,
): TrackAdminActions? {
    val isAdmin by adminViewModel.isAdmin.collectAsStateWithLifecycle()
    val deletableIds by adminViewModel.deletableIds.collectAsStateWithLifecycle()
    val currentOnEditMetadata by rememberUpdatedState(onEditMetadata)
    val currentOnEditImages by rememberUpdatedState(onEditImages)
    val currentOnEditLyrics by rememberUpdatedState(onEditLyrics)
    val currentOnRequest by rememberUpdatedState(onRequest)
    return remember(isAdmin, deletableIds, adminViewModel) {
        if (!isAdmin) {
            null
        } else {
            TrackAdminActions(
                deletableIds = deletableIds,
                onMenuOpened = { track -> adminViewModel.checkCanDelete(track.id) },
                onEditMetadata = { track -> currentOnEditMetadata(track.id.toString()) },
                onEditImages = { track -> currentOnEditImages(track.id.toString()) },
                onEditLyrics = { track -> currentOnEditLyrics(track.id.toString()) },
                onRefresh = { track ->
                    currentOnRequest(MusicAdminRequest.Refresh(track.id, track.name, false))
                },
                onDelete = { track ->
                    currentOnRequest(
                        MusicAdminRequest.Delete(track.id, track.name, MusicDeleteKind.TRACK)
                    )
                },
            )
        }
    }
}
