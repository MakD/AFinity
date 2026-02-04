package com.makd.afinity.player.audiobookshelf

import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfPlaybackManager @Inject constructor() {

    private val _playbackState = MutableStateFlow(AudiobookshelfPlaybackState())
    val playbackState: StateFlow<AudiobookshelfPlaybackState> = _playbackState.asStateFlow()

    private val _currentSession = MutableStateFlow<PlaybackSession?>(null)
    val currentSession: StateFlow<PlaybackSession?> = _currentSession.asStateFlow()

    fun setSession(session: PlaybackSession, serverUrl: String? = null, token: String? = null) {
        _currentSession.value = session

        val coverUrl = if (serverUrl != null) {
            val base = "$serverUrl/api/items/${session.libraryItemId}/cover"
            if (token != null) "$base?token=$token" else base
        } else {
            session.coverPath
        }

        _playbackState.value = _playbackState.value.copy(
            sessionId = session.id,
            itemId = session.libraryItemId,
            episodeId = session.episodeId,
            duration = session.duration,
            chapters = session.chapters ?: emptyList(),
            audioTracks = session.audioTracks ?: emptyList(),
            displayTitle = session.displayTitle ?: "Unknown",
            displayAuthor = session.displayAuthor,
            coverUrl = coverUrl
        )
    }

    fun updatePosition(currentTime: Double) {
        _playbackState.value = _playbackState.value.copy(
            currentTime = currentTime,
            currentChapter = findCurrentChapter(currentTime)
        )
    }

    fun updatePlayingState(isPlaying: Boolean) {
        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
    }

    fun updateBufferingState(isBuffering: Boolean) {
        _playbackState.value = _playbackState.value.copy(isBuffering = isBuffering)
    }

    fun updatePlaybackSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun setSleepTimer(endTimeMillis: Long?) {
        _playbackState.value = _playbackState.value.copy(sleepTimerEndTime = endTimeMillis)
    }

    fun clearSession() {
        _currentSession.value = null
        _playbackState.value = AudiobookshelfPlaybackState()
    }

    private fun findCurrentChapter(currentTime: Double): BookChapter? {
        return _playbackState.value.chapters.find { chapter ->
            currentTime >= chapter.start && currentTime < chapter.end
        }
    }

    fun getNextChapter(): BookChapter? {
        val currentChapter = _playbackState.value.currentChapter
            ?: return _playbackState.value.chapters.firstOrNull()
        val currentIndex = _playbackState.value.chapters.indexOf(currentChapter)
        return _playbackState.value.chapters.getOrNull(currentIndex + 1)
    }

    fun getPreviousChapter(): BookChapter? {
        val currentChapter = _playbackState.value.currentChapter ?: return null
        val currentIndex = _playbackState.value.chapters.indexOf(currentChapter)
        return _playbackState.value.chapters.getOrNull(currentIndex - 1)
    }
}

data class AudiobookshelfPlaybackState(
    val sessionId: String? = null,
    val itemId: String? = null,
    val episodeId: String? = null,
    val displayTitle: String = "",
    val displayAuthor: String? = null,
    val coverUrl: String? = null,
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val chapters: List<BookChapter> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val currentChapter: BookChapter? = null,
    val sleepTimerEndTime: Long? = null
) {
    val currentChapterIndex: Int
        get() = currentChapter?.let { chapter -> chapters.indexOf(chapter) } ?: -1

    val progress: Float
        get() = if (duration > 0) (currentTime / duration).toFloat() else 0f

    val remainingTime: Double
        get() = (duration - currentTime).coerceAtLeast(0.0)

    val hasSleepTimer: Boolean
        get() = sleepTimerEndTime != null && sleepTimerEndTime > System.currentTimeMillis()
}
