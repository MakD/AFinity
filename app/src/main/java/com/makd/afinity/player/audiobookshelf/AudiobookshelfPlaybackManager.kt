package com.makd.afinity.player.audiobookshelf

import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfPlaybackManager @Inject constructor() {

    private val _playbackState = MutableStateFlow(AudiobookshelfPlaybackState())
    val playbackState: StateFlow<AudiobookshelfPlaybackState> = _playbackState.asStateFlow()

    private val _currentSession = MutableStateFlow<PlaybackSession?>(null)
    val currentSession: StateFlow<PlaybackSession?> = _currentSession.asStateFlow()

    private val _currentAudioDecoder = MutableStateFlow("Unknown")
    val currentAudioDecoder: StateFlow<String> = _currentAudioDecoder.asStateFlow()

    fun updateAudioDecoder(decoderName: String, isHardwareAccelerated: Boolean) {
        _currentAudioDecoder.value = if (isHardwareAccelerated) "H/W Dec" else "S/W Dec"
    }

    fun setSession(session: PlaybackSession, serverUrl: String? = null, token: String? = null) {
        android.util.Log.d(
            "ABS-MiniPlayer",
            "PlaybackManager.setSession: id=${session.id} title=${session.displayTitle} author=${session.displayAuthor}",
        )
        _currentSession.value = session

        val coverUrl =
            if (session.id.startsWith("local_") && session.coverPath != null) {
                session.coverPath
            } else if (serverUrl != null) {
                val base = "$serverUrl/api/items/${session.libraryItemId}/cover"
                "$base?raw=1"
            } else {
                session.coverPath
            }

        _playbackState.value =
            _playbackState.value.copy(
                sessionId = session.id,
                itemId = session.libraryItemId,
                episodeId = session.episodeId,
                duration = session.duration,
                chapters = session.chapters ?: emptyList(),
                audioTracks = session.audioTracks ?: emptyList(),
                displayTitle = session.displayTitle ?: "Unknown",
                displayAuthor = session.displayAuthor,
                coverUrl = coverUrl,
            )
    }

    fun updatePosition(currentTime: Double, bufferedPosition: Double? = null) {
        _playbackState.value =
            _playbackState.value.copy(
                currentTime = currentTime,
                bufferedPosition = bufferedPosition ?: _playbackState.value.bufferedPosition,
                currentChapter = findCurrentChapter(currentTime),
            )
    }

    fun updatePlayingState(isPlaying: Boolean) {
        _playbackState.update { it.copy(isPlaying = isPlaying) }
    }

    fun updateBufferingState(isBuffering: Boolean) {
        _playbackState.update { it.copy(isBuffering = isBuffering) }
    }

    fun updatePlayerError(message: String?) {
        _playbackState.update { it.copy(playerError = message) }
    }

    fun updatePlaybackSpeed(speed: Float) {
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    fun setSleepTimer(endTimeMillis: Long?) {
        _playbackState.update { it.copy(sleepTimerEndTime = endTimeMillis) }
    }

    fun setPlaylistInfo(episodeIds: List<String>) {
        _playbackState.value =
            _playbackState.value.copy(isPodcastPlaylist = true, playlistEpisodeIds = episodeIds)
    }

    fun setChapterBasedPlayback(isChapterBased: Boolean) {
        _playbackState.update { it.copy(isChapterBasedPlayback = isChapterBased) }
    }

    fun updateSessionInfo(sessionId: String, episodeId: String?) {
        _playbackState.value =
            _playbackState.value.copy(sessionId = sessionId, episodeId = episodeId)
    }

    fun clearSession() {
        Timber.tag("ABS-MiniPlayer")
            .d(
                "PlaybackManager.clearSession: was sessionId=${_currentSession.value?.id} displayTitle=${_playbackState.value.displayTitle}"
            )
        _currentSession.value = null
        _playbackState.update { current ->
            AudiobookshelfPlaybackState(
                itemId = current.itemId,
                displayTitle = current.displayTitle,
                displayAuthor = current.displayAuthor,
                coverUrl = current.coverUrl,
            )
        }
    }

    fun preloadDisplayMetadata(itemId: String, title: String, author: String?, coverUrl: String?) {
        _playbackState.update { current ->
            if (current.sessionId != null && current.itemId == itemId) return@update current
            current.copy(
                itemId = itemId,
                displayTitle = title,
                displayAuthor = author,
                coverUrl = coverUrl,
            )
        }
    }

    private fun findCurrentChapter(currentTime: Double): BookChapter? {
        return _playbackState.value.chapters.find { chapter ->
            currentTime >= chapter.start && currentTime < chapter.end
        }
    }

    fun getNextChapter(): BookChapter? {
        val currentChapter =
            _playbackState.value.currentChapter
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
    val bufferedPosition: Double = 0.0,
    val duration: Double = 0.0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val chapters: List<BookChapter> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val currentChapter: BookChapter? = null,
    val sleepTimerEndTime: Long? = null,
    val isPodcastPlaylist: Boolean = false,
    val playlistEpisodeIds: List<String> = emptyList(),
    val isChapterBasedPlayback: Boolean = false,
    val playerError: String? = null,
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
