package com.makd.afinity.ui.item.components.shared

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import org.jellyfin.sdk.model.api.MediaStreamType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetadataRow(
    item: AfinityItem
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLandscape) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, horizontalAlignment),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (item !is AfinityBoxSet && item.sources.isNotEmpty()) {
                    val source = item.sources.firstOrNull()

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.let { videoStream ->
                        val resolution = when {
                            (videoStream.height ?: 0) <= 2160 && (videoStream.width ?: 0) <= 3840 &&
                                    ((videoStream.height ?: 0) > 1080 || (videoStream.width ?: 0) > 1920) -> "4K"

                            (videoStream.height ?: 0) <= 1080 && (videoStream.width ?: 0) <= 1920 &&
                                    ((videoStream.height ?: 0) > 720 || (videoStream.width ?: 0) > 1280) -> "HD"

                            else -> "SD"
                        }

                        VideoMetadataChip(text = resolution)
                    }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.codec?.takeIf { it.isNotEmpty() }?.let { codec ->
                        VideoMetadataChip(text = codec.uppercase())
                    }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.let { videoStream ->
                        if (videoStream.videoDoViTitle != null) {
                            VideoMetadataChipWithIcon(text = "Vision", iconRes = R.drawable.ic_dolby)
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

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }?.codec?.takeIf { it.isNotEmpty() }?.let { codec ->
                        when (codec.lowercase()) {
                            "ac3" -> VideoMetadataChipWithIcon(text = "Digital", iconRes = R.drawable.ic_dolby)
                            "eac3" -> VideoMetadataChipWithIcon(text = "Digital+", iconRes = R.drawable.ic_dolby)
                            "truehd" -> VideoMetadataChipWithIcon(text = "TrueHD", iconRes = R.drawable.ic_dolby)
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

                    val hasSubtitles = source?.mediaStreams?.any { it.type == MediaStreamType.SUBTITLE } == true

                    if (hasSubtitles) {
                        VideoMetadataChip(text = "CC")
                    }
                }

                if (item !is AfinityBoxSet && item.playbackPositionTicks > 0 && item.runtimeTicks > 0) {
                    val progress = item.playbackPositionTicks.toFloat() / item.runtimeTicks.toFloat()
                    val remainingTicks = item.runtimeTicks - item.playbackPositionTicks
                    val remainingHours = (remainingTicks / 10_000_000 / 3600).toInt()
                    val remainingMinutes = ((remainingTicks / 10_000_000 % 3600) / 60).toInt()

                    val remainingText = if (remainingHours > 0) {
                        "${remainingHours}h ${remainingMinutes}m left"
                    } else {
                        "${remainingMinutes}m left"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 2.dp)

                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFFFFC107),
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )

                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                if (item is AfinityBoxSet) {
                    item.itemCount?.let { count ->
                        Text(
                            text = "$count items",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                val communityRating = when (item) {
                    is AfinityMovie -> item.communityRating
                    is AfinityShow -> item.communityRating
                    is AfinityBoxSet -> item.communityRating
                    else -> null
                }

                communityRating?.let { imdbRating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_imdb_logo),
                            contentDescription = "IMDB",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = String.format("%.1f", imdbRating),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                val criticRating = when (item) {
                    is AfinityMovie -> item.criticRating
                    else -> null
                }

                criticRating?.let { rtRating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (rtRating > 60) {
                                    R.drawable.ic_rotten_tomato_fresh
                                } else {
                                    R.drawable.ic_rotten_tomato_rotten
                                }
                            ),
                            contentDescription = "Rotten Tomatoes",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${rtRating.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                when (item) {
                    is AfinityMovie -> item.productionYear?.toString()
                    is AfinityShow -> item.productionYear?.toString()
                    is AfinityBoxSet -> item.productionYear?.toString()
                    else -> null
                }?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (item !is AfinityBoxSet && item.runtimeTicks > 0) {
                    val hours = (item.runtimeTicks / 10_000_000 / 3600).toInt()
                    val minutes = ((item.runtimeTicks / 10_000_000 % 3600) / 60).toInt()

                    val runtimeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

                    Text(
                        text = runtimeText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                when (item) {
                    is AfinityMovie -> item.officialRating
                    is AfinityShow -> item.officialRating
                    is AfinityBoxSet -> item.officialRating
                    else -> null
                }?.let { rating ->
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                val genres = when (item) {
                    is AfinityMovie -> item.genres
                    is AfinityShow -> item.genres
                    is AfinityBoxSet -> item.genres
                    else -> emptyList()
                }

                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.take(2).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        } else {
            if (item !is AfinityBoxSet && item.sources.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, horizontalAlignment),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val source = item.sources.firstOrNull()

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.let { videoStream ->
                        val resolution = when {
                            (videoStream.height ?: 0) <= 2160 && (videoStream.width ?: 0) <= 3840 &&
                                    ((videoStream.height ?: 0) > 1080 || (videoStream.width ?: 0) > 1920) -> "4K"

                            (videoStream.height ?: 0) <= 1080 && (videoStream.width ?: 0) <= 1920 &&
                                    ((videoStream.height ?: 0) > 720 || (videoStream.width ?: 0) > 1280) -> "HD"

                            else -> "SD"
                        }

                        VideoMetadataChip(text = resolution)
                    }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.codec?.takeIf { it.isNotEmpty() }?.let { codec ->
                        VideoMetadataChip(text = codec.uppercase())
                    }

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.let { videoStream ->
                        if (videoStream.videoDoViTitle != null) {
                            VideoMetadataChipWithIcon(text = "Vision", iconRes = R.drawable.ic_dolby)
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

                    source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }?.codec?.takeIf { it.isNotEmpty() }?.let { codec ->
                        when (codec.lowercase()) {
                            "ac3" -> VideoMetadataChipWithIcon(text = "Digital", iconRes = R.drawable.ic_dolby)
                            "eac3" -> VideoMetadataChipWithIcon(text = "Digital+", iconRes = R.drawable.ic_dolby)
                            "truehd" -> VideoMetadataChipWithIcon(text = "TrueHD", iconRes = R.drawable.ic_dolby)
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

                    val hasSubtitles = source?.mediaStreams?.any { it.type == MediaStreamType.SUBTITLE } == true

                    if (hasSubtitles) {
                        VideoMetadataChip(text = "CC")
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp, horizontalAlignment),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (item !is AfinityBoxSet && item.playbackPositionTicks > 0 && item.runtimeTicks > 0) {
                    val progress = item.playbackPositionTicks.toFloat() / item.runtimeTicks.toFloat()
                    val remainingTicks = item.runtimeTicks - item.playbackPositionTicks
                    val remainingHours = (remainingTicks / 10_000_000 / 3600).toInt()
                    val remainingMinutes = ((remainingTicks / 10_000_000 % 3600) / 60).toInt()

                    val remainingText = if (remainingHours > 0) {
                        "${remainingHours}h ${remainingMinutes}m left"
                    } else {
                        "${remainingMinutes}m left"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFFFFC107),
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )

                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                if (item is AfinityBoxSet) {
                    item.itemCount?.let { count ->
                        Text(
                            text = "$count items",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                val communityRating = when (item) {
                    is AfinityMovie -> item.communityRating
                    is AfinityShow -> item.communityRating
                    is AfinityBoxSet -> item.communityRating
                    else -> null
                }

                communityRating?.let { imdbRating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_imdb_logo),
                            contentDescription = "IMDB",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = String.format("%.1f", imdbRating),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                val criticRating = when (item) {
                    is AfinityMovie -> item.criticRating
                    else -> null
                }

                criticRating?.let { rtRating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (rtRating > 60) {
                                    R.drawable.ic_rotten_tomato_fresh
                                } else {
                                    R.drawable.ic_rotten_tomato_rotten
                                }
                            ),
                            contentDescription = "Rotten Tomatoes",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${rtRating.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }
                }

                when (item) {
                    is AfinityMovie -> item.productionYear?.toString()
                    is AfinityShow -> item.productionYear?.toString()
                    is AfinityBoxSet -> item.productionYear?.toString()
                    else -> null
                }?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                    )
                }

                if (item !is AfinityBoxSet && item.runtimeTicks > 0) {
                    val hours = (item.runtimeTicks / 10_000_000 / 3600).toInt()
                    val minutes = ((item.runtimeTicks / 10_000_000 % 3600) / 60).toInt()

                    val runtimeText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

                    Text(
                        text = runtimeText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                    )
                }

                when (item) {
                    is AfinityMovie -> item.officialRating
                    is AfinityShow -> item.officialRating
                    is AfinityBoxSet -> item.officialRating
                    else -> null
                }?.let { rating ->
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                    )
                }

                val genres = when (item) {
                    is AfinityMovie -> item.genres
                    is AfinityShow -> item.genres
                    is AfinityBoxSet -> item.genres
                    else -> emptyList()
                }

                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.take(2).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
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
            modifier = Modifier.size(11.dp)
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