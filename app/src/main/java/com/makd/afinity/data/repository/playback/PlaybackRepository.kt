package com.makd.afinity.data.repository.playback

import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import java.util.UUID

interface PlaybackRepository {

    suspend fun getPlaybackInfo(
        itemId: UUID,
        maxStreamingBitrate: Int? = null,
        maxAudioChannels: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        mediaSourceId: String? = null,
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

    suspend fun getMediaSources(
        itemId: UUID,
        maxStreamingBitrate: Int? = null,
        maxAudioChannels: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        mediaSourceId: String? = null,
    ): List<org.jellyfin.sdk.model.api.MediaSourceInfo>

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
    ): Boolean

    suspend fun pingSession(sessionId: String): Boolean

    suspend fun getActiveSession(): String?

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
