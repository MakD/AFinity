package com.makd.afinity.ui.item.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.makd.afinity.R
import com.makd.afinity.data.models.download.DownloadQuality
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.storage.StorageVolumeInfo
import com.makd.afinity.player.profile.AndroidDeviceProfileFactory
import com.makd.afinity.ui.components.EndAlignedDropdownMenu
import com.makd.afinity.ui.player.components.musicQualityLabel
import com.makd.afinity.ui.player.components.settingsQualityLabel
import com.makd.afinity.util.formatFileSize
import org.jellyfin.sdk.model.api.MediaStreamType

@Composable
fun QualitySelectionDialog(
    sources: List<AfinitySource>,
    onSourceSelected: (AfinitySource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    volumes: List<StorageVolumeInfo> = emptyList(),
    selectedVolumeId: String? = null,
    onVolumeSelected: (String) -> Unit = {},
    initialQualityBitrate: Int = VideoQuality.ORIGINAL_BITRATE,
    onConfirm: (source: AfinitySource, volumeId: String?, quality: DownloadQuality) -> Unit =
        { source, _, _ ->
            onSourceSelected(source)
        },
) {
    var selectedSource by remember(sources) { mutableStateOf(sources.singleOrNull()) }
    val showSourcePicker = sources.size > 1
    val showVolumePicker = volumes.size > 1
    var burnSubtitleIndex by remember(selectedSource) { mutableStateOf<Int?>(null) }
    val qualityOptions =
        remember(selectedSource) {
            val source = selectedSource
            val options =
                if (source == null) {
                    VideoQuality.settingsLadder()
                } else {
                    VideoQuality.optionsFor(
                        sourceBitrate = source.bitrate?.toInt(),
                        sourceWidth =
                            source.mediaStreams
                                .firstOrNull { it.type == MediaStreamType.VIDEO }
                                ?.width ?: source.width,
                    )
                }
            options.filterNot { it.isAuto }.map { it.maxBitrate }
        }
    var qualityBitrate by
        remember(initialQualityBitrate, qualityOptions) {
            mutableStateOf(
                initialQualityBitrate.takeIf { it in qualityOptions }
                    ?: VideoQuality.ORIGINAL_BITRATE
            )
        }
    val imageSubtitles =
        remember(selectedSource) {
            selectedSource?.mediaStreams.orEmpty().filter { stream ->
                stream.type == MediaStreamType.SUBTITLE &&
                    !stream.isExternal &&
                    stream.codec.lowercase() !in
                        AndroidDeviceProfileFactory.DOWNLOAD_TEXT_SUBTITLE_CODECS
            }
        }
    val isConverting = qualityBitrate > 0
    val audioStreams =
        remember(selectedSource) {
            selectedSource?.mediaStreams.orEmpty().filter { stream ->
                stream.type == MediaStreamType.AUDIO && !stream.isExternal
            }
        }
    var audioStreamIndex by
        remember(selectedSource) {
            mutableStateOf(
                (audioStreams.firstOrNull { it.isDefault } ?: audioStreams.firstOrNull())?.index
            )
        }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text =
                        stringResource(
                            if (showSourcePicker) R.string.quality_dialog_title
                            else R.string.action_download
                        ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                if (showSourcePicker) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.quality_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(sources, key = { it.id }) { source ->
                            QualityOption(
                                source = source,
                                isSelected = selectedSource == source,
                                onSelect = { selectedSource = source },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                PickerDropdown(
                    label = stringResource(R.string.download_quality_title),
                    selected = qualityBitrate,
                    options = qualityOptions,
                    optionLabel = { bitrate ->
                        settingsQualityLabel(VideoQuality.fromBitrate(bitrate))
                    },
                    onSelected = { qualityBitrate = it },
                )

                if (isConverting && audioStreams.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))

                    PickerDropdown(
                        label = stringResource(R.string.download_audio_track_title),
                        selected = audioStreamIndex,
                        options = audioStreams.map<AfinityMediaStream, Int?> { it.index },
                        optionLabel = { index ->
                            audioStreams
                                .firstOrNull { it.index == index }
                                ?.let { it.displayTitle ?: it.language }
                                .orEmpty()
                        },
                        onSelected = { audioStreamIndex = it },
                    )
                }

                if (isConverting && imageSubtitles.isNotEmpty()) {
                    val noneLabel = stringResource(R.string.download_burn_subtitles_none)
                    Spacer(modifier = Modifier.height(8.dp))

                    PickerDropdown(
                        label = stringResource(R.string.download_burn_subtitles_title),
                        selected = burnSubtitleIndex,
                        options = listOf<Int?>(null) + imageSubtitles.map { it.index },
                        optionLabel = { index ->
                            imageSubtitles
                                .firstOrNull { it.index == index }
                                ?.let { it.displayTitle ?: it.language } ?: noneLabel
                        },
                        onSelected = { burnSubtitleIndex = it },
                    )
                }

                if (showVolumePicker) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.download_location_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(volumes, key = { it.id }) { volume ->
                            VolumeOption(
                                volume = volume,
                                isSelected = selectedVolumeId == volume.id,
                                onSelect = { onVolumeSelected(volume.id) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            selectedSource?.let { source ->
                                onConfirm(
                                    source,
                                    selectedVolumeId,
                                    DownloadQuality(
                                        bitrate = qualityBitrate,
                                        burnSubtitleIndex =
                                            burnSubtitleIndex.takeIf { isConverting },
                                        audioStreamIndex =
                                            audioStreamIndex.takeIf {
                                                isConverting && audioStreams.size > 1
                                            },
                                    ),
                                )
                            }
                            onDismiss()
                        },
                        enabled = selectedSource != null,
                    ) {
                        Text(stringResource(R.string.action_download))
                    }
                }
            }
        }
    }
}

@Composable
fun StorageLocationDialog(
    volumes: List<StorageVolumeInfo>,
    selectedVolumeId: String?,
    onVolumeSelected: (String) -> Unit,
    onConfirm: (DownloadQuality) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialQualityBitrate: Int = VideoQuality.ORIGINAL_BITRATE,
) {
    val showVolumePicker = volumes.size > 1
    var qualityBitrate by remember(initialQualityBitrate) { mutableStateOf(initialQualityBitrate) }
    val qualityOptions = remember {
        VideoQuality.settingsLadder().filterNot { it.isAuto }.map { it.maxBitrate }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.action_download),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                PickerDropdown(
                    label = stringResource(R.string.download_quality_title),
                    selected = qualityBitrate,
                    options = qualityOptions,
                    optionLabel = { bitrate ->
                        settingsQualityLabel(VideoQuality.fromBitrate(bitrate))
                    },
                    onSelected = { qualityBitrate = it },
                )

                if (showVolumePicker) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.download_location_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        items(volumes, key = { it.id }) { volume ->
                            VolumeOption(
                                volume = volume,
                                isSelected = selectedVolumeId == volume.id,
                                onSelect = { onVolumeSelected(volume.id) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            onConfirm(DownloadQuality(bitrate = qualityBitrate))
                            onDismiss()
                        },
                        enabled = !showVolumePicker || selectedVolumeId != null,
                    ) {
                        Text(stringResource(R.string.action_download))
                    }
                }
            }
        }
    }
}

@Composable
fun MusicDownloadQualityDialog(
    initialBitrate: Int,
    onConfirm: (DownloadQuality) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBitrate by remember(initialBitrate) { mutableStateOf(initialBitrate) }
    val options = remember { MusicQuality.options() }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.download_quality_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(options, key = { it.maxBitrate }) { option ->
                        LabelOption(
                            label = musicQualityLabel(option),
                            isSelected = selectedBitrate == option.maxBitrate,
                            onSelect = { selectedBitrate = option.maxBitrate },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            onConfirm(DownloadQuality(bitrate = selectedBitrate))
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.action_download))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> PickerDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = optionLabel(selected),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        EndAlignedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VolumeOption(
    volume: StorageVolumeInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabelOption(
        label = volume.displayName,
        isSelected = isSelected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun LabelOption(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )

            if (isSelected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun QualityOption(
    source: AfinitySource,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formatFileSize(LocalContext.current, source.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isSelected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
