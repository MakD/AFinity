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
import com.makd.afinity.ui.livetv.cachedChannelsFor
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private var windowPending = true
    private var neededBlocks: IntRange = 0..0
    private val blockJobs = mutableMapOf<Int, Job>()
    private val loadedBlocks = mutableSetOf<Int>()
    private val staleBlocks = mutableSetOf<Int>()
    private val failedBlocks = mutableSetOf<Int>()

    private val refresh =
        VisibleRefresh(viewModelScope, REFRESH_INTERVAL) {
            val running = loadJob?.takeIf { it.isActive }
            when {
                running != null -> running.join()
                windowPending -> startLoad().join()
                else -> refreshBlocks()
            }
        }

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
                isWindowLoading = windowPending && it.epgChannels.isNotEmpty(),
                loadFailed = false,
            )
        }
        if (windowPending) startLoad() else ensureBlocks(neededBlocks)
    }

    fun onVisibleChannelsChanged(first: Int, last: Int) {
        val blocks = blockOf(first - PREFETCH_ROWS)..blockOf(last + PREFETCH_ROWS)
        if (blocks == neededBlocks) return
        neededBlocks = blocks
        blockJobs.forEach { (block, job) -> if (block !in blocks) job.cancel() }
        if (!windowPending) ensureBlocks(blocks)
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
        blockJobs.values.forEach { it.cancel() }
        blockJobs.clear()
        windowPending = true
        val start = requestedStart
        return viewModelScope.launch { loadWindow(this, start) }.also { loadJob = it }
    }

    private suspend fun loadWindow(scope: CoroutineScope, start: LocalDateTime) {
        try {
            val query =
                preferencesRepository
                    .getLiveTvViewOptionsFlow()
                    .first()
                    .channelListQuery(includeCategories = true)
            val daysDeferred = scope.async { _uiState.value.days.ifEmpty { loadDays() } }
            val reused =
                _uiState.value.epgChannels.takeIf { it.isNotEmpty() && channelsQuery == query }
            val cached = if (reused == null) liveTvRepository.cachedChannelsFor(query) else null
            val channelsResult =
                (reused ?: cached)?.let { Result.success(it) }
                    ?: liveTvRepository.getChannelsFor(query, withCurrentProgram = false)
            val channels = channelsResult.getOrElse { e ->
                onLoadFailed(e)
                return
            }

            if (channels.isEmpty()) {
                Timber.w("EPG: No channels found.")
                val days = daysDeferred.await()
                channelsQuery = query
                windowPending = false
                loadedBlocks.clear()
                staleBlocks.clear()
                failedBlocks.clear()
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

            val lastBlock = (channels.size - 1) / BLOCK_SIZE
            val firstWanted = neededBlocks.first.coerceAtMost(lastBlock)
            val wanted = firstWanted..neededBlocks.last.coerceIn(firstWanted, lastBlock)
            val results = coroutineScope {
                wanted
                    .map { block -> async { block to fetchBlock(block, channels, start) } }
                    .awaitAll()
            }
            val loaded =
                results
                    .mapNotNull { (block, result) -> result.getOrNull()?.let { block to it } }
                    .toMap()
            if (loaded.isEmpty()) {
                onLoadFailed(results.firstNotNullOf { it.second.exceptionOrNull() })
                return
            }
            results.forEach { (block, result) ->
                result.exceptionOrNull()?.let { e -> logBlockFailure(block, e) }
            }
            val days = daysDeferred.await()

            val scrollTo = pendingScrollTo
            pendingScrollTo = null
            channelsQuery = query
            windowPending = false

            val current = _uiState.value
            val sameWindow = current.epgStartTime == start && current.epgChannels == channels
            if (!sameWindow) {
                loadedBlocks.clear()
                staleBlocks.clear()
                failedBlocks.clear()
            }
            loadedBlocks += loaded.keys
            staleBlocks -= loaded.keys
            failedBlocks -= loaded.keys
            failedBlocks += wanted.filter { it !in loaded }
            val programs =
                loaded.values.fold(emptyMap<UUID, List<AfinityProgram>>()) { all, block ->
                    all + block
                }

            _uiState.update {
                val base = if (sameWindow) it.epgPrograms else emptyMap()
                val changed = programs.filter { (id, list) -> base[id] != list }
                it.copy(
                    epgChannels = if (sameWindow) it.epgChannels else channels,
                    epgPrograms = if (changed.isEmpty()) base else base + changed,
                    epgStartTime = start,
                    days = days,
                    scrollToTime = scrollTo ?: it.scrollToTime,
                    isEpgLoading = false,
                    isWindowLoading = false,
                    loadFailed = failedBlocks.isNotEmpty(),
                )
            }
            ensureBlocks(neededBlocks)
            if (cached != null) {
                val shown = _uiState.value.epgChannels
                viewModelScope.launch { revalidateChannels(query, shown) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onLoadFailed(e)
        }
    }

    private suspend fun revalidateChannels(query: ChannelListQuery, shown: List<AfinityChannel>) {
        val fresh =
            liveTvRepository.getChannelsFor(query, withCurrentProgram = false).getOrNull() ?: return
        if (channelsQuery != query || _uiState.value.epgChannels !== shown || fresh == shown) return
        channelsQuery = null
        startLoad()
    }

    private fun ensureBlocks(blocks: IntRange) {
        val state = _uiState.value
        val channels = state.epgChannels
        if (channels.isEmpty()) return
        val lastBlock = (channels.size - 1) / BLOCK_SIZE
        for (block in blocks.first..minOf(blocks.last, lastBlock)) {
            if (block in loadedBlocks && block !in staleBlocks) continue
            if (blockJobs[block]?.isActive == true) continue
            blockJobs[block] = viewModelScope.launch {
                loadBlock(block, channels, state.epgStartTime)
            }
        }
    }

    private suspend fun refreshBlocks() {
        staleBlocks += loadedBlocks
        ensureBlocks(neededBlocks)
        blockJobs.values.toList().forEach { it.join() }
    }

    private suspend fun loadBlock(
        block: Int,
        channels: List<AfinityChannel>,
        start: LocalDateTime,
    ) {
        val result = fetchBlock(block, channels, start)
        val state = _uiState.value
        if (state.epgChannels !== channels || state.epgStartTime != start) return
        result
            .onSuccess { programs ->
                loadedBlocks += block
                staleBlocks -= block
                failedBlocks -= block
                _uiState.update {
                    val changed = programs.filter { (id, list) -> it.epgPrograms[id] != list }
                    it.copy(
                        epgPrograms =
                            if (changed.isEmpty()) it.epgPrograms else it.epgPrograms + changed,
                        loadFailed = failedBlocks.isNotEmpty(),
                    )
                }
            }
            .onFailure { e ->
                logBlockFailure(block, e)
                failedBlocks += block
                _uiState.update { it.copy(loadFailed = true) }
            }
    }

    private suspend fun fetchBlock(
        block: Int,
        channels: List<AfinityChannel>,
        start: LocalDateTime,
    ): Result<Map<UUID, List<AfinityProgram>>> {
        val ids =
            channels
                .subList(block * BLOCK_SIZE, minOf((block + 1) * BLOCK_SIZE, channels.size))
                .map { it.id }
        return liveTvRepository
            .getGuideProgramsResult(
                channelIds = ids,
                windowStart = start,
                windowEnd = start.plusHours(GUIDE_HOURS.toLong()),
            )
            .map { programs ->
                val byChannel = programs.groupBy { it.channelId }
                ids.associateWith { byChannel[it].orEmpty() }
            }
    }

    private fun blockOf(channelIndex: Int): Int = channelIndex.coerceAtLeast(0) / BLOCK_SIZE

    private fun logBlockFailure(block: Int, e: Throwable) {
        if (e !is NoActiveSessionException) Timber.e(e, "Failed to load guide block $block")
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
        const val BLOCK_SIZE = 100
        const val PREFETCH_ROWS = 30
    }
}
