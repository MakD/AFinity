package com.makd.afinity.ui.admin.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import org.jellyfin.sdk.model.api.TaskCompletionStatus

internal enum class TaskTone {
    NEUTRAL,
    ACTIVE,
    DONE,
    ERROR,
}

internal fun taskIconRes(key: String?): Int =
    when (key) {
        "RefreshLibrary" -> R.drawable.ic_refresh
        "AudioNormalization" -> R.drawable.ic_equalizer
        "DownloadLyrics" -> R.drawable.ic_microphone
        "DownloadSubtitles" -> R.drawable.ic_subtitles
        "RefreshChapterImages" -> R.drawable.ic_photo_search
        "RefreshTrickplayImages" -> R.drawable.ic_animation
        "KeyframeExtraction" -> R.drawable.ic_key
        "TaskExtractMediaSegments" -> R.drawable.ic_scissors
        "RefreshPeople" -> R.drawable.ic_users_group
        "TmdbRefreshUpcomingEpisodes" -> R.drawable.ic_calendar
        "RefreshGuide" -> R.drawable.ic_epg
        "RefreshInternetChannels" -> R.drawable.ic_channels
        "CleanActivityLog" -> R.drawable.ic_history
        "DeleteCacheFiles",
        "CleanLogFiles",
        "DeleteTranscodeFiles" -> R.drawable.ic_broom
        "MoveTrickplayImages" -> R.drawable.ic_folder
        "OptimizeDatabaseTask" -> R.drawable.ic_database
        "CleanupUserDataTask" -> R.drawable.ic_user
        "PluginUpdates" -> R.drawable.ic_puzzle
        else -> R.drawable.ic_schedule
    }

internal fun TaskCompletionStatus.labelRes(): Int =
    when (this) {
        TaskCompletionStatus.COMPLETED -> R.string.task_status_completed
        TaskCompletionStatus.FAILED -> R.string.task_status_failed
        TaskCompletionStatus.CANCELLED -> R.string.task_status_cancelled
        TaskCompletionStatus.ABORTED -> R.string.task_status_aborted
    }

internal fun formatTaskProgress(progress: Double): String = "${"%.1f".format(progress)}%"

@Composable
internal fun TaskTile(
    iconRes: Int,
    tone: TaskTone,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val colors = MaterialTheme.colorScheme
    val container =
        when (tone) {
            TaskTone.NEUTRAL -> colors.surfaceContainerHighest
            TaskTone.ACTIVE -> colors.primaryContainer
            TaskTone.DONE -> colors.tertiaryContainer
            TaskTone.ERROR -> colors.errorContainer
        }
    val content =
        when (tone) {
            TaskTone.NEUTRAL -> colors.onSurfaceVariant
            TaskTone.ACTIVE -> colors.onPrimaryContainer
            TaskTone.DONE -> colors.onTertiaryContainer
            TaskTone.ERROR -> colors.onErrorContainer
        }

    Box(
        modifier = modifier.size(40.dp).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
    }
}
