package com.makd.afinity.ui.player.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.rememberAfinitySheetState
import org.jellyfin.sdk.model.api.MediaStreamType

private val CastBitrateOptions = listOf(16_000_000, 8_000_000, 4_000_000, 2_000_000, 1_000_000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastChaptersSheet(
    chapters: List<AfinityChapter>,
    currentPositionMs: Long,
    onChapterClick: (AfinityChapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberAfinitySheetState()
    val currentIndex = chapters.indexOfLast { it.startPosition <= currentPositionMs }
    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))

    AfinityModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.chapters_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    val isCurrent = index == currentIndex
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent)
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                            alpha = 0.4f
                                        )
                                    else Color.Transparent
                                )
                                .clickable(role = Role.Button) {
                                    onChapterClick(chapter)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(
                            text =
                                chapter.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.chapter_number_fmt, index + 1),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatCastClock(chapter.startPosition),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CastTracksSheet(
    castState: CastSessionState,
    castManager: CastManager,
    onDismiss: () -> Unit,
) {
    val currentItem = castState.currentItem ?: return
    val baseUrl = castState.serverBaseUrl ?: return
    val mediaSource =
        currentItem.sources.firstOrNull { it.id == castState.mediaSourceId }
            ?: currentItem.sources.firstOrNull()
            ?: return
    val audioStreams = mediaSource.mediaStreams.filter { it.type == MediaStreamType.AUDIO }
    val subtitleStreams = mediaSource.mediaStreams.filter { it.type == MediaStreamType.SUBTITLE }
    val sheetState = rememberAfinitySheetState()

    AfinityModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.cast_audio_and_subs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (audioStreams.isNotEmpty()) {
                SheetSectionLabel(stringResource(R.string.player_audio_title))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    audioStreams.forEach { stream ->
                        val selected =
                            castState.audioStreamIndex?.let { it == stream.index }
                                ?: stream.isDefault
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    castManager.switchAudioTrack(
                                        audioStreamIndex = stream.index,
                                        item = currentItem,
                                        serverBaseUrl = baseUrl,
                                        mediaSourceId = mediaSource.id,
                                        subtitleStreamIndex = castState.subtitleStreamIndex,
                                        maxBitrate = castState.castBitrate,
                                        enableHevc = castState.enableHevc,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text =
                                        audioLabel(
                                            stream.displayTitle,
                                            stream.language,
                                            stream.codec,
                                            stream.channels,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            SheetSectionLabel(stringResource(R.string.player_subtitle_title))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val anySelected =
                    castState.subtitleStreamIndex != null &&
                        subtitleStreams.any { it.index == castState.subtitleStreamIndex }
                FilterChip(
                    selected = !anySelected,
                    onClick = {
                        if (anySelected) {
                            castManager.switchSubtitleTrack(
                                subtitleStreamIndex = null,
                                item = currentItem,
                                serverBaseUrl = baseUrl,
                                mediaSourceId = mediaSource.id,
                                audioStreamIndex = castState.audioStreamIndex,
                                maxBitrate = castState.castBitrate,
                                enableHevc = castState.enableHevc,
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.track_none)) },
                )
                subtitleStreams.forEach { stream ->
                    val selected = stream.index == castState.subtitleStreamIndex
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                castManager.switchSubtitleTrack(
                                    subtitleStreamIndex = stream.index,
                                    item = currentItem,
                                    serverBaseUrl = baseUrl,
                                    mediaSourceId = mediaSource.id,
                                    audioStreamIndex = castState.audioStreamIndex,
                                    maxBitrate = castState.castBitrate,
                                    enableHevc = castState.enableHevc,
                                )
                            }
                        },
                        label = {
                            Text(
                                text =
                                    stream.displayTitle?.takeIf { it.isNotBlank() }
                                        ?: stream.language.uppercase(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            SheetSectionLabel(stringResource(R.string.cast_quality))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CastBitrateOptions.forEachIndexed { index, bitrate ->
                    val selected = castState.castBitrate == bitrate
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                castManager.changeBitrate(
                                    bitrate = bitrate,
                                    item = currentItem,
                                    serverBaseUrl = baseUrl,
                                    mediaSourceId = castState.mediaSourceId ?: mediaSource.id,
                                    audioStreamIndex = castState.audioStreamIndex,
                                    subtitleStreamIndex = castState.subtitleStreamIndex,
                                    enableHevc = castState.enableHevc,
                                )
                            }
                        },
                        label = {
                            Text(
                                if (index == 0) stringResource(R.string.cast_quality_max)
                                else
                                    stringResource(
                                        R.string.cast_quality_mbps_fmt,
                                        bitrate / 1_000_000,
                                    )
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.cast_stream_restart_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun audioLabel(
    displayTitle: String?,
    language: String,
    codec: String,
    channels: Int?,
): String =
    displayTitle?.takeIf { it.isNotBlank() }
        ?: buildString {
            append(language.uppercase())
            if (codec.isNotBlank()) append(" · ").append(codec.uppercase())
            val ch = channels ?: 0
            if (ch > 0) {
                append(" · ")
                when (ch) {
                    1 -> append(stringResource(R.string.audio_channels_mono))
                    2 -> append(stringResource(R.string.audio_channels_stereo))
                    else -> append(stringResource(R.string.audio_channels_fmt, ch))
                }
            }
        }

internal fun formatCastClock(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
