package com.makd.afinity.ui.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.item.components.shared.PlaybackSelection
import com.makd.afinity.ui.item.components.shared.PlaybackSelectionButton
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailOverlay(
    episode: AfinityEpisode,
    isLoading: Boolean,
    isInWatchlist: Boolean,
    downloadInfo: com.makd.afinity.data.models.download.DownloadInfo?,
    onDismiss: () -> Unit,
    onPlayClick: (AfinityEpisode, PlaybackSelection) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownloadClick: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onGoToSeries: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = episode.seriesName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "S${episode.parentIndexNumber}:E${episode.indexNumber} • ${episode.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val imageUrl = remember(episode.id) {
                episode.images.primaryImageUrl ?: episode.images.thumbImageUrl
            }

            val blurHash = remember(episode.id) {
                episode.images.primaryBlurHash ?: episode.images.thumbBlurHash
            }

            OptimizedAsyncImage(
                imageUrl = imageUrl,
                contentDescription = episode.name,
                blurHash = blurHash,
                targetWidth = 400.dp,
                targetHeight = 225.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                episode.premiereDate?.let { date ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_calendar),
                            contentDescription = "Air date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (episode.runtimeTicks > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clock),
                            contentDescription = "Duration",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        val minutes = (episode.runtimeTicks / 600000000).toInt()
                        Text(
                            text = "${minutes}m",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                episode.communityRating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_imdb_logo),
                            contentDescription = "IMDB",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (episode.overview.isNotBlank()) {
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(0.2f)) {
                    PlaybackSelectionButton(
                        item = episode,
                        buttonText = if (episode.playbackPositionTicks > 0) "Resume" else "Play",
                        buttonIcon = painterResource(id = R.drawable.ic_play_arrow),
                        onPlayClick = { selection ->
                            onPlayClick(episode, selection)
                        }
                    )
                }

                onGoToSeries?.let { goToSeries ->
                    IconButton(onClick = goToSeries) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = "Go to Series",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                IconButton(onClick = onToggleWatchlist) {
                    Icon(
                        painter = if (isInWatchlist) painterResource(id = R.drawable.ic_bookmark_filled) else painterResource(id = R.drawable.ic_bookmark),
                        contentDescription = "Watchlist",
                        tint = if (isInWatchlist) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        painter = if (episode.favorite) painterResource(id = R.drawable.ic_favorite_filled) else painterResource(id = R.drawable.ic_favorite),
                        contentDescription = "Favorite",
                        tint = if (episode.favorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onToggleWatched) {
                    Icon(
                        painter = if (episode.played) painterResource(id = R.drawable.ic_circle_check) else painterResource(id = R.drawable.ic_circle_check_outline),
                        contentDescription = "Watched",
                        tint = if (episode.played) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                DownloadProgressIndicator(
                    downloadInfo = downloadInfo,
                    onDownloadClick = onDownloadClick,
                    onPauseClick = onPauseDownload,
                    onResumeClick = onResumeDownload,
                    onCancelClick = onCancelDownload
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}