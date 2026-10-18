package com.makd.afinity.ui.livetv.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.LiveTvViewOptions
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.ChannelListQuery
import com.makd.afinity.ui.livetv.VisibleRefresh
import com.makd.afinity.ui.livetv.channelListQuery
import com.makd.afinity.ui.livetv.getChannelsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class LiveTvGuideUiState(
    val epgChannels: List<AfinityChannel> = emptyList(),
    val epgPrograms: Map<UUID, List<AfinityProgram>> = emptyMap(),
    val epgStartTime: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS),
    val epgHours: Int = GUIDE_HOURS,
    val days: List<LocalDate> = emptyList(),
    val scrollToTime: LocalDateTime? = null,
    val isEpgLoading: Boolean = true,
    val isWindowLoading: Boolean = false,
    val loadFailed: Boolean = false,
) {
    val selectedDay: LocalDate
        get() = epgStartTime.toLocalDate()
}

const val GUIDE_HOURS = 24

@HiltViewModel
class LiveTvGuideViewModel
@Inject
constructor(
    private val liveTvRepository: LiveTvRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvGuideUiState())
    val uiState: StateFlow<LiveTvGuideUiState> = _uiState.asStateFlow()

    val viewOptions: StateFlow<LiveTvViewOptions> =
        preferencesRepository
            .getLiveTvViewOptionsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LiveTvViewOptions(),
            )

    private var requestedStart: LocalDateTime = _uiState.value.epgStartTime
    private var pendingScrollTo: LocalDateTime? = currentHalfHour()
    private var loadJob: Job? = null
    private var channelsQuery: ChannelListQuery? = null

    private val refresh = VisibleRefresh(viewModelScope, REFRESH_INTERVAL) { startLoad().join() }

    init {
        viewModelScope.launch {
            preferencesRepository
                .getLiveTvViewOptionsFlow()
                .map { it.channelListQuery(includeCategories = true) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    _uiState.update { it.copy(isWindowLoading = true, loadFailed = false) }
                    startLoad()
                }
        }
    }

    fun setScreenVisible(visible: Boolean) = refresh.setVisible(visible)

    fun applyViewOptions(options: LiveTvViewOptions) {
        viewModelScope.launch { preferencesRepository.setLiveTvViewOptions(options) }
    }

    fun retry() {
        _uiState.update {
            it.copy(
                isEpgLoading = it.epgChannels.isEmpty(),
                isWindowLoading = it.epgChannels.isNotEmpty(),
                loadFailed = false,
            )
        }
        startLoad()
    }

    fun jumpToNow() {
        val now = LocalDateTime.now()
        val state = _uiState.value
        val loadedEnd = state.epgStartTime.plusHours(state.epgHours.toLong())
        val todayLoaded =
            requestedStart == state.epgStartTime &&
                !now.isBefore(state.epgStartTime) &&
                now.isBefore(loadedEnd)
        if (todayLoaded) {
            _uiState.update { it.copy(scrollToTime = currentHalfHour()) }
        } else {
            requestWindow(dayStart(now.toLocalDate()), currentHalfHour())
        }
    }

    fun selectDay(day: LocalDate, timeOfDay: LocalTime) {
        val start = dayStart(day)
        val target = day.atTime(timeOfDay).let { if (it.isBefore(start)) start else it }
        requestWindow(start, target)
    }

    fun onScrollConsumed() {
        _uiState.update { it.copy(scrollToTime = null) }
    }

    private fun requestWindow(start: LocalDateTime, scrollTo: LocalDateTime?) {
        requestedStart = start
        pendingScrollTo = scrollTo
        _uiState.update { it.copy(isWindowLoading = true, loadFailed = false) }
        startLoad()
    }

    private fun startLoad(): Job {
        loadJob?.cancel()
        val start = requestedStart
        return viewModelScope.launch { loadEpgData(start) }.also { loadJob = it }
    }

    private suspend fun loadEpgData(start: LocalDateTime) {
        try {
            val query =
                preferencesRepository
                    .getLiveTvViewOptionsFlow()
                    .first()
                    .channelListQuery(includeCategories = true)
            val (channelsResult, days) =
                coroutineScope {
                    val channels = async {
                        val loaded = _uiState.value.epgChannels
                        if (loaded.isNotEmpty() && channelsQuery == query) Result.success(loaded)
                        else liveTvRepository.getChannelsFor(query)
                    }
                    val days = async { _uiState.value.days.ifEmpty { loadDays() } }
                    channels.await() to days.await()
                }
            val channels = channelsResult.getOrElse { e ->
                onLoadFailed(e)
                return
            }

            if (channels.isEmpty()) {
                Timber.w("EPG: No channels found.")
                channelsQuery = query
                _uiState.update {
                    it.copy(
                        epgChannels = emptyList(),
                        epgPrograms = emptyMap(),
                        epgStartTime = start,
                        days = days,
                        isEpgLoading = false,
                        isWindowLoading = false,
                        loadFailed = false,
                    )
                }
                return
            }

            val end = start.plusHours(GUIDE_HOURS.toLong())

            val programsByChannel =
                liveTvRepository
                    .getGuideProgramsResult(
                        channelIds = channels.map { it.id },
                        windowStart = start,
                        windowEnd = end,
                    )
                    .getOrElse { e ->
                        onLoadFailed(e)
                        return
                    }
                    .groupBy { it.channelId }

            val scrollTo = pendingScrollTo
            pendingScrollTo = null
            channelsQuery = query

            _uiState.update {
                if (
                    it.epgStartTime == start &&
                        it.epgPrograms == programsByChannel &&
                        it.epgChannels == channels &&
                        it.days == days &&
                        scrollTo == null
                ) {
                    it.copy(isEpgLoading = false, isWindowLoading = false, loadFailed = false)
                } else {
                    it.copy(
                        epgChannels = channels,
                        epgPrograms = programsByChannel,
                        epgStartTime = start,
                        days = days,
                        scrollToTime = scrollTo ?: it.scrollToTime,
                        isEpgLoading = false,
                        isWindowLoading = false,
                        loadFailed = false,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onLoadFailed(e)
        }
    }

    private suspend fun loadDays(): List<LocalDate> {
        val today = LocalDate.now()
        val dataEnd = liveTvRepository.getGuideDataEnd() ?: return listOf(today)
        val lastDay =
            if (dataEnd.toLocalTime() == LocalTime.MIDNIGHT) dataEnd.toLocalDate().minusDays(1)
            else dataEnd.toLocalDate()
        if (lastDay.isBefore(today)) return listOf(today)
        return generateSequence(today) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .toList()
    }

    private fun dayStart(day: LocalDate): LocalDateTime =
        if (day == LocalDate.now()) LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        else day.atStartOfDay()

    private fun currentHalfHour(): LocalDateTime {
        val now = LocalDateTime.now()
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        return if (now.minute >= 30) hour.plusMinutes(30) else hour
    }

    private fun onLoadFailed(e: Throwable) {
        if (e !is NoActiveSessionException) Timber.e(e, "Failed to load EPG data")
        _uiState.update {
            it.copy(isEpgLoading = false, isWindowLoading = false, loadFailed = true)
        }
    }

    private companion object {
        val REFRESH_INTERVAL = 60.seconds
    }
}
