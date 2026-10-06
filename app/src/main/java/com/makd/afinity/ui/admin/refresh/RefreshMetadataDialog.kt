package com.makd.afinity.ui.admin.refresh

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinitySwitch

private val OptionShape = RoundedCornerShape(16.dp)

@Composable
fun RefreshMetadataDialog(
    itemId: String,
    itemName: String,
    onDismiss: () -> Unit,
    includesChildren: Boolean = false,
    viewModel: RefreshMetadataViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var mode by remember(itemId) { mutableStateOf(RefreshMode.Scan) }
    var replaceImages by remember(itemId) { mutableStateOf(false) }
    var regenerateTrickplay by remember(itemId) { mutableStateOf(false) }
    var refreshing by remember(itemId) { mutableStateOf(false) }
    var failed by remember(itemId) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!refreshing) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.admin_refresh_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (includesChildren) {
                    Text(
                        text = stringResource(R.string.admin_refresh_includes_children),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RefreshModeOption(
                        label = stringResource(R.string.admin_refresh_mode_scan),
                        description = stringResource(R.string.admin_refresh_mode_scan_desc),
                        selected = mode == RefreshMode.Scan,
                        enabled = !refreshing,
                        onSelect = { mode = RefreshMode.Scan },
                    )
                    RefreshModeOption(
                        label = stringResource(R.string.admin_refresh_mode_missing),
                        description = stringResource(R.string.admin_refresh_mode_missing_desc),
                        selected = mode == RefreshMode.Missing,
                        enabled = !refreshing,
                        onSelect = { mode = RefreshMode.Missing },
                    )
                    RefreshModeOption(
                        label = stringResource(R.string.admin_refresh_mode_replace),
                        description = stringResource(R.string.admin_refresh_mode_replace_desc),
                        selected = mode == RefreshMode.ReplaceAll,
                        enabled = !refreshing,
                        onSelect = { mode = RefreshMode.ReplaceAll },
                    )
                }

                if (mode != RefreshMode.Scan) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SwitchRow(
                        label = stringResource(R.string.admin_refresh_replace_images),
                        checked = replaceImages,
                        enabled = !refreshing,
                        onToggle = { replaceImages = it },
                    )
                    SwitchRow(
                        label = stringResource(R.string.admin_refresh_replace_trickplay),
                        checked = regenerateTrickplay,
                        enabled = !refreshing,
                        onToggle = { regenerateTrickplay = it },
                    )
                }

                if (failed) {
                    Text(
                        text = stringResource(R.string.admin_refresh_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    refreshing = true
                    failed = false
                    viewModel.refresh(itemId, mode, replaceImages, regenerateTrickplay) { queued ->
                        refreshing = false
                        if (queued) {
                            Toast.makeText(
                                    context,
                                    R.string.admin_refresh_queued,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                            currentOnDismiss()
                        } else {
                            failed = true
                        }
                    }
                },
                enabled = !refreshing,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.admin_btn_refresh))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !refreshing) {
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
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(OptionShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.surfaceContainerHighest
                    else Color.Transparent
                )
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    onClick = onSelect,
                    role = Role.RadioButton,
                )
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(OptionShape)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onToggle,
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AfinitySwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
