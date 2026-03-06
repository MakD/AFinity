package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import timber.log.Timber

@Composable
fun PrimaryPlaybackButton(
    item: AfinityItem,
    nextEpisode: AfinityEpisode?,
    selectedMediaSource: MediaSourceOption?,
    onPlayRequested: (AfinityItem, PlaybackSelection) -> Unit,
) {
    val targetItem =
        when (item) {
            is AfinityShow,
            is AfinitySeason -> nextEpisode
            else -> item
        }

    if (targetItem == null) {
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.status_loading))
        }
        return
    }

    val (buttonText, buttonIcon) =
        when {
            targetItem.playbackPositionTicks > 0 &&
                targetItem.playbackPositionTicks >= targetItem.runtimeTicks -> {
                stringResource(R.string.action_rewatch) to
                    painterResource(id = R.drawable.ic_replay)
            }
            targetItem.playbackPositionTicks > 0 && targetItem.runtimeTicks > 0 -> {
                stringResource(R.string.action_resume_playback) to
                    painterResource(id = R.drawable.ic_player_play_filled)
            }
            else -> {
                stringResource(R.string.action_play) to
                    painterResource(id = R.drawable.ic_player_play_filled)
            }
        }

    PlaybackSelectionButton(
        item = targetItem,
        buttonText = buttonText,
        buttonIcon = buttonIcon,
        onPlayClick = { selection ->
            if (targetItem.sources.isEmpty()) {
                Timber.w("Item ${targetItem.name} has no media sources")
                return@PlaybackSelectionButton
            }

            val mediaSourceId =
                selectedMediaSource?.id
                    ?: targetItem.sources
                        .firstOrNull {
                            it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL
                        }
                        ?.id
                    ?: targetItem.sources.firstOrNull()?.id
                    ?: ""

            val startPositionMs =
                if (targetItem.playbackPositionTicks > 0) {
                    targetItem.playbackPositionTicks / 10000
                } else 0L

            val finalSelection =
                selection.copy(mediaSourceId = mediaSourceId, startPositionMs = startPositionMs)

            onPlayRequested(targetItem, finalSelection)
        },
    )
}
