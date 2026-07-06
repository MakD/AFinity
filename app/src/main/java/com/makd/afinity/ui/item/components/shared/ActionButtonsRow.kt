package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.FavoriteToggleButton
import com.makd.afinity.ui.components.WatchedToggleButton
import com.makd.afinity.ui.components.WatchlistToggleButton
import com.makd.afinity.ui.item.components.DownloadProgressIndicator

@Composable
fun ActionButtonsRow(
    item: AfinityItem,
    isInWatchlist: Boolean,
    hasTrailer: Boolean,
    downloadInfo: DownloadInfo?,
    hasPlayableItems: Boolean = true,
    onPlayTrailer: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onShufflePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    canDownload: Boolean = true,
    isLandscape: Boolean = false,
    downloadUnavailable: Boolean = false,
    isAdmin: Boolean = false,
    onAdminAction: (AdminAction) -> Unit = {},
    onDownloadLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            if (isLandscape) Arrangement.spacedBy(12.dp, Alignment.Start)
            else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPlayTrailer, enabled = hasTrailer) {
            Icon(
                painter = painterResource(id = R.drawable.ic_video),
                contentDescription = stringResource(R.string.hero_btn_play_trailer),
                tint =
                    if (hasTrailer) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(28.dp),
            )
        }

        WatchlistToggleButton(isInWatchlist = isInWatchlist, onClick = onToggleWatchlist)

        if (item is AfinityShow || item is AfinitySeason) {
            IconButton(onClick = onShufflePlay, enabled = hasPlayableItems) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrows_shuffle),
                    contentDescription = stringResource(R.string.cd_shuffle_play),
                    tint =
                        if (hasPlayableItems) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        FavoriteToggleButton(isFavorite = item.favorite, onClick = onToggleFavorite)

        val visuallyPlayed = item.played && hasPlayableItems

        WatchedToggleButton(
            isPlayed = visuallyPlayed,
            onClick = onToggleWatched,
            enabled = hasPlayableItems,
        )

        DownloadProgressIndicator(
            downloadInfo = downloadInfo,
            onDownloadClick = onDownloadClick,
            onPauseClick = onPauseDownload,
            onResumeClick = onResumeDownload,
            onCancelClick = onCancelDownload,
            canDownload = canDownload && hasPlayableItems,
            isLandscape = isLandscape,
            isUnavailable = downloadUnavailable,
            onDownloadLongClick = onDownloadLongClick,
        )

        if (isAdmin) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_options),
                        contentDescription = stringResource(R.string.cd_admin_manage),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.admin_action_edit_metadata)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit_circle),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAdminAction(AdminAction.EditMetadata)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.admin_action_identify)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAdminAction(AdminAction.Identify)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.admin_action_edit_images)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_photo_search),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAdminAction(AdminAction.EditImages)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.admin_action_refresh_metadata)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_refresh),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAdminAction(AdminAction.Refresh)
                        },
                    )
                }
            }
        }
    }
}
