package com.makd.afinity.data.repository.player

import android.content.Context
import android.content.res.AssetManager
import android.view.SurfaceHolder
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.player.PlayerError
import com.makd.afinity.data.models.player.PlayerState
import com.makd.afinity.data.repository.playback.PlaybackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.model.api.MediaSourceInfo
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaStream
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class LibMpvPlayerRepository constructor(
    @ApplicationContext private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val apiClient: org.jellyfin.sdk.api.client.ApiClient
) : PlayerRepository, MPVLib.EventObserver {

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackReportingJob: Job? = null

    private var mpvInitialized = false

    private var currentTimePos: Double = 0.0
    private var currentDuration: Double = 0.0
    private var isPaused: Boolean = false
    private var isSeeking: Boolean = false
    private var isIdle: Boolean = true

    private var pendingSeekPosition: Double = 0.0

    private var currentMediaSource: MediaSourceInfo? = null

    private var subtitleTrackMapping = mutableMapOf<Int, Int>()

    private var onPlaybackCompleted: ((AfinityItem) -> Unit)? = null
    private var isNaturalEnd = false


    private fun initializeMpv() {
        try {

            val mpvDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "mpv")
            Timber.i("mpv config dir: $mpvDir")
            if (!mpvDir.exists()) mpvDir.mkdirs()

            arrayOf("mpv.conf", "subfont.ttf").forEach { fileName ->
                val file = File(mpvDir, fileName)
                if (file.exists()) return@forEach
                context.assets.open(fileName, AssetManager.ACCESS_STREAMING)
                    .copyTo(FileOutputStream(file))
            }

            MPVLib.create(context)

            MPVLib.setOptionString("config", "yes")
            MPVLib.setOptionString("config-dir", mpvDir.path)
            MPVLib.setOptionString("profile", "fast")
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("ao", "audiotrack")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("opengl-es", "yes")
            MPVLib.setOptionString("vid", "no")

            MPVLib.setOptionString("hwdec", "mediacodec")
            MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")

            MPVLib.setOptionString("tls-verify", "no")

            MPVLib.setOptionString("cache", "yes")
            MPVLib.setOptionString("cache-pause-initial", "yes")
            MPVLib.setOptionString("demuxer-max-bytes", "32MiB")
            MPVLib.setOptionString("demuxer-max-back-bytes", "32MiB")

            MPVLib.setOptionString("sub-scale-with-window", "yes")
            MPVLib.setOptionString("sub-use-margins", "no")

            MPVLib.setOptionString("force-window", "no")
            MPVLib.setOptionString("keep-open", "always")
            MPVLib.setOptionString("save-position-on-quit", "no")
            MPVLib.setOptionString("sub-font-provider", "none")
            MPVLib.setOptionString("ytdl", "no")

            MPVLib.init()
            mpvInitialized = true

            MPVLib.setOptionString("sub-auto", "exact")
            MPVLib.setOptionString("sub-visibility", "yes")

            MPVLib.addObserver(this)

            arrayOf(
                "track-list" to MPVLib.MPV_FORMAT_STRING,
                "paused-for-cache" to MPVLib.MPV_FORMAT_FLAG,
                "eof-reached" to MPVLib.MPV_FORMAT_FLAG,
                "seekable" to MPVLib.MPV_FORMAT_FLAG,
                "time-pos" to MPVLib.MPV_FORMAT_DOUBLE,
                "duration" to MPVLib.MPV_FORMAT_DOUBLE,
                "demuxer-cache-time" to MPVLib.MPV_FORMAT_DOUBLE,
                "speed" to MPVLib.MPV_FORMAT_DOUBLE,
                "playlist-count" to MPVLib.MPV_FORMAT_INT64,
                "playlist-current-pos" to MPVLib.MPV_FORMAT_INT64,
                "pause" to MPVLib.MPV_FORMAT_FLAG,
                "core-idle" to MPVLib.MPV_FORMAT_FLAG,
                "seeking" to MPVLib.MPV_FORMAT_FLAG,
            ).forEach { (name, format) ->
                MPVLib.observeProperty(name, format)
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MPV")
            updateState {
                it.copy(error = PlayerError(
                    code = -1,
                    message = "Failed to initialize player: ${e.message}",
                    cause = e
                ))
            }
        }
    }

    override fun eventProperty(property: String) {
    }

    override fun eventProperty(property: String, value: Long) {
    }

    override fun eventProperty(property: String, value: Double) {
        scope.launch {
            when (property) {
                "time-pos" -> {
                    currentTimePos = value
                    updatePlayerState()
                }
                "duration" -> {
                    currentDuration = value
                    updatePlayerState()
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        scope.launch {
            when (property) {
                "pause" -> {
                    isPaused = value
                    updatePlayerState()
                }
                "core-idle" -> {
                    isIdle = value
                    updatePlayerState()
                }
                "seeking" -> {
                    isSeeking = value
                    updatePlayerState()
                }
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
    }

    override fun event(eventId: Int) {
        Timber.d("MPV Event received: $eventId (${getEventName(eventId)})")
        scope.launch {
            when (eventId) {
                MPVLib.MPV_EVENT_START_FILE -> {
                    Timber.d("MPV: File started loading")
                    isNaturalEnd = false
                    updateState { it.copy(isLoading = true) }
                    withContext(Dispatchers.Main) {
                        for (command in externalSubtitleCommands) {
                            try {
                                MPVLib.command(command)
                                Timber.d("Executed subtitle command: ${command.joinToString(" ")}")

                                delay(100)
                                try {
                                    val trackList = MPVLib.getPropertyString("track-list")
                                    val subtitleCount = trackList.count {
                                        it == '"' && trackList.indexOf(
                                            "\"type\":\"sub\"",
                                            trackList.indexOf(it)
                                        ) != -1
                                    }
                                    Timber.d("Subtitle tracks in MPV after adding: $subtitleCount")
                                } catch (e: Exception) {
                                    Timber.w("Could not verify subtitle addition: ${e.message}")
                                }

                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "Failed to execute subtitle command: ${command.joinToString(" ")}"
                                )
                            }
                        }
                    }
                }

                MPVLib.MPV_EVENT_FILE_LOADED -> {
                    Timber.d("MPV: File loaded successfully")
                    updateState { it.copy(isLoading = false) }

                    buildSubtitleTrackMapping()
                    try {
                        MPVLib.setPropertyBoolean("pause", false)
                        Timber.d("Auto-starting playback after file load")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to auto-start playback")
                    }
                }

                MPVLib.MPV_EVENT_END_FILE -> {
                    Timber.d("=== MPV: File ended ===")

                    val currentItem = _playerState.value.currentItem
                    val currentPosition = currentTimePos
                    val duration = currentDuration

                    Timber.d("Current item: ${currentItem?.name}")
                    Timber.d("Current position: ${currentPosition}s")
                    Timber.d("Duration: ${duration}s")
                    Timber.d("Position threshold: ${duration - 5.0}s")

                    isNaturalEnd = duration > 0 && currentPosition >= (duration - 5.0)

                    Timber.d("Playback ended naturally: $isNaturalEnd")
                    Timber.d("Has completion callback: ${onPlaybackCompleted != null}")

                    subtitleTrackMapping.clear()

                    updateState {
                        it.copy(
                            isPlaying = false,
                            isPaused = false,
                            currentPosition = 0L
                        )
                    }

                    stopPlaybackReporting()

                    if (isNaturalEnd && currentItem != null) {
                        Timber.d("=== TRIGGERING AUTOPLAY CALLBACK ===")
                        Timber.d("Item completed: ${currentItem.name}")
                        scope.launch {
                            delay(500)
                            Timber.d("Executing autoplay callback for: ${currentItem.name}")
                            onPlaybackCompleted?.invoke(currentItem)
                            Timber.d("Autoplay callback completed")
                        }
                    } else {
                        if (!isNaturalEnd) {
                            Timber.d("Not triggering autoplay - not natural end")
                        }
                        if (currentItem == null) {
                            Timber.d("Not triggering autoplay - no current item")
                        }
                    }
                    Timber.d("=== End file event completed ===")
                }

                MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                    Timber.d("MPV: Playback restarted")

                    if (pendingSeekPosition > 0.0) {
                        try {
                            MPVLib.command(
                                arrayOf(
                                    "seek",
                                    pendingSeekPosition.toString(),
                                    "absolute"
                                )
                            )
                            Timber.d("Client-side seek to ${pendingSeekPosition}s")
                            pendingSeekPosition = 0.0
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to perform client-side seek")
                        }
                    }
                }

                MPVLib.MPV_EVENT_SEEK -> {
                    Timber.d("MPV: Seek completed")
                }

                else -> {
                    Timber.d("MPV: Unhandled event $eventId")
                }
            }
        }
    }

    private fun getEventName(eventId: Int): String {
        return when (eventId) {
            MPVLib.MPV_EVENT_NONE -> "NONE"
            MPVLib.MPV_EVENT_SHUTDOWN -> "SHUTDOWN"
            MPVLib.MPV_EVENT_LOG_MESSAGE -> "LOG_MESSAGE"
            MPVLib.MPV_EVENT_GET_PROPERTY_REPLY -> "GET_PROPERTY_REPLY"
            MPVLib.MPV_EVENT_SET_PROPERTY_REPLY -> "SET_PROPERTY_REPLY"
            MPVLib.MPV_EVENT_COMMAND_REPLY -> "COMMAND_REPLY"
            MPVLib.MPV_EVENT_START_FILE -> "START_FILE"
            MPVLib.MPV_EVENT_END_FILE -> "END_FILE"
            MPVLib.MPV_EVENT_FILE_LOADED -> "FILE_LOADED"
            MPVLib.MPV_EVENT_PLAYBACK_RESTART -> "PLAYBACK_RESTART"
            MPVLib.MPV_EVENT_SEEK -> "SEEK"
            MPVLib.MPV_EVENT_VIDEO_RECONFIG -> "VIDEO_RECONFIG"
            MPVLib.MPV_EVENT_AUDIO_RECONFIG -> "AUDIO_RECONFIG"
            else -> "UNKNOWN($eventId)"
        }
    }

    private fun updatePlayerState() {
        updateState { state ->
            state.copy(
                currentPosition = (currentTimePos * 1000).toLong(),
                duration = (currentDuration * 1000).toLong(),
                isPaused = isPaused,
                isPlaying = !isPaused && !isIdle,
                isBuffering = isSeeking
            )
        }
    }

    private var externalSubtitleCommands = mutableListOf<Array<String>>()


    private suspend fun configureExternalSubtitles(mediaSource: MediaSourceInfo, item: AfinityItem) {
        try {
            subtitleTrackMapping.clear()
            val externalSubtitles = mediaSource.mediaStreams?.filter { mediaStream ->
                mediaStream.isExternal && mediaStream.type == MediaStreamType.SUBTITLE && !mediaStream.path.isNullOrBlank()
            }

            if (externalSubtitles.isNullOrEmpty()) {
                Timber.d("No external subtitles found")
                return
            }

            Timber.d("Found ${externalSubtitles.size} external subtitle streams")

            externalSubtitleCommands.clear()

            externalSubtitles.forEach { subtitle ->
                val title = subtitle.title ?: "${subtitle.language ?: "Unknown"}"
                val language = subtitle.language ?: "und"

                val subtitleUrl = if (subtitle.path?.startsWith("http") == true) {
                    subtitle.path!!
                } else {
                    val currentMediaSourceId = currentMediaSource?.id ?: ""

                    val format = when {
                        subtitle.codec?.lowercase() == "subrip" -> "srt"
                        subtitle.codec?.lowercase() == "ass" -> "ass"
                        subtitle.codec?.lowercase() == "ssa" -> "ass"
                        subtitle.codec?.lowercase() == "webvtt" -> "vtt"
                        subtitle.codec?.lowercase() == "vobsub" -> "sub"
                        subtitle.path?.contains(".srt", ignoreCase = true) == true -> "srt"
                        subtitle.path?.contains(".ass", ignoreCase = true) == true -> "ass"
                        subtitle.path?.contains(".vtt", ignoreCase = true) == true -> "vtt"
                        subtitle.path?.contains(".sub", ignoreCase = true) == true -> "sub"
                        else -> "srt"
                    }

                    "${apiClient.baseUrl}/Videos/${item.id}/${currentMediaSourceId}/Subtitles/${subtitle.index}/Stream.${format}?api_key=${apiClient.accessToken}"
                }

                Timber.d("External subtitle found:")
                Timber.d("  Title: $title")
                Timber.d("  Language: $language")
                Timber.d("  Path/URL: $subtitleUrl")
                Timber.d("  Jellyfin Index: ${subtitle.index}")

                externalSubtitleCommands.add(arrayOf(
                    "sub-add",
                    subtitleUrl,
                    "auto",
                    title,
                    language
                ))

                Timber.d("Prepared external subtitle command: sub-add $subtitleUrl auto $title $language")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to configure external subtitles")
        }
    }

    private suspend fun buildSubtitleTrackMapping() = withContext(Dispatchers.Main) {
        try {
            val trackListJson = MPVLib.getPropertyString("track-list")
            Timber.d("Available tracks after load: $trackListJson")

            val allSubtitleStreams = currentMediaSource?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }
            if (allSubtitleStreams.isNullOrEmpty()) {
                Timber.d("No subtitle streams to map")
                return@withContext
            }

            var mpvTrackId = 1

            val internalSubtitles = allSubtitleStreams.filter { !it.isExternal }
            internalSubtitles.forEach { subtitle ->
                subtitleTrackMapping[subtitle.index] = mpvTrackId
                Timber.d("Mapped internal subtitle: Jellyfin index ${subtitle.index} -> MPV track $mpvTrackId")
                mpvTrackId++
            }

            val externalSubtitles = allSubtitleStreams.filter { it.isExternal }
            externalSubtitles.forEach { subtitle ->
                subtitleTrackMapping[subtitle.index] = mpvTrackId
                Timber.d("Mapped external subtitle: Jellyfin index ${subtitle.index} -> MPV track $mpvTrackId")
                mpvTrackId++
            }

            Timber.d("Subtitle track mapping completed: $subtitleTrackMapping")

        } catch (e: Exception) {
            Timber.e(e, "Failed to build subtitle track mapping")
        }
    }


    override suspend fun loadMedia(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!mpvInitialized) {
                Timber.d("MPV not initialized in loadMedia, initializing...")
                withContext(Dispatchers.Main) {
                    initializeIfNeeded()
                }
                if (!mpvInitialized) {
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = PlayerError(
                                code = -2,
                                message = "Failed to initialize player",
                                cause = null
                            )
                        )
                    }
                    return@withContext false
                }
            }

            updateState {
                it.copy(
                    isLoading = true,
                    error = null,
                    currentItem = item,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex
                )
            }

            val sessionId = UUID.randomUUID().toString()
            updateState { it.copy(sessionId = sessionId) }

            val mediaSources = try {
                playbackRepository.getMediaSources(
                    itemId = item.id,
                    maxStreamingBitrate = null,
                    maxAudioChannels = null,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    mediaSourceId = mediaSourceId
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to get media sources")
                updateState {
                    it.copy(
                        isLoading = false,
                        error = PlayerError(
                            code = -4,
                            message = "Failed to get media sources: ${e.message}",
                            cause = e
                        )
                    )
                }
                return@withContext false
            }

            if (mediaSources.isEmpty()) {
                Timber.e("No media sources available for item: ${item.id}")
                updateState {
                    it.copy(
                        isLoading = false,
                        error = PlayerError(
                            code = -5,
                            message = "No compatible media sources found",
                            cause = null
                        )
                    )
                }
                return@withContext false
            }

            val selectedMediaSource = mediaSources.firstOrNull { it.id == mediaSourceId }
                ?: mediaSources.firstOrNull()

            currentMediaSource = selectedMediaSource

            if (selectedMediaSource == null) {
                Timber.e("Could not select media source")
                updateState {
                    it.copy(
                        isLoading = false,
                        error = PlayerError(
                            code = -6,
                            message = "Could not select media source",
                            cause = null
                        )
                    )
                }
                return@withContext false
            }

            Timber.d("Selected media source: ${selectedMediaSource.id}, name: ${selectedMediaSource.name}")

            val startTimeTicks = if (startPositionMs > 0) startPositionMs * 10000 else null

            val streamUrl = try {
                withContext(Dispatchers.IO) {
                    playbackRepository.getStreamUrl(
                        itemId = item.id,
                        mediaSourceId = selectedMediaSource.id ?: "",
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        videoStreamIndex = null,
                        maxStreamingBitrate = null,
                        startTimeTicks = startTimeTicks
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get stream URL")
                updateState {
                    it.copy(
                        isLoading = false,
                        error = PlayerError(
                            code = -7,
                            message = "Failed to get stream URL: ${e.message}",
                            cause = e
                        )
                    )
                }
                return@withContext false
            }

            if (streamUrl.isNullOrBlank()) {
                Timber.e("Stream URL is null or empty")
                updateState {
                    it.copy(
                        isLoading = false,
                        error = PlayerError(
                            code = -8,
                            message = "Stream URL is empty",
                            cause = null
                        )
                    )
                }
                return@withContext false
            }

            Timber.d("Stream URL: $streamUrl")

            withContext(Dispatchers.Main) {
                try {
                    pendingSeekPosition = if (startPositionMs > 0) startPositionMs / 1000.0 else 0.0
                    MPVLib.command(arrayOf("loadfile", streamUrl))
                    Timber.d("MPV loadfile command sent successfully with server-side resume")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to send loadfile command to MPV")
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = PlayerError(
                                code = -9,
                                message = "Failed to load media: ${e.message}",
                                cause = e
                            )
                        )
                    }
                    return@withContext false
                }
            }

            configureExternalSubtitles(selectedMediaSource, item)

            audioStreamIndex?.let { selectAudioTrack(it) }
            subtitleStreamIndex?.let { selectSubtitleTrack(it) }

            startPlaybackReporting()

            Timber.d("Media loading initiated successfully with server-side resume from ${startPositionMs}ms")
            true

        } catch (e: Exception) {
            Timber.e(e, "Failed to load media")
            updateState {
                it.copy(
                    isLoading = false,
                    error = PlayerError(
                        code = -1,
                        message = "Failed to load media: ${e.message}",
                        cause = e
                    )
                )
            }
            false
        }
    }

    override suspend fun play() = withContext(Dispatchers.Main) {
        try {
            MPVLib.setPropertyBoolean("pause", false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to play")
        }
    }

    override suspend fun pause() = withContext(Dispatchers.Main) {
        try {
            MPVLib.setPropertyBoolean("pause", true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to pause")
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        try {
            isNaturalEnd = false
            MPVLib.command(arrayOf("stop"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop")
        }
    }

    override suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main) {
        try {
            val seekSeconds = positionMs / 1000.0
            MPVLib.command(arrayOf("seek", seekSeconds.toString(), "absolute"))
            Timber.d("User-initiated seek to ${seekSeconds}s")
        } catch (e: Exception) {
            Timber.e(e, "Failed to seek to position: $positionMs")
        }
    }

    override suspend fun seekRelative(deltaMs: Long) = withContext(Dispatchers.Main) {
        try {
            val deltaSeconds = deltaMs / 1000.0
            MPVLib.command(arrayOf("seek", deltaSeconds.toString(), "relative"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to seek relative: $deltaMs")
        }
    }

    override suspend fun setVolume(volume: Int) = withContext(Dispatchers.Main) {
        try {
            val clampedVolume = volume.coerceIn(0, 100)
            MPVLib.setPropertyInt("volume", clampedVolume)
            updateState { it.copy(volume = clampedVolume) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set volume: $volume")
        }
    }

    override suspend fun setBrightness(brightness: Float) = withContext(Dispatchers.Main) {
        try {
            val clampedBrightness = brightness.coerceIn(0.0f, 1.0f)
            val mpvBrightness = ((clampedBrightness - 0.5f) * 200).toInt()
            MPVLib.setPropertyInt("brightness", mpvBrightness)
            updateState { it.copy(brightness = clampedBrightness) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set brightness: $brightness")
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) = withContext(Dispatchers.Main) {
        try {
            val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
            MPVLib.setPropertyDouble("speed", clampedSpeed.toDouble())
            updateState { it.copy(playbackSpeed = clampedSpeed) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set playback speed: $speed")
        }
    }

    override suspend fun selectAudioTrack(index: Int) = withContext(Dispatchers.Main) {
        try {
            val audioStreams = currentMediaSource?.mediaStreams?.filter { it.type == MediaStreamType.AUDIO }

            if (audioStreams != null) {
                val mpvTrackNumber = audioStreams.indexOfFirst { it.index == index } + 1

                if (mpvTrackNumber > 0) {
                    MPVLib.setPropertyInt("aid", mpvTrackNumber)
                    updateState { it.copy(audioStreamIndex = index) }
                    Timber.d("Selected audio track: MPV track $mpvTrackNumber for stream index $index")
                } else {
                    Timber.e("Audio stream with index $index not found")
                }
            } else {
                Timber.e("No audio streams available")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to select audio track: $index")
        }
    }

    override suspend fun selectSubtitleTrack(index: Int?) = withContext(Dispatchers.Main) {
        try {
            if (index == null) {
                MPVLib.setPropertyString("sid", "no")
                updateState { it.copy(subtitleStreamIndex = null) }
                Timber.d("Disabled subtitles")
            } else {
                val mpvTrackId = subtitleTrackMapping[index]

                if (mpvTrackId != null) {
                    MPVLib.setPropertyInt("sid", mpvTrackId)

                    MPVLib.setPropertyString("sub-visibility", "yes")

                    updateState { it.copy(subtitleStreamIndex = index) }
                    Timber.d("Selected subtitle track: Jellyfin stream $index -> MPV track $mpvTrackId")

                    try {
                        val subVisible = MPVLib.getPropertyString("sub-visibility")
                        val currentSid = MPVLib.getPropertyString("sid")
                        Timber.d("Subtitle debug - Visibility: $subVisible, Current SID: $currentSid")
                    } catch (e: Exception) {
                        Timber.w("Failed to read subtitle properties: ${e.message}")
                    }
                } else {
                    Timber.e("Subtitle stream with index $index not found in mapping: $subtitleTrackMapping")

                    val subtitleStreams = currentMediaSource?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }
                    if (subtitleStreams != null) {
                        val mpvTrackNumber = subtitleStreams.indexOfFirst { it.index == index } + 1

                        if (mpvTrackNumber > 0) {
                            MPVLib.setPropertyInt("sid", mpvTrackNumber)
                            MPVLib.setPropertyString("sub-visibility", "yes")
                            updateState { it.copy(subtitleStreamIndex = index) }
                            Timber.d("Fallback: Selected subtitle track: Jellyfin stream $index -> MPV track $mpvTrackNumber")
                        } else {
                            Timber.e("Subtitle stream with index $index not found in streams")
                        }
                    } else {
                        Timber.e("No subtitle streams available")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to select subtitle track: $index")
        }
    }


    override suspend fun toggleFullscreen() {
        updateState { it.copy(isFullscreen = !it.isFullscreen) }
    }

    override suspend fun toggleControlsLock() = withContext(Dispatchers.Main) {
        updateState { it.copy(isControlsLocked = !it.isControlsLocked) }
        Timber.d("Controls lock toggled: ${_playerState.value.isControlsLocked}")
    }

    override suspend fun reportPlaybackStart() {
        val state = _playerState.value
        val item = state.currentItem ?: return
        val sessionId = state.sessionId ?: return
        val mediaSourceId = state.mediaSourceId ?: return

        try {
            playbackRepository.reportPlaybackStart(
                itemId = item.id,
                sessionId = sessionId,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = state.audioStreamIndex,
                subtitleStreamIndex = state.subtitleStreamIndex,
                playMethod = "DirectPlay",
                canSeek = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    override suspend fun reportPlaybackProgress() {
        val state = _playerState.value
        val item = state.currentItem ?: return
        val sessionId = state.sessionId ?: return

        try {
            playbackRepository.reportPlaybackProgress(
                itemId = item.id,
                sessionId = sessionId,
                positionTicks = state.currentPosition * 10000,
                isPaused = state.isPaused,
                isMuted = state.volume == 0,
                volumeLevel = state.volume,
                audioStreamIndex = state.audioStreamIndex,
                subtitleStreamIndex = state.subtitleStreamIndex,
                playMethod = "DirectPlay",
                repeatMode = "RepeatNone"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback progress")
        }
    }

    override suspend fun reportPlaybackStop() {
        val state = _playerState.value
        val item = state.currentItem ?: return
        val sessionId = state.sessionId ?: return
        val mediaSourceId = state.mediaSourceId ?: return

        try {

            Timber.d("=== REPORTING PLAYBACK STOP ===")
            Timber.d("Item: ${item.name} (${item.id})")
            Timber.d("Position: ${state.currentPosition}ms")

            val success = playbackRepository.reportPlaybackStop(
                itemId = item.id,
                sessionId = sessionId,
                positionTicks = state.currentPosition * 10000,
                mediaSourceId = mediaSourceId,
                nextMediaType = null,
                playlistItemId = null
            )
            Timber.d("Playback stop reported successfully: $success")
            if (success) {
                Timber.d("Triggering playback stopped callback")
                onPlaybackStoppedCallback?.invoke()
            } else {
                Timber.w("Playback stop failed - not invalidating cache")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback stop")
        }
    }

    private var onPlaybackStoppedCallback: (() -> Unit)? = null

    override fun setOnPlaybackStoppedCallback(callback: () -> Unit) {
        onPlaybackStoppedCallback = callback
    }

    private fun startPlaybackReporting() {
        playbackReportingJob?.cancel()
        playbackReportingJob = scope.launch {
            reportPlaybackStart()

            while (isActive) {
                delay(10000)
                reportPlaybackProgress()
            }
        }
    }

    private fun stopPlaybackReporting() {
        playbackReportingJob?.cancel()
        runBlocking {
            reportPlaybackStop()
        }
    }

    private fun updateState(update: (PlayerState) -> PlayerState) {
        _playerState.value = update(_playerState.value)
    }

    override fun onResume() {
        if (mpvInitialized) {
            try {
                val state = _playerState.value
                if (state.isPlaying && state.isPaused) {
                    scope.launch { play() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume player")
            }
        }
    }

    override fun onPause() {
        if (mpvInitialized && _playerState.value.isPlaying) {
            scope.launch { pause() }
        }
    }

    override fun onDestroy() {
        stopPlaybackReporting()
    }

    fun cleanup() {
        releaseResources()
        scope.cancel()
    }

    private suspend fun debugMediaSources(item: AfinityItem, mediaSourceId: String) {
        try {
            val mediaSources = playbackRepository.getMediaSources(
                itemId = item.id,
                mediaSourceId = mediaSourceId
            )

            Timber.d("=== MEDIA SOURCES DEBUG ===")
            Timber.d("Item: ${item.name} (${item.id})")
            Timber.d("Requested mediaSourceId: $mediaSourceId")
            Timber.d("Available media sources: ${mediaSources.size}")

            mediaSources.forEachIndexed { index, source ->
                Timber.d("Source $index:")
                Timber.d("  ID: ${source.id}")
                Timber.d("  Name: ${source.name}")
                Timber.d("  Protocol: ${source.protocol}")
                Timber.d("  Path: ${source.path}")
                Timber.d("  Container: ${source.container}")
                Timber.d("  IsRemote: ${source.isRemote}")
            }

            val selectedSource = mediaSources.firstOrNull { it.id == mediaSourceId }
                ?: mediaSources.firstOrNull()
            Timber.d("Selected source: ${selectedSource?.id}")

        } catch (e: Exception) {
            Timber.e(e, "Debug failed")
        }
    }

    fun setOnPlaybackCompleted(callback: (AfinityItem) -> Unit) {
        onPlaybackCompleted = callback
    }

    fun initializeIfNeeded() {
        if (!mpvInitialized) {
            Timber.d("Initializing MPV...")
            initializeMpv()
        }
    }

    fun releaseResources() {
        Timber.d("Releasing MPV resources...")
        stopPlaybackReporting()

        if (mpvInitialized) {
            try {
                synchronized(this) {
                    if (!mpvInitialized) {
                        Timber.d("MPV already released, skipping")
                        return
                    }

                    try {
                        MPVLib.command(arrayOf("stop"))
                        Thread.sleep(100)
                    } catch (e: Exception) {
                        Timber.w("MPV stop command failed: ${e.message}")
                    }

                    try {
                        MPVLib.removeObserver(this)
                    } catch (e: Exception) {
                        Timber.w("Failed to remove MPV observer: ${e.message}")
                    }

                    try {
                        MPVLib.destroy()
                        mpvInitialized = false
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to destroy MPV")
                        mpvInitialized = false
                    }

                    updateState { PlayerState() }
                    Timber.d("MPV resources released successfully")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to release MPV resources")
                mpvInitialized = false
            }
        } else {
            Timber.d("MPV not initialized, nothing to release")
        }
    }
}