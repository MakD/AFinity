package com.makd.afinity.ui.audiobookshelf.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.cast.CastEvent
import com.makd.afinity.cast.CastManager
import com.makd.afinity.data.models.player.PlaybackStats
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfEqualizerManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.audiobookshelf.AudiobookshelfSkipSilenceManager
import com.makd.afinity.player.common.EqualizerPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfPlayerViewModel
@OptIn(UnstableApi::class)
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
    val playbackManager: AudiobookshelfPlaybackManager,
    private val equalizerManager: AudiobookshelfEqualizerManager,
    private val skipSilenceManager: AudiobookshelfSkipSilenceManager,
    private val castManager: CastManager,
    private val securePreferencesRepository: SecurePreferencesRepository,
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

    val isAbsCasting: StateFlow<Boolean> =
        castManager.castState
            .map { it.isAbsCasting }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        startPlayback()
        observePlayerErrors()
        observeCastState()
        observeCastEvents()
    }

    private fun observePlayerErrors() {
        viewModelScope.launch {
            playbackManager.playbackState.collect { state ->
                val error = state.playerError ?: return@collect
                _uiState.value = _uiState.value.copy(error = error)
                playbackManager.updatePlayerError(null)
            }
        }
    }

    private fun observeCastState() {
        viewModelScope.launch {
            castManager.castState.collect { state ->
                if (state.isAbsCasting) {
                    val globalPositionSeconds = state.currentPosition / 1000.0
                    playbackManager.updatePosition(globalPositionSeconds)
                    playbackManager.updatePlayingState(state.isPlaying)
                    playbackManager.updateBufferingState(state.isBuffering)
                }
            }
        }
    }

    private fun observeCastEvents() {
        viewModelScope.launch {
            castManager.castEvents.collect { event ->
                when (event) {
                    is CastEvent.Connected -> {
                        val state = playbackManager.playbackState.value
                        val sessionId = state.sessionId
                        if (
                            sessionId != null &&
                                !sessionId.startsWith("local_") &&
                                !isAbsCasting.value
                        ) {
                            castCurrentSession()
                        }
                    }
                    is CastEvent.AbsCastDisconnected -> {
                        audiobookshelfPlayer.seekToPosition(event.lastPositionSeconds)
                        audiobookshelfPlayer.play()
                    }
                    else -> {}
                }
            }
        }
    }

    fun castCurrentSession() {
        val state = playbackManager.playbackState.value
        val sessionId = state.sessionId ?: return
        if (sessionId.startsWith("local_")) return
        val tracks = state.audioTracks
        if (tracks.isEmpty()) return
        val baseUrl = currentConfig.value?.serverUrl ?: return
        val token = securePreferencesRepository.getCachedAudiobookshelfToken() ?: return

        castManager.loadAbsSession(
            tracks = tracks,
            startGlobalTimeSeconds = state.currentTime,
            displayTitle = state.displayTitle,
            author = state.displayAuthor,
            coverUrl = state.coverUrl,
            baseUrl = baseUrl,
            token = token,
        )
        audiobookshelfPlayer.pause()
    }

    @UnstableApi
    private fun startPlayback() {
        if (castManager.castState.value.isAbsCasting) return
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
            val needsPreload = currentState.displayTitle.isEmpty() || currentState.itemId != itemId
            if (needsPreload) {
                val cached = audiobookshelfRepository.getCachedItemMetadata(itemId)
                if (cached != null) {
                    val (title, author, coverUrl) = cached
                    playbackManager.preloadDisplayMetadata(itemId, title, author, coverUrl)
                }
            }

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
                                error = context.getString(R.string.error_server_url_not_available),
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
        if (isAbsCasting.value) {
            if (castManager.castState.value.isPlaying) castManager.pause() else castManager.play()
        } else if (audiobookshelfPlayer.isPlaying()) {
            audiobookshelfPlayer.pause()
        } else {
            audiobookshelfPlayer.play()
        }
    }

    fun seekTo(positionSeconds: Double) {
        if (isAbsCasting.value) {
            castManager.seekAbsGlobalPosition((positionSeconds * 1000).toLong())
        } else {
            audiobookshelfPlayer.seekToPosition(positionSeconds)
        }
    }

    fun skipForward() {
        if (isAbsCasting.value) {
            val newPos =
                (castManager.castState.value.currentPosition + 30_000L).coerceAtMost(
                    (playbackManager.playbackState.value.duration * 1000).toLong()
                )
            castManager.seekAbsGlobalPosition(newPos)
        } else {
            audiobookshelfPlayer.skipForward(30)
        }
    }

    fun skipBackward() {
        if (isAbsCasting.value) {
            val newPos = (castManager.castState.value.currentPosition - 30_000L).coerceAtLeast(0L)
            castManager.seekAbsGlobalPosition(newPos)
        } else {
            audiobookshelfPlayer.skipBackward(30)
        }
    }

    fun seekToChapter(chapterIndex: Int) {
        if (isAbsCasting.value) {
            val chapter =
                playbackManager.playbackState.value.chapters.getOrNull(chapterIndex) ?: return
            castManager.seekAbsGlobalPosition((chapter.start * 1000).toLong())
        } else {
            audiobookshelfPlayer.seekToChapter(chapterIndex)
        }
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
        statsPollingJob?.cancel()
        super.onCleared()
    }

    private var statsPollingJob: kotlinx.coroutines.Job? = null

    fun togglePlaybackStats() {
        val willShow = !_uiState.value.showPlaybackStats
        _uiState.value = _uiState.value.copy(showPlaybackStats = willShow)
        if (willShow) {
            startStatsPolling()
        } else {
            statsPollingJob?.cancel()
        }
    }

    private fun startStatsPolling() {
        statsPollingJob?.cancel()
        statsPollingJob = viewModelScope.launch {
            while (true) {
                if (_uiState.value.showPlaybackStats) {
                    _uiState.value =
                        _uiState.value.copy(playbackStats = audiobookshelfPlayer.getPlaybackStats())
                }
                delay(1000L)
            }
        }
    }

    fun stopPlayback() {
        audiobookshelfPlayer.pause()
        audiobookshelfPlayer.closeSession()
    }
}

data class AudiobookshelfPlayerUiState(
    val isLoading: Boolean = false,
    val showChapterSelector: Boolean = false,
    val showSpeedSelector: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showEqualizer: Boolean = false,
    val error: String? = null,
    val showPlaybackStats: Boolean = false,
    val playbackStats: PlaybackStats = PlaybackStats(),
)
