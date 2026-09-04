package com.makd.afinity.data.repository.playback

import com.makd.afinity.data.models.player.StreamDecision
import com.makd.afinity.data.models.player.VideoQuality
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.TranscodingInfo
import java.util.UUID

interface PlaybackRepository {

    suspend fun getPlaybackInfo(
        itemId: UUID,
        quality: VideoQuality = VideoQuality.ORIGINAL,
        maxAudioChannels: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        mediaSourceId: String? = null,
        startTimeTicks: Long = 0L,
        enableDirectPlay: Boolean = true,
        allowTranscoding: Boolean = true,
    ): PlaybackInfoResponse?

    suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        videoStreamIndex: Int? = null,
        maxStreamingBitrate: Int? = null,
        startTimeTicks: Long? = null,
        playSessionId: String? = null,
        tag: String? = null,
    ): String?

    suspend fun resolveAudioStream(
        itemId: UUID,
        playSessionId: String? = null,
        maxStreamingBitrate: Int? = null,
    ): StreamDecision?

    suspend fun resolveStream(
        itemId: UUID,
        source: MediaSourceInfo,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startTimeTicks: Long? = null,
        playSessionId: String? = null,
    ): StreamDecision?

    suspend fun getMediaSources(
        itemId: UUID,
        quality: VideoQuality = VideoQuality.ORIGINAL,
        maxAudioChannels: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        mediaSourceId: String? = null,
    ): List<MediaSourceInfo>

    suspend fun reportPlaybackStart(
        itemId: UUID,
        sessionId: String,
        mediaSourceId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        playMethod: String = "DirectPlay",
        liveStreamId: String? = null,
        canSeek: Boolean = true,
    ): Boolean

    suspend fun reportPlaybackProgress(
        itemId: UUID,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean = false,
        isMuted: Boolean = false,
        volumeLevel: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        playMethod: String = "DirectPlay",
        liveStreamId: String? = null,
        repeatMode: String = "RepeatNone",
    ): Boolean

    suspend fun reportPlaybackStop(
        itemId: UUID,
        sessionId: String,
        positionTicks: Long,
        mediaSourceId: String,
        liveStreamId: String? = null,
        nextMediaType: String? = null,
        playlistItemId: String? = null,
        runtimeTicks: Long = 0L,
        isEnded: Boolean = false,
    ): Boolean

    suspend fun savePlaybackStopOffline(
        itemId: UUID,
        positionTicks: Long,
        runtimeTicks: Long = 0L,
        isEnded: Boolean = false,
    )

    suspend fun pingSession(sessionId: String): Boolean

    suspend fun getActiveSession(): String?

    suspend fun getTranscodingInfo(): TranscodingInfo?

    suspend fun endSession(sessionId: String): Boolean

    suspend fun stopTranscoding(deviceId: String): Boolean

    suspend fun getTranscodingJob(deviceId: String): Any?

    suspend fun getBitrateTestBytes(size: Int): ByteArray?

    suspend fun detectMaxBitrate(): Int?

    suspend fun getPlaybackInfoForCast(
        itemId: UUID,
        deviceProfile: DeviceProfile,
        maxStreamingBitrate: Int?,
        maxAudioChannels: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        mediaSourceId: String?,
        startTimeTicks: Long = 0L,
    ): PlaybackInfoResponse?
}
