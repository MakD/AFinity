package com.makd.afinity.ui.admin.refresh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R

@Composable
fun RefreshMetadataDialog(
    itemId: String,
    onDismiss: () -> Unit,
    viewModel: RefreshMetadataViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!uiState.refreshing) onDismiss() },
        title = { Text(stringResource(R.string.admin_refresh_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.admin_refresh_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    RefreshModeOption(
                        label = stringResource(R.string.admin_refresh_mode_default),
                        description = stringResource(R.string.admin_refresh_mode_default_desc),
                        selected = uiState.mode == RefreshMode.Default,
                        onSelect = { viewModel.setMode(RefreshMode.Default) },
                    )
                    RefreshModeOption(
                        label = stringResource(R.string.admin_refresh_mode_validate),
                        description = stringResource(R.string.admin_refresh_mode_validate_desc),
                        selected = uiState.mode == RefreshMode.Validate,
                        onSelect = { viewModel.setMode(RefreshMode.Validate) },
                    )
                    RefreshModeOption(
                        label = stringResource(R.string.admin_refresh_mode_full),
                        description = stringResource(R.string.admin_refresh_mode_full_desc),
                        selected = uiState.mode == RefreshMode.Full,
                        onSelect = { viewModel.setMode(RefreshMode.Full) },
                    )
                }

                if (uiState.mode != RefreshMode.Default) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))

                    SwitchRow(
                        label = stringResource(R.string.admin_refresh_replace_images),
                        checked = uiState.replaceImages,
                        onToggle = { viewModel.toggleReplaceImages() },
                    )

                    if (uiState.mode == RefreshMode.Full) {
                        SwitchRow(
                            label = stringResource(R.string.admin_refresh_replace_metadata),
                            checked = uiState.replaceMetadata,
                            onToggle = { viewModel.toggleReplaceMetadata() },
                        )
                    }
                }

                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (uiState.refreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                TextButton(onClick = { viewModel.refresh() }) {
                    Text(stringResource(R.string.admin_btn_refresh))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.refreshing) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RefreshModeOption(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
