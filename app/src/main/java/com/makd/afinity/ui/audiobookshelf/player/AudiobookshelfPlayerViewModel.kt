package com.makd.afinity.ui.audiobookshelf.player

import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfEqualizerManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.audiobookshelf.AudiobookshelfSkipSilenceManager
import com.makd.afinity.player.audiobookshelf.EqualizerPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfPlayerViewModel
@OptIn(UnstableApi::class)
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
    val playbackManager: AudiobookshelfPlaybackManager,
    private val equalizerManager: AudiobookshelfEqualizerManager,
    private val skipSilenceManager: AudiobookshelfSkipSilenceManager,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>("itemId") ?: ""
    private val episodeId: String? = savedStateHandle.get<String>("episodeId")
    private val startPosition: Double? =
        savedStateHandle.get<String>("startPosition")?.toDoubleOrNull()
    private val episodeSort: String? = savedStateHandle.get<String>("episodeSort")

    private val _uiState = MutableStateFlow(AudiobookshelfPlayerUiState())
    val uiState: StateFlow<AudiobookshelfPlayerUiState> = _uiState.asStateFlow()

    val playbackState = playbackManager.playbackState
    val currentConfig = audiobookshelfRepository.currentConfig
    val equalizerState = equalizerManager.state
    val skipSilenceEnabled = skipSilenceManager.isEnabled

    init {
        startPlayback()
    }

    private fun startPlayback() {
        val currentState = playbackManager.playbackState.value
        if (currentState.sessionId != null && currentState.itemId == itemId) {
            Timber.d("Resuming existing playback session for item: $itemId")
            if (startPosition != null) {
                audiobookshelfPlayer.seekToPosition(startPosition)
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = audiobookshelfRepository.startPlaybackSession(itemId, episodeId)

            result.fold(
                onSuccess = { session ->
                    val serverUrl = currentConfig.value?.serverUrl
                    if (serverUrl != null) {
                        audiobookshelfPlayer.loadSession(
                            session,
                            serverUrl,
                            startPosition,
                            episodeSort,
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        Timber.d("Started playback session: ${session.id}")
                    } else {
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                error = "Server URL not available",
                            )
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                    Timber.e(error, "Failed to start playback session")
                },
            )
        }
    }

    fun togglePlayPause() {
        if (audiobookshelfPlayer.isPlaying()) {
            audiobookshelfPlayer.pause()
        } else {
            audiobookshelfPlayer.play()
        }
    }

    fun seekTo(positionSeconds: Double) {
        audiobookshelfPlayer.seekToPosition(positionSeconds)
    }

    fun skipForward() {
        audiobookshelfPlayer.skipForward(30)
    }

    fun skipBackward() {
        audiobookshelfPlayer.skipBackward(30)
    }

    fun seekToChapter(chapterIndex: Int) {
        audiobookshelfPlayer.seekToChapter(chapterIndex)
        dismissChapterSelector()
    }

    fun setPlaybackSpeed(speed: Float) {
        audiobookshelfPlayer.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        audiobookshelfPlayer.setSleepTimer(minutes)
        dismissSleepTimerDialog()
    }

    fun cancelSleepTimer() {
        audiobookshelfPlayer.cancelSleepTimer()
    }

    fun showChapterSelector() {
        _uiState.value = _uiState.value.copy(showChapterSelector = true)
    }

    fun dismissChapterSelector() {
        _uiState.value = _uiState.value.copy(showChapterSelector = false)
    }

    fun showSpeedSelector() {
        _uiState.value = _uiState.value.copy(showSpeedSelector = true)
    }

    fun dismissSpeedSelector() {
        _uiState.value = _uiState.value.copy(showSpeedSelector = false)
    }

    fun showSleepTimerDialog() {
        _uiState.value = _uiState.value.copy(showSleepTimerDialog = true)
    }

    fun dismissSleepTimerDialog() {
        _uiState.value = _uiState.value.copy(showSleepTimerDialog = false)
    }

    fun showEqualizer() {
        _uiState.value = _uiState.value.copy(showEqualizer = true)
    }

    fun dismissEqualizer() {
        _uiState.value = _uiState.value.copy(showEqualizer = false)
    }

    fun setEqEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)

    fun applyEqPreset(preset: EqualizerPreset) = equalizerManager.applyPreset(preset)

    fun setEqBandGain(index: Int, gainDb: Int) = equalizerManager.setBandGain(index, gainDb)

    fun setVolumeBoost(db: Int) = equalizerManager.setVolumeBoost(db)

    @OptIn(UnstableApi::class)
    fun setSkipSilence(enabled: Boolean) = skipSilenceManager.setEnabled(enabled)

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun stopPlayback() {
        viewModelScope.launch {
            audiobookshelfPlayer.pause()
            audiobookshelfPlayer.closeSession()
        }
    }
}

data class AudiobookshelfPlayerUiState(
    val isLoading: Boolean = false,
    val showChapterSelector: Boolean = false,
    val showSpeedSelector: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showEqualizer: Boolean = false,
    val error: String? = null,
)
