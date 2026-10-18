package com.makd.afinity.ui.livetv.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.ProgramRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
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

data class LiveTvChannelUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val channel: AfinityChannel? = null,
    val isScheduleLoading: Boolean = true,
    val scheduleFailed: Boolean = false,
    val schedule: List<AfinityProgram> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LiveTvChannelViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val liveTvRepository: LiveTvRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val channelIdStr = savedStateHandle.get<String>("channelId")
    private val channelId: UUID? =
        try {
            channelIdStr?.let { UUID.fromString(it) }
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid channel id: $channelIdStr")
            null
        }

    private val _uiState = MutableStateFlow(LiveTvChannelUiState())
    val uiState: StateFlow<LiveTvChannelUiState> = _uiState.asStateFlow()

    val canManageLiveTv: StateFlow<Boolean> =
        sessionManager.currentSession
            .map { it?.canManageLiveTv == true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

    val recorder = ProgramRecorder(viewModelScope, liveTvRepository) { loadSchedule() }

    init {
        loadChannel()
        loadSchedule()
        viewModelScope.launch {
            liveTvRepository.channelFavoriteChanges.collect { change ->
                if (change.channelId == channelId) {
                    _uiState.update { state ->
                        state.copy(channel = state.channel?.copy(favorite = change.favorite))
                    }
                }
            }
        }
        viewModelScope.launch {
            liveTvRepository.timerChanges.debounce(TIMER_CHANGE_DEBOUNCE).collect {
                loadSchedule()
            }
        }
    }

    fun retry() {
        if (_uiState.value.channel == null) {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            loadChannel()
        }
        _uiState.update { it.copy(isScheduleLoading = true, scheduleFailed = false) }
        loadSchedule()
    }

    fun toggleFavorite() {
        val id = channelId ?: return
        viewModelScope.launch {
            try {
                liveTvRepository.toggleChannelFavorite(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite for channel: $id")
            }
        }
    }

    private fun loadChannel() {
        val id = channelId
        if (id == null) {
            _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            return
        }
        viewModelScope.launch {
            liveTvRepository
                .getChannelResult(id)
                .onSuccess { channel ->
                    _uiState.update {
                        it.copy(isLoading = false, loadFailed = false, channel = channel)
                    }
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) Timber.e(e, "Failed to load channel $id")
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
        }
    }

    private fun loadSchedule() {
        val id = channelId
        if (id == null) {
            _uiState.update { it.copy(isScheduleLoading = false, scheduleFailed = true) }
            return
        }
        viewModelScope.launch {
            liveTvRepository
                .getChannelScheduleResult(id)
                .onSuccess { schedule ->
                    _uiState.update {
                        it.copy(
                            isScheduleLoading = false,
                            scheduleFailed = false,
                            schedule = schedule,
                        )
                    }
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to load the schedule for channel $id")
                    }
                    _uiState.update { it.copy(isScheduleLoading = false, scheduleFailed = true) }
                }
        }
    }

    private companion object {
        val TIMER_CHANGE_DEBOUNCE = 1.seconds
    }
}
