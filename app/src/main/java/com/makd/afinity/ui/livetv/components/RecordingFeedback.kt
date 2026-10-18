package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.ui.livetv.RecordingEvent
import com.makd.afinity.ui.livetv.RecordingPrompt
import kotlinx.coroutines.flow.SharedFlow
import org.jellyfin.sdk.model.api.RecordingStatus

@Composable
fun RecordingEventsSnackbar(
    events: SharedFlow<RecordingEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val scheduled = stringResource(R.string.livetv_recording_scheduled)
    val seriesScheduled = stringResource(R.string.livetv_series_recording_scheduled)
    val cancelled = stringResource(R.string.livetv_recording_cancelled)
    val seriesCancelled = stringResource(R.string.livetv_series_cancelled)
    val failed = stringResource(R.string.livetv_recording_failed)

    LaunchedEffect(events) {
        events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    RecordingEvent.SCHEDULED -> scheduled
                    RecordingEvent.SERIES_SCHEDULED -> seriesScheduled
                    RecordingEvent.CANCELLED -> cancelled
                    RecordingEvent.SERIES_CANCELLED -> seriesCancelled
                    RecordingEvent.FAILED -> failed
                }
            )
        }
    }
}

@Composable
fun RecordingPromptDialog(
    prompt: RecordingPrompt,
    onCancelTimer: () -> Unit,
    onCancelSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelTimerLabel =
        if (prompt.program.timerStatus == RecordingStatus.IN_PROGRESS) {
            stringResource(R.string.livetv_stop_recording_header)
        } else {
            stringResource(R.string.livetv_cancel_recording)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.livetv_confirm_recording_cancellation)) },
        confirmButton = {
            when (prompt) {
                is RecordingPrompt.ConfirmCancel ->
                    TextButton(onClick = onCancelTimer) {
                        Text(text = cancelTimerLabel, color = MaterialTheme.colorScheme.error)
                    }
                is RecordingPrompt.SeriesChoice ->
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = onCancelTimer) {
                            Text(text = cancelTimerLabel, color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = onCancelSeries) {
                            Text(
                                text = stringResource(R.string.livetv_cancel_series),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.livetv_keep_recording))
                        }
                    }
            }
        },
        dismissButton = {
            if (prompt is RecordingPrompt.ConfirmCancel) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.livetv_keep_recording))
                }
            }
        },
    )
}
