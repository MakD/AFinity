package com.makd.afinity.ui.settings.logs

import com.makd.afinity.util.logging.CrashReport
import com.makd.afinity.util.logging.LabelPart
import com.makd.afinity.util.logging.LogEntry
import com.makd.afinity.util.logging.LogLevel

enum class LogDensity {
    COMPACT,
    COMFORTABLE,
}

enum class LogTab {
    LOGS,
    CRASHES,
}

enum class LogWindow(val durationMillis: Long?) {
    ALL(null),
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(5 * 60_000L),
    FIFTEEN_MINUTES(15 * 60_000L),
}

enum class LogEmptyReason {
    NONE,
    BUFFER_EMPTY,
    NO_MATCHES,
    NO_CRASHES,
}

sealed interface TimelineRow {
    val key: String

    data class Launch(override val key: String, val timeMillis: Long, val version: String) :
        TimelineRow

    data class Event(
        override val key: String,
        val entry: LogEntry,
        val label: List<LabelPart>,
        val relativeLabel: String?,
        val count: Int,
        val occurrenceTimes: List<Long>,
        val highlights: List<IntRange>,
    ) : TimelineRow

    data class Gap(override val key: String, val durationMillis: Long) : TimelineRow
}

data class LogTagCount(val tag: String, val total: Int, val warnings: Int, val errors: Int)

data class LogScope(
    val minLevel: LogLevel? = null,
    val query: String = "",
    val tags: Set<String> = emptySet(),
    val window: LogWindow = LogWindow.ALL,
) {
    val isFiltered: Boolean
        get() = minLevel != null || query.isNotBlank() || tags.isNotEmpty() || window != LogWindow.ALL
}

data class LogViewerUiState(
    val revision: Long = 0L,
    val tab: LogTab = LogTab.LOGS,
    val rows: List<TimelineRow> = emptyList(),
    val scope: LogScope = LogScope(),
    val following: Boolean = true,
    val groupRepeats: Boolean = true,
    val searchActive: Boolean = false,
    val expandedKey: String? = null,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val totalCount: Int = 0,
    val matchCount: Int = 0,
    val groupCount: Int = 0,
    val bufferCapacity: Int = 0,
    val errorRowIndices: List<Int> = emptyList(),
    val tagCounts: List<LogTagCount> = emptyList(),
    val crashes: List<CrashReport> = emptyList(),
    val unseenCrashes: Int = 0,
    val newCrashCount: Int = 0,
    val openCrash: CrashReport? = null,
    val isExporting: Boolean = false,
) {
    val density: LogDensity
        get() =
            if (following && scope.minLevel == null) LogDensity.COMPACT else LogDensity.COMFORTABLE

    val emptyReason: LogEmptyReason
        get() =
            when {
                tab == LogTab.CRASHES ->
                    if (crashes.isEmpty()) LogEmptyReason.NO_CRASHES else LogEmptyReason.NONE
                rows.isNotEmpty() -> LogEmptyReason.NONE
                totalCount == 0 -> LogEmptyReason.BUFFER_EMPTY
                else -> LogEmptyReason.NO_MATCHES
            }
}