package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.RecordingPadding
import com.makd.afinity.ui.components.AfinityTextField

@Composable
fun RecordingOptionsDialog(
    padding: RecordingPadding,
    onSave: (RecordingPadding) -> Unit,
    onCancelRecording: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pre by rememberSaveable { mutableStateOf(padding.prePaddingMinutes.toString()) }
    var post by rememberSaveable { mutableStateOf(padding.postPaddingMinutes.toString()) }
    val preMinutes = pre.toIntOrNull()?.takeIf { it >= 0 }
    val postMinutes = post.toIntOrNull()?.takeIf { it >= 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.livetv_recording_options)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaddingMinutesField(
                    value = pre,
                    onValueChange = { pre = it },
                    label = stringResource(R.string.livetv_start_when_possible),
                    supportingText = stringResource(R.string.livetv_minutes_before),
                )
                PaddingMinutesField(
                    value = post,
                    onValueChange = { post = it },
                    label = stringResource(R.string.livetv_stop_when_possible),
                    supportingText = stringResource(R.string.livetv_minutes_after),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (preMinutes != null && postMinutes != null) {
                        onSave(RecordingPadding(preMinutes, postMinutes))
                    }
                },
                enabled = preMinutes != null && postMinutes != null,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelRecording) {
                Text(
                    text = stringResource(R.string.livetv_cancel_recording),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
fun PaddingMinutesField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    AfinityTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = label,
        supportingText = supportingText,
        isError = value.toIntOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
