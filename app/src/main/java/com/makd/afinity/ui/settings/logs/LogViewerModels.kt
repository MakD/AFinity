package com.makd.afinity.ui.settings.logs

import com.makd.afinity.util.logging.LabelPart
import com.makd.afinity.util.logging.LogEntry
import com.makd.afinity.util.logging.LogLevel

enum class LogDensity {
    COMPACT,
    COMFORTABLE,
}

sealed interface TimelineRow {
    data class Event(
        val key: String,
        val entry: LogEntry,
        val label: List<LabelPart>,
        val relativeLabel: String?,
        val count: Int,
        val occurrenceTimes: List<Long>,
    ) : TimelineRow

    data class Gap(val key: String, val durationMillis: Long) : TimelineRow
}

data class LogScope(val levels: Set<LogLevel>? = null) {
    val isFiltered: Boolean
        get() = levels != null
}

data class LogViewerUiState(
    val revision: Long = 0L,
    val rows: List<TimelineRow> = emptyList(),
    val scope: LogScope = LogScope(),
    val following: Boolean = true,
    val groupRepeats: Boolean = true,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val totalCount: Int = 0,
    val matchCount: Int = 0,
    val groupCount: Int = 0,
    val bufferCapacity: Int = 0,
    val isExporting: Boolean = false,
) {
    val density: LogDensity
        get() =
            if (following && !scope.isFiltered) LogDensity.COMPACT else LogDensity.COMFORTABLE
}
