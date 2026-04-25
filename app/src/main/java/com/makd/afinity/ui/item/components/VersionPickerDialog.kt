package com.makd.afinity.ui.item.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinitySource

/**
 * A card-style dialog for selecting a media version before playback starts. Used from
 * [ItemDetailScreen] when an item has multiple merged versions.
 */
@Composable
fun VersionPickerDialog(
    sources: List<AfinitySource>,
    onVersionSelected: (AfinitySource) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.player_version_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.version_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(sources, key = { it.id }) { source ->
                        VersionOption(
                            source = source,
                            isSelected = false,
                            onSelect = { onVersionSelected(source) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionOption(
    source: AfinitySource,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color =
            if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val label =
                    source.name.takeIf { it.isNotBlank() && it != "Default" }
                        ?: stringResource(R.string.player_version_default_label)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )

                val details = buildVersionDetail(source)
                if (details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun buildVersionDetail(source: AfinitySource): String {
    val parts = mutableListOf<String>()
    val h = source.height
    val w = source.width
    if (h != null && h > 0) {
        parts +=
            when {
                h > 2160 -> "8K"
                h > 1080 -> "4K"
                h > 720 -> "1080p"
                h > 480 -> "720p"
                else -> "${h}p"
            }
    } else if (w != null && w > 0) {
        parts += "${w}×?"
    }
    source.videoCodec?.uppercase()?.let { parts += it }
    source.audioCodec?.uppercase()?.let { parts += it }
    val bitrate = source.bitrate
    if (bitrate != null && bitrate > 0) {
        parts += "${bitrate / 1_000_000} Mbps"
    }
    return parts.joinToString(" · ")
}
