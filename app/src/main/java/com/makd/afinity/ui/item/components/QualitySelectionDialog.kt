package com.makd.afinity.ui.item.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinitySource

@Composable
fun QualitySelectionDialog(
    sources: List<AfinitySource>,
    onSourceSelected: (AfinitySource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSource by remember { mutableStateOf<AfinitySource?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.quality_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.quality_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources) { source ->
                        QualityOption(
                            source = source,
                            isSelected = selectedSource == source,
                            onSelect = { selectedSource = source },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            selectedSource?.let { onSourceSelected(it) }
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
private fun QualityOption(
    source: AfinitySource,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        tonalElevation = if (isSelected) 4.dp else 0.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter =
                    if (isSelected) painterResource(id = R.drawable.ic_radio_button_checked)
                    else painterResource(id = R.drawable.ic_radio_button_unchecked),
                contentDescription =
                    if (isSelected) stringResource(R.string.cd_selected)
                    else stringResource(R.string.cd_not_selected),
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )

                Spacer(modifier = Modifier.height(4.dp))

                val sizeInMB = source.size / (1024 * 1024)
                val sizeText =
                    if (sizeInMB > 1024) {
                        stringResource(R.string.file_size_gb, sizeInMB / 1024.0)
                    } else {
                        stringResource(R.string.file_size_mb, sizeInMB)
                    }

                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
