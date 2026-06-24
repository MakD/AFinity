package com.makd.afinity.cast

import android.content.Context
import androidx.core.net.toUri
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastManager
@Inject
constructor(
    private val playbackRepository: PlaybackRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val castDeviceProfileFactory: CastDeviceProfileFactory,
    private val securePreferencesRepository: SecurePreferencesRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _castState = MutableStateFlow(CastSessionState())
    val castState: StateFlow<CastSessionState> = _castState.asStateFlow()

    private val _castEvents = MutableSharedFlow<CastEvent>(extraBufferCapacity = 8)
    val castEvents = _castEvents.asSharedFlow()

    val isCasting: Boolean
        get() = _castState.value.isConnected

    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var remoteMediaClient: RemoteMediaClient? = null

    private var progressReportingJob: Job? = null
    private var positionPollingJob: Job? = null
    private var volumeDebounceJob: Job? = null

    private var currentServerBaseUrl: String? = null
    private var currentEnableHevc: Boolean = false
    private var absAudioTracks: List<AudioTrack> = emptyList()
    private var absTrackStartOffsets: Map<Int, Long> = emptyMap()

    fun initialize(context: Context) {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext
                ?.sessionManager
                ?.addSessionManagerListener(castSessionManagerListener, CastSession::class.java)
            MediaRouter.getInstance(context).routerParams =
                MediaRouterParams.Builder()
                    .setDialogType(MediaRouterParams.DIALOG_TYPE_DYNAMIC_GROUP)
                    .setOutputSwitcherEnabled(true)
                    .build()
            Timber.d("CastManager initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize CastManager")
        }
    }

    fun loadMedia(
        item: AfinityItem,
        serverBaseUrl: String,
        mediaSourceId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startPositionMs: Long = 0L,
        maxBitrate: Int = 16_000_000,
        enableHevc: Boolean = false,
    ) {
        scope.launch {
            try {
                val client = remoteMediaClient
                if (client == null) {
                    Timber.e("RemoteMediaClient is null, cannot load media")
                    _castEvents.emit(CastEvent.PlaybackError("Not connected to a cast device"))
                    return@launch
                }

                currentServerBaseUrl = serverBaseUrl
                currentEnableHevc = enableHevc

                val deviceProfile = castDeviceProfileFactory.createProfile(enableHevc, maxBitrate)

                val playbackInfo =
                    withContext(Dispatchers.IO) {
                        playbackRepository.getPlaybackInfoForCast(
                            itemId = item.id,
                            deviceProfile = deviceProfile,
                            maxStreamingBitrate = maxBitrate,
                            maxAudioChannels = 6,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = subtitleStreamIndex,
                            mediaSourceId = mediaSourceId,
                            startTimeTicks = startPositionMs * 10_000L,
                        )
                    }

                if (playbackInfo == null) {
                    Timber.e("Failed to get playback info for cast")
                    _castEvents.emit(CastEvent.PlaybackError("Failed to get playback info"))
                    return@launch
                }

                val mediaSource = playbackInfo.mediaSources.firstOrNull()
                if (mediaSource == null) {
                    Timber.e("No media source available for cast")
                    _castEvents.emit(CastEvent.PlaybackError("No media source available"))
                    return@launch
                }

                val streamUrl =
                    if (mediaSource.transcodingUrl != null) {
                        serverBaseUrl.trimEnd('/') + mediaSource.transcodingUrl
                    } else {
                        withContext(Dispatchers.IO) {
                            playbackRepository.getStreamUrl(
                                itemId = item.id,
                                mediaSourceId = mediaSource.id ?: mediaSourceId,
                                audioStreamIndex = audioStreamIndex,
                                subtitleStreamIndex = subtitleStreamIndex,
                            )
                        }
                            ?: run {
                                _castEvents.emit(
                                    CastEvent.PlaybackError("Failed to get stream URL")
                                )
                                return@launch
                            }
                    }

                val isTranscoding = mediaSource.transcodingUrl != null
                val playMethod = if (isTranscoding) "Transcode" else "DirectPlay"
                val playSessionId = playbackInfo.playSessionId ?: ""

                val contentType =
                    when {
                        isTranscoding && streamUrl.contains(".m3u8") -> "application/x-mpegURL"
                        isTranscoding -> "video/mp2t"
                        else ->
                            when (mediaSource.container?.lowercase()) {
                                "mkv",
                                "matroska" -> "video/x-matroska"
                                "webm" -> "video/webm"
                                "ts" -> "video/mp2t"
                                else -> "video/mp4"
                            }
                    }

                Timber.d(
                    "Cast stream URL: $streamUrl (method: $playMethod, contentType: $contentType)"
                )

                val apiToken =
                    withContext(Dispatchers.IO) { securePreferencesRepository.getAccessToken() }
                        ?: ""
                val textSubtitleCodecs = setOf("srt", "subrip", "vtt", "webvtt")
                val textSubtitleStreams =
                    mediaSource.mediaStreams
                        ?.filter { stream ->
                            stream.type == MediaStreamType.SUBTITLE &&
                                stream.codec?.lowercase() in textSubtitleCodecs
                        }
                        .orEmpty()
                val mediaTracks = textSubtitleStreams.map { stream ->
                    val subUrl =
                        "${serverBaseUrl.trimEnd('/')}/Videos/${item.id}/" +
                            "${mediaSource.id ?: mediaSourceId}/Subtitles/${stream.index}/Stream.vtt" +
                            "?api_key=$apiToken"
                    MediaTrack.Builder(stream.index.toLong(), MediaTrack.TYPE_TEXT)
                        .setName(stream.displayTitle ?: stream.language ?: "Subtitle")
                        .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                        .setContentId(subUrl)
                        .setContentType("text/vtt")
                        .setLanguage(stream.language ?: "und")
                        .build()
                }
                val activeTrackIds: LongArray? =
                    if (
                        subtitleStreamIndex != null &&
                            textSubtitleStreams.any { it.index == subtitleStreamIndex }
                    ) {
                        longArrayOf(subtitleStreamIndex.toLong())
                    } else {
                        null
                    }

                val artworkUrl = "${serverBaseUrl.trimEnd('/')}/Items/${item.id}/Images/Primary"
                val metadata =
                    MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                        putString(MediaMetadata.KEY_TITLE, item.name)
                        addImage(WebImage(artworkUrl.toUri()))
                    }

                val mediaInfo =
                    MediaInfo.Builder(streamUrl)
                        .setContentType(contentType)
                        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                        .setMetadata(metadata)
                        .setMediaTracks(mediaTracks.ifEmpty { null })
                        .build()

                val loadRequest =
                    MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .setAutoplay(true)
                        .setCurrentTime(startPositionMs / 1000)
                        .setActiveTrackIds(activeTrackIds)
                        .build()

                Timber.d(
                    "Cast loadMedia: startPositionMs=$startPositionMs, setCurrentTime=${startPositionMs / 1000.0}s"
                )
                val loadTask = client.load(loadRequest)
                if (startPositionMs > 0) {
                    loadTask.setResultCallback { result ->
                        if (result.status.isSuccess) {
                            Timber.d("Cast post-load seek to ${startPositionMs}ms")
                            client.seek(
                                MediaSeekOptions.Builder().setPosition(startPositionMs).build()
                            )
                        }
                    }
                }

                _castState.value =
                    _castState.value.copy(
                        currentItem = item,
                        currentItemId = item.id,
                        mediaSourceId = mediaSource.id ?: mediaSourceId,
                        sessionId = playSessionId,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        castBitrate = maxBitrate,
                        duration = item.runtimeTicks / 10000,
                        playMethod = playMethod,
                        serverBaseUrl = serverBaseUrl,
                        enableHevc = enableHevc,
                        loadedTextTrackIndices = textSubtitleStreams.map { it.index }.toSet(),
                    )

                withContext(Dispatchers.IO) {
                    playbackRepository.reportPlaybackStart(
                        itemId = item.id,
                        sessionId = playSessionId,
                        mediaSourceId = mediaSource.id ?: mediaSourceId,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        playMethod = playMethod,
                    )
                }

                playbackStateManager.trackPlaybackSession(
                    sessionId = playSessionId,
                    itemId = item.id,
                    mediaSourceId = mediaSource.id ?: mediaSourceId,
                )

                startProgressReporting()
                startPositionPolling()

                _castEvents.emit(CastEvent.PlaybackStarted(item.id))
                Timber.d("Cast media loaded: ${item.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load media on cast device")
                _castEvents.emit(CastEvent.PlaybackError("Failed to load media: ${e.message}"))
            }
        }
    }

    fun loadMusicQueue(
        tracks: List<AfinityTrack>,
        startIndex: Int,
        startPositionMs: Long,
        serverBaseUrl: String,
        token: String,
    ) {
        scope.launch {
            try {
                val client =
                    remoteMediaClient
                        ?: run {
                            _castEvents.emit(
                                CastEvent.PlaybackError("Not connected to a cast device")
                            )
                            return@launch
                        }

                val base = serverBaseUrl.trimEnd('/')

                val queueItems = tracks.map { track ->
                    val streamUrl = "$base/Audio/${track.id}/stream?static=true&api_key=$token"
                    val durationMs = track.runtimeTicks / 10_000

                    val metadata =
                        MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                            putString(MediaMetadata.KEY_TITLE, track.name)
                            track.artist?.let { putString(MediaMetadata.KEY_ARTIST, it) }
                            track.album?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it) }
                            track.images.primary?.let { addImage(WebImage(it)) }
                        }

                    val mediaInfo =
                        MediaInfo.Builder(streamUrl)
                            .setContentType("audio/mpeg")
                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                            .setStreamDuration(durationMs)
                            .setMetadata(metadata)
                            .build()

                    MediaQueueItem.Builder(mediaInfo).build()
                }

                val queueData =
                    MediaQueueData.Builder()
                        .setItems(queueItems)
                        .setStartIndex(startIndex)
                        .setStartTime(startPositionMs / 1000)
                        .build()

                val loadRequest =
                    MediaLoadRequestData.Builder().setQueueData(queueData).setAutoplay(true).build()

                client.load(loadRequest)
                _castState.update { it.copy(isMusicCasting = true) }
                startPositionPolling()

                Timber.d(
                    "Music queue loaded to Cast: ${tracks.size} tracks, starting at $startIndex"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load music queue on cast device")
                _castEvents.emit(CastEvent.PlaybackError("Failed to load music: ${e.message}"))
            }
        }
    }

    fun skipMusicTrack(forward: Boolean) {
        scope.launch {
            try {
                if (forward) remoteMediaClient?.queueNext(null)
                else remoteMediaClient?.queuePrev(null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to skip music track on cast device")
            }
        }
    }

    fun loadAbsSession(
        tracks: List<AudioTrack>,
        startGlobalTimeSeconds: Double,
        displayTitle: String,
        author: String?,
        coverUrl: String?,
        baseUrl: String,
        token: String,
    ) {
        scope.launch {
            try {
                val client =
                    remoteMediaClient
                        ?: run {
                            _castEvents.emit(
                                CastEvent.PlaybackError("Not connected to a cast device")
                            )
                            return@launch
                        }

                val startTrackIndex =
                    tracks.indexOfLast { it.startOffset <= startGlobalTimeSeconds }.coerceAtLeast(0)
                val startPositionInTrackMs =
                    ((startGlobalTimeSeconds - tracks[startTrackIndex].startOffset) * 1000).toLong()

                absAudioTracks = tracks
                absTrackStartOffsets = emptyMap()

                val queueItems = tracks.map { track ->
                    val streamUrl = buildAbsStreamUrl(track.contentUrl ?: "", baseUrl, token)
                    val durationMs = (track.duration * 1000).toLong()
                    val contentType = track.mimeType ?: "audio/mpeg"

                    val metadata =
                        MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                            putString(MediaMetadata.KEY_TITLE, displayTitle)
                            author?.let { putString(MediaMetadata.KEY_ARTIST, it) }
                            coverUrl?.let { addImage(WebImage(it.toUri())) }
                        }

                    val mediaInfo =
                        MediaInfo.Builder(streamUrl)
                            .setContentType(contentType)
                            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                            .setStreamDuration(durationMs)
                            .setMetadata(metadata)
                            .build()

                    MediaQueueItem.Builder(mediaInfo).build()
                }

                val queueData =
                    MediaQueueData.Builder()
                        .setItems(queueItems)
                        .setStartIndex(startTrackIndex)
                        .setStartTime(startPositionInTrackMs / 1000)
                        .build()

                val loadRequest =
                    MediaLoadRequestData.Builder().setQueueData(queueData).setAutoplay(true).build()

                client.load(loadRequest)
                _castState.update { it.copy(isAbsCasting = true) }
                startPositionPolling()

                Timber.d(
                    "ABS loaded to Cast: ${tracks.size} tracks, starting at index $startTrackIndex, pos ${startPositionInTrackMs}ms"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load ABS session on cast device")
                _castEvents.emit(CastEvent.PlaybackError("Failed to load audiobook: ${e.message}"))
            }
        }
    }

    fun seekAbsGlobalPosition(globalPositionMs: Long) {
        scope.launch {
            try {
                val client = remoteMediaClient ?: return@launch
                val sorted = absTrackStartOffsets.entries.sortedBy { it.value }
                val targetEntry = sorted.lastOrNull { it.value <= globalPositionMs }
                if (targetEntry != null) {
                    val positionWithinTrackMs = globalPositionMs - targetEntry.value
                    val currentItemId = client.mediaStatus?.currentItemId
                    if (currentItemId == targetEntry.key) {
                        client.seek(
                            MediaSeekOptions.Builder().setPosition(positionWithinTrackMs).build()
                        )
                    } else {
                        client.queueJumpToItem(targetEntry.key, positionWithinTrackMs, null)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to seek ABS position on cast device")
            }
        }
    }

    private fun buildAbsStreamUrl(contentUrl: String, baseUrl: String, token: String): String {
        val url =
            if (contentUrl.startsWith("http") || contentUrl.startsWith("file")) contentUrl
            else "$baseUrl$contentUrl"
        return if (token.isNotEmpty()) "$url?token=$token" else url
    }

    fun play() {
        remoteMediaClient?.play()
    }

    fun pause() {
        remoteMediaClient?.pause()
    }

    fun seekTo(positionMs: Long) {
        remoteMediaClient?.seek(MediaSeekOptions.Builder().setPosition(positionMs).build())
    }

    fun stop() {
        scope.launch {
            try {
                reportFinalPosition()
                remoteMediaClient?.stop()
                stopProgressReporting()
                stopPositionPolling()
                resetPlaybackState()
            } catch (e: Exception) {
                Timber.e(e, "Error stopping cast playback")
            }
        }
    }

    fun setVolume(volume: Double) {
        volumeDebounceJob?.cancel()
        volumeDebounceJob = scope.launch {
            delay(300)
            try {
                castSession?.volume = volume.coerceIn(0.0, 1.0)
                _castState.update { it.copy(volume = volume.coerceIn(0.0, 1.0)) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set cast volume")
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        remoteMediaClient?.setPlaybackRate(speed.toDouble())
        _castState.update { it.copy(playbackSpeed = speed) }
    }

    fun switchAudioTrack(
        audioStreamIndex: Int,
        item: AfinityItem,
        serverBaseUrl: String,
        mediaSourceId: String,
        subtitleStreamIndex: Int?,
        maxBitrate: Int,
        enableHevc: Boolean,
    ) {
        scope.launch {
            val currentPosition = _castState.value.currentPosition
            remoteMediaClient?.stop()
            stopProgressReporting()
            stopPositionPolling()
            loadMedia(
                item = item,
                serverBaseUrl = serverBaseUrl,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startPositionMs = currentPosition,
                maxBitrate = maxBitrate,
                enableHevc = enableHevc,
            )
        }
    }

    fun switchSubtitleTrack(
        subtitleStreamIndex: Int?,
        item: AfinityItem,
        serverBaseUrl: String,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        maxBitrate: Int,
        enableHevc: Boolean,
    ) {
        scope.launch {
            val state = _castState.value
            val loadedIndices = state.loadedTextTrackIndices

            val currentIsClean =
                state.subtitleStreamIndex == null || state.subtitleStreamIndex in loadedIndices
            val targetIsLoaded = subtitleStreamIndex == null || subtitleStreamIndex in loadedIndices

            if (currentIsClean && targetIsLoaded) {
                val trackIds =
                    if (subtitleStreamIndex != null) {
                        longArrayOf(subtitleStreamIndex.toLong())
                    } else {
                        longArrayOf()
                    }
                remoteMediaClient?.setActiveMediaTracks(trackIds)?.setResultCallback { result ->
                    if (result.status.isSuccess) {
                        _castState.value =
                            _castState.value.copy(subtitleStreamIndex = subtitleStreamIndex)
                        Timber.d("Cast subtitle switched natively to index $subtitleStreamIndex")
                    } else {
                        Timber.w("Native subtitle switch failed (${result.status.statusCode})")
                    }
                }
            } else {
                val currentPosition = state.currentPosition
                remoteMediaClient?.stop()
                stopProgressReporting()
                stopPositionPolling()
                loadMedia(
                    item = item,
                    serverBaseUrl = serverBaseUrl,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    startPositionMs = currentPosition,
                    maxBitrate = maxBitrate,
                    enableHevc = enableHevc,
                )
            }
        }
    }

    fun changeBitrate(
        bitrate: Int,
        item: AfinityItem,
        serverBaseUrl: String,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        enableHevc: Boolean,
    ) {
        scope.launch {
            val currentPosition = _castState.value.currentPosition
            remoteMediaClient?.stop()
            stopProgressReporting()
            stopPositionPolling()
            loadMedia(
                item = item,
                serverBaseUrl = serverBaseUrl,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startPositionMs = currentPosition,
                maxBitrate = bitrate,
                enableHevc = enableHevc,
            )
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                castContext?.sessionManager?.endCurrentSession(true)
            } catch (e: Exception) {
                Timber.e(e, "Error disconnecting cast session")
            }
        }
    }

    fun release() {
        stopProgressReporting()
        stopPositionPolling()
        volumeDebounceJob?.cancel()
        castContext
            ?.sessionManager
            ?.removeSessionManagerListener(castSessionManagerListener, CastSession::class.java)
        castContext = null
        scope.cancel()
        Timber.d("CastManager released")
    }

    private fun startProgressReporting() {
        stopProgressReporting()
        progressReportingJob = scope.launch {
            while (true) {
                delay(10_000)
                reportProgress()
            }
        }
    }

    private fun stopProgressReporting() {
        progressReportingJob?.cancel()
        progressReportingJob = null
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionPollingJob = scope.launch {
            while (true) {
                delay(1_000)
                updatePositionFromRemote()
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    private fun updatePositionFromRemote() {
        try {
            val client = remoteMediaClient ?: return
            val position = client.approximateStreamPosition
            if (position >= 0) {
                if (_castState.value.isAbsCasting && absTrackStartOffsets.isNotEmpty()) {
                    val currentItemId = client.mediaStatus?.currentItemId
                    val trackStartOffsetMs = absTrackStartOffsets[currentItemId] ?: 0L
                    val globalPositionMs = trackStartOffsetMs + position
                    _castState.update { it.copy(currentPosition = globalPositionMs) }
                    playbackStateManager.updatePlaybackPosition(globalPositionMs)
                } else {
                    _castState.update { it.copy(currentPosition = position) }
                    playbackStateManager.updatePlaybackPosition(position)
                }
            }

            val mediaStatus = client.mediaStatus
            if (mediaStatus != null) {
                val isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING
                val isPaused = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PAUSED
                val isBuffering = mediaStatus.playerState == MediaStatus.PLAYER_STATE_BUFFERING
                val duration = mediaStatus.mediaInfo?.streamDuration ?: 0L

                _castState.value =
                    _castState.value.copy(
                        isPlaying = isPlaying,
                        isPaused = isPaused,
                        isBuffering = isBuffering,
                        duration = if (duration > 0) duration else _castState.value.duration,
                    )
            }

            val session = castSession
            if (session != null) {
                _castState.value =
                    _castState.value.copy(volume = session.volume, isMuted = session.isMute)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error polling cast position")
        }
    }

    private suspend fun reportProgress() {
        val state = _castState.value
        val itemId = state.currentItemId ?: return
        val sessionId = state.sessionId ?: return

        withContext(Dispatchers.IO) {
            playbackRepository.reportPlaybackProgress(
                itemId = itemId,
                sessionId = sessionId,
                positionTicks = state.currentPosition * 10000,
                isPaused = state.isPaused,
                isMuted = state.isMuted,
                audioStreamIndex = state.audioStreamIndex,
                subtitleStreamIndex = state.subtitleStreamIndex,
                playMethod = state.playMethod,
            )
        }
    }

    private suspend fun reportFinalPosition() {
        val state = _castState.value
        val itemId = state.currentItemId ?: return
        val sessionId = state.sessionId ?: return
        val mediaSourceId = state.mediaSourceId ?: return

        withContext(Dispatchers.IO) {
            playbackRepository.reportPlaybackStop(
                itemId = itemId,
                sessionId = sessionId,
                positionTicks = state.currentPosition * 10000,
                mediaSourceId = mediaSourceId,
            )
        }

        playbackStateManager.notifyPlaybackStopped(itemId, state.currentPosition)
    }

    private fun resetPlaybackState() {
        _castState.value =
            _castState.value.copy(
                isPlaying = false,
                isPaused = false,
                isBuffering = false,
                currentPosition = 0L,
                duration = 0L,
                currentItem = null,
                currentItemId = null,
                mediaSourceId = null,
                sessionId = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                isMusicCasting = false,
                isAbsCasting = false,
            )
        absAudioTracks = emptyList()
        absTrackStartOffsets = emptyMap()
    }

    private val castSessionManagerListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                Timber.d("Cast session starting")
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                val deviceName = session.castDevice?.friendlyName
                val volume = session.volume
                val isMuted = session.isMute
                Timber.d("Cast session started: $deviceName")
                castSession = session
                remoteMediaClient = session.remoteMediaClient
                remoteMediaClient?.registerCallback(remoteMediaClientCallback)

                _castState.value =
                    _castState.value.copy(
                        isConnected = true,
                        deviceName = deviceName,
                        volume = volume,
                        isMuted = isMuted,
                    )

                scope.launch {
                    _castEvents.emit(CastEvent.Connected(deviceName ?: "Unknown Device"))
                }
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                Timber.e("Cast session start failed with error: $error")
                scope.launch {
                    _castEvents.emit(CastEvent.PlaybackError("Failed to connect to cast device"))
                }
            }

            override fun onSessionEnding(session: CastSession) {
                Timber.d("Cast session ending")
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                Timber.d("Cast session ended (error: $error)")
                val finalState = _castState.value
                val wasMusicCasting = finalState.isMusicCasting
                val wasAbsCasting = finalState.isAbsCasting
                remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
                remoteMediaClient = null
                castSession = null
                _castState.value = CastSessionState()

                scope.launch {
                    stopProgressReporting()
                    stopPositionPolling()
                    val itemId = finalState.currentItemId
                    val sessionId = finalState.sessionId
                    val mediaSourceId = finalState.mediaSourceId
                    if (itemId != null && sessionId != null && mediaSourceId != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                playbackRepository.reportPlaybackStop(
                                    itemId = itemId,
                                    sessionId = sessionId,
                                    positionTicks = finalState.currentPosition * 10000,
                                    mediaSourceId = mediaSourceId,
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to report cast playback stop")
                            }
                        }
                        playbackStateManager.notifyPlaybackStopped(
                            itemId,
                            finalState.currentPosition,
                        )
                    }
                    if (wasMusicCasting) {
                        _castEvents.emit(
                            CastEvent.MusicCastDisconnected(
                                lastPositionMs = finalState.currentPosition
                            )
                        )
                    } else if (wasAbsCasting) {
                        _castEvents.emit(
                            CastEvent.AbsCastDisconnected(
                                lastPositionSeconds = finalState.currentPosition / 1000.0
                            )
                        )
                    } else {
                        _castEvents.emit(
                            CastEvent.Disconnected(lastPositionMs = finalState.currentPosition)
                        )
                    }
                    playbackStateManager.clearSession()
                }
            }

            override fun onSessionResuming(session: CastSession, sessionId: String) {
                Timber.d("Cast session resuming")
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                Timber.d("Cast session resumed")
                castSession = session
                remoteMediaClient = session.remoteMediaClient
                remoteMediaClient?.registerCallback(remoteMediaClientCallback)

                _castState.value =
                    _castState.value.copy(
                        isConnected = true,
                        deviceName = session.castDevice?.friendlyName,
                    )
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                Timber.e("Cast session resume failed: $error")
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) {
                Timber.d("Cast session suspended: $reason")
            }
        }

    private val remoteMediaClientCallback =
        object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                if (_castState.value.isAbsCasting && absTrackStartOffsets.isEmpty()) {
                    val items = remoteMediaClient?.mediaStatus?.queueItems
                    if (!items.isNullOrEmpty()) {
                        absTrackStartOffsets =
                            items
                                .mapIndexed { idx, item ->
                                    item.itemId to
                                        ((absAudioTracks.getOrNull(idx)?.startOffset ?: 0.0) * 1000)
                                            .toLong()
                                }
                                .toMap()
                    }
                }
                updatePositionFromRemote()
            }

            override fun onMetadataUpdated() {
                Timber.d("Cast metadata updated")
            }
        }
}
