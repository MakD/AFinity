package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.item.components.DownloadProgressIndicator

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
    canDownload: Boolean = true,
    isLandscape: Boolean = false,
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

        IconButton(onClick = onToggleWatchlist) {
            Icon(
                painter =
                    if (isInWatchlist) painterResource(id = R.drawable.ic_bookmark_filled)
                    else painterResource(id = R.drawable.ic_bookmark),
                contentDescription =
                    if (isInWatchlist) stringResource(R.string.cd_watchlist_remove)
                    else stringResource(R.string.cd_watchlist_add),
                tint =
                    if (isInWatchlist) Color(0xFFFF9800)
                    else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }

        if (item is AfinityShow || item is AfinitySeason) {
            IconButton(onClick = onShufflePlay) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrows_shuffle),
                    contentDescription = stringResource(R.string.cd_shuffle_play),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                painter =
                    if (item.favorite) painterResource(id = R.drawable.ic_favorite_filled)
                    else painterResource(id = R.drawable.ic_favorite),
                contentDescription =
                    if (item.favorite) stringResource(R.string.cd_favorite_remove)
                    else stringResource(R.string.cd_favorite_add),
                tint = if (item.favorite) Color.Red else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }

        IconButton(onClick = onToggleWatched) {
            Icon(
                painter =
                    if (item.played) painterResource(id = R.drawable.ic_circle_check)
                    else painterResource(id = R.drawable.ic_circle_check_outline),
                contentDescription =
                    if (item.played) stringResource(R.string.cd_watched_unmark)
                    else stringResource(R.string.cd_watched_mark),
                tint = if (item.played) Color.Green else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }

        DownloadProgressIndicator(
            downloadInfo = downloadInfo,
            onDownloadClick = onDownloadClick,
            onPauseClick = onPauseDownload,
            onResumeClick = onResumeDownload,
            onCancelClick = onCancelDownload,
            canDownload = canDownload,
            isLandscape = isLandscape,
        )
    }
}
