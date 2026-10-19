package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.GuideIndicator
import com.makd.afinity.data.models.livetv.ProgramCategory
import com.makd.afinity.ui.components.AfinityBadge
import org.jellyfin.sdk.model.api.RecordingStatus

val RecordingRed = Color(0xFFCC3333)

fun AfinityProgram.guideIndicator(enabled: Set<GuideIndicator>): GuideIndicator? =
    when {
        isLive && GuideIndicator.LIVE in enabled -> GuideIndicator.LIVE
        isPremiere && GuideIndicator.PREMIERE in enabled -> GuideIndicator.PREMIERE
        isSeries && !isRepeat && GuideIndicator.NEW in enabled -> GuideIndicator.NEW
        isSeries && isRepeat && GuideIndicator.REPEAT in enabled -> GuideIndicator.REPEAT
        else -> null
    }

fun AfinityProgram.matchesCategories(categories: Set<ProgramCategory>): Boolean {
    if (categories.containsAll(ProgramCategory.entries)) return true
    return when {
        isKids -> ProgramCategory.KIDS in categories
        isSports -> ProgramCategory.SPORTS in categories
        isNews -> ProgramCategory.NEWS in categories
        isMovie -> ProgramCategory.MOVIES in categories
        isSeries -> ProgramCategory.SHOWS in categories
        else -> false
    }
}

fun AfinityProgram.categoryColor(): Color? =
    when {
        isKids -> Color(0xFF039BE5)
        isSports -> Color(0xFF3949AB)
        isNews -> Color(0xFF43A047)
        isMovie -> Color(0xFF5E35B1)
        else -> null
    }

@Composable
fun ProgramIndicatorBadge(
    indicator: GuideIndicator,
    modifier: Modifier = Modifier,
    textStyle: TextStyle? = null,
) {
    when (indicator) {
        GuideIndicator.LIVE -> LiveBadge(modifier = modifier, textStyle = textStyle)
        GuideIndicator.NEW ->
            AfinityBadge(
                text = stringResource(R.string.livetv_badge_new),
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                textStyle = textStyle,
            )
        GuideIndicator.PREMIERE ->
            AfinityBadge(
                text = stringResource(R.string.livetv_badge_premiere),
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                textStyle = textStyle,
            )
        GuideIndicator.REPEAT ->
            AfinityBadge(
                text = stringResource(R.string.livetv_badge_repeat),
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                textStyle = textStyle,
            )
        GuideIndicator.HD ->
            AfinityBadge(
                text = stringResource(R.string.meta_res_hd),
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                textStyle = textStyle,
            )
    }
}

@Composable
fun ProgramTimerIcon(program: AfinityProgram, size: Dp, modifier: Modifier = Modifier) {
    if (program.timerId == null && program.seriesTimerId == null) return
    val cancelled = (program.timerStatus ?: RecordingStatus.CANCELLED) == RecordingStatus.CANCELLED

    if (program.seriesTimerId != null) {
        Icon(
            painter = painterResource(id = R.drawable.ic_record_series),
            contentDescription = stringResource(R.string.livetv_action_record_series),
            tint = if (cancelled) LocalContentColor.current.copy(alpha = 0.7f) else RecordingRed,
            modifier = modifier.size(size),
        )
    } else {
        Icon(
            painter = painterResource(id = R.drawable.ic_record),
            contentDescription = stringResource(R.string.livetv_action_record),
            tint = RecordingRed,
            modifier = modifier.size(size),
        )
    }
}
