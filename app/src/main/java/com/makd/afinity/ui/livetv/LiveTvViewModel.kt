package com.makd.afinity.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.livetv.models.ProgramWithChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class LiveTvViewModel @Inject constructor(private val liveTvRepository: LiveTvRepository) :
    ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val _selectedLetter = MutableStateFlow<String?>(null)
    val selectedLetter: StateFlow<String?> = _selectedLetter.asStateFlow()

    private var allChannelsCache: List<AfinityChannel> = emptyList()

    private var refreshJob: Job? = null

    init {
        checkLiveTvAccess()
    }

    fun onLetterSelected(letter: String) {
        val newLetter = if (_selectedLetter.value == letter) null else letter
        _selectedLetter.value = newLetter
        applyFilterToCache(newLetter)
    }

    fun clearLetterFilter() {
        _selectedLetter.value = null
        applyFilterToCache(null)
    }

    private fun applyFilterToCache(letter: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentCache = allChannelsCache

            val filteredList =
                if (letter == null) {
                    currentCache
                } else {
                    currentCache.filter { channel ->
                        val firstChar = channel.name.firstOrNull()?.uppercase() ?: ""
                        if (letter == "#") {
                            firstChar.isNotEmpty() && !firstChar[0].isLetter()
                        } else {
                            firstChar == letter
                        }
                    }
                }

            _uiState.update { it.copy(channels = filteredList) }
        }
    }

    private fun checkLiveTvAccess() {
        viewModelScope.launch {
            try {
                val hasAccess = liveTvRepository.hasLiveTvAccess()
                _uiState.update { it.copy(hasLiveTvAccess = hasAccess) }
                if (hasAccess) {
                    loadChannels()
                    loadCategorizedPrograms()
                    startPeriodicRefresh()
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check Live TV access")
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to check Live TV access")
                }
            }
        }
    }

    private fun loadChannels() {
        viewModelScope.launch {
            try {
                val channels = liveTvRepository.getChannels()
                allChannelsCache = channels
                applyFilterToCache(_selectedLetter.value)

                _uiState.update { it.copy(epgChannels = channels, isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load channels")
                _uiState.update { it.copy(isLoading = false, error = "Failed to load channels") }
            }
        }
    }

    fun loadCategorizedPrograms() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCategoriesLoading = true) }
                val channels = liveTvRepository.getChannels()
                val channelsMap = channels.associateBy { it.id }
                val categorizedPrograms =
                    awaitAll(
                        async(Dispatchers.IO) {
                            LiveTvCategory.ON_NOW to
                                liveTvRepository.getPrograms(hasAired = false, limit = 20)
                        },
                        async(Dispatchers.IO) {
                            LiveTvCategory.MOVIES to
                                liveTvRepository.getPrograms(
                                    hasAired = false,
                                    isMovie = true,
                                    limit = 20,
                                )
                        },
                        async(Dispatchers.IO) {
                            LiveTvCategory.SHOWS to
                                liveTvRepository.getPrograms(
                                    hasAired = false,
                                    isSeries = true,
                                    limit = 20,
                                )
                        },
                        async(Dispatchers.IO) {
                            LiveTvCategory.SPORTS to
                                liveTvRepository.getPrograms(
                                    hasAired = false,
                                    isSports = true,
                                    limit = 20,
                                )
                        },
                        async(Dispatchers.IO) {
                            LiveTvCategory.KIDS to
                                liveTvRepository.getPrograms(
                                    hasAired = false,
                                    isKids = true,
                                    limit = 20,
                                )
                        },
                        async(Dispatchers.IO) {
                            LiveTvCategory.NEWS to
                                liveTvRepository.getPrograms(
                                    hasAired = false,
                                    isNews = true,
                                    limit = 20,
                                )
                        },
                    )
                val categorized = mutableMapOf<LiveTvCategory, MutableList<ProgramWithChannel>>()

                categorizedPrograms.forEach { (category, programs) ->
                    val programsWithChannel =
                        programs
                            .filter { program ->
                                program.isCurrentlyAiring() &&
                                    channelsMap.containsKey(program.channelId)
                            }
                            .map { program ->
                                ProgramWithChannel(program, channelsMap[program.channelId]!!)
                            }
                            .toMutableList()

                    when (category) {
                        LiveTvCategory.ON_NOW -> {
                            programsWithChannel.sortBy {
                                it.channel.channelNumber?.toIntOrNull() ?: Int.MAX_VALUE
                            }
                        }
                        else -> {
                            programsWithChannel.sortBy { it.program.name.lowercase() }
                        }
                    }

                    categorized[category] = programsWithChannel
                }
                val filteredCategories =
                    categorized.filter { (category, programs) ->
                        category == LiveTvCategory.ON_NOW || programs.isNotEmpty()
                    }

                _uiState.update {
                    it.copy(categorizedPrograms = filteredCategories, isCategoriesLoading = false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load categorized programs")
                _uiState.update { it.copy(isCategoriesLoading = false) }
            }
        }
    }

    fun loadEpgData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isEpgLoading = true) }

                val channels =
                    if (allChannelsCache.isNotEmpty()) allChannelsCache
                    else liveTvRepository.getChannels()

                if (channels.isEmpty()) {
                    Timber.w("EPG: No channels found.")
                    _uiState.update { it.copy(isEpgLoading = false) }
                    return@launch
                }

                val startTime = _uiState.value.epgStartTime
                val endTime = startTime.plusHours(_uiState.value.epgVisibleHours.toLong() + 1)

                val allPrograms =
                    channels
                        .chunked(100)
                        .map { batch ->
                            async(Dispatchers.IO) {
                                try {
                                    liveTvRepository.getPrograms(
                                        channelIds = batch.map { it.id },
                                        minStartDate = startTime,
                                        maxEndDate = endTime,
                                    )
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        .awaitAll()
                        .flatten()

                val programsByChannel = allPrograms.groupBy { it.channelId }

                _uiState.update {
                    it.copy(
                        epgChannels = channels,
                        epgPrograms = programsByChannel,
                        isEpgLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load EPG data")
                _uiState.update { it.copy(isEpgLoading = false) }
            }
        }
    }

    fun selectTab(tab: LiveTvTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            LiveTvTab.HOME -> loadCategorizedPrograms()
            LiveTvTab.GUIDE -> loadEpgData()
            LiveTvTab.CHANNELS -> {
                if (allChannelsCache.isEmpty()) {
                    loadChannels()
                } else {
                    applyFilterToCache(_selectedLetter.value)
                }
            }
        }
    }

    fun jumpToNow() {
        _uiState.update {
            it.copy(epgStartTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS))
        }
        loadEpgData()
    }

    fun navigateEpgTime(hours: Int) {
        val newStartTime = _uiState.value.epgStartTime.plusHours(hours.toLong())
        _uiState.update { it.copy(epgStartTime = newStartTime) }
        loadEpgData()
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(60000L)
                    refreshCurrentTabData()
                }
            }
    }

    private suspend fun refreshCurrentTabData() {
        try {
            when (_uiState.value.selectedTab) {
                LiveTvTab.HOME -> loadCategorizedPrograms()
                LiveTvTab.GUIDE -> loadEpgData()
                LiveTvTab.CHANNELS -> {
                    val channels = liveTvRepository.getChannels()
                    allChannelsCache = channels
                    applyFilterToCache(_selectedLetter.value)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh tab data")
        }
    }

    fun toggleFavorite(channelId: UUID) {
        viewModelScope.launch {
            try {
                val success = liveTvRepository.toggleChannelFavorite(channelId)
                if (success) {
                    allChannelsCache =
                        allChannelsCache.map { channel ->
                            if (channel.id == channelId) channel.copy(favorite = !channel.favorite)
                            else channel
                        }
                    applyFilterToCache(_selectedLetter.value)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite for channel: $channelId")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val channels = liveTvRepository.getChannels()
                allChannelsCache = channels

                loadCategorizedPrograms()
                loadEpgData()

                applyFilterToCache(_selectedLetter.value)

                _uiState.update { it.copy(isRefreshing = false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh")
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun getChannelStreamUrl(channelId: UUID, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val url = liveTvRepository.getChannelStreamUrl(channelId)
            onResult(url)
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
