package com.makd.afinity.ui.player.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.ui.player.PlayerViewModel
import org.jellyfin.sdk.model.api.MediaStreamType

data class CastBitrateOption(val label: String, val bitrate: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastPlaybackSettingsSheet(
    castState: CastSessionState,
    castManager: CastManager,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentItem = castState.currentItem

    val bitrateOptions =
        listOf(
            CastBitrateOption("Max (16 Mbps)", 16_000_000),
            CastBitrateOption("8 Mbps", 8_000_000),
            CastBitrateOption("4 Mbps", 4_000_000),
            CastBitrateOption("2 Mbps", 2_000_000),
            CastBitrateOption("1 Mbps", 1_000_000),
        )

    val audioStreams =
        currentItem?.sources?.firstOrNull()?.mediaStreams?.filter {
            it.type == MediaStreamType.AUDIO
        } ?: emptyList()

    val subtitleStreams =
        currentItem?.sources?.firstOrNull()?.mediaStreams?.filter {
            it.type == MediaStreamType.SUBTITLE
        } ?: emptyList()

    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.cast_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.cast_quality),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            bitrateOptions.forEach { option ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable {
                                if (currentItem != null) {
                                    castManager.changeBitrate(
                                        bitrate = option.bitrate,
                                        item = currentItem,
                                        serverBaseUrl =
                                            viewModel.castManager.castState.value.let { "" },
                                        mediaSourceId = castState.mediaSourceId ?: "",
                                        audioStreamIndex = castState.audioStreamIndex,
                                        subtitleStreamIndex = castState.subtitleStreamIndex,
                                        enableHevc = false,
                                    )
                                }
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = castState.castBitrate == option.bitrate,
                        onClick = null,
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            ),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = option.label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (audioStreams.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.player_audio_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))

                audioStreams.forEachIndexed { index, stream ->
                    val displayName = buildString {
                        append(stream.language?.uppercase() ?: "Unknown")
                        append(" - ${stream.codec?.uppercase() ?: "N/A"}")
                        if ((stream.channels ?: 0) > 0) {
                            append(" (${stream.channels}ch)")
                        }
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (currentItem != null) {
                                        castManager.switchAudioTrack(
                                            audioStreamIndex = stream.index ?: index,
                                            item = currentItem,
                                            serverBaseUrl = "",
                                            mediaSourceId = castState.mediaSourceId ?: "",
                                            subtitleStreamIndex = castState.subtitleStreamIndex,
                                            maxBitrate = castState.castBitrate,
                                            enableHevc = false,
                                        )
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = castState.audioStreamIndex == (stream.index ?: index),
                            onClick = null,
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                ),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = stringResource(R.string.player_subtitle_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (currentItem != null) {
                                castManager.switchSubtitleTrack(
                                    subtitleStreamIndex = null,
                                    item = currentItem,
                                    serverBaseUrl = "",
                                    mediaSourceId = castState.mediaSourceId ?: "",
                                    audioStreamIndex = castState.audioStreamIndex,
                                    maxBitrate = castState.castBitrate,
                                    enableHevc = false,
                                )
                            }
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = castState.subtitleStreamIndex == null,
                    onClick = null,
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        ),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.track_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            subtitleStreams.forEachIndexed { index, stream ->
                val displayName = buildString {
                    append(stream.displayTitle ?: stream.language?.uppercase() ?: "Unknown")
                    stream.codec?.let { append(" ($it)") }
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable {
                                if (currentItem != null) {
                                    castManager.switchSubtitleTrack(
                                        subtitleStreamIndex = stream.index ?: index,
                                        item = currentItem,
                                        serverBaseUrl = "",
                                        mediaSourceId = castState.mediaSourceId ?: "",
                                        audioStreamIndex = castState.audioStreamIndex,
                                        maxBitrate = castState.castBitrate,
                                        enableHevc = false,
                                    )
                                }
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = castState.subtitleStreamIndex == (stream.index ?: index),
                        onClick = null,
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            ),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = stringResource(R.string.cast_playback_speed),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            speedOptions.forEach { speed ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable {
                                castManager.setPlaybackSpeed(speed)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = castState.playbackSpeed == speed,
                        onClick = null,
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            ),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "${speed}x", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
