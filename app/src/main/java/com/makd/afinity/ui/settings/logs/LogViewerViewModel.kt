package com.makd.afinity.ui.settings.logs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.AfinityApplication
import com.makd.afinity.util.logging.LabelPart
import com.makd.afinity.util.logging.LogEntry
import com.makd.afinity.util.logging.LogExporter
import com.makd.afinity.util.logging.LogLevel
import com.makd.afinity.util.logging.LogSecretsCollector
import com.makd.afinity.util.logging.LogSignatures
import com.makd.afinity.util.logging.RingBufferTree
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class LogViewerViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val logSecretsCollector: LogSecretsCollector,
) : ViewModel() {

    private val tree: RingBufferTree?
        get() = (context.applicationContext as? AfinityApplication)?.ringBufferTree

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    private var frozen: List<LogEntry>? = null
    private var rebuildJob: Job? = null

    init {
        viewModelScope.launch {
            val source = tree ?: return@launch
            source.updates.onStart { emit(Unit) }.debounce(REFRESH_DEBOUNCE_MS).collect { rebuild() }
        }
    }

    fun selectLevel(level: LogLevel?) {
        val scope = if (level == null) LogScope() else LogScope(levels = setOf(level))
        val following = level == null && frozen == null
        if (level == null) frozen = null
        _uiState.update { it.copy(scope = scope, following = following) }
        rebuild()
    }

    fun toggleGrouping() {
        _uiState.update { it.copy(groupRepeats = !it.groupRepeats) }
        rebuild()
    }

    fun setFollowing(following: Boolean) {
        frozen = if (following) null else snapshot()
        _uiState.update { it.copy(following = following) }
        rebuild()
    }

    fun clearBuffer() {
        tree?.clear()
        frozen = null
        rebuild()
    }

    fun export(visibleOnly: Boolean) {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val entries =
                if (visibleOnly) {
                    _uiState.value.rows.filterIsInstance<TimelineRow.Event>().map { it.entry }
                } else {
                    null
                }
            LogExporter.export(context, tree, logSecretsCollector.collect(), entries)
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    private fun snapshot(): List<LogEntry> = tree?.snapshot().orEmpty()

    private fun rebuild() {
        rebuildJob?.cancel()
        rebuildJob =
            viewModelScope.launch {
                val entries = frozen ?: snapshot()
                val state = _uiState.value
                val result =
                    withContext(Dispatchers.Default) {
                        build(entries, state.scope, state.density, state.groupRepeats)
                    }
                _uiState.update {
                    it.copy(
                        revision = it.revision + 1,
                        rows = result.rows,
                        errorCount = entries.count { entry -> entry.level == LogLevel.ERROR },
                        warningCount = entries.count { entry -> entry.level == LogLevel.WARN },
                        totalCount = entries.size,
                        matchCount = result.matchCount,
                        groupCount = result.rows.count { row -> row is TimelineRow.Event },
                        bufferCapacity = BUFFER_CAPACITY,
                    )
                }
            }
    }

    private data class BuildResult(val rows: List<TimelineRow>, val matchCount: Int)

    private fun build(
        entries: List<LogEntry>,
        scope: LogScope,
        density: LogDensity,
        groupRepeats: Boolean,
    ): BuildResult {
        val matching =
            if (scope.levels == null) entries else entries.filter { it.level in scope.levels }
        if (matching.isEmpty()) return BuildResult(emptyList(), 0)

        val comfortable = density == LogDensity.COMFORTABLE
        val rows = mutableListOf<TimelineRow>()

        if (comfortable && groupRepeats) {
            val groups = LinkedHashMap<String, MutableList<LogEntry>>()
            matching.forEach { entry ->
                val key =
                    "${entry.tag}|${entry.level}|${LogSignatures.of(entry.message).chunks.hashCode()}"
                groups.getOrPut(key) { mutableListOf() }.add(entry)
            }

            var previous: LogEntry? = null
            groups.forEach { (key, group) ->
                val representative = group.first()
                appendGap(rows, previous, representative)
                rows.add(
                    TimelineRow.Event(
                        key = key,
                        entry = representative,
                        label = LogSignatures.label(group.map { LogSignatures.of(it.message) }),
                        relativeLabel = relativeLabel(previous, representative),
                        count = group.size,
                        occurrenceTimes = group.map { it.timeMillis },
                    )
                )
                previous = representative
            }
        } else {
            var previous: LogEntry? = null
            matching.forEach { entry ->
                if (comfortable) appendGap(rows, previous, entry)
                rows.add(
                    TimelineRow.Event(
                        key = "e${entry.sequence}",
                        entry = entry,
                        label = listOf(LabelPart.Literal(entry.message)),
                        relativeLabel =
                            if (comfortable) relativeLabel(previous, entry) else null,
                        count = 1,
                        occurrenceTimes = emptyList(),
                    )
                )
                previous = entry
            }
        }

        return BuildResult(rows, matching.size)
    }

    private fun appendGap(rows: MutableList<TimelineRow>, previous: LogEntry?, next: LogEntry) {
        val delta = previous?.let { next.timeMillis - it.timeMillis } ?: return
        if (delta >= GAP_THRESHOLD_MS) {
            rows.add(TimelineRow.Gap(key = "g${next.sequence}", durationMillis = delta))
        }
    }

    private fun relativeLabel(previous: LogEntry?, next: LogEntry): String? {
        val delta = previous?.let { next.timeMillis - it.timeMillis } ?: return null
        return if (delta in 1 until GAP_THRESHOLD_MS) "+%.1fs".format(delta / 1000f) else null
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 200L
        const val GAP_THRESHOLD_MS = 3000L
        const val BUFFER_CAPACITY = 2000
    }
}
