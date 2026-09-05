package com.makd.afinity.ui.settings.logs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.AfinityApplication
import com.makd.afinity.BuildConfig
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.util.logging.CrashReport
import com.makd.afinity.util.logging.CrashStore
import com.makd.afinity.util.logging.LabelPart
import com.makd.afinity.util.logging.LogClipboard
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
    private val crashStore: CrashStore,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val tree: RingBufferTree?
        get() = (context.applicationContext as? AfinityApplication)?.ringBufferTree

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    private var frozen: List<LogEntry>? = null
    private var visibleEntries: List<LogEntry> = emptyList()
    private var rebuildJob: Job? = null

    init {
        viewModelScope.launch {
            val source = tree ?: return@launch
            source.updates.onStart { emit(Unit) }.debounce(REFRESH_DEBOUNCE_MS).collect { rebuild() }
        }
        viewModelScope.launch { loadCrashes() }
    }

    fun selectLevel(level: LogLevel?) {
        _uiState.update { it.copy(scope = it.scope.copy(minLevel = level), expandedKey = null) }
        rebuild()
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                searchActive = active,
                scope = if (active) it.scope else it.scope.copy(query = ""),
            )
        }
        if (!active) rebuild()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(scope = it.scope.copy(query = query), expandedKey = null) }
        rebuild()
    }

    fun toggleTag(tag: String) {
        _uiState.update {
            val tags = it.scope.tags
            val next = if (tag in tags) tags - tag else tags + tag
            it.copy(scope = it.scope.copy(tags = next), expandedKey = null)
        }
        rebuild()
    }

    fun clearTags() {
        _uiState.update { it.copy(scope = it.scope.copy(tags = emptySet())) }
        rebuild()
    }

    fun setWindow(window: LogWindow) {
        _uiState.update { it.copy(scope = it.scope.copy(window = window)) }
        rebuild()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(scope = LogScope(window = it.scope.window), searchActive = false, expandedKey = null)
        }
        rebuild()
    }

    fun toggleGrouping() {
        _uiState.update { it.copy(groupRepeats = !it.groupRepeats) }
        rebuild()
    }

    fun setFollowing(following: Boolean) {
        if (_uiState.value.following == following) return
        frozen = if (following) null else snapshot()
        _uiState.update { it.copy(following = following) }
        rebuild()
    }

    fun toggleExpanded(key: String) {
        _uiState.update { it.copy(expandedKey = if (it.expandedKey == key) null else key) }
    }

    fun collapse() {
        _uiState.update { it.copy(expandedKey = null) }
    }

    fun openTab(tab: LogTab) {
        _uiState.update { it.copy(tab = tab, openCrash = null) }
        if (tab == LogTab.CRASHES) markCrashesSeen()
    }

    fun openCrash(report: CrashReport?) {
        _uiState.update { it.copy(openCrash = report) }
    }

    fun clearBuffer() {
        tree?.clear()
        frozen = null
        _uiState.update { it.copy(following = true, expandedKey = null) }
        rebuild()
    }

    fun deleteCrash(report: CrashReport) {
        viewModelScope.launch {
            crashStore.delete(report)
            _uiState.update { it.copy(openCrash = null) }
            loadCrashes()
        }
    }

    fun deleteAllCrashes() {
        viewModelScope.launch {
            crashStore.deleteAll()
            _uiState.update { it.copy(openCrash = null) }
            loadCrashes()
        }
    }

    fun export(visibleOnly: Boolean) {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val entries = if (visibleOnly) visibleEntries else null
            LogExporter.export(context, tree, logSecretsCollector.collect(), entries)
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun shareCrash(report: CrashReport) {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            LogExporter.exportCrash(context, report, logSecretsCollector.collect())
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun copyVisible() {
        val source = tree ?: return
        copyText(source.format(visibleEntries))
    }

    fun copyRow(row: TimelineRow.Event) {
        val source = tree ?: return
        val entries =
            if (row.count <= 1) {
                listOf(row.entry)
            } else {
                visibleEntries.filter { groupKey(it) == row.key }.ifEmpty { listOf(row.entry) }
            }
        copyText(source.format(entries))
    }

    fun copyRowWithContext(row: TimelineRow.Event) {
        val source = tree ?: return
        val all = frozen ?: snapshot()
        val index = all.indexOfFirst { it.sequence == row.entry.sequence }
        val entries =
            if (index < 0) {
                listOf(row.entry)
            } else {
                all.subList((index - CONTEXT_LINES).coerceAtLeast(0), index + 1)
            }
        copyText(source.format(entries))
    }

    fun copyCrashForIssue(report: CrashReport) {
        copyText(
            buildString {
                appendLine("```")
                appendLine(LogExporter.buildHeader(context))
                appendLine()
                appendLine(LogExporter.buildCrashSummary(report))
                appendLine()
                appendLine(report.stackTrace)
                appendLine("```")
            }
        )
    }

    private fun copyText(text: String) {
        viewModelScope.launch {
            val secrets = logSecretsCollector.collect()
            val scrubbed = withContext(Dispatchers.Default) { LogExporter.scrub(text, secrets) }
            LogClipboard.copy(context, CLIP_LABEL, scrubbed)
        }
    }

    private suspend fun loadCrashes() {
        val reports = crashStore.load()
        val lastSeen =
            preferencesRepository.getStringPreference(KEY_LAST_SEEN_CRASH)?.toLongOrNull() ?: 0L
        val unseen = reports.count { it.timeMillis > lastSeen }
        _uiState.update {
            it.copy(crashes = reports, unseenCrashes = unseen, newCrashCount = unseen)
        }
    }

    private fun markCrashesSeen() {
        val newest = _uiState.value.crashes.maxOfOrNull { it.timeMillis } ?: return
        viewModelScope.launch {
            preferencesRepository.setStringPreference(KEY_LAST_SEEN_CRASH, newest.toString())
            _uiState.update { it.copy(unseenCrashes = 0) }
        }
    }

    private fun snapshot(): List<LogEntry> = tree?.snapshot().orEmpty()

    private fun rebuild() {
        rebuildJob?.cancel()
        rebuildJob =
            viewModelScope.launch {
                val entries = frozen ?: snapshot()
                val state = _uiState.value
                val anchor = frozen?.lastOrNull()?.timeMillis ?: System.currentTimeMillis()
                val result =
                    withContext(Dispatchers.Default) {
                        build(entries, state.scope, state.density, state.groupRepeats, anchor)
                    }
                visibleEntries = result.matching
                _uiState.update {
                    it.copy(
                        revision = it.revision + 1,
                        rows = result.rows,
                        errorCount = entries.count { entry -> entry.level == LogLevel.ERROR },
                        warningCount = entries.count { entry -> entry.level >= LogLevel.WARN },
                        totalCount = entries.size,
                        matchCount = result.matching.size,
                        groupCount = result.rows.count { row -> row is TimelineRow.Event },
                        bufferCapacity = tree?.capacity ?: 0,
                        errorRowIndices = result.errorRowIndices,
                        tagCounts = result.tagCounts,
                    )
                }
            }
    }

    private data class BuildResult(
        val rows: List<TimelineRow>,
        val matching: List<LogEntry>,
        val tagCounts: List<LogTagCount>,
        val errorRowIndices: List<Int>,
    )

    private fun build(
        entries: List<LogEntry>,
        scope: LogScope,
        density: LogDensity,
        groupRepeats: Boolean,
        anchorMillis: Long,
    ): BuildResult {
        val tagCounts = countTags(entries)
        val since = scope.window.durationMillis?.let { anchorMillis - it }
        val query = scope.query.trim()

        val matching =
            entries.filter { entry ->
                (since == null || entry.timeMillis >= since) &&
                    (scope.minLevel == null || entry.level >= scope.minLevel) &&
                    (scope.tags.isEmpty() || entry.tag in scope.tags) &&
                    (query.isEmpty() || entry.matches(query))
            }

        val rows = mutableListOf<TimelineRow>()
        if (entries.firstOrNull()?.sequence == 0L) {
            rows.add(
                TimelineRow.Launch(
                    key = "launch",
                    timeMillis = tree?.launchTimeMillis ?: entries.first().timeMillis,
                    version = versionLabel,
                )
            )
        }

        if (matching.isEmpty()) {
            return BuildResult(emptyList(), matching, tagCounts, emptyList())
        }

        val comfortable = density == LogDensity.COMFORTABLE

        if (comfortable && groupRepeats) {
            val groups = LinkedHashMap<String, MutableList<LogEntry>>()
            matching.forEach { entry -> groups.getOrPut(groupKey(entry)) { mutableListOf() }.add(entry) }

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
                        highlights = representative.message.highlights(query),
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
                        relativeLabel = if (comfortable) relativeLabel(previous, entry) else null,
                        count = 1,
                        occurrenceTimes = emptyList(),
                        highlights = entry.message.highlights(query),
                    )
                )
                previous = entry
            }
        }

        val errorRowIndices =
            rows.indices.filter { index ->
                val row = rows[index]
                row is TimelineRow.Event && row.entry.level == LogLevel.ERROR
            }

        return BuildResult(rows, matching, tagCounts, errorRowIndices)
    }

    private fun countTags(entries: List<LogEntry>): List<LogTagCount> {
        if (entries.isEmpty()) return emptyList()
        val totals = HashMap<String, IntArray>()
        entries.forEach { entry ->
            val slot = totals.getOrPut(entry.tag) { IntArray(3) }
            slot[0]++
            if (entry.level == LogLevel.WARN) slot[1]++
            if (entry.level == LogLevel.ERROR) slot[2]++
        }
        return totals
            .map { (tag, slot) -> LogTagCount(tag, slot[0], slot[1], slot[2]) }
            .sortedWith(compareByDescending<LogTagCount> { it.total }.thenBy { it.tag })
    }

    private fun groupKey(entry: LogEntry): String =
        "${entry.tag}|${entry.level}|${LogSignatures.of(entry.message).chunks.hashCode()}"

    private fun LogEntry.matches(query: String): Boolean =
        message.contains(query, ignoreCase = true) ||
            tag.contains(query, ignoreCase = true) ||
            stackTrace?.contains(query, ignoreCase = true) == true

    private fun String.highlights(query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var index = indexOf(query, ignoreCase = true)
        while (index >= 0) {
            ranges.add(index until index + query.length)
            index = indexOf(query, index + query.length, ignoreCase = true)
        }
        return ranges
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

    private val versionLabel: String
        get() =
            "${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"})"

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 200L
        const val GAP_THRESHOLD_MS = 3000L
        const val CONTEXT_LINES = 20
        const val CLIP_LABEL = "AFinity logs"
        const val KEY_LAST_SEEN_CRASH = "log_viewer_last_seen_crash"
    }
}