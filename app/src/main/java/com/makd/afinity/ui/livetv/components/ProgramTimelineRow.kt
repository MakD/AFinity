package com.makd.afinity.ui.livetv.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.ui.item.components.shared.MetadataTimeLeft
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.RecordingStatus

private val TimeColumnWidth = 60.sp
private val TimeGutter = 12.dp
private val RailColumnWidth = 18.dp
private val FirstLineHeight = 18.sp

@Composable
fun ProgramTimelineRow(
    program: AfinityProgram,
    now: LocalDateTime,
    showDay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    recordingStyle: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val isAiring = program.isCurrentlyAiring(now)
    val nodeColor =
        when {
            recordingStyle -> RecordingRed
            isAiring -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val filledNode = recordingStyle && isAiring
    val density = LocalDensity.current
    val timeWidth = with(density) { TimeColumnWidth.toDp() }
    val firstLineHeight = with(density) { FirstLineHeight.toDp() }
    val railCenterPx = with(density) { (timeWidth + TimeGutter + RailColumnWidth / 2).toPx() }
    val railWidthPx = with(density) { 1.dp.toPx() }

    val tagLine =
        listOfNotNull(
                program.channelName?.takeIf { recordingStyle },
                programEpisodeNumber(program),
                programDuration(program),
            )
            .joinToString(" · ")
    val channelNumber = program.channelNumber?.takeIf { recordingStyle && it.isNotBlank() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val tagText =
        remember(channelNumber, tagLine, primaryColor) {
            buildAnnotatedString {
                if (channelNumber != null) {
                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append(channelNumber)
                    }
                    if (tagLine.isNotEmpty()) append(' ')
                }
                append(tagLine)
            }
        }

    Row(
        modifier =
            modifier.fillMaxWidth().clickable(onClick = onClick).drawBehind {
                val center =
                    if (layoutDirection == LayoutDirection.Ltr) railCenterPx
                    else size.width - railCenterPx
                drawRect(
                    color = railColor,
                    topLeft = Offset(center - railWidthPx / 2f, 0f),
                    size = Size(railWidthPx, size.height),
                )
            }
    ) {
        TimelineTimeColumn(time = program.startDate, showDay = showDay, now = now)

        Spacer(modifier = Modifier.width(TimeGutter))

        Box(
            modifier = Modifier.width(RailColumnWidth).height(firstLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.requiredSize(17.dp)
                        .clip(CircleShape)
                        .background(
                            if (filledNode) nodeColor else MaterialTheme.colorScheme.background
                        )
                        .border(2.dp, nodeColor, CircleShape)
            )
            when {
                filledNode ->
                    Box(
                        modifier =
                            Modifier.requiredSize(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                    )
                isAiring || recordingStyle ->
                    Box(
                        modifier =
                            Modifier.requiredSize(9.dp).clip(CircleShape).background(nodeColor)
                    )
            }
        }

        Row(modifier = Modifier.weight(1f).padding(start = 14.dp, bottom = 22.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.heightIn(min = firstLineHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val end = program.endDate
                    if (isAiring && end != null && !recordingStyle) {
                        LiveBadge()
                        MetadataTimeLeft(
                            progress = program.getProgressPercent(now) / 100f,
                            remainingTicks = minutesToTicks(Duration.between(now, end).toMinutes()),
                        )
                    }
                    if (tagText.isNotEmpty()) {
                        Text(
                            text = tagText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = program.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val end = program.endDate
                if (recordingStyle && isAiring && end != null) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RecBadge()
                        MetadataTimeLeft(
                            progress = program.getProgressPercent(now) / 100f,
                            remainingTicks = minutesToTicks(Duration.between(now, end).toMinutes()),
                        )
                    }
                }
                if (program.overview.isNotEmpty()) {
                    Text(
                        text = program.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

class TimelineRecording(
    val busyIds: Set<UUID>,
    val onRecordClick: (AfinityProgram) -> Unit,
)

class TimelineScheduled(
    val busyIds: Set<UUID>,
    val onCancelClick: ((AfinityProgram) -> Unit)?,
)

fun LazyListScope.programTimelineItems(
    keyPrefix: String,
    programs: List<AfinityProgram>,
    now: LocalDateTime,
    horizontalPadding: Dp,
    onProgramClick: (AfinityProgram) -> Unit,
    recording: TimelineRecording?,
    endOfGuideText: String? = null,
    scheduled: TimelineScheduled? = null,
) {
    val rowModifier = Modifier.padding(start = horizontalPadding + 4.dp, end = horizontalPadding)

    itemsIndexed(items = programs, key = { _, program -> "$keyPrefix${program.id}" }) {
        index,
        program ->
        val previousDay = programs.getOrNull(index - 1)?.startDate?.toLocalDate()
        ProgramTimelineRow(
            program = program,
            now = now,
            showDay = index == 0 || program.startDate?.toLocalDate() != previousDay,
            onClick = { onProgramClick(program) },
            modifier = rowModifier,
            recordingStyle = scheduled != null,
            trailing =
                when {
                    scheduled?.onCancelClick != null -> {
                        { TimelineCancelButton(program = program, scheduled = scheduled) }
                    }
                    recording != null -> {
                        { TimelineRecordButton(program = program, recording = recording) }
                    }
                    else -> null
                },
        )
    }

    val last = programs.lastOrNull()
    val end = last?.endDate
    if (endOfGuideText != null && end != null) {
        item(key = "${keyPrefix}end") {
            ProgramTimelineEndRow(
                time = end,
                now = now,
                showDay = end.toLocalDate() != last.startDate?.toLocalDate(),
                text = endOfGuideText,
                modifier = rowModifier,
            )
        }
    }
}

@Composable
private fun TimelineRecordButton(program: AfinityProgram, recording: TimelineRecording) {
    val bySeries = program.hasActiveTimer && program.seriesTimerId != null
    RecordIconButton(
        iconRes = if (bySeries) R.drawable.ic_record_series else R.drawable.ic_record,
        contentDescription =
            stringResource(
                if (program.hasActiveTimer) R.string.livetv_do_not_record
                else R.string.livetv_action_record
            ),
        iconSize = 24.dp,
        active = program.hasActiveTimer,
        onClick =
            if (program.id in recording.busyIds) null
            else {
                { recording.onRecordClick(program) }
            },
    )
}

@Composable
private fun TimelineCancelButton(program: AfinityProgram, scheduled: TimelineScheduled) {
    val inProgress = program.timerStatus == RecordingStatus.IN_PROGRESS
    val onCancelClick = scheduled.onCancelClick
    IconButton(
        onClick = { onCancelClick?.invoke(program) },
        enabled = onCancelClick != null && program.id !in scheduled.busyIds,
    ) {
        Icon(
            painter =
                painterResource(
                    id = if (inProgress) R.drawable.ic_player_stop_filled else R.drawable.ic_close
                ),
            contentDescription =
                stringResource(
                    if (inProgress) R.string.livetv_stop_recording
                    else R.string.livetv_cancel_recording
                ),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun RecordIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    iconSize: Dp,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    IconButton(onClick = { onClick?.invoke() }, enabled = onClick != null) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = if (active) RecordingRed else LocalContentColor.current,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun ProgramTimelineEndRow(
    time: LocalDateTime,
    now: LocalDateTime,
    showDay: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        TimelineTimeColumn(time = time, showDay = showDay, now = now)

        Spacer(modifier = Modifier.width(TimeGutter))

        Column(
            modifier = Modifier.width(RailColumnWidth).height(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(4) {
                Box(
                    modifier =
                        Modifier.width(1.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
    }
}

@Composable
private fun TimelineTimeColumn(time: LocalDateTime?, showDay: Boolean, now: LocalDateTime) {
    val timeFormatter = rememberProgramTimeFormatter()
    val density = LocalDensity.current
    val timeWidth = with(density) { TimeColumnWidth.toDp() }
    val firstLineHeight = with(density) { FirstLineHeight.toDp() }

    Column(modifier = Modifier.width(timeWidth), horizontalAlignment = Alignment.End) {
        Text(
            text = time?.format(timeFormatter).orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier =
                Modifier.height(firstLineHeight).wrapContentHeight(Alignment.CenterVertically),
        )
        if (showDay && time != null) {
            Text(
                text = programDayLabel(time, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                maxLines = 2,
            )
        }
    }
}
