package com.makd.afinity.ui.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.ui.components.AfinityMenuItem
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.DownloadConfirm
import com.makd.afinity.ui.components.DownloadConfirmDialog
import com.makd.afinity.ui.components.formatClockTicks
import com.makd.afinity.ui.components.matches
import com.makd.afinity.ui.components.mediainfo.TrackMediaInfoSheet
import com.makd.afinity.ui.components.singleDownloadConfirm
import com.makd.afinity.ui.item.components.DownloadProgressIndicator

@Composable
fun MusicTrackRow(
    track: AfinityTrack,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    trackNumber: Int? = null,
    isHot: Boolean = false,
    showAlbumArt: Boolean = true,
    onClick: () -> Unit,
    onInstantMix: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onAddNext: (() -> Unit)? = null,
    onAddLast: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    isDownloadEnabled: Boolean = true,
    downloadInfo: DownloadInfo? = null,
    adminActions: TrackAdminActions? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }
    val downloadStatus = downloadInfo?.status
    val isDownloadIdle =
        downloadStatus == null ||
            downloadStatus == DownloadStatus.FAILED ||
            downloadStatus == DownloadStatus.CANCELLED

    var pendingDeleteConfirm by remember { mutableStateOf<DownloadConfirm?>(null) }

    if (showMediaInfo) {
        TrackMediaInfoSheet(track = track, onDismiss = { showMediaInfo = false })
    }

    pendingDeleteConfirm?.let { confirm ->
        DownloadConfirmDialog(
            confirm = confirm,
            onConfirm = {
                pendingDeleteConfirm = null
                if (downloadInfo?.matches(confirm) == true) onCancelDownload?.invoke()
            },
            onDismiss = { pendingDeleteConfirm = null },
        )
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAlbumArt) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    imageUrl = track.images.primary?.toString(),
                    contentDescription = track.album,
                    blurHash = track.images.primaryImageBlurHash,
                    targetWidth = 48.dp,
                    targetHeight = 48.dp,
                    modifier = Modifier.size(48.dp).aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )
                if (isPlaying) {
                    Box(
                        modifier =
                            Modifier.size(48.dp)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_music),
                            contentDescription = stringResource(R.string.cd_music_now_playing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else if (trackNumber != null) {
            if (isHot) {
                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flame_hot_outline),
                        contentDescription = stringResource(R.string.cd_music_hot_track),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color =
                        if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isHot && (showAlbumArt || trackNumber == null)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flame_hot_outline),
                        contentDescription = stringResource(R.string.cd_music_hot_track),
                        tint = Color.Unspecified,
                        modifier = Modifier.padding(start = 4.dp).size(15.dp),
                    )
                }
            }
            val subtitle =
                listOfNotNull(
                        track.artist ?: track.artists.firstOrNull(),
                        if (showAlbumArt) track.album else null,
                    )
                    .joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            downloadStatus == DownloadStatus.COMPLETED ->
                Icon(
                    painter = painterResource(R.drawable.ic_sd_card),
                    contentDescription = stringResource(R.string.cd_music_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp).size(14.dp),
                )

            !isDownloadIdle && onCancelDownload != null ->
                DownloadProgressIndicator(
                    modifier = Modifier.padding(start = 6.dp).size(32.dp),
                    downloadInfo = downloadInfo,
                    onDownloadClick = { onDownload?.invoke() },
                    onPauseClick = {},
                    onResumeClick = { onDownload?.invoke() },
                    onCancelClick = onCancelDownload,
                    canDownload = isDownloadEnabled,
                    iconSize = 20.dp,
                )
        }

        Text(
            text = formatClockTicks(track.runtimeTicks),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )

        Box {
            IconButton(
                onClick = {
                    showMenu = true
                    adminActions?.onMenuOpened?.invoke(track)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_dots_vertical),
                    contentDescription = stringResource(R.string.cd_music_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (onFavorite != null) {
                    AfinityMenuItem(
                        text =
                            stringResource(
                                if (track.favorite) R.string.cd_favorite_remove
                                else R.string.cd_favorite_add
                            ),
                        iconRes =
                            if (track.favorite) R.drawable.ic_favorite_filled
                            else R.drawable.ic_favorite,
                        iconTint = if (track.favorite) MaterialTheme.colorScheme.error else null,
                        onClick = {
                            showMenu = false
                            onFavorite()
                        },
                    )
                }
                if (onInstantMix != null) {
                    AfinityMenuItem(
                        text = stringResource(R.string.music_action_instant_mix),
                        iconRes = R.drawable.ic_compass,
                        onClick = {
                            showMenu = false
                            onInstantMix()
                        },
                    )
                }
                if (onStartRadio != null) {
                    AfinityMenuItem(
                        text = stringResource(R.string.music_action_start_radio),
                        iconRes = R.drawable.ic_radio,
                        onClick = {
                            showMenu = false
                            onStartRadio()
                        },
                    )
                }
                if (onAddNext != null) {
                    AfinityMenuItem(
                        text = stringResource(R.string.music_action_play_next),
                        iconRes = R.drawable.ic_player_skip_forward,
                        onClick = {
                            showMenu = false
                            onAddNext()
                        },
                    )
                }
                if (onAddLast != null) {
                    AfinityMenuItem(
                        text = stringResource(R.string.music_action_add_to_queue),
                        iconRes = R.drawable.ic_playlist_alt,
                        onClick = {
                            showMenu = false
                            onAddLast()
                        },
                    )
                }
                if (onAddToPlaylist != null) {
                    AfinityMenuItem(
                        text = stringResource(R.string.music_action_add_to_playlist),
                        iconRes = R.drawable.ic_playlist_add,
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        },
                    )
                }
                if (onRemoveFromPlaylist != null) {
                    AfinityMenuItem(
                        text = stringResource(R.string.music_action_remove_from_playlist),
                        iconRes = R.drawable.ic_delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = {
                            showMenu = false
                            onRemoveFromPlaylist()
                        },
                    )
                }
                if (onDownload != null) {
                    val labelRes =
                        when (downloadStatus) {
                            DownloadStatus.QUEUED,
                            DownloadStatus.DOWNLOADING -> R.string.cd_cancel_download
                            DownloadStatus.PAUSED -> R.string.cd_resume_download
                            DownloadStatus.COMPLETED -> R.string.cd_delete_download
                            else -> R.string.action_download
                        }
                    val iconRes =
                        when (downloadStatus) {
                            DownloadStatus.QUEUED,
                            DownloadStatus.DOWNLOADING -> R.drawable.ic_cancel
                            DownloadStatus.COMPLETED -> R.drawable.ic_delete
                            else -> R.drawable.ic_download
                        }
                    AfinityMenuItem(
                        text = stringResource(labelRes),
                        iconRes = iconRes,
                        iconTint =
                            if (
                                downloadStatus == DownloadStatus.COMPLETED ||
                                    downloadStatus == DownloadStatus.QUEUED ||
                                    downloadStatus == DownloadStatus.DOWNLOADING
                            )
                                MaterialTheme.colorScheme.error
                            else null,
                        enabled = isDownloadEnabled || !isDownloadIdle,
                        onClick = {
                            showMenu = false
                            if (isDownloadIdle || downloadStatus == DownloadStatus.PAUSED) {
                                onDownload()
                            } else if (downloadStatus == DownloadStatus.COMPLETED) {
                                pendingDeleteConfirm =
                                    downloadInfo?.singleDownloadConfirm(track.name)
                            } else {
                                onCancelDownload?.invoke()
                            }
                        },
                    )
                }
                AfinityMenuItem(
                    text = stringResource(R.string.media_info_title),
                    iconRes = R.drawable.ic_info,
                    onClick = {
                        showMenu = false
                        showMediaInfo = true
                    },
                )
                if (adminActions != null) {
                    HorizontalDivider()
                    AfinityMenuItem(
                        text = stringResource(R.string.admin_action_edit_metadata),
                        iconRes = R.drawable.ic_edit_circle,
                        onClick = {
                            showMenu = false
                            adminActions.onEditMetadata(track)
                        },
                    )
                    AfinityMenuItem(
                        text = stringResource(R.string.admin_action_edit_images),
                        iconRes = R.drawable.ic_photo_search,
                        onClick = {
                            showMenu = false
                            adminActions.onEditImages(track)
                        },
                    )
                    AfinityMenuItem(
                        text = stringResource(R.string.admin_action_edit_lyrics),
                        iconRes = R.drawable.ic_article,
                        onClick = {
                            showMenu = false
                            adminActions.onEditLyrics(track)
                        },
                    )
                    AfinityMenuItem(
                        text = stringResource(R.string.admin_action_refresh_metadata),
                        iconRes = R.drawable.ic_refresh,
                        onClick = {
                            showMenu = false
                            adminActions.onRefresh(track)
                        },
                    )
                    if (track.id in adminActions.deletableIds) {
                        HorizontalDivider()
                        AfinityMenuItem(
                            text = stringResource(R.string.music_admin_delete_track),
                            iconRes = R.drawable.ic_delete,
                            destructive = true,
                            onClick = {
                                showMenu = false
                                adminActions.onDelete(track)
                            },
                        )
                    }
                }
            }
        }
    }
}
