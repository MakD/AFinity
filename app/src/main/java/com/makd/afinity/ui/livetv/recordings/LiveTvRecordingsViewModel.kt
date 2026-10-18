package com.makd.afinity.ui.livetv.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.AfinityRecording
import com.makd.afinity.data.models.livetv.SeriesRecordingOptions
import com.makd.afinity.data.models.livetv.SeriesRecordingRule
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.admin.AdminRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.ui.components.EpisodeSelection
import com.makd.afinity.ui.item.DeleteOutcome
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import com.makd.afinity.ui.item.toDeleteOutcome
import com.makd.afinity.ui.livetv.ProgramRecorder
import com.makd.afinity.ui.livetv.VisibleRefresh
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class LiveTvRecordingsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val scheduled: List<AfinityProgram> = emptyList(),
    val seriesRules: List<SeriesRecordingRule> = emptyList(),
    val recordings: List<AfinityRecording> = emptyList(),
    val openSeriesRule: SeriesRecordingRule? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LiveTvRecordingsViewModel
@Inject
constructor(
    private val liveTvRepository: LiveTvRepository,
    private val adminRepository: AdminRepository,
    private val mediaRefreshBus: MediaRefreshBus,
    sessionManager: SessionManager,
    mediaRepository: MediaRepository,
    userDataRepository: UserDataRepository,
    downloadRepository: DownloadRepository,
    itemUserDataDelegate: ItemUserDataDelegate,
    downloadPermissions: DownloadPermissions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvRecordingsUiState())
    val uiState: StateFlow<LiveTvRecordingsUiState> = _uiState.asStateFlow()

    val canManageLiveTv: StateFlow<Boolean> =
        sessionManager.currentSession
            .map { it?.canManageLiveTv == true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer
    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    val recorder = ProgramRecorder(viewModelScope, liveTvRepository) { load() }

    val episodeSelection =
        EpisodeSelection(
            scope = viewModelScope,
            mediaRepository = mediaRepository,
            userDataRepository = userDataRepository,
            itemUserDataDelegate = itemUserDataDelegate,
            downloadRepository = downloadRepository,
        )

    private val refresh = VisibleRefresh(viewModelScope, REFRESH_INTERVAL) { load() }

    init {
        viewModelScope.launch {
            merge(
                    liveTvRepository.timerChanges,
                    mediaRefreshBus.events
                        .filter { it == RefreshTrigger.LIBRARY_CHANGED }
                        .map {},
                )
                .debounce(TIMER_CHANGE_DEBOUNCE)
                .collect { refresh.invalidate() }
        }
    }

    fun setScreenVisible(visible: Boolean) {
        if (visible) refresh.invalidate()
        refresh.setVisible(visible)
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        refresh.invalidate()
    }

    fun openSeriesRule(rule: SeriesRecordingRule) {
        _uiState.update { it.copy(openSeriesRule = rule) }
    }

    fun dismissSeriesRule() {
        _uiState.update { it.copy(openSeriesRule = null) }
    }

    fun saveSeriesRule(options: SeriesRecordingOptions) {
        val rule = _uiState.value.openSeriesRule ?: return
        _uiState.update { it.copy(openSeriesRule = null) }
        viewModelScope.launch {
            liveTvRepository
                .updateSeriesRecordingOptionsResult(rule.id, options)
                .onSuccess { load() }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to save series options for ${rule.id}")
                    }
                    recorder.reportFailure()
                }
        }
    }

    fun cancelSeriesRule() {
        val rule = _uiState.value.openSeriesRule ?: return
        _uiState.update { it.copy(openSeriesRule = null) }
        recorder.cancelSeriesRule(rule.id)
    }

    fun deleteRecording(itemId: UUID, onResult: (DeleteOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = adminRepository.deleteItem(itemId.toString()).toDeleteOutcome()
            if (outcome == DeleteOutcome.DELETED) {
                _uiState.update { state ->
                    state.copy(recordings = state.recordings.filter { it.item.id != itemId })
                }
            }
            onResult(outcome)
        }
    }

    private suspend fun load() = coroutineScope {
        val scheduledCall = async { liveTvRepository.getScheduledRecordingsResult() }
        val seriesCall = async { liveTvRepository.getSeriesRecordingRulesResult() }
        val recordedCall = async { liveTvRepository.getRecordingsResult() }
        val scheduled = scheduledCall.await()
        val series = seriesCall.await()
        val recorded = recordedCall.await()
        val results = listOf(scheduled, series, recorded)
        results.forEach { result ->
            result.exceptionOrNull()?.let { e ->
                if (e !is NoActiveSessionException) Timber.e(e, "Failed to load recordings")
            }
        }
        _uiState.update { state ->
            val next =
                state.copy(
                    scheduled = scheduled.getOrDefault(state.scheduled),
                    seriesRules = series.getOrDefault(state.seriesRules),
                    recordings = recorded.getOrDefault(state.recordings),
                )
            val nothingShown =
                next.scheduled.isEmpty() && next.seriesRules.isEmpty() && next.recordings.isEmpty()
            next.copy(
                isLoading = false,
                loadFailed = nothingShown && results.any { it.isFailure },
            )
        }
    }

    private companion object {
        val REFRESH_INTERVAL = 60.seconds
        val TIMER_CHANGE_DEBOUNCE = 1.seconds
    }
}
