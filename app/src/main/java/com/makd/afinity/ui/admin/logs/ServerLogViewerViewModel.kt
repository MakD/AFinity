package com.makd.afinity.ui.admin.logs

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.admin.ServerLogTail
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.ServerAdminRepository
import com.makd.afinity.util.logging.LogClipboard
import com.makd.afinity.util.logging.LogExporter
import com.makd.afinity.util.logging.LogLevel
import com.makd.afinity.util.logging.LogSecretsCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.BitSet
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ServerLogViewerUiState(
    val failed: Boolean = false,
    val plain: ServerLogTail? = null,
    val view: ServerLogView? = null,
    val level: LogLevel? = null,
    val tags: Set<String> = emptySet(),
    val query: String = "",
    val searchActive: Boolean = false,
    val groupRepeats: Boolean = true,
    val groupFilter: Int? = null,
    val expandedKey: String? = null,
) {
    val isLoaded: Boolean
        get() = view != null || plain != null

    val isFiltered: Boolean
        get() = level != null || tags.isNotEmpty() || query.isNotBlank() || groupFilter != null
}

private class SearchResult(val query: String, val matches: BitSet)

private class LoadedLog(val document: ServerLogDocument?, val plain: ServerLogTail?)

@HiltViewModel
class ServerLogViewerViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val adminRepository: ServerAdminRepository,
    private val logSecretsCollector: LogSecretsCollector,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val MAX_PLAIN_LINES = 2_000
        private const val CONTEXT_ENTRIES = 20
        private const val SEARCH_DEBOUNCE_MS = 250L
        private const val CACHE_DIRECTORY = "server_logs"
        private const val CLIP_LABEL = "Server log"
    }

    val fileName: String = savedStateHandle.get<String>("name").orEmpty()

    private val cacheDirectory = File(context.cacheDir, CACHE_DIRECTORY)
    private val cacheFile = File(cacheDirectory, "${UUID.randomUUID()}.log")

    private val _state = MutableStateFlow(ServerLogViewerUiState())
    val state: StateFlow<ServerLogViewerUiState> = _state.asStateFlow()

    private var document: ServerLogDocument? = null
    @Volatile private var lastSearch: SearchResult? = null
    private var loadJob: Job? = null
    private var viewJob: Job? = null

    init {
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(failed = false) }
        loadJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { prepareCacheDirectory() }
            val failure = adminRepository.downloadLog(fileName, cacheFile).exceptionOrNull()
            if (failure != null) {
                if (failure !is NoActiveSessionException) {
                    Timber.w(failure, "Failed to load log file")
                }
                _state.update { it.copy(failed = true) }
                return@launch
            }
            val loaded =
                try {
                    withContext(Dispatchers.IO) { open() }
                } catch (e: IOException) {
                    Timber.w(e, "Failed to read log file")
                    null
                }
            if (loaded == null) {
                _state.update { it.copy(failed = true) }
                return@launch
            }
            document = loaded.document
            _state.update { it.copy(plain = loaded.plain) }
            rebuild()
        }
    }

    fun selectLevel(level: LogLevel?) {
        _state.update { it.copy(level = level, expandedKey = null) }
        rebuild()
    }

    fun toggleTag(tag: String) {
        _state.update {
            val tags = if (tag in it.tags) it.tags - tag else it.tags + tag
            it.copy(tags = tags, expandedKey = null)
        }
        rebuild()
    }

    fun clearTags() {
        _state.update { it.copy(tags = emptySet(), expandedKey = null) }
        rebuild()
    }

    fun clearFilters() {
        _state.update {
            it.copy(
                level = null,
                tags = emptySet(),
                query = "",
                groupFilter = null,
                groupRepeats = it.groupRepeats || it.groupFilter != null,
                expandedKey = null,
            )
        }
        rebuild()
    }

    fun showGroup(groupId: Int) {
        _state.update { it.copy(groupFilter = groupId, groupRepeats = false, expandedKey = null) }
        rebuild()
    }

    fun clearGroupFilter() {
        _state.update { it.copy(groupFilter = null, groupRepeats = true, expandedKey = null) }
        rebuild()
    }

    fun setSearchActive(active: Boolean) {
        val hadQuery = _state.value.query.isNotEmpty()
        _state.update {
            it.copy(
                searchActive = active,
                query = if (active) it.query else "",
                expandedKey = if (active) it.expandedKey else null,
            )
        }
        if (!active && hadQuery) rebuild()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, expandedKey = null) }
        rebuild(debounce = true)
    }

    fun toggleGrouping() {
        _state.update { it.copy(groupRepeats = !it.groupRepeats, expandedKey = null) }
        rebuild()
    }

    fun toggleExpanded(key: String) {
        _state.update { it.copy(expandedKey = if (it.expandedKey == key) null else key) }
    }

    fun copyRow(entryIndex: Int) {
        copy(entryIndex, entryIndex)
    }

    fun copyRowWithContext(entryIndex: Int) {
        copy(entryIndex - CONTEXT_ENTRIES, entryIndex)
    }

    override fun onCleared() {
        document?.close()
        cacheFile.delete()
    }

    private fun rebuild(debounce: Boolean = false) {
        val current = document ?: return
        viewJob?.cancel()
        viewJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val snapshot = _state.value
            val query = snapshot.query.trim()
            val view =
                try {
                    withContext(Dispatchers.Default) {
                        val matches = if (query.isEmpty()) null else search(current, query)
                        ensureActive()
                        current.view(
                            level = snapshot.level,
                            selectedTags = snapshot.tags,
                            matches = matches,
                            grouped = snapshot.groupRepeats,
                            groupId = snapshot.groupFilter,
                        )
                    }
                } catch (e: IOException) {
                    Timber.w(e, "Failed to search log file")
                    return@launch
                }
            _state.update { it.copy(view = view) }
        }
    }

    private suspend fun search(current: ServerLogDocument, query: String): BitSet {
        lastSearch?.let { if (it.query == query) return it.matches }
        val scope = currentCoroutineContext()
        val matches = current.search(query) { scope.ensureActive() }
        lastSearch = SearchResult(query, matches)
        return matches
    }

    private fun copy(fromIndex: Int, toIndex: Int) {
        val current = document ?: return
        viewModelScope.launch {
            val secrets = logSecretsCollector.collect()
            val text =
                withContext(Dispatchers.IO) {
                    LogExporter.scrub(current.rawText(fromIndex, toIndex), secrets)
                }
            LogClipboard.copy(context, CLIP_LABEL, text)
        }
    }

    private suspend fun open(): LoadedLog {
        val scope = currentCoroutineContext()
        val opened = ServerLogDocument.open(cacheFile) { scope.ensureActive() }
        if (opened != null) return LoadedLog(document = opened, plain = null)
        val tail = ServerLogDocument.readTail(cacheFile, MAX_PLAIN_LINES) { scope.ensureActive() }
        return LoadedLog(document = null, plain = tail)
    }

    private fun prepareCacheDirectory() {
        cacheDirectory.mkdirs()
        cacheDirectory.listFiles()?.forEach { stale -> stale.delete() }
    }
}
