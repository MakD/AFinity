package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VideoQualitySelection(
    mediaSourceOptions: List<MediaSourceOption>,
    selectedSource: MediaSourceOption?,
    onSourceSelected: (MediaSourceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mediaSourceOptions.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            items(mediaSourceOptions) { source ->
                QualityChip(
                    source = source,
                    isSelected = source == selectedSource,
                    onClick = { onSourceSelected(source) },
                )
            }
        }
    }
}

@Composable
private fun QualityChip(source: MediaSourceOption, isSelected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
            ),
        border =
            BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            ),
    ) {
        Text(
            text =
                if (
                    source.name.contains("K") ||
                        source.name.contains("p") ||
                        source.name.contains(source.codec, ignoreCase = true)
                ) {
                    source.name
                } else {
                    "${source.name} • ${source.quality} ${source.codec}"
                }
        )
    }
}
