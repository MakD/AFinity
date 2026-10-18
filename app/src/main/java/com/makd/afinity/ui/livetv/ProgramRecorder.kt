package com.makd.afinity.ui.livetv

import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class RecordingEvent {
    SCHEDULED,
    SERIES_SCHEDULED,
    CANCELLED,
    SERIES_CANCELLED,
    FAILED,
}

sealed interface RecordingPrompt {
    val program: AfinityProgram

    data class ConfirmCancel(override val program: AfinityProgram) : RecordingPrompt

    data class SeriesChoice(override val program: AfinityProgram) : RecordingPrompt
}

class ProgramRecorder(
    private val scope: CoroutineScope,
    private val liveTvRepository: LiveTvRepository,
    private val onChanged: suspend () -> Unit,
) {
    private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()

    private val _prompt = MutableStateFlow<RecordingPrompt?>(null)
    val prompt: StateFlow<RecordingPrompt?> = _prompt.asStateFlow()

    private val _busyIds = MutableStateFlow<Set<UUID>>(emptySet())
    val busyIds: StateFlow<Set<UUID>> = _busyIds.asStateFlow()

    fun toggleSingle(program: AfinityProgram) {
        val activeTimerId = program.timerId?.takeIf { program.hasActiveTimer }
        if (activeTimerId != null) {
            run(program, RecordingEvent.CANCELLED) {
                liveTvRepository.cancelRecordingResult(activeTimerId)
            }
        } else {
            run(program, RecordingEvent.SCHEDULED) {
                liveTvRepository.scheduleRecordingResult(program.id)
            }
        }
    }

    fun toggleSeries(program: AfinityProgram) {
        val seriesTimerId = program.seriesTimerId
        if (seriesTimerId != null) {
            run(program, RecordingEvent.SERIES_CANCELLED) {
                liveTvRepository.cancelSeriesRecordingResult(seriesTimerId)
            }
        } else {
            run(program, RecordingEvent.SERIES_SCHEDULED) {
                liveTvRepository.scheduleSeriesRecordingResult(program.id)
            }
        }
    }

    fun onRowRecordClick(program: AfinityProgram) {
        val hasTimer = program.hasActiveTimer
        when {
            hasTimer && program.seriesTimerId != null ->
                _prompt.value = RecordingPrompt.SeriesChoice(program)
            hasTimer && program.isSeries ->
                run(program, RecordingEvent.SERIES_SCHEDULED) {
                    liveTvRepository.scheduleSeriesRecordingResult(program.id)
                }
            hasTimer -> _prompt.value = RecordingPrompt.ConfirmCancel(program)
            else ->
                run(program, RecordingEvent.SCHEDULED) {
                    liveTvRepository.scheduleRecordingResult(program.id)
                }
        }
    }

    fun confirmCancel(program: AfinityProgram) {
        _prompt.value = RecordingPrompt.ConfirmCancel(program)
    }

    fun cancelSeriesRule(seriesTimerId: String) {
        run(null, RecordingEvent.SERIES_CANCELLED) {
            liveTvRepository.cancelSeriesRecordingResult(seriesTimerId)
        }
    }

    fun cancelTimer(program: AfinityProgram) {
        _prompt.value = null
        val timerId = program.timerId ?: return
        run(program, RecordingEvent.CANCELLED) { liveTvRepository.cancelRecordingResult(timerId) }
    }

    fun cancelSeries(program: AfinityProgram) {
        _prompt.value = null
        val seriesTimerId = program.seriesTimerId ?: return
        run(program, RecordingEvent.SERIES_CANCELLED) {
            liveTvRepository.cancelSeriesRecordingResult(seriesTimerId)
        }
    }

    fun dismissPrompt() {
        _prompt.value = null
    }

    fun reportFailure() {
        _events.tryEmit(RecordingEvent.FAILED)
    }

    private fun run(
        program: AfinityProgram,
        successEvent: RecordingEvent,
        action: suspend () -> Result<Unit>,
    ) = run(program.id, successEvent, action)

    private fun run(
        busyId: UUID?,
        successEvent: RecordingEvent,
        action: suspend () -> Result<Unit>,
    ) {
        if (busyId != null) {
            if (busyId in _busyIds.value) return
            _busyIds.update { it + busyId }
        }
        scope.launch {
            action()
                .onSuccess {
                    _events.emit(successEvent)
                    onChanged()
                }
                .onFailure { e ->
                    if (e !is NoActiveSessionException) {
                        Timber.e(e, "Failed to change a recording ($successEvent)")
                    }
                    _events.emit(RecordingEvent.FAILED)
                }
            if (busyId != null) _busyIds.update { it - busyId }
        }
    }
}
