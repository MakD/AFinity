package com.makd.afinity.ui.item.components.shared

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.hdrLabel
import com.makd.afinity.data.models.media.isDolbyVision
import org.jellyfin.sdk.model.api.MediaStreamType

@Composable
fun MediaStreamBadges(source: AfinitySource?) {
    val videoStream = source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audioStream = source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }

    videoStream?.let { VideoMetadataChip(text = stringResource(it.resolutionLabelRes())) }

    videoStream
        ?.codec
        ?.takeIf { it.isNotEmpty() }
        ?.let { codec -> VideoMetadataChip(text = codec.uppercase()) }

    if (videoStream != null) {
        if (videoStream.isDolbyVision()) {
            VideoMetadataChipWithIcon(
                text = stringResource(R.string.meta_vision),
                iconRes = R.drawable.ic_brand_dolby_digital,
            )
        } else {
            videoStream.hdrLabel()?.let { VideoMetadataChip(text = it) }
        }
    }

    audioStream?.let { stream ->
        val codec = stream.codec.takeIf { it.isNotEmpty() }
        val isAtmos = stream.profile?.contains("Atmos", ignoreCase = true) == true
        when {
            isAtmos ->
                VideoMetadataChipWithIcon(
                    text = stringResource(R.string.meta_atmos),
                    iconRes = R.drawable.ic_brand_dolby_digital,
                )
            codec == null -> Unit
            codec.equals("ac3", ignoreCase = true) ->
                VideoMetadataChipWithIcon(
                    text = stringResource(R.string.meta_digital),
                    iconRes = R.drawable.ic_brand_dolby_digital,
                )
            codec.equals("eac3", ignoreCase = true) ->
                VideoMetadataChipWithIcon(
                    text = stringResource(R.string.meta_digital_plus),
                    iconRes = R.drawable.ic_brand_dolby_digital,
                )
            codec.equals("truehd", ignoreCase = true) ->
                VideoMetadataChipWithIcon(
                    text = stringResource(R.string.meta_truehd),
                    iconRes = R.drawable.ic_brand_dolby_digital,
                )
            codec.equals("dts", ignoreCase = true) -> VideoMetadataChip(text = "DTS")
            else -> VideoMetadataChip(text = codec.uppercase())
        }
    }

    audioStream?.channelLayout?.channelLabel()?.let { VideoMetadataChip(text = it) }

    if (source?.mediaStreams?.any { it.type == MediaStreamType.SUBTITLE } == true) {
        VideoMetadataChip(text = stringResource(R.string.meta_cc))
    }
}

private fun AfinityMediaStream.resolutionLabelRes(): Int {
    val height = height ?: 0
    val width = width ?: 0
    return when {
        height <= 2160 && width <= 3840 && (height > 1080 || width > 1920) -> R.string.meta_res_4k
        height <= 1080 && width <= 1920 && (height > 720 || width > 1280) -> R.string.meta_res_hd
        else -> R.string.meta_res_sd
    }
}

private fun String.channelLabel(): String? =
    when {
        contains("7.1") -> "7.1"
        contains("5.1") -> "5.1"
        contains("2.1") -> "2.1"
        contains("2.0") || contains("stereo") -> "2.0"
        else -> null
    }

@Composable
internal fun VideoMetadataChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        modifier =
            Modifier.background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun VideoMetadataChipWithIcon(text: String, @DrawableRes iconRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier.background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
    }
}
