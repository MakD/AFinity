package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityItem

data class PlaybackSelection(
    val mediaSourceId: String,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val videoStreamIndex: Int?,
    val startPositionMs: Long = 0L
)

data class MediaSourceOption(
    val id: String,
    val name: String,
    val quality: String,
    val codec: String,
    val size: Long,
    val isDefault: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSelectionButton(
    item: AfinityItem,
    buttonText: String,
    buttonIcon: ImageVector,
    onPlayClick: (PlaybackSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            val startPositionMs = if (item.playbackPositionTicks > 0) {
                item.playbackPositionTicks / 10000
            } else {
                0L
            }

            val selectedSource = item.sources.firstOrNull {
                it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
            } ?: item.sources.firstOrNull()

            onPlayClick(
                PlaybackSelection(
                    mediaSourceId = selectedSource?.id ?: "",
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    videoStreamIndex = null,
                    startPositionMs = startPositionMs
                )
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = buttonIcon,
                contentDescription = null
            )
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}