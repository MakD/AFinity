package com.makd.afinity.ui.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import org.jellyfin.sdk.model.api.MediaStreamType
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
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var needsSeparator = false

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
                    needsSeparator = true
                }

                if (episode.runtimeTicks > 0) {
                    if (needsSeparator) MetadataDot()

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
                    needsSeparator = true
                }

                episode.communityRating?.let { rating ->
                    if (needsSeparator) MetadataDot()

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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (episode.sources.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val source = episode.sources.firstOrNull()

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let { videoStream ->
                            val resolution = when {
                                (videoStream.height ?: 0) <= 2160 && (videoStream.width
                                    ?: 0) <= 3840 &&
                                        ((videoStream.height ?: 0) > 1080 || (videoStream.width
                                            ?: 0) > 1920) -> "4K"

                                (videoStream.height ?: 0) <= 1080 && (videoStream.width
                                    ?: 0) <= 1920 &&
                                        ((videoStream.height ?: 0) > 720 || (videoStream.width
                                            ?: 0) > 1280) -> "HD"

                                else -> "SD"
                            }

                            VideoMetadataChip(text = resolution)
                        }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.codec?.takeIf { it.isNotEmpty() }
                        ?.let { codec ->
                            VideoMetadataChip(text = codec.uppercase())
                        }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let { videoStream ->
                            if (videoStream.videoDoViTitle != null) {
                                VideoMetadataChipWithIcon(
                                    text = "Vision",
                                    iconRes = R.drawable.ic_brand_dolby_digital
                                )
                            } else {
                                videoStream.videoRangeType?.let { rangeType ->
                                    val hdrType = when (rangeType.name) {
                                        "HDR10" -> "HDR10"
                                        "HDR10Plus" -> "HDR10+"
                                        "HLG" -> "HLG"
                                        else -> null
                                    }
                                    hdrType?.let { VideoMetadataChip(text = it) }
                                }
                            }
                        }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }?.codec?.takeIf { it.isNotEmpty() }
                        ?.let { codec ->
                            when (codec.lowercase()) {
                                "ac3" -> VideoMetadataChipWithIcon(
                                    text = "Digital",
                                    iconRes = R.drawable.ic_brand_dolby_digital
                                )

                                "eac3" -> VideoMetadataChipWithIcon(
                                    text = "Digital+",
                                    iconRes = R.drawable.ic_brand_dolby_digital
                                )

                                "truehd" -> VideoMetadataChipWithIcon(
                                    text = "TrueHD",
                                    iconRes = R.drawable.ic_brand_dolby_digital
                                )

                                "dts" -> VideoMetadataChip(text = "DTS")
                                else -> VideoMetadataChip(text = codec.uppercase())
                            }
                        }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }?.channelLayout?.let { layout ->
                        val channels = when {
                            layout.contains("7.1") -> "7.1"
                            layout.contains("5.1") -> "5.1"
                            layout.contains("2.1") -> "2.1"
                            layout.contains("2.0") || layout.contains("stereo") -> "2.0"
                            else -> null
                        }
                        channels?.let { VideoMetadataChip(text = it) }
                    }

                    val hasSubtitles =
                        source?.mediaStreams?.any { it.type == MediaStreamType.SUBTITLE } == true

                    if (hasSubtitles) {
                        VideoMetadataChip(text = "CC")
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaybackSelectionButton(
                    item = episode,
                    buttonText = if (episode.playbackPositionTicks > 0) "Resume" else "Play",
                    buttonIcon = painterResource(id = R.drawable.ic_play_arrow),
                    onPlayClick = { selection ->
                        onPlayClick(episode, selection)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
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
                            painter = if (isInWatchlist) painterResource(id = R.drawable.ic_bookmark_filled) else painterResource(
                                id = R.drawable.ic_bookmark
                            ),
                            contentDescription = "Watchlist",
                            tint = if (isInWatchlist) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            painter = if (episode.favorite) painterResource(id = R.drawable.ic_favorite_filled) else painterResource(
                                id = R.drawable.ic_favorite
                            ),
                            contentDescription = "Favorite",
                            tint = if (episode.favorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = onToggleWatched) {
                        Icon(
                            painter = if (episode.played) painterResource(id = R.drawable.ic_circle_check) else painterResource(
                                id = R.drawable.ic_circle_check_outline
                            ),
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
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetadataDot() {
    Text(
        text = "•",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun VideoMetadataChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun VideoMetadataChipWithIcon(text: String, iconRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(16.dp)
                .padding(bottom = 1.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}