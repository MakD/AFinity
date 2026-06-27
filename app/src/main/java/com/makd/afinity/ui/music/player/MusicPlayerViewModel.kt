package com.makd.afinity.ui.music.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.cast.CastEvent
import com.makd.afinity.cast.CastManager
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.music.AfinityLyricLine
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.player.AudioService
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.player.music.MusicQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MusicPlayerViewModel
@Inject
constructor(
    private val playbackManager: MusicPlaybackManager,
    private val queueManager: MusicQueueManager,
    private val musicRepository: MusicRepository,
    private val castManager: CastManager,
    private val sessionManager: SessionManager,
    private val offlineModeManager: OfflineModeManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val playbackState: StateFlow<MusicPlaybackState> = playbackManager.state
    val queue: StateFlow<List<AfinityTrack>> = queueManager.queue
    val currentIndex: StateFlow<Int> = queueManager.currentIndex

    val isOffline: StateFlow<Boolean> =
        offlineModeManager.isOffline.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isMusicCasting: StateFlow<Boolean> =
        castManager.castState
            .map { it.isMusicCasting }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _lyrics = MutableStateFlow<List<AfinityLyricLine>>(emptyList())
    val lyrics: StateFlow<List<AfinityLyricLine>> = _lyrics.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collectLatest { trackId ->
                    if (trackId != null) loadLyrics(trackId) else _lyrics.value = emptyList()
                }
        }
        viewModelScope.launch {
            castManager.castState.collect { state ->
                if (state.isMusicCasting) {
                    val durationMs =
                        playbackState.value.currentTrack?.runtimeTicks?.div(10_000)
                            ?: state.duration
                    playbackManager.updatePosition(state.currentPosition, 0L, durationMs)
                    playbackManager.updatePlayingState(state.isPlaying)
                    playbackManager.updateBufferingState(state.isBuffering)
                }
            }
        }
        viewModelScope.launch {
            castManager.castEvents.collect { event ->
                when (event) {
                    is CastEvent.Connected -> {
                        if (playbackState.value.currentTrack != null && !isMusicCasting.value) {
                            castCurrentQueue()
                        }
                    }
                    is CastEvent.MusicCastDisconnected -> resumeFromCast(event.lastPositionMs)
                    else -> {}
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun resumeFromCast(lastPositionMs: Long) {
        val tracks = queueManager.queue.value
        val index = queueManager.currentIndex.value
        if (tracks.isEmpty()) return
        queueManager.loadQueue(tracks, index, lastPositionMs)
        context.startService(
            Intent(context, AudioService::class.java).setAction(AudioService.ACTION_ENGINE_MUSIC)
        )
    }

    @OptIn(UnstableApi::class)
    fun castCurrentQueue() {
        val tracks = queueManager.queue.value
        if (tracks.isEmpty()) return
        val index = queueManager.currentIndex.value
        val positionMs = playbackState.value.positionMs
        val serverUrl = sessionManager.currentSession.value?.serverUrl ?: return
        val token = sessionManager.getCurrentApiClient()?.accessToken ?: return

        castManager.loadMusicQueue(
            tracks = tracks,
            startIndex = index,
            startPositionMs = positionMs,
            serverBaseUrl = serverUrl,
            token = token,
        )
        context.startService(
            Intent(context, AudioService::class.java).setAction(AudioService.ACTION_PAUSE_FOR_CAST)
        )
    }

    private suspend fun loadLyrics(trackId: UUID) {
        _lyricsLoading.value = true
        _lyrics.value = runCatching { musicRepository.getLyrics(trackId) }.getOrDefault(emptyList())
        _lyricsLoading.value = false
    }

    fun playQueue(tracks: List<AfinityTrack>, startIndex: Int = 0) {
        queueManager.loadQueue(tracks, startIndex)
    }

    fun play() {
        if (isMusicCasting.value) castManager.play() else playbackManager.play()
    }

    fun pause() {
        if (isMusicCasting.value) castManager.pause() else playbackManager.pause()
    }

    fun togglePlayPause() {
        if (isMusicCasting.value) {
            if (castManager.castState.value.isPlaying) castManager.pause() else castManager.play()
        } else {
            if (playbackState.value.isPlaying) playbackManager.pause() else playbackManager.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (isMusicCasting.value) castManager.seekTo(positionMs)
        else playbackManager.seekTo(positionMs)
    }

    fun seekForward(amountMs: Long = 15_000L) {
        if (isMusicCasting.value) {
            val pos =
                (castManager.castState.value.currentPosition + amountMs).coerceAtMost(
                    playbackState.value.durationMs
                )
            castManager.seekTo(pos)
        } else {
            val pos =
                (playbackState.value.positionMs + amountMs).coerceAtMost(
                    playbackState.value.durationMs
                )
            playbackManager.seekTo(pos)
        }
    }

    fun seekBackward(amountMs: Long = 15_000L) {
        if (isMusicCasting.value) {
            val pos = (castManager.castState.value.currentPosition - amountMs).coerceAtLeast(0L)
            castManager.seekTo(pos)
        } else {
            val pos = (playbackState.value.positionMs - amountMs).coerceAtLeast(0L)
            playbackManager.seekTo(pos)
        }
    }

    fun skipNext() {
        if (isMusicCasting.value) {
            castManager.skipMusicTrack(forward = true)
        } else {
            playbackManager.skipToNext()
        }
    }

    fun skipPrevious() {
        if (isMusicCasting.value) {
            castManager.skipMusicTrack(forward = false)
        } else {
            val player = playbackManager.getPlayer() ?: return
            if (player.currentPosition > 3_000L) {
                player.seekTo(0L)
            } else {
                player.seekToPreviousMediaItem()
            }
        }
    }

    fun skipToIndex(index: Int) {
        if (!isMusicCasting.value) {
            playbackManager.seekToIndex(index)
            queueManager.setCurrentIndex(index)
        }
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle(playbackState.value.positionMs)
        playbackManager.updateShuffled(!playbackState.value.shuffled)
    }

    fun cycleRepeatMode() {
        val next =
            when (playbackState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        playbackManager.updateRepeatMode(next)
        playbackManager.getPlayer()?.repeatMode =
            when (next) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
    }

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }

    fun addNext(tracks: List<AfinityTrack>) = queueManager.addNext(tracks)

    fun addLast(tracks: List<AfinityTrack>) = queueManager.addLast(tracks)

    fun removeFromQueue(index: Int) = queueManager.removeAt(index)

    fun moveInQueue(from: Int, to: Int) = queueManager.moveTrack(from, to)

    fun clearQueue() = queueManager.clearQueue()

    fun playInstantMix(itemId: UUID) {
        viewModelScope.launch {
            val tracks = runCatching {
                musicRepository.getInstantMix(itemId)
            }
                .getOrDefault(emptyList())
            if (tracks.isNotEmpty()) playQueue(tracks)
        }
    }

    fun playArtistRadio(artistId: UUID) {
        viewModelScope.launch {
            val tracks = runCatching {
                musicRepository.getArtistRadio(artistId)
            }
                .getOrDefault(emptyList())
            if (tracks.isNotEmpty()) playQueue(tracks)
        }
    }

    fun toggleCurrentTrackFavorite() {
        val track = playbackState.value.currentTrack ?: return
        val newFav = !track.favorite
        val updated = track.copy(favorite = newFav)
        playbackManager.updateTrack(updated)
        queueManager.updateTrackInQueue(updated)
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(track.id, newFav) }
                .onFailure {
                    playbackManager.updateTrack(track)
                    queueManager.updateTrackInQueue(track)
                }
        }
    }

    fun setSleepTimer(durationMs: Long) = playbackManager.setSleepTimer(durationMs)

    fun cancelSleepTimer() = playbackManager.cancelSleepTimer()
}
