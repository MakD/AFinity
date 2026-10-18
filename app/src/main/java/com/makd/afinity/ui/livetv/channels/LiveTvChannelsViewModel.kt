package com.makd.afinity.ui.livetv.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.LiveTvViewOptions
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.ui.livetv.VisibleRefresh
import com.makd.afinity.ui.livetv.channelListQuery
import com.makd.afinity.ui.livetv.getChannelsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
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

    private val refresh = VisibleRefresh(viewModelScope, REFRESH_INTERVAL) { loadChannels() }

    init {
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
    }

    fun clearLetterFilter() {
        _uiState.update { it.copy(selectedLetter = null) }
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
            .getChannelsFor(query)
            .onSuccess { channels ->
                _uiState.update {
                    it.copy(isLoading = false, loadFailed = false, allChannels = channels)
                }
            }
            .onFailure { e ->
                if (e !is NoActiveSessionException) Timber.e(e, "Failed to load channels")
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
    }

    private companion object {
        val REFRESH_INTERVAL = 60.seconds
    }
}
