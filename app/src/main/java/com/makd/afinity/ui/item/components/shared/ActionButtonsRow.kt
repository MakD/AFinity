package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.DetailMenuAction
import com.makd.afinity.ui.components.DetailMoreMenu
import com.makd.afinity.ui.components.FavoriteToggleButton
import com.makd.afinity.ui.components.WatchedToggleButton
import com.makd.afinity.ui.components.WatchlistToggleButton
import com.makd.afinity.ui.components.mediainfo.JellyfinMediaInfoSheet
import com.makd.afinity.ui.item.components.DownloadProgressIndicator
import com.makd.afinity.ui.preview.FontScalePreviews
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.preview.WidthPreviews

@Composable
fun ActionButtonsRow(
    item: AfinityItem,
    isInWatchlist: Boolean,
    hasTrailer: Boolean,
    downloadInfo: DownloadInfo?,
    onPlayTrailer: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onShufflePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
    hasPlayableItems: Boolean = true,
    isDownloadAllowedByServer: Boolean = true,
    canDownloadOnNetwork: Boolean = true,
    isLandscape: Boolean = false,
    downloadUnavailable: Boolean = false,
    isAdmin: Boolean = false,
    canIdentify: Boolean = true,
    canDelete: Boolean = false,
    showUserDataToggles: Boolean = true,
    onAdminAction: (AdminAction) -> Unit = {},
    onDownloadLongClick: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onAddToCollection: (() -> Unit)? = null,
    onRemoveFromCollection: (() -> Unit)? = null,
    onManageCollectionItems: (() -> Unit)? = null,
    selectedSourceId: String? = null,
) {
    var showMediaInfo by remember { mutableStateOf(false) }
    val hasMediaInfo = item.sources.isNotEmpty()

    if (showMediaInfo) {
        JellyfinMediaInfoSheet(
            subtitle =
                if (item is AfinityEpisode) listOf(item.seriesName, item.name).joinToString(" · ")
                else item.name,
            sources = item.sources,
            runtimeTicks = item.runtimeTicks,
            selectedSourceId = selectedSourceId,
            onDismiss = { showMediaInfo = false },
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            if (isLandscape) Arrangement.spacedBy(12.dp, Alignment.Start)
            else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasTrailer) {
            IconButton(onClick = onPlayTrailer) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_video),
                    contentDescription = stringResource(R.string.hero_btn_play_trailer),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        if (showUserDataToggles) {
            WatchlistToggleButton(isInWatchlist = isInWatchlist, onClick = onToggleWatchlist)

            FavoriteToggleButton(isFavorite = item.favorite, onClick = onToggleFavorite)

            val visuallyPlayed = item.played && hasPlayableItems

            WatchedToggleButton(
                isPlayed = visuallyPlayed,
                onClick = onToggleWatched,
                enabled = hasPlayableItems,
            )
        }

        if (isDownloadAllowedByServer) {
            DownloadProgressIndicator(
                downloadInfo = downloadInfo,
                onDownloadClick = onDownloadClick,
                onPauseClick = onPauseDownload,
                onResumeClick = onResumeDownload,
                onCancelClick = onCancelDownload,
                canDownload = canDownloadOnNetwork && hasPlayableItems,
                isLandscape = isLandscape,
                isUnavailable = downloadUnavailable,
                onDownloadLongClick = onDownloadLongClick,
            )
        }

        val canShuffle = item is AfinityShow || item is AfinitySeason || item is AfinityBoxSet
        val hasManageItems = isAdmin || canDelete
        val hasCollectionActions =
            onAddToCollection != null ||
                onRemoveFromCollection != null ||
                onManageCollectionItems != null
        val showMenu = hasManageItems || hasMediaInfo || canShuffle || hasCollectionActions

        if (!showMenu && onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_playlist_add),
                    contentDescription = stringResource(R.string.cd_music_add_to_playlist),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        if (showMenu) {
            val userActions = buildList {
                onManageCollectionItems?.let {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.collection_action_manage),
                            iconRes = R.drawable.ic_list_details,
                            onClick = it,
                        )
                    )
                }
                if (canShuffle) {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.cd_shuffle_play),
                            iconRes = R.drawable.ic_arrows_shuffle,
                            onClick = onShufflePlay,
                            enabled = hasPlayableItems,
                        )
                    )
                }
                if (hasMediaInfo) {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.media_info_title),
                            iconRes = R.drawable.ic_info,
                            onClick = { showMediaInfo = true },
                        )
                    )
                }
                onAddToPlaylist?.let {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.cd_music_add_to_playlist),
                            iconRes = R.drawable.ic_playlist_add,
                            onClick = it,
                        )
                    )
                }
                onAddToCollection?.let {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.collection_action_add),
                            iconRes = R.drawable.ic_collection,
                            onClick = it,
                        )
                    )
                }
                onRemoveFromCollection?.let {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.collection_action_remove),
                            iconRes = R.drawable.ic_remove,
                            onClick = it,
                        )
                    )
                }
            }
            val adminActions = buildList {
                if (isAdmin) {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.admin_action_edit_metadata),
                            iconRes = R.drawable.ic_edit_circle,
                            onClick = { onAdminAction(AdminAction.EditMetadata) },
                            dividerBefore = userActions.isNotEmpty(),
                        )
                    )
                    if (canIdentify) {
                        add(
                            DetailMenuAction(
                                label = stringResource(R.string.admin_action_identify),
                                iconRes = R.drawable.ic_search,
                                onClick = { onAdminAction(AdminAction.Identify) },
                            )
                        )
                    }
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.admin_action_edit_images),
                            iconRes = R.drawable.ic_photo_search,
                            onClick = { onAdminAction(AdminAction.EditImages) },
                        )
                    )
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.admin_action_refresh_metadata),
                            iconRes = R.drawable.ic_refresh,
                            onClick = { onAdminAction(AdminAction.Refresh) },
                        )
                    )
                }
                if (canDelete) {
                    add(
                        DetailMenuAction(
                            label = stringResource(R.string.admin_action_delete),
                            iconRes = R.drawable.ic_delete,
                            onClick = { onAdminAction(AdminAction.Delete) },
                            destructive = true,
                        )
                    )
                }
            }
            DetailMoreMenu(
                actions = userActions + adminActions,
                iconSize = 28.dp,
                contentDescription =
                    stringResource(
                        if (hasManageItems) R.string.cd_admin_manage
                        else R.string.cd_music_more_options
                    ),
            )
        }
    }
}

@Composable
private fun ActionButtonsRowPreviewContent() {
    PreviewHost {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            listOf<AfinityItem>(PreviewData.show(), PreviewData.movie()).forEach { item ->
                ActionButtonsRow(
                    item = item,
                    isInWatchlist = false,
                    hasTrailer = true,
                    downloadInfo = null,
                    onPlayTrailer = {},
                    onToggleWatchlist = {},
                    onShufflePlay = {},
                    onToggleFavorite = {},
                    onToggleWatched = {},
                    onDownloadClick = {},
                    onPauseDownload = {},
                    onResumeDownload = {},
                    onCancelDownload = {},
                    modifier = Modifier.padding(horizontal = 16.dp),
                    isAdmin = true,
                )
            }
        }
    }
}

@WidthPreviews
@Composable
private fun ActionButtonsRowWidthPreview() {
    ActionButtonsRowPreviewContent()
}

@FontScalePreviews
@Composable
private fun ActionButtonsRowFontScalePreview() {
    ActionButtonsRowPreviewContent()
}
