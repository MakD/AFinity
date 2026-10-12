package com.makd.afinity.ui.admin.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminDayToggles
import com.makd.afinity.ui.admin.components.AdminTimeDialog
import com.makd.afinity.ui.admin.components.AdminValueTile
import com.makd.afinity.ui.components.AfinityTextField
import org.jellyfin.sdk.model.api.DayOfWeek
import org.jellyfin.sdk.model.api.TaskTriggerInfo
import org.jellyfin.sdk.model.api.TaskTriggerInfoType

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val DEFAULT_TIME_MINUTES = 3 * MINUTES_PER_HOUR
private const val DEFAULT_INTERVAL_MINUTES = MINUTES_PER_DAY
private const val SELECTED_OPTION_ALPHA = 0.08f

private val INTERVAL_OPTIONS_MINUTES =
    listOf(15, 30, 45, 60, 2 * 60, 3 * 60, 4 * 60, 6 * 60, 8 * 60, 12 * 60, 24 * 60)

private val TIME_LIMIT_OPTIONS_HOURS = listOf(1, 2, 4, 8, 12)

private val TRIGGER_TYPES =
    listOf(
        TaskTriggerInfoType.DAILY_TRIGGER,
        TaskTriggerInfoType.WEEKLY_TRIGGER,
        TaskTriggerInfoType.INTERVAL_TRIGGER,
        TaskTriggerInfoType.STARTUP_TRIGGER,
    )

@Composable
internal fun TaskScheduleDialog(
    initial: TaskTriggerInfo?,
    onSave: (TaskTriggerInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialTimeMinutes =
        initial?.timeOfDayTicks?.let {
            (it / TICKS_PER_MINUTE).toInt().coerceIn(0, MINUTES_PER_DAY - 1)
        } ?: DEFAULT_TIME_MINUTES
    val initialIntervalMinutes =
        initial?.intervalTicks?.let { (it / TICKS_PER_MINUTE).toInt() } ?: DEFAULT_INTERVAL_MINUTES
    val initialLimitTicks = initial?.maxRuntimeTicks
    val intervalOptions =
        remember(initialIntervalMinutes) {
            (INTERVAL_OPTIONS_MINUTES + initialIntervalMinutes).distinct().sorted()
        }

    var type by rememberSaveable {
        mutableStateOf(initial?.type ?: TaskTriggerInfoType.DAILY_TRIGGER)
    }
    var timeMinutes by rememberSaveable { mutableIntStateOf(initialTimeMinutes) }
    var day by rememberSaveable { mutableStateOf(initial?.dayOfWeek ?: DayOfWeek.MONDAY) }
    var intervalMinutes by rememberSaveable { mutableIntStateOf(initialIntervalMinutes) }
    var limitTicks by rememberSaveable { mutableStateOf(initialLimitTicks) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showCustomLimit by rememberSaveable { mutableStateOf(false) }

    val limitOptions =
        remember(initialLimitTicks, limitTicks) {
            val listed = TIME_LIMIT_OPTIONS_HOURS.map { it * TICKS_PER_HOUR }
            val extra = listOfNotNull(initialLimitTicks, limitTicks)
            listOf<Long?>(null) + (listed + extra).distinct().sorted()
        }

    val usesTime =
        type == TaskTriggerInfoType.DAILY_TRIGGER || type == TaskTriggerInfoType.WEEKLY_TRIGGER
    val draft =
        TaskTriggerInfo(
            type = type,
            timeOfDayTicks = if (usesTime) timeMinutes * TICKS_PER_MINUTE else null,
            intervalTicks =
                if (type == TaskTriggerInfoType.INTERVAL_TRIGGER) {
                    intervalMinutes * TICKS_PER_MINUTE
                } else {
                    null
                },
            dayOfWeek = if (type == TaskTriggerInfoType.WEEKLY_TRIGGER) day else null,
            maxRuntimeTicks = limitTicks,
        )
    val summary =
        listOfNotNull(
                if (usesTime) {
                    stringResource(R.string.task_schedule_summary_server_time_fmt, draft.label())
                } else {
                    stringResource(R.string.task_schedule_summary_fmt, draft.label())
                },
                limitTicks?.let {
                    stringResource(
                        R.string.task_schedule_summary_limit_fmt,
                        formatDurationTicks(it),
                    )
                },
            )
            .joinToString(" ")

    if (showTimePicker) {
        AdminTimeDialog(
            title = stringResource(R.string.task_trigger_server_time),
            initialMinutes = timeMinutes,
            onConfirm = { minutes ->
                timeMinutes = minutes
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showCustomLimit) {
        CustomTimeLimitDialog(
            initialTicks = limitTicks,
            onConfirm = { ticks ->
                limitTicks = ticks
                showCustomLimit = false
            },
            onDismiss = { showCustomLimit = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    stringResource(
                        if (initial == null) R.string.task_schedule_add
                        else R.string.task_schedule_edit
                    ),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TRIGGER_TYPES.forEach { option ->
                        KindTile(
                            type = option,
                            selected = option == type,
                            onClick = { type = option },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (type == TaskTriggerInfoType.WEEKLY_TRIGGER) {
                    AdminDayToggles(
                        selected = setOf(java.time.DayOfWeek.valueOf(day.name)),
                        onToggle = { picked -> day = DayOfWeek.valueOf(picked.name) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (usesTime) {
                        AdminValueTile(
                            label = stringResource(R.string.task_trigger_server_time),
                            value =
                                formatTimeOfDay(
                                    timeMinutes / MINUTES_PER_HOUR,
                                    timeMinutes % MINUTES_PER_HOUR,
                                ),
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (type == TaskTriggerInfoType.INTERVAL_TRIGGER) {
                        MenuTile(
                            label = stringResource(R.string.task_trigger_interval_label),
                            value = formatDurationCompact(intervalMinutes.toLong()),
                            options = intervalOptions,
                            selected = intervalMinutes,
                            optionLabel = { formatDurationMinutes(it.toLong()) },
                            onSelect = { intervalMinutes = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MenuTile(
                        label = stringResource(R.string.task_schedule_time_limit),
                        value = timeLimitLabel(limitTicks, compact = true),
                        options = limitOptions,
                        selected = limitTicks,
                        optionLabel = { timeLimitLabel(it, compact = false) },
                        onSelect = { limitTicks = it },
                        modifier = Modifier.weight(1f),
                        extraLabel = stringResource(R.string.task_schedule_limit_custom),
                        onExtra = { showCustomLimit = true },
                    )
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(
                    stringResource(
                        if (initial == null) R.string.action_add else R.string.action_save
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun KindTile(
    type: TaskTriggerInfoType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color =
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = type.iconRes()),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(type.typeLabelRes()),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun <T> MenuTile(
    label: String,
    value: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    extraLabel: String? = null,
    onExtra: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AdminValueTile(
            label = label,
            value = value,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier =
                        Modifier.background(
                            if (option == selected) {
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = SELECTED_OPTION_ALPHA
                                )
                            } else {
                                Color.Transparent
                            }
                        ),
                )
            }
            if (extraLabel != null) {
                DropdownMenuItem(
                    text = { Text(text = extraLabel) },
                    onClick = {
                        expanded = false
                        onExtra()
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomTimeLimitDialog(
    initialTicks: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialTicks?.let(::formatLimitHours).orEmpty()) }
    val ticks = parseLimitHours(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.task_schedule_time_limit),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            AfinityTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.task_schedule_limit_hours_label),
                isError = text.isNotBlank() && ticks == null,
                supportingText = stringResource(R.string.task_schedule_limit_hours_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { ticks?.let(onConfirm) }, enabled = ticks != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun timeLimitLabel(ticks: Long?, compact: Boolean): String =
    when {
        ticks == null -> stringResource(R.string.task_schedule_no_limit)
        compact -> formatDurationCompact(ticks / TICKS_PER_MINUTE)
        else -> formatDurationTicks(ticks)
    }

private fun TaskTriggerInfoType.typeLabelRes(): Int =
    when (this) {
        TaskTriggerInfoType.DAILY_TRIGGER -> R.string.task_trigger_type_daily
        TaskTriggerInfoType.WEEKLY_TRIGGER -> R.string.task_trigger_type_weekly
        TaskTriggerInfoType.INTERVAL_TRIGGER -> R.string.task_trigger_type_interval
        TaskTriggerInfoType.STARTUP_TRIGGER -> R.string.task_trigger_type_startup
    }
