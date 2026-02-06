package com.makd.afinity.ui.audiobookshelf.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AudiobookshelfPlayerViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
    val playbackManager: AudiobookshelfPlaybackManager,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>("itemId") ?: ""
    private val episodeId: String? = savedStateHandle.get<String>("episodeId")

    private val _uiState = MutableStateFlow(AudiobookshelfPlayerUiState())
    val uiState: StateFlow<AudiobookshelfPlayerUiState> = _uiState.asStateFlow()

    val playbackState = playbackManager.playbackState
    val currentConfig = audiobookshelfRepository.currentConfig

    init {
        startPlayback()
    }

    private fun startPlayback() {
        val currentState = playbackManager.playbackState.value
        if (currentState.sessionId != null && currentState.itemId == itemId) {
            Timber.d("Resuming existing playback session for item: $itemId")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = audiobookshelfRepository.startPlaybackSession(itemId, episodeId)

            result.fold(
                onSuccess = { session ->
                    val serverUrl = currentConfig.value?.serverUrl
                    if (serverUrl != null) {
                        audiobookshelfPlayer.loadSession(session, serverUrl)
                        audiobookshelfPlayer.play()
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
    val error: String? = null,
)
