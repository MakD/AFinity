package com.makd.afinity.ui.player.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.player.VideoQuality
import org.jellyfin.sdk.model.api.TranscodeReason
import java.util.Locale

fun resolutionLabelFor(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    return when {
        width >= 3840 -> "4K"
        width >= 2560 -> "1440P"
        width >= 1920 -> "1080P"
        width >= 1280 -> "720P"
        width >= 854 -> "480P"
        width >= 640 -> "360P"
        else -> "SD"
    }
}

@Composable
fun ResolutionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
        color = Color.White.copy(alpha = 0.75f),
        modifier = modifier,
    )
}

@Composable
fun PlayMethodBadge(isTranscoding: Boolean, modifier: Modifier = Modifier) {
    Text(
        text =
            stringResource(
                if (isTranscoding) R.string.player_badge_transcoding
                else R.string.player_badge_direct
            ),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
        color =
            if (isTranscoding) MaterialTheme.colorScheme.primary
            else Color.White.copy(alpha = 0.75f),
        modifier = modifier,
    )
}

@Composable
fun qualityLabel(quality: VideoQuality): String =
    when {
        quality.isAuto -> stringResource(R.string.player_quality_auto)
        quality.isOriginal -> stringResource(R.string.player_quality_original)
        else ->
            quality.maxHeight?.let { stringResource(R.string.player_quality_resolution_fmt, it) }
                ?: formatBitrate(quality.maxBitrate)
    }

@Composable
fun settingsQualityLabel(quality: VideoQuality): String =
    when {
        quality.isAuto -> stringResource(R.string.player_quality_auto)
        quality.isOriginal -> stringResource(R.string.player_quality_original)
        else -> {
            val resolution =
                quality.maxHeight?.let {
                    stringResource(R.string.player_quality_resolution_fmt, it)
                }
            val bitrate = formatBitrate(quality.maxBitrate)
            if (resolution != null) "$resolution · $bitrate" else bitrate
        }
    }

@Composable
fun musicQualityLabel(quality: MusicQuality): String =
    if (quality.isOriginal) {
        stringResource(R.string.player_quality_original)
    } else {
        stringResource(R.string.music_quality_opus_fmt, quality.maxBitrate / 1000)
    }

@Composable
fun musicQualityShortLabel(quality: MusicQuality): String =
    if (quality.isOriginal) {
        stringResource(R.string.music_quality_short_original)
    } else {
        stringResource(R.string.music_quality_short_kbps_fmt, quality.maxBitrate / 1000)
    }

@Composable
fun qualitySecondaryLabel(
    quality: VideoQuality,
    sourceWidth: Int?,
    sourceHeight: Int?,
    sourceBitrate: Int?,
): String? =
    when {
        quality.isAuto -> stringResource(R.string.player_quality_auto_summary)
        quality.isOriginal -> {
            val resolution =
                if (sourceWidth != null && sourceHeight != null && sourceWidth > 0) {
                    "${sourceWidth}×${sourceHeight}"
                } else {
                    null
                }
            val bitrate = sourceBitrate?.takeIf { it > 0 }?.let { formatBitrate(it) }
            listOfNotNull(resolution, bitrate).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
        else -> formatBitrate(quality.maxBitrate)
    }

@Composable
private fun formatBitrate(bitrate: Int): String =
    if (bitrate < 1_000_000) {
        stringResource(R.string.player_quality_kbps_fmt, bitrate / 1000)
    } else {
        val mbps = bitrate / 1_000_000.0
        val text =
            if (mbps % 1.0 == 0.0) {
                mbps.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.1f", mbps)
            }
        stringResource(R.string.player_quality_mbps_fmt, text)
    }

@Composable
fun transcodeReasonText(reasons: List<TranscodeReason>): String? {
    if (reasons.isEmpty()) return null
    val detail =
        reasons.map { stringResource(transcodeReasonRes(it)) }.distinct().joinToString(", ")
    return stringResource(R.string.player_transcode_reason_prefix, detail)
}

fun transcodeReasonRes(reason: TranscodeReason): Int =
    when (reason) {
        TranscodeReason.AUDIO_CODEC_NOT_SUPPORTED -> R.string.player_transcode_reason_audio_codec
        TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED -> R.string.player_transcode_reason_video_codec
        TranscodeReason.CONTAINER_NOT_SUPPORTED -> R.string.player_transcode_reason_container
        TranscodeReason.SUBTITLE_CODEC_NOT_SUPPORTED ->
            R.string.player_transcode_reason_subtitle_codec
        TranscodeReason.CONTAINER_BITRATE_EXCEEDS_LIMIT ->
            R.string.player_transcode_reason_bitrate_limit
        TranscodeReason.VIDEO_BITRATE_NOT_SUPPORTED ->
            R.string.player_transcode_reason_video_bitrate
        TranscodeReason.AUDIO_BITRATE_NOT_SUPPORTED ->
            R.string.player_transcode_reason_audio_bitrate
        TranscodeReason.VIDEO_RESOLUTION_NOT_SUPPORTED ->
            R.string.player_transcode_reason_resolution
        TranscodeReason.VIDEO_RANGE_TYPE_NOT_SUPPORTED ->
            R.string.player_transcode_reason_video_range
        TranscodeReason.VIDEO_PROFILE_NOT_SUPPORTED ->
            R.string.player_transcode_reason_video_profile
        TranscodeReason.VIDEO_LEVEL_NOT_SUPPORTED -> R.string.player_transcode_reason_video_level
        TranscodeReason.AUDIO_CHANNELS_NOT_SUPPORTED ->
            R.string.player_transcode_reason_audio_channels
        TranscodeReason.VIDEO_BIT_DEPTH_NOT_SUPPORTED -> R.string.player_transcode_reason_bit_depth
        TranscodeReason.VIDEO_FRAMERATE_NOT_SUPPORTED -> R.string.player_transcode_reason_framerate
        TranscodeReason.INTERLACED_VIDEO_NOT_SUPPORTED ->
            R.string.player_transcode_reason_interlaced
        TranscodeReason.AUDIO_IS_EXTERNAL -> R.string.player_transcode_reason_external_audio
        TranscodeReason.DIRECT_PLAY_ERROR -> R.string.player_transcode_reason_direct_play_error
        TranscodeReason.ANAMORPHIC_VIDEO_NOT_SUPPORTED ->
            R.string.player_transcode_reason_anamorphic
        TranscodeReason.REF_FRAMES_NOT_SUPPORTED -> R.string.player_transcode_reason_ref_frames
        TranscodeReason.SECONDARY_AUDIO_NOT_SUPPORTED ->
            R.string.player_transcode_reason_secondary_audio
        TranscodeReason.AUDIO_PROFILE_NOT_SUPPORTED ->
            R.string.player_transcode_reason_audio_profile
        TranscodeReason.AUDIO_SAMPLE_RATE_NOT_SUPPORTED ->
            R.string.player_transcode_reason_audio_sample_rate
        TranscodeReason.AUDIO_BIT_DEPTH_NOT_SUPPORTED ->
            R.string.player_transcode_reason_audio_bit_depth
        TranscodeReason.UNKNOWN_VIDEO_STREAM_INFO -> R.string.player_transcode_reason_unknown_video
        TranscodeReason.UNKNOWN_AUDIO_STREAM_INFO -> R.string.player_transcode_reason_unknown_audio
        TranscodeReason.VIDEO_CODEC_TAG_NOT_SUPPORTED -> R.string.player_transcode_reason_codec_tag
        TranscodeReason.STREAM_COUNT_EXCEEDS_LIMIT -> R.string.player_transcode_reason_stream_count
        TranscodeReason.VIDEO_ROTATION_NOT_SUPPORTED -> R.string.player_transcode_reason_rotation
        else -> R.string.player_transcode_reason_other
    }
