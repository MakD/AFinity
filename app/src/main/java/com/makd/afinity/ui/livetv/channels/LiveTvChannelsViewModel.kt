package com.makd.afinity.ui.livetv.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.LiveTvViewOptions
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.VisibleRefresh
import com.makd.afinity.ui.livetv.cachedChannelsFor
import com.makd.afinity.ui.livetv.channelListQuery
import com.makd.afinity.ui.livetv.getChannelsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
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

data class LiveTvChannelsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val allChannels: List<AfinityChannel> = emptyList(),
    val programsLoaded: Set<UUID> = emptySet(),
    val selectedLetter: String? = null,
) {
    val channels: List<AfinityChannel> =
        if (selectedLetter == null) {
            allChannels
        } else {
            allChannels.filter { channel ->
                val firstChar = channel.name.firstOrNull()?.uppercase() ?: ""
                if (selectedLetter == "#") {
                    firstChar.isNotEmpty() && !firstChar[0].isLetter()
                } else {
                    firstChar == selectedLetter
                }
            }
        }
}

@HiltViewModel
class LiveTvChannelsViewModel
@Inject
constructor(
    private val liveTvRepository: LiveTvRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvChannelsUiState())
    val uiState: StateFlow<LiveTvChannelsUiState> = _uiState.asStateFlow()

    val viewOptions: StateFlow<LiveTvViewOptions> =
        preferencesRepository
            .getLiveTvViewOptionsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LiveTvViewOptions(),
            )

    private var currentPrograms: Map<UUID, AfinityProgram> = emptyMap()
    private val staleProgramIds = mutableSetOf<UUID>()
    private val programJobs = mutableListOf<Pair<Set<UUID>, Job>>()
    private var visibleItems: IntRange = 0..PREFETCH_ITEMS

    private val refresh = VisibleRefresh(viewModelScope, REFRESH_INTERVAL) { loadChannels() }

    init {
        viewModelScope.launch {
            val query =
                preferencesRepository
                    .getLiveTvViewOptionsFlow()
                    .first()
                    .channelListQuery(includeCategories = false)
            val cached = liveTvRepository.cachedChannelsFor(query) ?: return@launch
            if (!_uiState.value.isLoading) return@launch
            _uiState.update { it.copy(isLoading = false, allChannels = cached) }
            ensurePrograms()
        }
        viewModelScope.launch {
            preferencesRepository
                .getLiveTvViewOptionsFlow()
                .map { it.channelListQuery(includeCategories = false) }
                .distinctUntilChanged()
                .drop(1)
                .collect { refresh.invalidate() }
        }
        viewModelScope.launch {
            liveTvRepository.channelFavoriteChanges.collect { change ->
                _uiState.update { state ->
                    state.copy(
                        allChannels =
                            state.allChannels.map { channel ->
                                if (channel.id == change.channelId) {
                                    channel.copy(favorite = change.favorite)
                                } else {
                                    channel
                                }
                            }
                    )
                }
                val options = preferencesRepository.getLiveTvViewOptionsFlow().first()
                if (options.favoritesFirst || options.favoritesOnly) refresh.invalidate()
            }
        }
    }

    fun setScreenVisible(visible: Boolean) = refresh.setVisible(visible)

    fun applyViewOptions(options: LiveTvViewOptions) {
        viewModelScope.launch { preferencesRepository.setLiveTvViewOptions(options) }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch { loadChannels() }
    }

    fun onLetterSelected(letter: String) {
        _uiState.update {
            it.copy(selectedLetter = if (it.selectedLetter == letter) null else letter)
        }
        ensurePrograms()
    }

    fun clearLetterFilter() {
        _uiState.update { it.copy(selectedLetter = null) }
        ensurePrograms()
    }

    fun onVisibleChannelsChanged(first: Int, last: Int) {
        val items = (first - PREFETCH_ITEMS).coerceAtLeast(0)..(last + PREFETCH_ITEMS)
        if (items == visibleItems) return
        visibleItems = items
        ensurePrograms()
    }

    fun toggleFavorite(channelId: UUID) {
        viewModelScope.launch {
            try {
                liveTvRepository.toggleChannelFavorite(channelId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite for channel: $channelId")
            }
        }
    }

    private suspend fun loadChannels() {
        val query =
            preferencesRepository
                .getLiveTvViewOptionsFlow()
                .first()
                .channelListQuery(includeCategories = false)
        liveTvRepository
            .getChannelsFor(query, withCurrentProgram = false)
            .onSuccess { channels ->
                staleProgramIds += _uiState.value.programsLoaded
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        allChannels =
                            channels.map { channel ->
                                currentPrograms[channel.id]?.let { program ->
                                    channel.copy(currentProgram = program)
                                } ?: channel
                            },
                    )
                }
                ensurePrograms()
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.e(e, "Failed to load channels")
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
    }

    private fun ensurePrograms() {
        val channels = _uiState.value.channels
        val window =
            if (visibleItems.first > channels.lastIndex) emptyList()
            else
                channels.subList(
                    visibleItems.first,
                    minOf(visibleItems.last, channels.lastIndex) + 1,
                )
        val wanted = window.mapTo(mutableSetOf()) { it.id }

        programJobs.removeAll { (ids, job) ->
            val drop = !job.isActive || ids.none { it in wanted }
            if (drop) job.cancel()
            drop
        }
        val inFlight = programJobs.flatMapTo(mutableSetOf()) { it.first }
        val loaded = _uiState.value.programsLoaded

        window
            .map { it.id }
            .filter { id -> (id !in loaded || id in staleProgramIds) && id !in inFlight }
            .chunked(PROGRAM_CHUNK)
            .forEach { ids ->
                val idSet = ids.toSet()
                programJobs += idSet to viewModelScope.launch { loadPrograms(ids) }
            }
    }

    private suspend fun loadPrograms(ids: List<UUID>) {
        liveTvRepository
            .getCurrentProgramsResult(ids)
            .onSuccess { programs ->
                currentPrograms = currentPrograms - ids.toSet() + programs
                staleProgramIds -= ids.toSet()
                _uiState.update { state ->
                    val idSet = ids.toSet()
                    state.copy(
                        allChannels =
                            state.allChannels.map { channel ->
                                if (channel.id !in idSet) return@map channel
                                val program = programs[channel.id]
                                if (channel.currentProgram == program) channel
                                else channel.copy(currentProgram = program)
                            },
                        programsLoaded = state.programsLoaded + idSet,
                    )
                }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) {
                    Timber.e(e, "Failed to load current programmes for ${ids.size} channels")
                }
            }
    }

    private companion object {
        val REFRESH_INTERVAL = 60.seconds
        const val PREFETCH_ITEMS = 60
        const val PROGRAM_CHUNK = 100
    }
}
