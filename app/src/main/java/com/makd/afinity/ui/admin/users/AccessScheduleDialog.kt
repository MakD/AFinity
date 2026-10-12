package com.makd.afinity.ui.admin.users

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminDayToggles
import com.makd.afinity.ui.admin.components.AdminTimeDialog
import com.makd.afinity.ui.admin.components.AdminValueTile
import com.makd.afinity.ui.admin.components.rememberAdminTimeFormatter
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val DEFAULT_START_MINUTES = 8 * MINUTES_PER_HOUR
private const val DEFAULT_END_MINUTES = 20 * MINUTES_PER_HOUR
private const val DAY_EVERYDAY = "Everyday"
private const val DAY_WEEKDAY = "Weekday"
private const val DAY_WEEKEND = "Weekend"

private val ALL_DAYS = DayOfWeek.entries.toSet()
private val WEEKEND_DAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val WEEKDAY_DAYS = ALL_DAYS - WEEKEND_DAYS

private enum class TimeField {
    START,
    END,
}

@Composable
internal fun AccessScheduleDialog(
    onConfirm: (List<AccessWindow>) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by rememberSaveable { mutableStateOf(emptySet<DayOfWeek>()) }
    var startMinutes by rememberSaveable { mutableIntStateOf(DEFAULT_START_MINUTES) }
    var endMinutes by rememberSaveable { mutableIntStateOf(DEFAULT_END_MINUTES) }
    var editing by rememberSaveable { mutableStateOf<TimeField?>(null) }
    val effectiveEnd = if (endMinutes == 0) MINUTES_PER_DAY else endMinutes
    val isOrdered = startMinutes < effectiveEnd

    when (editing) {
        TimeField.START ->
            AdminTimeDialog(
                title = stringResource(R.string.parental_schedule_start),
                initialMinutes = startMinutes,
                onConfirm = { minutes ->
                    startMinutes = minutes
                    editing = null
                },
                onDismiss = { editing = null },
            )
        TimeField.END ->
            AdminTimeDialog(
                title = stringResource(R.string.parental_schedule_end),
                initialMinutes = endMinutes,
                onConfirm = { minutes ->
                    endMinutes = minutes
                    editing = null
                },
                onDismiss = { editing = null },
            )
        null -> Unit
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.parental_add_schedule),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DayPresets(selected = days, onSelect = { days = it })
                AdminDayToggles(
                    selected = days,
                    onToggle = { day -> days = if (day in days) days - day else days + day },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdminValueTile(
                        label = stringResource(R.string.parental_schedule_start),
                        value = formatAccessHour(startMinutes.toDouble() / MINUTES_PER_HOUR),
                        onClick = { editing = TimeField.START },
                        modifier = Modifier.weight(1f),
                    )
                    AdminValueTile(
                        label = stringResource(R.string.parental_schedule_end),
                        value = formatAccessHour(endMinutes.toDouble() / MINUTES_PER_HOUR),
                        onClick = { editing = TimeField.END },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text =
                        if (isOrdered) stringResource(R.string.parental_schedule_midnight_hint)
                        else stringResource(R.string.parental_schedule_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isOrdered) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        accessWindows(
                            days = days,
                            startHour = startMinutes.toDouble() / MINUTES_PER_HOUR,
                            endHour = effectiveEnd.toDouble() / MINUTES_PER_HOUR,
                        )
                    )
                },
                enabled = days.isNotEmpty() && isOrdered,
            ) {
                Text(stringResource(R.string.action_add))
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
private fun DayPresets(selected: Set<DayOfWeek>, onSelect: (Set<DayOfWeek>) -> Unit) {
    val presets =
        listOf(
            R.string.parental_day_everyday to ALL_DAYS,
            R.string.parental_day_weekday to WEEKDAY_DAYS,
            R.string.parental_day_weekend to WEEKEND_DAYS,
        )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (label, presetDays) ->
            val isSelected = selected == presetDays
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) emptySet() else presetDays) },
                label = { Text(stringResource(label)) },
                shape = CircleShape,
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

private fun DayOfWeek.serverName(): String =
    name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

private fun accessWindows(
    days: Set<DayOfWeek>,
    startHour: Double,
    endHour: Double,
): List<AccessWindow> {
    val names =
        if (days.size == ALL_DAYS.size) {
            listOf(DAY_EVERYDAY)
        } else {
            buildList {
                var remaining = days
                if (remaining.containsAll(WEEKDAY_DAYS)) {
                    add(DAY_WEEKDAY)
                    remaining = remaining - WEEKDAY_DAYS
                }
                if (remaining.containsAll(WEEKEND_DAYS)) {
                    add(DAY_WEEKEND)
                    remaining = remaining - WEEKEND_DAYS
                }
                remaining.sorted().forEach { add(it.serverName()) }
            }
        }
    return names.map { AccessWindow(day = it, startHour = startHour, endHour = endHour) }
}

@Composable
internal fun accessDayLabel(day: String): String =
    when (day) {
        DAY_EVERYDAY -> stringResource(R.string.parental_day_everyday)
        DAY_WEEKDAY -> stringResource(R.string.parental_day_weekday)
        DAY_WEEKEND -> stringResource(R.string.parental_day_weekend)
        else ->
            runCatching { DayOfWeek.valueOf(day.uppercase(Locale.ROOT)) }
                .getOrNull()
                ?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: day
    }

@Composable
internal fun formatAccessHour(hour: Double): String {
    val totalMinutes = (hour * MINUTES_PER_HOUR).roundToInt().coerceIn(0, MINUTES_PER_DAY)
    val time =
        LocalTime.of(
            totalMinutes / MINUTES_PER_HOUR % 24,
            totalMinutes % MINUTES_PER_HOUR,
        )
    return time.format(rememberAdminTimeFormatter())
}
