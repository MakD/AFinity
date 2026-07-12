package com.makd.afinity.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.player.PlaybackStats

@Composable
fun PlaybackStatsOverlay(stats: PlaybackStats, onClose: () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onClose()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier.widthIn(min = 300.dp, max = 420.dp).padding(vertical = 24.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    /* Consume clicks */
                },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.playback_stats_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_playback_stats_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                InfoSectionTitle(stringResource(R.string.playback_stats_section_playback_info))
                InfoRow(stringResource(R.string.playback_stats_label_player), stats.playerType)
                if (stats.videoOutput.isNotBlank()) {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_video_output),
                        stats.videoOutput,
                    )
                }
                if (stats.playMethod != "Unknown") {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_play_method),
                        stats.playMethod,
                    )
                }
                if (stats.connection.isNotBlank()) {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_connection),
                        stats.connection,
                    )
                }
                if (stats.hwDec != "Unknown") {
                    InfoRow(stringResource(R.string.playback_stats_label_hardware_dec), stats.hwDec)
                }
                if (stats.decoderName.isNotBlank()) {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_decoder),
                        stats.decoderName,
                    )
                }
                if (stats.avSync.isNotBlank()) {
                    InfoRow(stringResource(R.string.playback_stats_label_av_sync), stats.avSync)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )

                InfoSectionTitle(stringResource(R.string.playback_stats_section_network_buffer))
                InfoRow(stringResource(R.string.playback_stats_label_forward_buffer), stats.bufferHealth)
                if (stats.networkSpeed.isNotBlank()) {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_network_speed),
                        stats.networkSpeed,
                    )
                }
                if (stats.cached.isNotBlank()) {
                    InfoRow(stringResource(R.string.playback_stats_label_cached), stats.cached)
                }
                if (stats.videoBitrate != "Unknown") {
                    InfoRow(stringResource(R.string.playback_stats_label_video_bitrate), stats.videoBitrate)
                }
                if (stats.audioBitrate.isNotBlank()) {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_audio_bitrate),
                        stats.audioBitrate,
                    )
                }

                if (stats.hasVideo) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    InfoSectionTitle(stringResource(R.string.playback_stats_section_video_info))
                    InfoRow(stringResource(R.string.playback_stats_label_resolution), stats.videoResolution)
                    if (stats.videoRange.isNotBlank()) {
                        InfoRow(
                            stringResource(R.string.playback_stats_label_video_range),
                            stats.videoRange,
                        )
                    }
                    if (stats.colorInfo.isNotBlank()) {
                        InfoRow(
                            stringResource(R.string.playback_stats_label_color),
                            stats.colorInfo,
                        )
                    }
                    if (stats.frameRate.isNotBlank()) {
                        InfoRow(
                            stringResource(R.string.playback_stats_label_frame_rate),
                            stats.frameRate,
                        )
                    }
                    InfoRow(
                        stringResource(R.string.playback_stats_label_dropped_frames),
                        stats.droppedFrames.toString(),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                )
                InfoSectionTitle(stringResource(R.string.playback_stats_section_media_info))
                if (stats.container.isNotBlank()) {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_container),
                        stats.container,
                    )
                }
                if (stats.hasVideo) {
                    InfoRow(stringResource(R.string.playback_stats_label_video_codec), stats.videoCodec)
                }
                InfoRow(stringResource(R.string.playback_stats_label_audio_codec), stats.audioCodec)
                InfoRow(
                    stringResource(R.string.playback_stats_label_audio_channels),
                    formatChannelCount(stats.audioChannels),
                )
                InfoRow(
                    stringResource(R.string.playback_stats_label_sample_rate),
                    stringResource(R.string.playback_stats_value_sample_rate_fmt, stats.audioSampleRate),
                )
                if (stats.subtitleTrack.isNotBlank()) {
                    InfoRow(
                        stringResource(R.string.playback_stats_label_subtitle_track),
                        stats.subtitleTrack,
                    )
                }
            }
        }
    }
}

private fun formatChannelCount(channels: Int): String =
    when (channels) {
        0 -> "Unknown"
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }

@Composable
private fun InfoSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
