package com.makd.afinity.ui.item.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.makd.afinity.data.models.media.AfinityItem

data class PlaybackSelection(
    val mediaSourceId: String,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val videoStreamIndex: Int?,
    val startPositionMs: Long = 0L,
)

data class MediaSourceOption(
    val id: String,
    val name: String,
    val quality: String,
    val codec: String,
    val size: Long,
    val isDefault: Boolean = false,
)

@Composable
fun PlaybackSelectionButton(
    item: AfinityItem,
    buttonText: String,
    buttonIcon: Painter,
    onPlayClick: (PlaybackSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailPrimaryButton(
        text = buttonText,
        icon = buttonIcon,
        modifier = modifier,
        onClick = {
            val startPositionMs =
                if (item.playbackPositionTicks > 0) {
                    item.playbackPositionTicks / 10000
                } else {
                    0L
                }

            val selectedSource =
                item.sources.firstOrNull {
                    it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                } ?: item.sources.firstOrNull()

            onPlayClick(
                PlaybackSelection(
                    mediaSourceId = selectedSource?.id ?: "",
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                    videoStreamIndex = null,
                    startPositionMs = startPositionMs,
                )
            )
        },
    )
}
