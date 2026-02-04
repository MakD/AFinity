package com.makd.afinity.player.audiobookshelf

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val progressSyncer: AudiobookshelfProgressSyncer,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val securePreferencesRepository: SecurePreferencesRepository
) {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var currentSession: PlaybackSession? = null
    private var serverUrl: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playbackManager.updatePlayingState(isPlaying)

            if (isPlaying) {
                startPositionUpdates()
                progressSyncer.startSyncing()
            } else {
                stopPositionUpdates()
                scope.launch {
                    progressSyncer.syncNow()
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    playbackManager.updateBufferingState(true)
                }

                Player.STATE_READY -> {
                    playbackManager.updateBufferingState(false)
                }

                Player.STATE_ENDED -> {
                    playbackManager.updatePlayingState(false)
                    stopPositionUpdates()
                    scope.launch {
                        progressSyncer.syncNow()
                        audiobookshelfRepository.updateProgress(
                            itemId = currentSession?.libraryItemId ?: return@launch,
                            episodeId = currentSession?.episodeId,
                            currentTime = playbackManager.playbackState.value.duration,
                            duration = playbackManager.playbackState.value.duration,
                            isFinished = true
                        )
                    }
                }

                Player.STATE_IDLE -> {
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Player error: ${error.message}")
            playbackManager.updatePlayingState(false)
            playbackManager.updateBufferingState(false)
        }
    }

    @OptIn(UnstableApi::class)
    fun initialize() {
        if (exoPlayer != null) return

        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                buildMap {
                    if (token != null) {
                        put("Authorization", "Bearer $token")
                    }
                }
            )

        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
                playWhenReady = false
            }

        Timber.d("AudiobookshelfPlayer initialized")
    }

    fun release() {
        stopPositionUpdates()
        progressSyncer.stopSyncing()
        cancelSleepTimer()

        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null

        playbackManager.clearSession()
        currentSession = null

        Timber.d("AudiobookshelfPlayer released")
    }

    suspend fun loadSession(session: PlaybackSession, baseUrl: String) {
        release()
        initialize()

        currentSession = session
        serverUrl = baseUrl

        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
        playbackManager.setSession(session, baseUrl, token)

        val audioTracks = session.audioTracks
        if (audioTracks.isNullOrEmpty()) {
            Timber.e("No audio tracks in session")
            return
        }

        val mediaItems = audioTracks.map { track ->
            val url = if (track.contentUrl?.startsWith("http") == true) {
                track.contentUrl
            } else {
                "$baseUrl${track.contentUrl}"
            }

            MediaItem.Builder()
                .setUri(url)
                .setMediaId(track.index.toString())
                .build()
        }

        exoPlayer?.apply {
            setMediaItems(mediaItems)
            prepare()
            val startTime = session.currentTime
            if (startTime > 0) {
                seekToPosition(startTime)
            }
        }

        Timber.d("Loaded session with ${audioTracks.size} tracks, starting at ${session.currentTime}s")
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        updatePosition()
    }

    fun seekToPosition(positionSeconds: Double) {
        val audioTracks = currentSession?.audioTracks ?: return
        var accumulatedDuration = 0.0

        for ((index, track) in audioTracks.withIndex()) {
            if (positionSeconds < accumulatedDuration + track.duration) {
                val positionInTrack = positionSeconds - accumulatedDuration
                exoPlayer?.apply {
                    seekTo(index, (positionInTrack * 1000).toLong())
                }
                break
            }
            accumulatedDuration += track.duration
        }

        updatePosition()
    }

    fun skipForward(seconds: Int = 30) {
        val currentPosition = getCurrentPositionSeconds()
        seekToPosition(currentPosition + seconds)
    }

    fun skipBackward(seconds: Int = 30) {
        val currentPosition = getCurrentPositionSeconds()
        seekToPosition((currentPosition - seconds).coerceAtLeast(0.0))
    }

    fun seekToChapter(chapterIndex: Int) {
        val chapters = playbackManager.playbackState.value.chapters
        if (chapterIndex in chapters.indices) {
            seekToPosition(chapters[chapterIndex].start)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        playbackManager.updatePlaybackSpeed(speed)
    }

    fun setSleepTimer(durationMinutes: Int) {
        cancelSleepTimer()

        if (durationMinutes <= 0) {
            playbackManager.setSleepTimer(null)
            return
        }

        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        playbackManager.setSleepTimer(endTime)

        sleepTimerJob = scope.launch {
            delay(durationMinutes * 60 * 1000L)
            pause()
            playbackManager.setSleepTimer(null)
            Timber.d("Sleep timer triggered - pausing playback")
        }

        Timber.d("Sleep timer set for $durationMinutes minutes")
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        playbackManager.setSleepTimer(null)
    }

    fun getCurrentPositionSeconds(): Double {
        val player = exoPlayer ?: return 0.0
        val audioTracks = currentSession?.audioTracks ?: return 0.0
        var totalPosition = 0.0
        val currentMediaItemIndex = player.currentMediaItemIndex

        for (i in 0 until currentMediaItemIndex) {
            totalPosition += audioTracks.getOrNull(i)?.duration ?: 0.0
        }

        totalPosition += player.currentPosition / 1000.0

        return totalPosition
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()

        positionUpdateJob = scope.launch {
            while (true) {
                updatePosition()
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updatePosition() {
        val position = getCurrentPositionSeconds()
        playbackManager.updatePosition(position)
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    suspend fun closeSession() {
        val state = playbackManager.playbackState.value
        val sessionId = state.sessionId ?: return

        progressSyncer.stopSyncing()

        try {
            audiobookshelfRepository.closePlaybackSession(
                sessionId = sessionId,
                currentTime = state.currentTime,
                timeListened = 0.0,
                duration = state.duration
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to close session")
        }

        playbackManager.clearSession()
    }
}
