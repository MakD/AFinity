package com.makd.afinity.ui.livetv.program

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.GuideIndicator
import com.makd.afinity.data.models.livetv.LiveTvSimilarItem
import com.makd.afinity.data.models.livetv.LiveTvViewOptions
import com.makd.afinity.data.models.livetv.RecordingPadding
import com.makd.afinity.data.models.livetv.SeriesRecordingOptions
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.ProgramRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class LiveTvProgramUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val program: AfinityProgram? = null,
    val upNext: List<AfinityProgram> = emptyList(),
    val similarItems: List<LiveTvSimilarItem> = emptyList(),
    val recordingOptions: RecordingPadding? = null,
    val seriesOptions: SeriesRecordingOptions? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LiveTvProgramViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val liveTvRepository: LiveTvRepository,
    preferencesRepository: PreferencesRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val programIdStr = savedStateHandle.get<String>("programId")
    private val programId: UUID? =
        try {
            programIdStr?.let { UUID.fromString(it) }
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid program id: $programIdStr")
            null
        }

    private val _uiState = MutableStateFlow(LiveTvProgramUiState())
    val uiState: StateFlow<LiveTvProgramUiState> = _uiState.asStateFlow()

    val indicators: StateFlow<Set<GuideIndicator>> =
        preferencesRepository
            .getLiveTvViewOptionsFlow()
            .map { it.indicators }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LiveTvViewOptions.DEFAULT_INDICATORS,
            )

    val canManageLiveTv: StateFlow<Boolean> =
        sessionManager.currentSession
            .map { it?.canManageLiveTv == true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

    val recorder = ProgramRecorder(viewModelScope, liveTvRepository) { refreshAfterRecording() }

    init {
        load()
        viewModelScope.launch {
            liveTvRepository.timerChanges.debounce(TIMER_CHANGE_DEBOUNCE).collect {
                if (_uiState.value.program != null) refreshAfterRecording()
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        load()
    }

    fun toggleRecording() {
        val program = _uiState.value.program ?: return
        _uiState.update { it.copy(recordingOptions = null) }
        recorder.toggleSingle(program)
    }

    fun toggleSeriesRecording() {
        val program = _uiState.value.program ?: return
        recorder.toggleSeries(program)
    }

    fun openRecordingOptions() {
        val program = _uiState.value.program ?: return
        val seriesTimerId = program.seriesTimerId
        if (seriesTimerId != null) {
            openSeriesOptions(seriesTimerId)
            return
        }
        val timerId = program.timerId ?: return
        viewModelScope.launch {
            liveTvRepository
                .getRecordingPaddingResult(timerId)
                .onSuccess { padding -> _uiState.update { it.copy(recordingOptions = padding) } }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to load recording options for timer $timerId")
                    }
                    recorder.reportFailure()
                }
        }
    }

    fun dismissRecordingOptions() {
        _uiState.update { it.copy(recordingOptions = null) }
    }

    fun saveRecordingOptions(padding: RecordingPadding) {
        val timerId = _uiState.value.program?.timerId ?: return
        _uiState.update { it.copy(recordingOptions = null) }
        viewModelScope.launch {
            liveTvRepository.updateRecordingPaddingResult(timerId, padding).onFailure { e ->
                if (e !is NoActiveSessionException) {
                    Timber.e(e, "Failed to save recording options for timer $timerId")
                }
                recorder.reportFailure()
            }
        }
    }

    fun dismissSeriesOptions() {
        _uiState.update { it.copy(seriesOptions = null) }
    }

    fun saveSeriesOptions(options: SeriesRecordingOptions) {
        val seriesTimerId = _uiState.value.program?.seriesTimerId ?: return
        _uiState.update { it.copy(seriesOptions = null) }
        viewModelScope.launch {
            liveTvRepository
                .updateSeriesRecordingOptionsResult(seriesTimerId, options)
                .onSuccess { refreshAfterRecording() }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to save series options for $seriesTimerId")
                    }
                    recorder.reportFailure()
                }
        }
    }

    fun cancelSeriesFromOptions() {
        val program = _uiState.value.program ?: return
        _uiState.update { it.copy(seriesOptions = null) }
        recorder.cancelSeries(program)
    }

    private fun openSeriesOptions(seriesTimerId: String) {
        viewModelScope.launch {
            liveTvRepository
                .getSeriesRecordingOptionsResult(seriesTimerId)
                .onSuccess { options -> _uiState.update { it.copy(seriesOptions = options) } }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to load series options for $seriesTimerId")
                    }
                    recorder.reportFailure()
                }
        }
    }

    private suspend fun refreshAfterRecording() {
        val id = programId ?: return
        liveTvRepository.getProgramResult(id).onSuccess { program ->
            _uiState.update { it.copy(program = program) }
            loadUpNext(program)
        }
    }

    private fun load() {
        val id = programId
        if (id == null) {
            _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            return
        }
        viewModelScope.launch {
            liveTvRepository
                .getProgramResult(id)
                .onSuccess { program ->
                    _uiState.update {
                        it.copy(isLoading = false, loadFailed = false, program = program)
                    }
                    launch { loadUpNext(program) }
                    launch { loadSimilarItems(program) }
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.e(e, "Failed to load program $id")
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
        }
    }

    private suspend fun loadUpNext(program: AfinityProgram) {
        val after = program.endDate ?: return
        liveTvRepository
            .getChannelScheduleResult(program.channelId)
            .onSuccess { schedule ->
                val upNext =
                    schedule
                        .filter { next ->
                            next.id != program.id && next.startDate?.isBefore(after) == false
                        }
                        .take(UP_NEXT_LIMIT)
                _uiState.update { it.copy(upNext = upNext) }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) {
                    Timber.e(e, "Failed to load the schedule after program ${program.id}")
                }
            }
    }

    private suspend fun loadSimilarItems(program: AfinityProgram) {
        val similar = liveTvRepository.getSimilarToProgram(program.id, SIMILAR_LIMIT)
        _uiState.update { it.copy(similarItems = similar) }
    }

    private companion object {
        const val UP_NEXT_LIMIT = 5
        const val SIMILAR_LIMIT = 12
        val TIMER_CHANGE_DEBOUNCE = 1.seconds
    }
}
