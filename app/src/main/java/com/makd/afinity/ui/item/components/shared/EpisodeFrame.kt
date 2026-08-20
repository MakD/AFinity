package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showBackdropBlurHash
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showPrimaryBlurHash
import com.makd.afinity.data.models.extensions.showPrimaryImageUrl
import com.makd.afinity.data.models.extensions.showThumbBlurHash
import com.makd.afinity.data.models.extensions.showThumbImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.ui.components.AsyncImage
import java.util.Locale

@Composable
fun EpisodeFrame(
    episode: AfinityEpisode,
    isUnaired: Boolean,
    modifier: Modifier = Modifier,
) {
    val imageUrl =
        remember(episode.id, isUnaired) {
            if (isUnaired) {
                episode.images.thumbImageUrl
                    ?: episode.images.showThumbImageUrl
                    ?: episode.images.showBackdropImageUrl
                    ?: episode.images.showPrimaryImageUrl
                    ?: episode.images.primaryImageUrl
            } else {
                episode.images.primaryImageUrl ?: episode.images.thumbImageUrl
            }
        }

    val blurHash =
        remember(episode.id, isUnaired) {
            if (isUnaired) {
                episode.images.thumbBlurHash
                    ?: episode.images.showThumbBlurHash
                    ?: episode.images.showBackdropBlurHash
                    ?: episode.images.showPrimaryBlurHash
                    ?: episode.images.primaryBlurHash
            } else {
                episode.images.primaryBlurHash ?: episode.images.thumbBlurHash
            }
        }

    val designator = remember(episode.id) { episodeDesignator(episode) }

    val progress =
        remember(episode.playbackPositionTicks, episode.runtimeTicks) {
            if (episode.runtimeTicks > 0 && episode.playbackPositionTicks > 0)
                (episode.playbackPositionTicks.toFloat() / episode.runtimeTicks).coerceIn(0f, 1f)
            else 0f
        }

    val readout =
        remember(episode.playbackPositionTicks, episode.runtimeTicks) {
            when {
                episode.runtimeTicks <= 0L -> null
                episode.playbackPositionTicks > 0L ->
                    "-" +
                        formatTimecode(
                            (episode.runtimeTicks - episode.playbackPositionTicks)
                                .coerceAtLeast(0L) / 10_000L
                        )
                else -> formatTimecode(episode.runtimeTicks / 10_000L)
            }
        }

    Box(
        modifier =
            modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
    ) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = episode.name,
            blurHash = blurHash,
            targetWidth = 400.dp,
            targetHeight = 225.dp,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
        )

        Row(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = designator,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.primary,
            )

            if (readout != null) {
                Text(
                    text = readout,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }

        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Black.copy(alpha = 0.3f),
            )
        }
    }
}

private fun episodeDesignator(episode: AfinityEpisode): String {
    val season = episode.parentIndexNumber ?: 0
    val number = episode.indexNumber ?: 0
    val end = episode.indexNumberEnd

    return if (end != null && end != episode.indexNumber) {
        String.format(Locale.US, "S%02dE%02d-%02d", season, number, end)
    } else {
        String.format(Locale.US, "S%02dE%02d", season, number)
    }
}

private fun formatTimecode(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}