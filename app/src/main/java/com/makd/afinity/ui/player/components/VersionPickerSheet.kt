package com.makd.afinity.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinitySource
import java.util.Locale

/**
 * Floating overlay (matching the existing audio/subtitle selector style) that lists
 * all available media versions for a merged-versions item. Used inside the player
 * and triggered from [PlayerControls].
 */
@Composable
fun VersionPickerSheet(
    sources: List<AfinitySource>,
    currentSourceId: String?,
    onVersionSelected: (AfinitySource) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clickable(onClick = {}) // consume — do not propagate
                .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .width(240.dp)
                .heightIn(max = 180.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.player_version_title),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            sources.forEach { source ->
                val isSelected = source.id == currentSourceId
                VersionItem(
                    source = source,
                    isSelected = isSelected,
                    onClick = {
                        onVersionSelected(source)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun VersionItem(
    source: AfinitySource,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else Color.Transparent
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Spacer(Modifier.width(22.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            // Primary label — plugin sets name from folder/file structure e.g. "1080p BluRay"
            Text(
                text = source.name.ifBlank { stringResource(R.string.player_version_default_label) },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
            )

            // Sub-label with tech details
            val sub = buildVersionSubLabel(source)
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun buildVersionSubLabel(source: AfinitySource): String {
    return buildList {
        val w = source.width
        val h = source.height
        if (w != null && h != null && w > 0 && h > 0) add("${w}×${h}")
        source.videoCodec?.uppercase()?.let { if (it.isNotBlank()) add(it) }
        source.bitrate?.let { bps ->
            val mbps = bps / 1_000_000.0
            add(String.format(Locale.US, "%.1f Mbps", mbps))
        }
        source.container?.uppercase()?.let { if (it.isNotBlank()) add(it) }
    }.joinToString(" · ")
}
