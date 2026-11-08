package com.makd.afinity.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.subtitles.AddedSubtitle
import com.makd.afinity.data.repository.subtitles.DeviceSubtitleStore
import com.makd.afinity.data.repository.subtitles.SubtitleCandidate
import com.makd.afinity.data.repository.subtitles.SubtitleDbNotEnabledException
import com.makd.afinity.data.repository.subtitles.SubtitleLanguageCodes
import com.makd.afinity.data.repository.subtitles.SubtitleLanguageCount
import com.makd.afinity.data.repository.subtitles.SubtitleSearchMode
import com.makd.afinity.data.repository.subtitles.SubtitleSearchRepository
import com.makd.afinity.player.common.TrackSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import timber.log.Timber

enum class SubtitleSearchStage {
    LOADING,
    OPT_IN,
    RESULTS,
    EMPTY,
    ERROR,
}

enum class SubtitleOptInReason {
    NO_PROVIDER,
    NO_PERMISSION,
    DOWNLOADED,
}

data class SubtitleLanguageEntry(val code: String, val count: Int?)

data class SubtitleSearchUiState(
    val stage: SubtitleSearchStage = SubtitleSearchStage.LOADING,
    val mode: SubtitleSearchMode? = null,
    val optInReason: SubtitleOptInReason = SubtitleOptInReason.NO_PERMISSION,
    val itemTitle: String = "",
    val runtimeTicks: Long = 0L,
    val language: String = "",
    val preferredLanguage: String = "",
    val languages: List<SubtitleLanguageEntry> = emptyList(),
    val allLanguages: List<String> = emptyList(),
    val allLanguagesLoading: Boolean = false,
    val candidates: List<SubtitleCandidate> = emptyList(),
    val savedOnDevice: Map<String, String> = emptyMap(),
    val downloadingId: String? = null,
    val addFailed: Boolean = false,
) {
    val isLanguageFiltered: Boolean
        get() =
            language.isNotBlank() &&
                preferredLanguage.isNotBlank() &&
                !SubtitleLanguageCodes.sameLanguage(language, preferredLanguage)

    val showLanguageFilter: Boolean
        get() = mode == SubtitleSearchMode.SERVER || languages.size > 1
}

@HiltViewModel
class SubtitleSearchViewModel
@Inject
constructor(
    private val repository: SubtitleSearchRepository,
    private val deviceStore: DeviceSubtitleStore,
    private val preferencesRepository: PreferencesRepository,
    private val sessionManager: SessionManager,
    offlineModeManager: OfflineModeManager,
) : ViewModel() {

    val available: StateFlow<Boolean> =
        combine(offlineModeManager.isOffline, sessionManager.currentSession) { offline, session ->
                !offline && session != null
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _uiState = MutableStateFlow(SubtitleSearchUiState())
    val uiState: StateFlow<SubtitleSearchUiState> = _uiState.asStateFlow()

    private val _added = Channel<AddedSubtitle>(Channel.BUFFERED)
    val added: Flow<AddedSubtitle> = _added.receiveAsFlow()

    private var item: AfinityItem? = null
    private var audioLanguages: List<String> = emptyList()
    private var searchJob: Job? = null
    private var addJob: Job? = null

    fun open(item: AfinityItem, audioLanguages: List<String>, playingDownload: Boolean) {
        this.item = item
        this.audioLanguages = audioLanguages.filter { it.isNotBlank() && it != "und" }
        searchJob?.cancel()
        addJob?.cancel()
        val sessionMode = repository.currentMode()
        val mode = if (playingDownload) SubtitleSearchMode.DEVICE else sessionMode
        val optInReason =
            when {
                playingDownload && sessionMode != SubtitleSearchMode.DEVICE ->
                    SubtitleOptInReason.DOWNLOADED
                mode == SubtitleSearchMode.UPLOAD -> SubtitleOptInReason.NO_PROVIDER
                else -> SubtitleOptInReason.NO_PERMISSION
            }
        _uiState.value =
            SubtitleSearchUiState(
                mode = mode,
                optInReason = optInReason,
                itemTitle = item.name,
                runtimeTicks = item.runtimeTicks,
            )
        if (mode == null) {
            _uiState.update { it.copy(stage = SubtitleSearchStage.ERROR) }
            return
        }
        viewModelScope.launch {
            val preferred = preferredLanguage()
            _uiState.update { it.copy(preferredLanguage = preferred, language = preferred) }
            if (mode.usesSubtitleDb && !repository.isSubtitleDbEnabled()) {
                _uiState.update { it.copy(stage = SubtitleSearchStage.OPT_IN) }
            } else {
                search(preferred)
            }
        }
    }

    fun enableSubtitleDb() {
        viewModelScope.launch {
            repository.setSubtitleDbEnabled(true)
            search(_uiState.value.language)
        }
    }

    fun selectLanguage(code: String) {
        search(code)
    }

    fun retry() {
        search(_uiState.value.language)
    }

    fun loadAllLanguages() {
        if (_uiState.value.allLanguages.isNotEmpty() || _uiState.value.allLanguagesLoading) return
        _uiState.update { it.copy(allLanguagesLoading = true) }
        viewModelScope.launch {
            val languages =
                repository
                    .serverLanguages()
                    .getOrElse {
                        Timber.w(it, "Failed to load the server language list")
                        emptyList()
                    }
                    .sortedBy { subtitleLanguageDisplayName(it) }
            _uiState.update { it.copy(allLanguages = languages, allLanguagesLoading = false) }
        }
    }

    fun add(candidate: SubtitleCandidate) {
        val target = item ?: return
        val mode = _uiState.value.mode ?: return
        if (_uiState.value.downloadingId != null) return
        _uiState.update { it.copy(downloadingId = candidate.id, addFailed = false) }
        addJob = viewModelScope.launch {
            repository
                .add(target, candidate, mode)
                .onSuccess { added ->
                    _uiState.update { it.copy(downloadingId = null) }
                    _added.send(added)
                }
                .onFailure { error ->
                    Timber.w(error, "Adding the subtitle failed")
                    if (mode != SubtitleSearchMode.DEVICE && error.isForbidden()) {
                        fallBackToDevice()
                    } else {
                        _uiState.update { it.copy(downloadingId = null, addFailed = true) }
                    }
                }
        }
    }

    fun remove(candidate: SubtitleCandidate) {
        val target = item ?: return
        val scope = repository.currentScope() ?: return
        val fileName = _uiState.value.savedOnDevice[candidate.id] ?: return
        viewModelScope.launch {
            try {
                deviceStore.remove(scope, target.id, fileName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to remove the device subtitle")
            }
            refreshSavedOnDevice()
        }
    }

    private fun search(language: String) {
        val target = item ?: return
        val mode = _uiState.value.mode ?: return
        searchJob?.cancel()
        _uiState.update {
            it.copy(stage = SubtitleSearchStage.LOADING, language = language, addFailed = false)
        }
        searchJob = viewModelScope.launch {
            val result = repository.search(target, language, mode)
            result
                .onSuccess { found ->
                    val languages =
                        if (mode == SubtitleSearchMode.SERVER) {
                            serverLanguageEntries(target, found.language, found.candidates.size)
                        } else {
                            subtitleDbLanguageEntries(found.languages)
                        }
                    _uiState.update {
                        it.copy(
                            stage =
                                if (found.candidates.isEmpty()) SubtitleSearchStage.EMPTY
                                else SubtitleSearchStage.RESULTS,
                            language = found.language,
                            candidates = found.candidates,
                            languages = languages,
                        )
                    }
                    if (mode == SubtitleSearchMode.DEVICE) refreshSavedOnDevice()
                }
                .onFailure { error ->
                    when {
                        error is SubtitleDbNotEnabledException ->
                            _uiState.update { it.copy(stage = SubtitleSearchStage.OPT_IN) }
                        mode != SubtitleSearchMode.DEVICE && error.isForbidden() ->
                            fallBackToDevice()
                        else -> {
                            Timber.w(error, "Subtitle search failed")
                            _uiState.update { it.copy(stage = SubtitleSearchStage.ERROR) }
                        }
                    }
                }
        }
    }

    private suspend fun fallBackToDevice() {
        _uiState.update {
            it.copy(
                mode = SubtitleSearchMode.DEVICE,
                optInReason = SubtitleOptInReason.NO_PERMISSION,
                downloadingId = null,
            )
        }
        if (repository.isSubtitleDbEnabled()) {
            search(_uiState.value.language)
        } else {
            _uiState.update { it.copy(stage = SubtitleSearchStage.OPT_IN) }
        }
    }

    private suspend fun serverLanguageEntries(
        target: AfinityItem,
        current: String,
        currentCount: Int,
    ): List<SubtitleLanguageEntry> {
        val wanted =
            (listOf(_uiState.value.preferredLanguage) + audioLanguages + appLanguage())
                .filter { it.isNotBlank() }
                .map { SubtitleLanguageCodes.toServer(it) }
                .distinct()
                .filterNot { SubtitleLanguageCodes.sameLanguage(it, current) }
        val counts = repository.serverLanguageCounts(target, wanted)
        val currentEntry = SubtitleLanguageEntry(current, currentCount)
        val others = wanted.map { code -> SubtitleLanguageEntry(code, counts[code]) }
        return listOf(currentEntry) + others
    }

    private fun subtitleDbLanguageEntries(
        languages: List<SubtitleLanguageCount>
    ): List<SubtitleLanguageEntry> {
        val preferred = _uiState.value.preferredLanguage
        val entries = languages.map { SubtitleLanguageEntry(it.code, it.count) }
        val pinned = entries.filter { SubtitleLanguageCodes.sameLanguage(it.code, preferred) }
        return pinned + (entries - pinned.toSet()).sortedByDescending { it.count ?: 0 }
    }

    private suspend fun refreshSavedOnDevice() {
        val target = item ?: return
        val scope = repository.currentScope() ?: return
        val saved =
            try {
                deviceStore.list(scope, target.id, null).associate {
                    it.meta.subtitleId to it.meta.fileName
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to read device subtitles")
                emptyMap()
            }
        _uiState.update { it.copy(savedOnDevice = saved) }
    }

    private suspend fun preferredLanguage(): String {
        val local = preferencesRepository.getPreferredSubtitleLanguage()
        val chosen =
            if (local != TrackSelection.FOLLOW_SERVER_LANGUAGE) local
            else sessionManager.currentSession.value?.userConfiguration?.subtitleLanguagePreference
        return chosen?.takeIf { it.isNotBlank() } ?: appLanguage()
    }

    private fun appLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

    private fun Throwable.isForbidden(): Boolean = (this as? InvalidStatusException)?.status == 403
}
