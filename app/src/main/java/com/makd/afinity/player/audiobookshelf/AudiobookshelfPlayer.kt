package com.makd.afinity.player.audiobookshelf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.makd.afinity.R
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.EpisodeSort
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisodeOrder
import com.makd.afinity.data.models.player.PlaybackStats
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsProgressSyncScheduler
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val RESUME_SNAP_MS = 1_000L

@Singleton
class AudiobookshelfPlayer
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val sessionManager: SessionManager,
    private val absSyncScheduler: AbsProgressSyncScheduler,
    private val musicPlaybackManager: MusicPlaybackManager,
    private val progressSyncer: AudiobookshelfProgressSyncer,
    private val preferencesRepository: PreferencesRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
) {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var queueStartPositions: Map<Int, Double> = emptyMap()

    private class PodcastQueue(
        val episodes: List<PodcastEpisode>,
        val tracks: List<AudioTrack>,
        val startPositions: Map<Int, Double>,
    )

    private val queueListener =
        object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val isAutoAdvance = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                if (!isAutoAdvance && reason != Player.DISCONTINUITY_REASON_SEEK) return
                val state = playbackManager.playbackState.value
                if (!state.isPodcastPlaylist) return
                val oldIndex = oldPosition.mediaItemIndex
                val newIndex = newPosition.mediaItemIndex
                if (oldIndex == newIndex) return
                val previousEpisodeId = state.playlistEpisodeIds.getOrNull(oldIndex) ?: return
                val newEpisodeId = state.playlistEpisodeIds.getOrNull(newIndex) ?: return
                if (previousEpisodeId != state.episodeId) return

                val previousDuration =
                    state.chapters.getOrNull(oldIndex)?.let {
                        (it.end - it.start).coerceAtLeast(0.0)
                    } ?: 0.0
                val previousTime =
                    if (isAutoAdvance) previousDuration
                    else (oldPosition.positionMs / 1000.0).coerceIn(0.0, previousDuration)

                playbackManager.setActiveEpisode(newEpisodeId)
                progressSyncer.onPlaylistEpisodeChanged(
                    previousEpisodeId,
                    previousTime,
                    previousDuration,
                    newEpisodeId,
                )

                val landedAtStart = newPosition.positionMs < RESUME_SNAP_MS
                if (isAutoAdvance || landedAtStart) {
                    queueStartPositions[newIndex]?.let { resumeAt ->
                        mediaController?.seekTo(newIndex, (resumeAt * 1000).toLong())
                    }
                }
            }
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepTimerJob: Job? = null

    init {
        scope.launch {
            var previousSessionKey: String? = null
            sessionManager.currentSession.collect { session ->
                val newKey = session?.let { "${it.serverId}/${it.userId}" }
                if (previousSessionKey != null && newKey != previousSessionKey) {
                    if (playbackManager.playbackState.value.sessionId != null) {
                        Timber.d("Jellyfin session changed, closing Audiobookshelf playback")
                        closeSession()
                    }
                }
                previousSessionKey = newKey
            }
        }

        scope.launch {
            var wasAuthenticated = false
            audiobookshelfRepository.isAuthenticated.collect { isAuthenticated ->
                if (wasAuthenticated && !isAuthenticated) {
                    if (playbackManager.playbackState.value.sessionId != null) {
                        Timber.w(
                            "Audiobookshelf auth lost while playback active - playback continues"
                        )
                    }
                }
                wasAuthenticated = isAuthenticated
            }
        }
    }

    @UnstableApi
    private suspend fun getConnectedController(): MediaController? {
        if (mediaController != null) {
            Timber.d(
                "ABS getConnectedController: FAST PATH — reusing cached controller, switchToAbs() may not have run yet"
            )
            return mediaController
        }

        Timber.d(
            "ABS getConnectedController: building new connection (coroutine will suspend during await)"
        )
        val sessionToken =
            SessionToken(
                context,
                ComponentName(context, com.makd.afinity.player.AudioService::class.java),
            )

        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        return try {
            mediaController = future.await().also { it.addListener(queueListener) }
            Timber.d("ABS getConnectedController: connected to AudioService")
            mediaController
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "ABS getConnectedController: FAILED to connect to AudioService")
            controllerFuture = null
            null
        }
    }

    @UnstableApi
    fun loadSession(
        session: PlaybackSession,
        baseUrl: String,
        startPosition: Double? = null,
        episodeSort: String? = null,
        includePlayed: Boolean = false,
    ) {
        scope.launch {
            val trackBefore = musicPlaybackManager.state.value.currentTrack
            Timber.d(
                "ABS loadSession: START itemId=${session.libraryItemId} musicTrack=${trackBefore?.name} controllerCached=${mediaController != null}"
            )
            musicPlaybackManager.updateTrack(null)
            Timber.d("ABS loadSession: music track cleared synchronously")
            context.startService(
                Intent(context, com.makd.afinity.player.AudioService::class.java)
                    .setAction(com.makd.afinity.player.AudioService.ACTION_ENGINE_ABS)
            )
            Timber.d("ABS loadSession: ACTION_ENGINE_ABS intent sent")
            val controller = getConnectedController() ?: return@launch
            Timber.d("ABS loadSession: controller connected")

            val token = securePreferencesRepository.getCachedAudiobookshelfToken()
            val queue = buildPodcastQueue(session, episodeSort, includePlayed)
            val isPodcastPlaylist = queue != null

            val audioTracks =
                if (queue != null) {
                    Timber.d("Loading ${queue.tracks.size} episodes for podcast playlist")
                    queue.tracks
                } else if (session.audioTracks.isNullOrEmpty()) {
                    Timber.e("No audio tracks found in session. Session data: $session")
                    return@launch
                } else {
                    session.audioTracks
                }

            val enhancedSession =
                if (queue != null) {
                    var accumulatedTime = 0.0
                    val episodeChapters =
                        queue.episodes.mapIndexed { index, episode ->
                            val length = queue.tracks[index].duration
                            BookChapter(
                                    id = index,
                                    start = accumulatedTime,
                                    end = accumulatedTime + length,
                                    title = episode.title,
                                )
                                .also { accumulatedTime += length }
                        }

                    session.copy(
                        audioTracks = audioTracks,
                        displayTitle =
                            session.mediaMetadata?.title ?: session.displayTitle ?: "Podcast",
                        displayAuthor = session.mediaMetadata?.authorName ?: session.displayAuthor,
                        duration = accumulatedTime,
                        chapters = episodeChapters,
                    )
                } else {
                    session
                }

            playbackManager.setSession(enhancedSession, baseUrl, token)
            progressSyncer.onSessionLoaded(session.episodeId)
            queueStartPositions = queue?.startPositions ?: emptyMap()

            if (queue != null) {
                playbackManager.setPlaylistInfo(queue.episodes.map { it.id })
            }

            val artUrl =
                if (enhancedSession.id.startsWith("local_")) {
                    enhancedSession.coverPath
                } else if (baseUrl.isNotEmpty()) {
                    "$baseUrl/api/items/${enhancedSession.libraryItemId}/cover?raw=1"
                } else {
                    enhancedSession.coverPath
                }

            val chapterMediaItems =
                if (!isPodcastPlaylist) {
                    buildChapterMediaItems(
                        chapters = enhancedSession.chapters ?: emptyList(),
                        audioTracks = audioTracks,
                        baseUrl = baseUrl,
                        token = token,
                        artUrl = artUrl,
                        bookTitle =
                            enhancedSession.displayTitle
                                ?: enhancedSession.mediaMetadata?.title
                                ?: "",
                        author =
                            enhancedSession.displayAuthor
                                ?: enhancedSession.mediaMetadata?.authorName
                                ?: "",
                    )
                } else null

            playbackManager.setChapterBasedPlayback(!isPodcastPlaylist && chapterMediaItems != null)

            val mediaItems =
                chapterMediaItems
                    ?: audioTracks.mapIndexed { index, track ->
                        val url =
                            if (
                                track.contentUrl?.startsWith("http") == true ||
                                    track.contentUrl?.startsWith("file") == true
                            )
                                track.contentUrl
                            else "$baseUrl${track.contentUrl}"

                        val itemTitle =
                            if (isPodcastPlaylist && track.title != null) {
                                track.title
                            } else {
                                enhancedSession.displayTitle
                                    ?: enhancedSession.mediaMetadata?.title
                                    ?: "Unknown"
                            }

                        MediaItem.Builder()
                            .setUri(url)
                            .setMediaId(index.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(itemTitle)
                                    .setArtist(
                                        enhancedSession.displayAuthor
                                            ?: enhancedSession.mediaMetadata?.authorName
                                            ?: ""
                                    )
                                    .setArtworkUri(artUrl?.toUri())
                                    .build()
                            )
                            .build()
                    }

            Timber.d("Sending ${mediaItems.size} items to player")

            controller.stop()
            controller.clearMediaItems()
            controller.setMediaItems(mediaItems)
            controller.prepare()

            if (queue != null && session.episodeId != null) {
                val episodeIndex = queue.episodes.indexOfFirst { it.id == session.episodeId }
                val seekPosition = startPosition ?: session.currentTime
                if (episodeIndex > 0) {
                    controller.seekTo(episodeIndex, (seekPosition * 1000).toLong())
                } else if (seekPosition > 0) {
                    controller.seekTo(0, (seekPosition * 1000).toLong())
                }
            } else {
                val seekPosition = startPosition ?: session.currentTime
                if (seekPosition > 0) {
                    seekToPosition(seekPosition)
                }
            }
            controller.play()
        }
    }

    private suspend fun buildPodcastQueue(
        session: PlaybackSession,
        episodeSort: String?,
        includePlayed: Boolean,
    ): PodcastQueue? {
        val chosenEpisodeId = session.episodeId ?: return null
        if (session.mediaType != "podcast") return null
        val itemId = session.libraryItemId

        var cachedItemValue: LibraryItem? = null
        var cachedItemLoaded = false
        suspend fun cachedItem(): LibraryItem? {
            if (!cachedItemLoaded) {
                cachedItemValue = audiobookshelfRepository.getCachedItem(itemId)
                cachedItemLoaded = true
            }
            return cachedItemValue
        }

        val allEpisodes =
            session.libraryItem?.media?.episodes?.takeIf { it.isNotEmpty() }
                ?: cachedItem()?.media?.episodes.orEmpty()
        if (allEpisodes.none { it.id == chosenEpisodeId }) return null

        val order =
            EpisodeSort.parse(episodeSort)
                ?: run {
                    val saved =
                        EpisodeSort.parse(
                            preferencesRepository.getStringPreference("abs_episode_sort_$itemId")
                        ) ?: EpisodeSort.Default
                    val seriesType =
                        session.libraryItem?.media?.metadata?.type
                            ?: cachedItem()?.media?.metadata?.type
                    if (seriesType.equals("serial", ignoreCase = true)) saved.ascendingOrder()
                    else saved
                }

        val progressMap = audiobookshelfRepository.getEpisodeProgressMapFlow(itemId).first()
        val localTracks = audiobookshelfRepository.getDownloadedEpisodeTracks(itemId)
        val isOnline = networkConnectivityMonitor.isCurrentlyConnected()

        val entries =
            PodcastEpisodeOrder.sorted(allEpisodes, order).mapNotNull { episode ->
                val isChosen = episode.id == chosenEpisodeId
                if (!isChosen) {
                    if (PodcastEpisodeOrder.isTrailer(episode)) return@mapNotNull null
                    if (!includePlayed && progressMap[episode.id]?.isFinished == true) {
                        return@mapNotNull null
                    }
                }
                val candidateTrack =
                    if (isChosen) {
                        session.audioTracks?.firstOrNull()
                            ?: localTracks[episode.id]
                            ?: episode.audioTrack
                    } else {
                        localTracks[episode.id] ?: episode.audioTrack?.takeIf { isOnline }
                    }
                val track = candidateTrack ?: return@mapNotNull null
                val length = track.duration.takeIf { it > 0 } ?: episode.duration ?: 0.0
                episode to track.copy(title = episode.title, duration = length)
            }
        if (entries.none { it.first.id == chosenEpisodeId }) return null

        val startPositions =
            if (includePlayed) {
                emptyMap()
            } else {
                entries
                    .mapIndexedNotNull { index, (episode, _) ->
                        if (episode.id == chosenEpisodeId) return@mapIndexedNotNull null
                        progressMap[episode.id]
                            ?.takeIf { !it.isFinished && it.currentTime > 0 }
                            ?.let { index to it.currentTime }
                    }
                    .toMap()
            }

        return PodcastQueue(
            episodes = entries.map { it.first },
            tracks = entries.map { it.second },
            startPositions = startPositions,
        )
    }

    private fun buildChapterMediaItems(
        chapters: List<BookChapter>,
        audioTracks: List<AudioTrack>,
        baseUrl: String,
        token: String?,
        artUrl: String?,
        bookTitle: String,
        author: String,
    ): List<MediaItem>? {
        if (chapters.isEmpty() || audioTracks.isEmpty()) return null

        val trackOffsets = mutableListOf<Double>()
        var acc = 0.0
        for (track in audioTracks) {
            trackOffsets.add(acc)
            acc += track.duration
        }

        val result = mutableListOf<MediaItem>()
        for (chapter in chapters) {
            val trackIndex = trackOffsets.indexOfLast { offset -> chapter.start >= offset - 0.1 }
            if (trackIndex < 0) return null

            val track = audioTracks[trackIndex]
            val trackStart = trackOffsets[trackIndex]
            val trackEnd = trackStart + track.duration

            if (chapter.end > trackEnd + 0.5) return null

            val trackUrl =
                track.contentUrl?.let { url ->
                    if (url.startsWith("http") || url.startsWith("file")) url else "$baseUrl$url"
                } ?: return null

            result.add(
                MediaItem.Builder()
                    .setUri(trackUrl)
                    .setMediaId("chapter_${chapter.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(chapter.title)
                            .setArtist(bookTitle)
                            .setAlbumArtist(author)
                            .setArtworkUri(artUrl?.toUri())
                            .build()
                    )
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(
                                ((chapter.start - trackStart) * 1000).toLong().coerceAtLeast(0L)
                            )
                            .setEndPositionMs(((chapter.end - trackStart) * 1000).toLong())
                            .build()
                    )
                    .build()
            )
        }
        return result
    }

    fun play() {
        val controller = mediaController ?: return
        if (controller.playbackState == Player.STATE_IDLE || controller.playerError != null) {
            controller.prepare()
        }
        controller.play()
    }

    fun pause() = mediaController?.pause()

    fun isPlaying(): Boolean = mediaController?.isPlaying == true

    @OptIn(UnstableApi::class)
    fun getPlaybackStats(): PlaybackStats {
        val controller =
            mediaController ?: return PlaybackStats(playerType = "ABS Service (Initializing)")

        var audioFormat: Format? = null
        for (group in controller.currentTracks.groups) {
            if (group.type == TRACK_TYPE_AUDIO && group.isSelected) {
                audioFormat = group.mediaTrackGroup.getFormat(0)
                break
            }
        }

        val bufferSeconds =
            ((controller.bufferedPosition - controller.currentPosition) / 1000L).coerceAtLeast(0)
        val bitrateKbps = (audioFormat?.bitrate ?: 0) / 1000f

        val session = playbackManager.currentSession.value
        val isLocal = session?.id?.startsWith("local_") == true
        val playMethod =
            when {
                isLocal -> context.getString(R.string.playback_stats_value_direct_play_local)
                session?.playMethod == 2 ->
                    context.getString(R.string.playback_stats_value_transcoding)
                session?.playMethod == 0 ->
                    context.getString(R.string.playback_stats_value_direct_play)
                else -> context.getString(R.string.playback_stats_value_direct_streaming)
            }

        return PlaybackStats(
            playerType = "ExoPlayer (ABS Service)",
            playMethod = playMethod,
            videoResolution = "0x0",
            audioCodec = PlaybackStats.friendlyCodecName(audioFormat?.sampleMimeType),
            audioChannels = audioFormat?.channelCount ?: 0,
            audioSampleRate = audioFormat?.sampleRate ?: 0,
            bufferHealth =
                context.resources.getQuantityString(
                    R.plurals.playback_stats_value_seconds_fmt,
                    bufferSeconds.toInt(),
                    bufferSeconds,
                ),
            audioBitrate =
                if (bitrateKbps > 0) String.format(Locale.US, "%.0f kbps", bitrateKbps) else "",
            hwDec = playbackManager.currentAudioDecoder.value,
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        playbackManager.updatePlaybackSpeed(speed)
    }

    fun seekToPosition(positionSeconds: Double) {
        val controller = mediaController ?: return
        val state = playbackManager.playbackState.value

        if (state.isChapterBasedPlayback) {
            val chapters = state.chapters
            val idx =
                chapters
                    .indexOfFirst { ch -> positionSeconds >= ch.start && positionSeconds < ch.end }
                    .let { if (it < 0) chapters.lastIndex else it }
            if (idx >= 0) {
                val ch = chapters[idx]
                controller.seekTo(
                    idx,
                    ((positionSeconds - ch.start) * 1000).toLong().coerceAtLeast(0L),
                )
            }
            return
        }

        val audioTracks = playbackManager.currentSession.value?.audioTracks ?: return
        var accumulatedDuration = 0.0
        for ((index, track) in audioTracks.withIndex()) {
            if (positionSeconds < accumulatedDuration + track.duration) {
                controller.seekTo(index, ((positionSeconds - accumulatedDuration) * 1000).toLong())
                break
            }
            accumulatedDuration += track.duration
        }
    }

    fun seekToChapter(chapterIndex: Int) {
        val controller = mediaController ?: return
        val state = playbackManager.playbackState.value
        if (chapterIndex !in state.chapters.indices) return
        if (state.isChapterBasedPlayback) {
            controller.seekTo(chapterIndex, 0)
        } else {
            seekToPosition(state.chapters[chapterIndex].start)
        }
    }

    fun skipForward(seconds: Int = 30) {
        val current = playbackManager.playbackState.value.currentTime
        seekToPosition(current + seconds)
    }

    fun skipBackward(seconds: Int = 30) {
        val current = playbackManager.playbackState.value.currentTime
        seekToPosition((current - seconds).coerceAtLeast(0.0))
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
            Timber.d("Sleep timer triggered")
        }
    }

    fun setChapterSleepTimer(extraChapters: Int = 0) {
        cancelSleepTimer()
        val state = playbackManager.playbackState.value
        val chapters = state.chapters

        val targetSeconds: Double
        val chapterIndex: Int?

        if (chapters.isEmpty()) {
            if (state.duration <= 0.0) return
            targetSeconds = state.duration
            chapterIndex = null
        } else {
            val currentIndex = state.currentChapterIndex.takeIf { it >= 0 } ?: 0
            val index = (currentIndex + extraChapters).coerceIn(chapters.indices)
            targetSeconds = chapters[index].end
            chapterIndex = index
        }

        if (targetSeconds <= state.currentTime) return

        playbackManager.setSleepTimerTarget(targetSeconds, chapterIndex)

        sleepTimerJob = scope.launch {
            while (isActive) {
                val current = playbackManager.playbackState.value
                val remainingSeconds = targetSeconds - current.currentTime
                if (remainingSeconds <= 0.0) break

                val speed = current.playbackSpeed.coerceAtLeast(0.1f)
                val remainingMs = (remainingSeconds / speed * 1000).toLong()
                delay(if (current.isPlaying) remainingMs.coerceAtMost(1_000L) else 1_000L)
            }
            pause()
            playbackManager.setSleepTimerTarget(null, null)
            Timber.d("Chapter sleep timer triggered at ${targetSeconds}s")
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        playbackManager.setSleepTimer(null)
    }

    @OptIn(UnstableApi::class)
    fun closeSession(stopService: Boolean = true) {
        cancelSleepTimer()
        val state = playbackManager.playbackState.value
        val sessionId = state.sessionId
        val listened = if (sessionId != null) progressSyncer.takeListenedSeconds() else 0.0
        Timber.d(
            "closeSession: sessionId=$sessionId itemId=${state.itemId} episodeId=${state.episodeId} currentTime=${state.currentTime} listened=$listened isLocal=${
                sessionId?.startsWith(
                    "local_"
                )
            }"
        )
        if (sessionId != null) {
            scope.launch {
                withContext(NonCancellable) {
                    try {
                        val currentTime: Double
                        val duration: Double

                        val playlistChapter =
                            if (state.isPodcastPlaylist) {
                                state.currentChapter
                                    ?: state.chapters.lastOrNull()?.takeIf {
                                        state.currentTime >= it.end
                                    }
                            } else {
                                null
                            }

                        if (playlistChapter != null) {
                            currentTime =
                                (state.currentTime - playlistChapter.start).coerceAtLeast(0.0)
                            duration =
                                (playlistChapter.end - playlistChapter.start).coerceAtLeast(0.0)
                        } else {
                            currentTime = state.currentTime
                            duration = state.duration
                        }

                        if (sessionId.startsWith("local_")) {
                            val itemId = state.itemId
                            val episodeId = state.episodeId
                            val activeContext = audiobookshelfRepository.currentActiveContext
                            Timber.d(
                                "closeSession[local]: saving final position itemId=$itemId episodeId=$episodeId currentTime=$currentTime duration=$duration"
                            )
                            if (itemId != null && activeContext != null) {
                                val (serverId, userId) = activeContext
                                audiobookshelfRepository.updateProgress(
                                    itemId = itemId,
                                    episodeId = episodeId,
                                    currentTime = currentTime,
                                    duration = duration,
                                    isFinished = duration > 0 && currentTime / duration >= 0.99,
                                    timeListened = listened,
                                )
                                absSyncScheduler.scheduleSync(serverId, userId)
                                Timber.d(
                                    "closeSession[local]: final position saved and sync scheduled"
                                )
                            } else {
                                Timber.w(
                                    "closeSession[local]: itemId or activeContext is null, skipping final position save"
                                )
                            }
                            return@withContext
                        }

                        val result =
                            audiobookshelfRepository.closePlaybackSession(
                                sessionId = sessionId,
                                currentTime = currentTime,
                                timeListened = listened,
                                duration = duration,
                            )
                        if (result.isSuccess) {
                            Timber.d("Session closed on server: $sessionId")
                        } else {
                            Timber.e(
                                "Failed to close session on server: ${result.exceptionOrNull()?.message}"
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Error closing session on server")
                    }
                }
            }
        }

        controllerFuture?.let { future ->
            if (mediaController != null) {
                MediaController.releaseFuture(future)
            } else {
                future.cancel(false)
            }
        }
        mediaController = null
        controllerFuture = null

        val musicTrackAtClose = musicPlaybackManager.state.value.currentTrack
        Timber.d(
            "ABS closeSession: calling clearSession — musicTrack at this point=${musicTrackAtClose?.name}"
        )
        playbackManager.clearSession()
        Timber.d(
            "ABS closeSession: DONE — sessionId now=${playbackManager.playbackState.value.sessionId} musicTrack=${musicPlaybackManager.state.value.currentTrack?.name}"
        )
        if (sessionId != null && stopService) {
            context.startService(
                Intent(context, com.makd.afinity.player.AudioService::class.java)
                    .setAction(com.makd.afinity.player.AudioService.ACTION_STOP)
            )
        }
    }

    fun release() {
        closeSession()
    }

    fun releaseForEngineSwitch() {
        closeSession(stopService = false)
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(Futures.getDone(this@await))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isCancelled) {
                        continuation.cancel(e)
                    } else {
                        continuation.resumeWithException(e)
                    }
                }
            },
            MoreExecutors.directExecutor(),
        )

        continuation.invokeOnCancellation { cancel(false) }
    }
}
