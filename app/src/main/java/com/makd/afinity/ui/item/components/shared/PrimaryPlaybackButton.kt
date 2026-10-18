package com.makd.afinity.ui.item.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySourceType
import timber.log.Timber

@Composable
fun PrimaryPlaybackButton(
    item: AfinityItem,
    nextEpisode: AfinityEpisode?,
    selectedMediaSource: MediaSourceOption?,
    isLoading: Boolean,
    onPlayRequested: (AfinityItem, PlaybackSelection) -> Unit,
) {
    val targetItem =
        when (item) {
            is AfinityShow,
            is AfinitySeason -> nextEpisode
            else -> item
        }

    val isUnplayable = targetItem == null || (targetItem as? AfinityEpisode)?.missing == true

    if (isUnplayable) {
        if (isLoading && targetItem == null) {
            DetailPrimaryButton(
                text = stringResource(R.string.status_loading),
                onClick = {},
                enabled = false,
                isLoading = true,
            )
        } else {
            DetailPrimaryButton(
                text = stringResource(R.string.play_error_missing_source),
                onClick = {},
                icon = painterResource(id = R.drawable.ic_alert_triangle),
                enabled = false,
            )
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
                    ?: targetItem.sources.firstOrNull { it.type == AfinitySourceType.LOCAL }?.id
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
