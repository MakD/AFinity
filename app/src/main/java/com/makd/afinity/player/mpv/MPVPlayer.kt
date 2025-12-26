package com.makd.afinity.player.mpv

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioFocusRequestCompat
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Size
import androidx.media3.common.util.Util
import dev.jdtech.mpv.MPVLib
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet

@androidx.media3.common.util.UnstableApi
class MPVPlayer(
    context: Context,
    private val audioAttributes: AudioAttributes = AudioAttributes.DEFAULT,
    private val handleAudioFocus: Boolean = true,
    private var trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT,
    private val seekBackIncrement: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS,
    private val seekForwardIncrement: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
    private val pauseAtEndOfMediaItems: Boolean = false,
    private val videoOutput: String = "gpu",
    private val audioOutput: String = "audiotrack",
    private val hwDec: String = "mediacodec",
) : BasePlayer(), MPVLib.EventObserver, AudioManager.OnAudioFocusChangeListener {

    private val audioManager: AudioManager by lazy { context.getSystemService()!! }
    private var audioFocusCallback: () -> Unit = {}
    private lateinit var audioFocusRequest: AudioFocusRequestCompat
    private val handler = Handler(context.mainLooper)

    private constructor(builder: Builder) : this(
        context = builder.context,
        audioAttributes = builder.audioAttributes,
        handleAudioFocus = builder.handleAudioFocus,
        trackSelectionParameters = builder.trackSelectionParameters,
        seekBackIncrement = builder.seekBackIncrementMs,
        seekForwardIncrement = builder.seekForwardIncrementMs,
        pauseAtEndOfMediaItems = builder.pauseAtEndOfMediaItems,
        videoOutput = builder.videoOutput,
        audioOutput = builder.audioOutput,
        hwDec = builder.hwDec,
    )

    class Builder(
        val context: Context,
    ) {
        var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
            private set

        var handleAudioFocus: Boolean = true
            private set

        var trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT
            private set

        var seekBackIncrementMs: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS
            private set

        var seekForwardIncrementMs: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS
            private set

        var pauseAtEndOfMediaItems: Boolean = false
            private set

        var videoOutput: String = "gpu"
            private set

        var audioOutput: String = "audiotrack"
            private set

        var hwDec: String = "mediacodec"
            private set

        fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) = apply {
            this.audioAttributes = audioAttributes
            this.handleAudioFocus = handleAudioFocus
        }

        fun setTrackSelectionParameters(trackSelectionParameters: TrackSelectionParameters) = apply {
            this.trackSelectionParameters = trackSelectionParameters
        }

        fun setSeekBackIncrementMs(seekBackIncrementMs: Long) = apply {
            this.seekBackIncrementMs = seekBackIncrementMs
        }

        fun setSeekForwardIncrementMs(seekForwardIncrementMs: Long) = apply {
            this.seekForwardIncrementMs = seekForwardIncrementMs
        }

        fun setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems: Boolean) = apply {
            this.pauseAtEndOfMediaItems = pauseAtEndOfMediaItems
        }

        fun setVideoOutput(videoOutput: String) = apply {
            this.videoOutput = videoOutput
        }

        fun setAudioOutput(audioOutput: String) = apply {
            this.audioOutput = audioOutput
        }

        fun setHwDec(hwDec: String) = apply {
            this.hwDec = hwDec
        }

        fun build() = MPVPlayer(this)
    }

    init {
        require(context is Application)
        val mpvDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "mpv")
        Timber.i("mpv config dir: $mpvDir")
        if (!mpvDir.exists()) mpvDir.mkdirs()

        arrayOf("mpv.conf", "subfont.ttf").forEach { fileName ->
            val file = File(mpvDir, fileName)
            if (file.exists()) return@forEach
            try {
                context.assets.open(fileName, AssetManager.ACCESS_STREAMING)
                    .copyTo(FileOutputStream(file))
            } catch (e: Exception) {
                Timber.w("Could not copy $fileName: ${e.message}")
            }
        }

        MPVLib.create(context)

        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", mpvDir.path)
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("vo", videoOutput)
        MPVLib.setOptionString("ao", audioOutput)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("vid", "no")

        MPVLib.setOptionString("hwdec", hwDec)
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

        MPVLib.setOptionString("sub-auto", "exact")
        MPVLib.setOptionString("sub-visibility", "yes")

        MPVLib.addObserver(this)

        arrayOf(
            Property("track-list", MPVLib.MPV_FORMAT_STRING),
            Property("paused", MPVLib.MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPVLib.MPV_FORMAT_FLAG),
            Property("eof-reached", MPVLib.MPV_FORMAT_FLAG),
            Property("seekable", MPVLib.MPV_FORMAT_FLAG),
            Property("time-pos", MPVLib.MPV_FORMAT_INT64),
            Property("duration", MPVLib.MPV_FORMAT_INT64),
            Property("demuxer-cache-time", MPVLib.MPV_FORMAT_INT64),
            Property("speed", MPVLib.MPV_FORMAT_DOUBLE),
            Property("playlist-count", MPVLib.MPV_FORMAT_INT64),
            Property("playlist-current-pos", MPVLib.MPV_FORMAT_INT64),
        ).forEach { (name, format) ->
            MPVLib.observeProperty(name, format)
        }

        if (handleAudioFocus) {
            audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(this)
                .build()
            val res = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
            if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                MPVLib.setPropertyBoolean("pause", true)
            }
        }
    }

    private val listeners: ListenerSet<Player.Listener> = ListenerSet(
        context.mainLooper,
        Clock.DEFAULT,
    ) { listener: Player.Listener, flags: FlagSet ->
        listener.onEvents(this, Player.Events(flags))
    }
    private val videoListeners = CopyOnWriteArraySet<Player.Listener>()

    private var internalMediaItems = mutableListOf<MediaItem>()

    @Player.State
    private var playbackState: Int = STATE_IDLE
    private var currentPlayWhenReady: Boolean = false

    @Player.RepeatMode
    private val repeatMode: Int = REPEAT_MODE_OFF
    private var currentTracks: Tracks = Tracks.EMPTY
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT

    private var isPlayerReady: Boolean = false
    private var isSeekable: Boolean = false
    private var currentMediaItemIndex: Int = 0
    private var currentPositionMs: Long? = null
    private var currentDurationMs: Long? = null
    private var currentCacheDurationMs: Long? = null
    private var initialCommands = mutableListOf<Array<String>>()
    private var initialIndex: Int = 0
    private var initialSeekTo: Long = 0L
    private var oldMediaItem: MediaItem? = null

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: String) {
        handler.post {
            when (property) {
                "track-list" -> {
                    val newTracks = getTracks(value)
                    currentTracks = newTracks
                    listeners.sendEvent(EVENT_TRACKS_CHANGED) { listener ->
                        listener.onTracksChanged(currentTracks)
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        handler.post {
            when (property) {
                "paused" -> {
                    if (isPlayerReady) {
                        setPlayerStateAndNotifyIfChanged(
                            playWhenReady = !value,
                            playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                        )
                    }
                }
                "eof-reached" -> {
                    if (value && isPlayerReady) {
                        if (currentMediaItemIndex < (internalMediaItems.size - 1)) {
                            if (pauseAtEndOfMediaItems) {
                                setPlayerStateAndNotifyIfChanged(
                                    playWhenReady = false,
                                    playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                                    playbackState = STATE_READY,
                                )
                            } else {
                                prepareMediaItem(currentMediaItemIndex + 1)
                                setPlayWhenReady(true)
                            }
                        } else {
                            setPlayerStateAndNotifyIfChanged(
                                playWhenReady = false,
                                playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                                playbackState = STATE_ENDED,
                            )
                            resetInternalState()
                        }
                    }
                }
                "paused-for-cache" -> {
                    if (isPlayerReady) {
                        if (value) {
                            setPlayerStateAndNotifyIfChanged(playbackState = STATE_BUFFERING)
                        } else {
                            setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)
                        }
                    }
                }
                "seekable" -> {
                    isSeekable = value
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Long) {
        handler.post {
            when (property) {
                "time-pos" -> {
                    currentPositionMs = value * 1000
                }
                "duration" -> {
                    currentDurationMs = value * 1000
                }
                "demuxer-cache-time" -> {
                    currentCacheDurationMs = value * 1000
                }
                "playlist-count" -> {
                    if (!isPlayerReady && value > 0) {
                        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { listener ->
                            listener.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
                        }
                    }
                }
                "playlist-current-pos" -> {
                    currentMediaItemIndex = value.toInt()
                    val newMediaItem = currentMediaItem
                    if (oldMediaItem?.mediaId != newMediaItem?.mediaId) {
                        oldMediaItem = newMediaItem
                        listeners.sendEvent(EVENT_MEDIA_ITEM_TRANSITION) { listener ->
                            listener.onMediaItemTransition(
                                newMediaItem,
                                MEDIA_ITEM_TRANSITION_REASON_AUTO,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String, value: Double) {
        handler.post {
            when (property) {
                "speed" -> {
                    playbackParameters = playbackParameters.withSpeed(value.toFloat())
                    listeners.sendEvent(EVENT_PLAYBACK_PARAMETERS_CHANGED) { listener ->
                        listener.onPlaybackParametersChanged(playbackParameters)
                    }
                }
            }
        }
    }

    override fun event(@MPVLib.Event eventId: Int) {
        handler.post {
            when (eventId) {
                MPVLib.MPV_EVENT_START_FILE -> {
                    if (!isPlayerReady) {
                        for (command in initialCommands) {
                            MPVLib.command(command)
                        }
                    }
                }
                MPVLib.MPV_EVENT_SEEK -> {
                    setPlayerStateAndNotifyIfChanged(playbackState = STATE_BUFFERING)
                }
                MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                    if (!isPlayerReady) {
                        isPlayerReady = true
                        seekTo(C.TIME_UNSET)
                        if (playWhenReady) {
                            Timber.d("Starting playback...")
                            MPVLib.setPropertyBoolean("pause", false)
                        }
                        for (videoListener in videoListeners) {
                            videoListener.onRenderedFirstFrame()
                        }
                    } else {
                        setPlayerStateAndNotifyIfChanged(playbackState = STATE_READY)
                    }
                }
            }
        }
    }

    private fun setPlayerStateAndNotifyIfChanged(
        playWhenReady: Boolean = getPlayWhenReady(),
        @Player.PlayWhenReadyChangeReason playWhenReadyChangeReason: Int = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        @Player.State playbackState: Int = getPlaybackState(),
    ) {
        var playerStateChanged = false
        val wasPlaying = isPlaying
        if (playbackState != getPlaybackState()) {
            this.playbackState = playbackState
            listeners.queueEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                listener.onPlaybackStateChanged(playbackState)
            }
            playerStateChanged = true
        }
        if (playWhenReady != getPlayWhenReady()) {
            this.currentPlayWhenReady = playWhenReady
            listeners.queueEvent(EVENT_PLAY_WHEN_READY_CHANGED) { listener ->
                listener.onPlayWhenReadyChanged(playWhenReady, playWhenReadyChangeReason)
            }
            playerStateChanged = true
        }
        if (playerStateChanged) {
            listeners.queueEvent(C.INDEX_UNSET) { listener ->
                listener.onPlaybackStateChanged(playbackState)
            }
        }
        if (wasPlaying != isPlaying) {
            listeners.queueEvent(EVENT_IS_PLAYING_CHANGED) { listener ->
                listener.onIsPlayingChanged(isPlaying)
            }
        }
        listeners.flushEvents()
    }

    private val timeline: Timeline = object : Timeline() {
        override fun getWindowCount(): Int = internalMediaItems.size

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            val currentMediaItem = internalMediaItems.getOrNull(windowIndex) ?: MediaItem.Builder().build()
            return window.set(
                windowIndex,
                currentMediaItem,
                null,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                isSeekable,
                !isSeekable,
                currentMediaItem.liveConfiguration,
                C.TIME_UNSET,
                Util.msToUs(currentDurationMs ?: C.TIME_UNSET),
                windowIndex,
                windowIndex,
                C.TIME_UNSET,
            )
        }

        override fun getPeriodCount(): Int = internalMediaItems.size

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            return period.set(null, null, periodIndex, C.TIME_UNSET, 0)
        }

        override fun getIndexOfPeriod(uid: Any): Int = C.INDEX_UNSET

        override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex as Any
    }

    override fun getApplicationLooper(): Looper = Looper.getMainLooper()

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        videoListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        videoListeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        MPVLib.command(arrayOf("playlist-clear"))
        MPVLib.command(arrayOf("playlist-remove", "current"))
        internalMediaItems = mediaItems
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        MPVLib.command(arrayOf("playlist-clear"))
        MPVLib.command(arrayOf("playlist-remove", "current"))
        internalMediaItems = mediaItems
        initialIndex = startIndex
        initialSeekTo = startPositionMs / 1000
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        internalMediaItems.addAll(index, mediaItems)
        mediaItems.forEach { mediaItem ->
            MPVLib.command(arrayOf("loadfile", "${mediaItem.localConfiguration?.uri}", "insert-at", index.toString()))
        }
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {}
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}

    override fun prepare() {
        internalMediaItems.forEachIndexed { index, mediaItem ->
            MPVLib.command(arrayOf("loadfile", "${mediaItem.localConfiguration?.uri}", if (index == 0) "replace" else "append"))
        }
        prepareMediaItem(initialIndex)
    }

    private fun prepareMediaItem(index: Int) {
        internalMediaItems.getOrNull(index)?.let { mediaItem ->
            resetInternalState()
            mediaItem.localConfiguration?.subtitleConfigurations?.forEach { subtitle ->
                initialCommands.add(
                    arrayOf(
                        "sub-add",
                        "${subtitle.uri}",
                        "auto",
                        "${subtitle.label}",
                        "${subtitle.language}",
                    ),
                )
            }
            if (currentMediaItemIndex != index) {
                MPVLib.command(arrayOf("playlist-play-index", "$index"))
            }
            setPlayerStateAndNotifyIfChanged(playbackState = STATE_BUFFERING)
        }
    }

    override fun getPlaybackState(): Int = playbackState
    override fun getPlaybackSuppressionReason(): Int = PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getPlayerError(): PlaybackException? = null

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (currentPlayWhenReady != playWhenReady) {
            setPlayerStateAndNotifyIfChanged(
                playWhenReady = playWhenReady,
                playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            if (isPlayerReady) {
                if (handleAudioFocus && playWhenReady) {
                    val res = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
                    if (res != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        MPVLib.setPropertyBoolean("pause", true)
                    } else {
                        MPVLib.setPropertyBoolean("pause", false)
                    }
                } else {
                    MPVLib.setPropertyBoolean("pause", !playWhenReady)
                }
            }
        }
    }

    override fun getPlayWhenReady(): Boolean = currentPlayWhenReady

    override fun setRepeatMode(repeatMode: Int) {}
    override fun getRepeatMode(): Int = repeatMode

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
    override fun getShuffleModeEnabled(): Boolean = false

    override fun isLoading(): Boolean = playbackState == STATE_BUFFERING

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
        @Player.Command seekCommand: Int,
        isRepeatingCurrentItem: Boolean,
    ) {
        if (mediaItemIndex == currentMediaItemIndex) {
            val seekTo =
                if (positionMs != C.TIME_UNSET) positionMs / C.MILLIS_PER_SECOND else initialSeekTo
            initialSeekTo = if (isPlayerReady) {
                MPVLib.command(arrayOf("seek", "$seekTo", "absolute"))
                Timber.d("MPV seeking to $seekTo seconds")
                0L
            } else {
                Timber.d("MPV not ready, storing initial seek: $seekTo seconds")
                seekTo
            }
        } else {
            prepareMediaItem(mediaItemIndex)
            play()
        }
    }

    override fun getSeekBackIncrement(): Long = seekBackIncrement
    override fun getSeekForwardIncrement(): Long = seekForwardIncrement
    override fun getMaxSeekToPreviousPosition(): Long = 0

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        MPVLib.setPropertyDouble("speed", playbackParameters.speed.toDouble())
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun stop() {
        release()
    }

    override fun release() {
        if (handleAudioFocus) {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        }
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun getCurrentTracks(): Tracks = currentTracks
    override fun getTrackSelectionParameters(): TrackSelectionParameters = trackSelectionParameters

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        trackSelectionParameters = parameters

        val disabledTrackTypes = parameters.disabledTrackTypes.map { MPVTrackType.fromMedia3TrackType(it) }

        val notOverriddenTypes = mutableSetOf(MPVTrackType.VIDEO, MPVTrackType.AUDIO, MPVTrackType.SUBTITLE)
        for (override in parameters.overrides) {
            val trackType = MPVTrackType.fromMedia3TrackType(override.key.type)
            notOverriddenTypes.remove(trackType)
            val id = override.key.getFormat(0).id ?: continue

            selectTrack(trackType, id)
        }
        for (notOverriddenType in notOverriddenTypes) {
            if (notOverriddenType in disabledTrackTypes) {
                selectTrack(notOverriddenType, "no")
            } else {
                selectTrack(notOverriddenType, "auto")
            }
        }

        listeners.sendEvent(EVENT_TRACK_SELECTION_PARAMETERS_CHANGED) { listener ->
            listener.onTrackSelectionParametersChanged(parameters)
        }
    }

    private fun selectTrack(
        trackType: MPVTrackType,
        id: String,
    ) {
        MPVLib.setPropertyString(trackType.type, id)
    }

    override fun getMediaMetadata(): MediaMetadata = currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}

    override fun getCurrentTimeline(): Timeline = timeline
    override fun getCurrentPeriodIndex(): Int = currentMediaItemIndex
    override fun getCurrentMediaItemIndex(): Int = currentMediaItemIndex

    override fun getDuration(): Long = currentDurationMs ?: C.TIME_UNSET
    override fun getCurrentPosition(): Long = currentPositionMs ?: C.TIME_UNSET
    override fun getBufferedPosition(): Long = (currentCacheDurationMs ?: getCurrentPosition()).coerceAtMost(duration)
    override fun getTotalBufferedDuration(): Long = (bufferedPosition - currentPosition).coerceAtLeast(0)

    override fun isPlayingAd(): Boolean = false
    override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET
    override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET
    override fun getContentPosition(): Long = currentPosition
    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setVolume(audioVolume: Float) {
        MPVLib.setPropertyInt("volume", (audioVolume * 100).toInt())
    }

    override fun getVolume(): Float = MPVLib.getPropertyInt("volume") / 100F

    override fun clearVideoSurface() {}
    override fun clearVideoSurface(surface: Surface?) {}
    override fun setVideoSurface(surface: Surface?) {}
    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        surfaceView?.holder?.addCallback(surfaceHolder)
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        surfaceView?.holder?.removeCallback(surfaceHolder)
    }

    override fun setVideoTextureView(textureView: TextureView?) {}
    override fun clearVideoTextureView(textureView: TextureView?) {}

    override fun getVideoSize(): VideoSize {
        val width = MPVLib.getPropertyInt("width")
        val height = MPVLib.getPropertyInt("height")
        if (width == null || height == null) return VideoSize.UNKNOWN
        return VideoSize(width, height)
    }

    override fun getSurfaceSize(): Size {
        val mpvSize = MPVLib.getPropertyString("android-surface-size").split("x")
        return try {
            Size(mpvSize[0].toInt(), mpvSize[1].toInt())
        } catch (_: IndexOutOfBoundsException) {
            Size.UNKNOWN
        }
    }

    override fun getCurrentCues(): CueGroup = CueGroup(emptyList(), 0)

    override fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL)
            .setMinVolume(0)
            .setMaxVolume(100)
            .build()
    }

    override fun getDeviceVolume(): Int = MPVLib.getPropertyInt("volume")
    override fun isDeviceMuted(): Boolean = MPVLib.getPropertyBoolean("mute")

    override fun mute() {
        MPVLib.setPropertyBoolean("mute", true)
    }

    override fun unmute() {
        MPVLib.setPropertyBoolean("mute", false)
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        MPVLib.setPropertyInt("volume", volume)
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        MPVLib.setPropertyInt("volume", volume)
    }

    override fun increaseDeviceVolume() {
        setDeviceVolume((getDeviceVolume() + 1).coerceAtMost(100))
    }

    override fun increaseDeviceVolume(flags: Int) {
        increaseDeviceVolume()
    }

    override fun decreaseDeviceVolume() {
        setDeviceVolume((getDeviceVolume() - 1).coerceAtLeast(0))
    }

    override fun decreaseDeviceVolume(flags: Int) {
        decreaseDeviceVolume()
    }

    override fun setDeviceMuted(muted: Boolean) {
        MPVLib.setPropertyBoolean("mute", muted)
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        setDeviceMuted(muted)
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}

    override fun getAvailableCommands(): Player.Commands {
        return Player.Commands.Builder()
            .addAll(permanentAvailableCommands)
            .addIf(COMMAND_SEEK_TO_DEFAULT_POSITION, !isPlayingAd)
            .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, isCurrentMediaItemSeekable && !isPlayingAd)
            .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem() && !isPlayingAd)
            .addIf(COMMAND_SEEK_TO_PREVIOUS, !currentTimeline.isEmpty && (hasPreviousMediaItem() || !isCurrentMediaItemLive || isCurrentMediaItemSeekable) && !isPlayingAd)
            .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem() && !isPlayingAd)
            .addIf(COMMAND_SEEK_TO_NEXT, !currentTimeline.isEmpty && (hasNextMediaItem() || (isCurrentMediaItemLive && isCurrentMediaItemDynamic)) && !isPlayingAd)
            .addIf(COMMAND_SEEK_TO_MEDIA_ITEM, !isPlayingAd)
            .addIf(COMMAND_SEEK_BACK, isCurrentMediaItemSeekable && !isPlayingAd)
            .addIf(COMMAND_SEEK_FORWARD, isCurrentMediaItemSeekable && !isPlayingAd)
            .build()
    }

    private fun resetInternalState() {
        isPlayerReady = false
        isSeekable = false
        playbackState = STATE_IDLE
        currentPlayWhenReady = false
        currentPositionMs = null
        currentDurationMs = null
        currentCacheDurationMs = null
        currentTracks = Tracks.EMPTY
        playbackParameters = PlaybackParameters.DEFAULT
        initialCommands.clear()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                audioFocusCallback = {
                    if (getPlayWhenReady()) {
                        setPlayWhenReady(true)
                    }
                    audioFocusCallback = {}
                }
                setPlayWhenReady(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                setVolume(AUDIO_FOCUS_DUCKING)
                audioFocusCallback = {
                    setVolume(1F)
                    audioFocusCallback = {}
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusCallback()
            }
        }
    }

    private val surfaceHolder = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            MPVLib.attachSurface(holder.surface)
            MPVLib.setOptionString("force-window", "yes")
            MPVLib.setOptionString("vo", videoOutput)
            MPVLib.setOptionString("vid", "auto")
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            MPVLib.setOptionString("vid", "no")
            MPVLib.setOptionString("vo", "null")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.detachSurface()
        }
    }

    fun setOption(name: String, value: String) {
        try {
            MPVLib.setPropertyString(name, value)
            Timber.d("MPV option set: $name = $value")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set MPV option: $name = $value")
        }
    }

    companion object {
        private const val AUDIO_FOCUS_DUCKING = 0.5f

        private val permanentAvailableCommands: Player.Commands = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_SET_SPEED_AND_PITCH,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA,
                COMMAND_CHANGE_MEDIA_ITEMS,
                COMMAND_SET_VIDEO_SURFACE,
                COMMAND_GET_TRACKS,
                COMMAND_SET_TRACK_SELECTION_PARAMETERS,
            )
            .build()

        private fun JSONObject.optNullableString(name: String): String? {
            return if (this.has(name) && !this.isNull(name)) {
                this.getString(name)
            } else {
                null
            }
        }

        private fun JSONObject.optNullableDouble(name: String): Double? {
            return if (this.has(name) && !this.isNull(name)) {
                this.getDouble(name)
            } else {
                null
            }
        }

        private fun createTracksGroupfromMpvJson(json: JSONObject): Tracks.Group {
            val trackType = MPVTrackType.entries.first { it.type == json.optString("type") }

            val baseFormat = Format.Builder()
                .setId(json.optInt("id"))
                .setLabel(json.optNullableString("title"))
                .setLanguage(json.optNullableString("lang"))
                .setSelectionFlags(if (json.optBoolean("default")) C.SELECTION_FLAG_DEFAULT else 0)
                .setCodecs(json.optNullableString("codec"))
                .build()

            val format = when (trackType) {
                MPVTrackType.VIDEO -> {
                    baseFormat.buildUpon()
                        .setSampleMimeType("video/${baseFormat.codecs}")
                        .setWidth(json.optInt("demux-w", Format.NO_VALUE))
                        .setHeight(json.optInt("demux-h", Format.NO_VALUE))
                        .setFrameRate((json.optNullableDouble("demux-fps") ?: Format.NO_VALUE).toFloat())
                        .build()
                }
                MPVTrackType.AUDIO -> {
                    baseFormat.buildUpon()
                        .setSampleMimeType("audio/${baseFormat.codecs}")
                        .setChannelCount(json.optInt("demux-channel-count", Format.NO_VALUE))
                        .setSampleRate(json.optInt("demux-samplerate", Format.NO_VALUE))
                        .build()
                }
                MPVTrackType.SUBTITLE -> {
                    baseFormat.buildUpon()
                        .setSampleMimeType("text/${baseFormat.codecs}")
                        .build()
                }
            }

            val trackGroup = TrackGroup(format)

            return Tracks.Group(
                trackGroup,
                false,
                IntArray(trackGroup.length) { C.FORMAT_HANDLED },
                BooleanArray(trackGroup.length) { json.optBoolean("selected") },
            )
        }

        private fun getTracks(trackList: String): Tracks {
            var tracks = Tracks.EMPTY
            val trackGroups = mutableListOf<Tracks.Group>()
            try {
                val currentTrackList = JSONArray(trackList)
                for (index in 0 until currentTrackList.length()) {
                    val tracksGroup = createTracksGroupfromMpvJson(currentTrackList.getJSONObject(index))
                    trackGroups.add(tracksGroup)
                }
                if (trackGroups.isNotEmpty()) {
                    tracks = Tracks(trackGroups)
                }
            } catch (_: JSONException) {
            }
            return tracks
        }
    }

    data class Property(
        val name: String,
        val format: Int,
    )
}