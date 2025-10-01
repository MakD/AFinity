package com.makd.afinity.ui.item.components.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

data class PlaybackSelection(
    val mediaSourceId: String,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val videoStreamIndex: Int?,
    val startPositionMs: Long = 0L
)

data class MediaSourceOption(
    val id: String,
    val name: String,
    val quality: String,
    val codec: String,
    val size: Long,
    val isDefault: Boolean = false
)

data class AudioStreamOption(
    val stream: AfinityMediaStream,
    val displayName: String,
    val isDefault: Boolean = false
)

data class SubtitleStreamOption(
    val stream: AfinityMediaStream?,
    val displayName: String,
    val isNone: Boolean = false,
    val isDefault: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSelectionButton(
    item: AfinityItem,
    buttonText: String,
    buttonIcon: ImageVector,
    onPlayClick: (PlaybackSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedMediaSource by remember { mutableStateOf<MediaSourceOption?>(null) }
    var selectedAudioStream by remember { mutableStateOf<AudioStreamOption?>(null) }
    var selectedSubtitleStream by remember { mutableStateOf<SubtitleStreamOption?>(null) }

    val audioStreamOptions = remember(item) {
        val source = item.sources.firstOrNull()
        source?.mediaStreams?.filter { it.type == MediaStreamType.AUDIO }?.mapIndexed { index, stream ->
            val channels = when {
                stream.channelLayout?.contains("7.1") == true -> "7.1"
                stream.channelLayout?.contains("5.1") == true -> "5.1"
                stream.channelLayout?.contains("2.1") == true -> "2.1"
                stream.channelLayout?.contains("2.0") == true || stream.channelLayout?.contains("stereo") == true -> "2.0"
                else -> "2.0"
            }

            AudioStreamOption(
                stream = stream,
                displayName = "${stream.language.ifEmpty { "Unknown" }} • ${stream.codec.uppercase()} $channels",
                isDefault = index == 0
            )
        } ?: emptyList()
    }

    val subtitleStreamOptions = remember(item) {
        val source = item.sources.firstOrNull()
        val options = mutableListOf<SubtitleStreamOption>()

        options.add(
            SubtitleStreamOption(
                stream = null,
                displayName = "None",
                isNone = true,
                isDefault = true
            )
        )

        source?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }?.forEach { stream ->
            val displayName = buildString {
                append(stream.language.ifEmpty { "Unknown" })
                append(" • ${stream.codec.uppercase()}")

                if (stream.title.contains("forced", ignoreCase = true)) {
                    append(" (Forced)")
                } else if (stream.isExternal) {
                    append(" (External)")
                }
            }

            options.add(
                SubtitleStreamOption(
                    stream = stream,
                    displayName = displayName,
                    isNone = false,
                    isDefault = false
                )
            )
        }

        options
    }

    LaunchedEffect(audioStreamOptions) {
        if (selectedAudioStream == null && audioStreamOptions.isNotEmpty()) {
            selectedAudioStream = audioStreamOptions.find { it.isDefault } ?: audioStreamOptions.first()
        }
    }

    LaunchedEffect(subtitleStreamOptions) {
        if (selectedSubtitleStream == null && subtitleStreamOptions.isNotEmpty()) {
            selectedSubtitleStream = subtitleStreamOptions.find { it.isDefault } ?: subtitleStreamOptions.first()
        }
    }

    val hasMultipleOptions = audioStreamOptions.size > 1 ||
            subtitleStreamOptions.size > 1

    Column(modifier = modifier) {
        if (hasMultipleOptions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            val startPositionMs = if (item.playbackPositionTicks > 0) {
                                item.playbackPositionTicks / 10000
                            } else {
                                0L
                            }
                            onPlayClick(
                                PlaybackSelection(
                                    mediaSourceId = selectedMediaSource?.id ?: item.sources.firstOrNull()?.id ?: "",
                                    audioStreamIndex = getAudioStreamIndex(selectedAudioStream),
                                    subtitleStreamIndex = getSubtitleStreamIndex(selectedSubtitleStream),
                                    videoStreamIndex = selectedMediaSource?.let { option ->
                                        item.sources.find { it.id == option.id }
                                            ?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.index
                                    } ?: 0,
                                    startPositionMs = startPositionMs
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .clickable { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    val startPositionMs = if (item.playbackPositionTicks > 0) {
                        item.playbackPositionTicks / 10000
                    } else {
                        0L
                    }
                    onPlayClick(
                        PlaybackSelection(
                            mediaSourceId = item.sources.firstOrNull()?.id ?: "",
                            audioStreamIndex = getAudioStreamIndex(audioStreamOptions.firstOrNull()),
                            subtitleStreamIndex = null,
                            videoStreamIndex = item.sources.firstOrNull()?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.index,
                            startPositionMs = startPositionMs
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null
                    )
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded && hasMultipleOptions,
            enter = expandVertically(
            ) + fadeIn(),
            exit = shrinkVertically(
            ) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (audioStreamOptions.size > 1) {
                        SelectionSection(
                            title = "Audio",
                            options = audioStreamOptions,
                            selectedOption = selectedAudioStream,
                            onOptionSelected = { selectedAudioStream = it },
                            optionDisplayText = { it.displayName }
                        )
                    }

                    if (subtitleStreamOptions.size > 1) {
                        SelectionSection(
                            title = "Subtitles",
                            options = subtitleStreamOptions,
                            selectedOption = selectedSubtitleStream,
                            onOptionSelected = { selectedSubtitleStream = it },
                            optionDisplayText = { it.displayName }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SelectionSection(
    title: String,
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    optionDisplayText: (T) -> String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(options) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOptionSelected(option) }
                        .background(
                            if (option == selectedOption) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = option == selectedOption,
                        onClick = { onOptionSelected(option) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = optionDisplayText(option),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun getVideoStreamIndex(mediaSourceOption: MediaSourceOption?): Int? {
    return 0
}

private fun getAudioStreamIndex(audioStreamOption: AudioStreamOption?): Int? {
    return audioStreamOption?.stream?.index
}

private fun getSubtitleStreamIndex(subtitleStreamOption: SubtitleStreamOption?): Int? {
    return when {
        subtitleStreamOption == null || subtitleStreamOption.isNone -> null
        else -> subtitleStreamOption.stream?.index
    }
}