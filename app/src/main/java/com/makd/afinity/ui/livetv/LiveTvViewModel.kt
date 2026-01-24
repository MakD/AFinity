package com.makd.afinity.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.models.LiveTvCategory
import com.makd.afinity.ui.livetv.models.ProgramWithChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val liveTvRepository: LiveTvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        checkLiveTvAccess()
    }

    private fun checkLiveTvAccess() {
        viewModelScope.launch {
            try {
                val hasAccess = liveTvRepository.hasLiveTvAccess()
                _uiState.value = _uiState.value.copy(hasLiveTvAccess = hasAccess)
                if (hasAccess) {
                    loadChannels()
                    loadCategorizedPrograms()
                    startPeriodicRefresh()
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check Live TV access")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to check Live TV access"
                )
            }
        }
    }

    private fun loadChannels() {
        viewModelScope.launch {
            try {
                val channels = liveTvRepository.getChannels()
                _uiState.value = _uiState.value.copy(
                    channels = channels,
                    epgChannels = channels,
                    isLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load channels")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load channels"
                )
            }
        }
    }

    fun loadCategorizedPrograms() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isCategoriesLoading = true)

                val channels = liveTvRepository.getChannels()
                val channelsMap = channels.associateBy { it.id }

                val now = LocalDateTime.now()
                val programs = liveTvRepository.getPrograms(
                    minStartDate = now.minusHours(2),
                    maxEndDate = now.plusHours(4),
                    limit = 200
                )

                val categorized = mutableMapOf<LiveTvCategory, MutableList<ProgramWithChannel>>()
                LiveTvCategory.entries.forEach { categorized[it] = mutableListOf() }

                programs.forEach { program ->
                    val channel = channelsMap[program.channelId] ?: return@forEach
                    val programWithChannel = ProgramWithChannel(program, channel)
                    val categories = LiveTvCategory.categorizeProgram(program)

                    categories.forEach { category ->
                        categorized[category]?.add(programWithChannel)
                    }
                }

                categorized.forEach { (category, programList) ->
                    when (category) {
                        LiveTvCategory.ON_NOW -> {
                            programList.sortBy { it.channel.channelNumber?.toIntOrNull() ?: Int.MAX_VALUE }
                        }
                        else -> {
                            programList.sortBy { it.program.name.lowercase() }
                        }
                    }
                }

                val filteredCategories = categorized.filter { (category, programs) ->
                    category == LiveTvCategory.ON_NOW || programs.isNotEmpty()
                }

                _uiState.value = _uiState.value.copy(
                    categorizedPrograms = filteredCategories,
                    isCategoriesLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load categorized programs")
                _uiState.value = _uiState.value.copy(isCategoriesLoading = false)
            }
        }
    }

    fun loadEpgData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isEpgLoading = true)

                val channels = liveTvRepository.getChannels()
                if (channels.isEmpty()) {
                    Timber.w("EPG: No channels found.")
                    _uiState.value = _uiState.value.copy(isEpgLoading = false)
                    return@launch
                }

                val startTime = _uiState.value.epgStartTime
                val endTime = startTime.plusHours(_uiState.value.epgVisibleHours.toLong() + 1)

                Timber.d("EPG: Loading programs for ${channels.size} channels from $startTime to $endTime")

                val allPrograms = channels.chunked(100).map { batch ->
                    async(Dispatchers.IO) {
                        try {
                            val result = liveTvRepository.getPrograms(
                                channelIds = batch.map { it.id },
                                minStartDate = startTime,
                                maxEndDate = endTime
                            )
                            Timber.d("EPG Batch: Fetched ${result.size} programs for ${batch.size} channels")
                            result
                        } catch (e: Exception) {
                            Timber.e(e, "EPG Batch Failed for ${batch.size} channels")
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()

                Timber.d("EPG: Total Loaded ${allPrograms.size} programs")

                val programsByChannel = allPrograms.groupBy { it.channelId }

                _uiState.value = _uiState.value.copy(
                    epgChannels = channels,
                    epgPrograms = programsByChannel,
                    isEpgLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load EPG data")
                _uiState.value = _uiState.value.copy(isEpgLoading = false)
            }
        }
    }

    fun selectTab(tab: LiveTvTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        when (tab) {
            LiveTvTab.HOME -> loadCategorizedPrograms()
            LiveTvTab.GUIDE -> loadEpgData()
            LiveTvTab.CHANNELS -> loadChannels()
        }
    }

    fun jumpToNow() {
        _uiState.value = _uiState.value.copy(
            epgStartTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        )
        loadEpgData()
    }

    fun navigateEpgTime(hours: Int) {
        val newStartTime = _uiState.value.epgStartTime.plusHours(hours.toLong())
        _uiState.value = _uiState.value.copy(epgStartTime = newStartTime)
        loadEpgData()
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
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
                    _uiState.value = _uiState.value.copy(channels = channels)
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
                    val updatedChannels = _uiState.value.channels.map { channel ->
                        if (channel.id == channelId) {
                            channel.copy(favorite = !channel.favorite)
                        } else {
                            channel
                        }
                    }
                    _uiState.value = _uiState.value.copy(channels = updatedChannels)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite for channel: $channelId")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                loadChannels()
                loadCategorizedPrograms()
                loadEpgData()
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh")
                _uiState.value = _uiState.value.copy(isRefreshing = false)
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