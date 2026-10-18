package com.makd.afinity.ui.livetv.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.ui.components.formatRuntimeTicks
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TICKS_PER_MINUTE = 600_000_000L

@Composable
fun rememberProgramTimeFormatter(): DateTimeFormatter {
    val timePattern = stringResource(R.string.livetv_time_pattern)
    return remember(timePattern) { DateTimeFormatter.ofPattern(timePattern) }
}

@Composable
fun programDayLabel(date: LocalDateTime, now: LocalDateTime): String {
    val day = date.toLocalDate()
    val today = now.toLocalDate()
    return when (day) {
        today -> stringResource(R.string.time_today)
        today.plusDays(1) -> stringResource(R.string.livetv_tomorrow)
        else -> {
            val datePattern = stringResource(R.string.livetv_guide_date_fmt)
            val dateFormatter = remember(datePattern) { DateTimeFormatter.ofPattern(datePattern) }
            day.format(dateFormatter)
        }
    }
}

@Composable
fun programTimeRange(program: AfinityProgram, formatter: DateTimeFormatter): String? {
    val start = program.startDate ?: return null
    val end = program.endDate ?: return start.format(formatter)
    return stringResource(
        R.string.livetv_epg_time_range,
        start.format(formatter),
        end.format(formatter),
    )
}

@Composable
fun programDuration(program: AfinityProgram): String? =
    program.durationMinutes?.takeIf { it > 0 }?.let { formatRuntimeTicks(it * TICKS_PER_MINUTE) }

@Composable
fun programEpisodeNumber(program: AfinityProgram): String? {
    if (!program.isSeries) return null
    val season = program.seasonNumber ?: return null
    val episode = program.episodeNumber ?: return null
    return stringResource(R.string.playlist_entry_episode_fmt, season, episode)
}

fun minutesToTicks(minutes: Long): Long = minutes * TICKS_PER_MINUTE
