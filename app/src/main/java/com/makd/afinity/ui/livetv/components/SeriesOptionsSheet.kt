package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.RecordingPadding
import com.makd.afinity.data.models.livetv.SeriesRecordingOptions
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.MinimalSwitchTile

@Composable
fun SeriesOptionsSheet(
    options: SeriesRecordingOptions,
    subtitle: String?,
    onSave: (SeriesRecordingOptions) -> Unit,
    onCancelSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    var recordNewOnly by rememberSaveable { mutableStateOf(options.recordNewOnly) }
    var skipInLibrary by rememberSaveable { mutableStateOf(options.skipEpisodesInLibrary) }
    var anyChannel by rememberSaveable { mutableStateOf(options.recordAnyChannel) }
    var anyTime by rememberSaveable { mutableStateOf(options.recordAnyTime) }
    var keepUpTo by rememberSaveable { mutableIntStateOf(options.keepUpTo) }
    var pre by rememberSaveable { mutableStateOf(options.padding.prePaddingMinutes.toString()) }
    var post by rememberSaveable { mutableStateOf(options.padding.postPaddingMinutes.toString()) }
    val preMinutes = pre.toIntOrNull()
    val postMinutes = post.toIntOrNull()
    val timeFormatter = rememberProgramTimeFormatter()

    AfinityModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.livetv_series_options),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SectionLabel(stringResource(R.string.livetv_label_record))
            TwoWayChoice(
                first = stringResource(R.string.livetv_new_episodes_only),
                second = stringResource(R.string.livetv_all_episodes),
                firstSelected = recordNewOnly,
                onSelect = { recordNewOnly = it },
            )
            Spacer(Modifier.height(12.dp))
            MinimalSwitchTile(
                title = stringResource(R.string.livetv_skip_episodes_in_library),
                subtitle = stringResource(R.string.livetv_skip_episodes_in_library_help),
                checked = skipInLibrary,
                onCheckedChange = { skipInLibrary = it },
            )

            SectionLabel(stringResource(R.string.livetv_label_channels))
            TwoWayChoice(
                first =
                    options.channelName?.let {
                        stringResource(R.string.livetv_channel_name_only, it)
                    } ?: stringResource(R.string.livetv_one_channel),
                second = stringResource(R.string.livetv_all_channels),
                firstSelected = !anyChannel,
                onSelect = { anyChannel = !it },
            )

            options.startDate?.let { startDate ->
                SectionLabel(stringResource(R.string.livetv_label_air_time))
                TwoWayChoice(
                    first =
                        stringResource(
                            R.string.livetv_around_time,
                            startDate.format(timeFormatter),
                        ),
                    second = stringResource(R.string.livetv_anytime),
                    firstSelected = !anyTime,
                    onSelect = { anyTime = !it },
                )
            }

            SectionLabel(stringResource(R.string.livetv_label_keep_up_to))
            KeepUpToField(value = keepUpTo, onValueChange = { keepUpTo = it })

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                PaddingMinutesField(
                    value = pre,
                    onValueChange = { pre = it },
                    label = stringResource(R.string.livetv_start_when_possible),
                    supportingText = stringResource(R.string.livetv_minutes_before),
                    modifier = Modifier.weight(1f),
                )
                PaddingMinutesField(
                    value = post,
                    onValueChange = { post = it },
                    label = stringResource(R.string.livetv_stop_when_possible),
                    supportingText = stringResource(R.string.livetv_minutes_after),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            TextButton(onClick = onCancelSeries) {
                Text(
                    text = stringResource(R.string.livetv_cancel_series),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (preMinutes != null && postMinutes != null) {
                        onSave(
                            options.copy(
                                recordNewOnly = recordNewOnly,
                                skipEpisodesInLibrary = skipInLibrary,
                                recordAnyChannel = anyChannel,
                                recordAnyTime = anyTime,
                                keepUpTo = keepUpTo,
                                padding = RecordingPadding(preMinutes, postMinutes),
                            )
                        )
                    }
                },
                enabled = preMinutes != null && postMinutes != null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun TwoWayChoice(
    first: String,
    second: String,
    firstSelected: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(first to true, second to false).forEachIndexed { index, (label, isFirst) ->
            SegmentedButton(
                selected = firstSelected == isFirst,
                onClick = { onSelect(isFirst) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
            ) {
                Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun KeepUpToField(value: Int, onValueChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(
                    text = keepUpToLabel(value),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier.heightIn(max = 320.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            (0..KEEP_UP_TO_MAX).forEach { count ->
                DropdownMenuItem(
                    text = { Text(keepUpToLabel(count)) },
                    onClick = {
                        expanded = false
                        onValueChange(count)
                    },
                    modifier =
                        Modifier.background(
                            if (count == value) {
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = SELECTED_OPTION_ALPHA
                                )
                            } else {
                                Color.Transparent
                            }
                        ),
                )
            }
        }
    }
}

@Composable
private fun keepUpToLabel(count: Int): String =
    if (count == 0) {
        stringResource(R.string.livetv_as_many_as_possible)
    } else {
        pluralStringResource(R.plurals.livetv_episode_count, count, count)
    }

private const val KEEP_UP_TO_MAX = 50
private const val SELECTED_OPTION_ALPHA = 0.08f
