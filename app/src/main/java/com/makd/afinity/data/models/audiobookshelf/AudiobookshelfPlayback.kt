package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudioTrack(
    @SerialName("index") val index: Int,
    @SerialName("startOffset") val startOffset: Double,
    @SerialName("duration") val duration: Double,
    @SerialName("title") val title: String? = null,
    @SerialName("contentUrl") val contentUrl: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("codec") val codec: String? = null,
    @SerialName("metadata") val metadata: FileMetadata? = null,
)

@Serializable
data class BookChapter(
    @SerialName("id") val id: Int,
    @SerialName("start") val start: Double,
    @SerialName("end") val end: Double,
    @SerialName("title") val title: String,
)

@Serializable
data class MediaProgress(
    @SerialName("id") val id: String,
    @SerialName("libraryItemId") val libraryItemId: String,
    @SerialName("episodeId") val episodeId: String? = null,
    @SerialName("duration") val duration: Double,
    @SerialName("progress") val progress: Double,
    @SerialName("currentTime") val currentTime: Double,
    @SerialName("isFinished") val isFinished: Boolean,
    @SerialName("hideFromContinueListening") val hideFromContinueListening: Boolean? = null,
    @SerialName("ebookLocation") val ebookLocation: String? = null,
    @SerialName("ebookProgress") val ebookProgress: Double? = null,
    @SerialName("lastUpdate") val lastUpdate: Long,
    @SerialName("startedAt") val startedAt: Long,
    @SerialName("finishedAt") val finishedAt: Long? = null,
)

@Serializable
data class PlaybackSession(
    @SerialName("id") val id: String,
    @SerialName("userId") val userId: String,
    @SerialName("libraryId") val libraryId: String,
    @SerialName("libraryItemId") val libraryItemId: String,
    @SerialName("episodeId") val episodeId: String? = null,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("mediaMetadata") val mediaMetadata: MediaMetadata? = null,
    @SerialName("chapters") val chapters: List<BookChapter>? = null,
    @SerialName("displayTitle") val displayTitle: String? = null,
    @SerialName("displayAuthor") val displayAuthor: String? = null,
    @SerialName("coverPath") val coverPath: String? = null,
    @SerialName("duration") val duration: Double,
    @SerialName("playMethod") val playMethod: Int,
    @SerialName("mediaPlayer") val mediaPlayer: String? = null,
    @SerialName("deviceInfo") val deviceInfo: DeviceInfo? = null,
    @SerialName("serverVersion") val serverVersion: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("dayOfWeek") val dayOfWeek: String? = null,
    @SerialName("timeListening") val timeListening: Double? = null,
    @SerialName("startTime") val startTime: Double,
    @SerialName("currentTime") val currentTime: Double,
    @SerialName("startedAt") val startedAt: Long,
    @SerialName("updatedAt") val updatedAt: Long,
    @SerialName("audioTracks") val audioTracks: List<AudioTrack>? = null,
    @SerialName("videoTrack") val videoTrack: VideoTrack? = null,
    @SerialName("libraryItem") val libraryItem: LibraryItem? = null,
)

@Serializable
data class VideoTrack(
    @SerialName("index") val index: Int,
    @SerialName("startOffset") val startOffset: Double? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("contentUrl") val contentUrl: String? = null,
    @SerialName("mimeType") val mimeType: String? = null,
    @SerialName("codec") val codec: String? = null,
)

@Serializable
data class DeviceInfo(
    @SerialName("id") val id: String? = null,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("ipAddress") val ipAddress: String? = null,
    @SerialName("browserName") val browserName: String? = null,
    @SerialName("browserVersion") val browserVersion: String? = null,
    @SerialName("osName") val osName: String? = null,
    @SerialName("osVersion") val osVersion: String? = null,
    @SerialName("deviceType") val deviceType: String? = null,
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("sdkVersion") val sdkVersion: Int? = null,
    @SerialName("clientName") val clientName: String? = null,
    @SerialName("clientVersion") val clientVersion: String? = null,
)

@Serializable
data class PlaybackSessionRequest(
    @SerialName("deviceInfo") val deviceInfo: DeviceInfo,
    @SerialName("forceDirectPlay") val forceDirectPlay: Boolean? = null,
    @SerialName("forceTranscode") val forceTranscode: Boolean? = null,
    @SerialName("supportedMimeTypes") val supportedMimeTypes: List<String>? = null,
    @SerialName("mediaPlayer") val mediaPlayer: String? = null,
)

@Serializable
data class MediaProgressSyncData(
    @SerialName("currentTime") val currentTime: Double,
    @SerialName("timeListened") val timeListened: Double,
    @SerialName("duration") val duration: Double,
    @SerialName("progress") val progress: Double? = null,
    @SerialName("isFinished") val isFinished: Boolean? = null,
)

@Serializable
data class SyncResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("success") val success: Boolean? = null,
)

@Serializable
data class ProgressUpdateRequest(
    @SerialName("currentTime") val currentTime: Double? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("progress") val progress: Double? = null,
    @SerialName("isFinished") val isFinished: Boolean? = null,
)

@Serializable
data class BatchLocalSessionRequest(@SerialName("sessions") val sessions: List<LocalSessionData>)

@Serializable
data class LocalSessionData(
    @SerialName("id") val id: String,
    @SerialName("libraryItemId") val libraryItemId: String,
    @SerialName("episodeId") val episodeId: String? = null,
    @SerialName("currentTime") val currentTime: Double,
    @SerialName("timeListening") val timeListening: Double,
    @SerialName("duration") val duration: Double,
    @SerialName("progress") val progress: Double,
    @SerialName("startedAt") val startedAt: Long,
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class BatchSyncResponse(
    @SerialName("results") val results: List<BatchSyncResult>? = null,
    @SerialName("numSuccessful") val numSuccessful: Int? = null,
    @SerialName("numFailed") val numFailed: Int? = null,
)

@Serializable
data class BatchSyncResult(
    @SerialName("id") val id: String,
    @SerialName("success") val success: Boolean,
    @SerialName("progressSynced") val progressSynced: Boolean? = null,
    @SerialName("error") val error: String? = null,
)

enum class PlayMethod(val value: Int) {
    DIRECT_PLAY(0),
    DIRECT_STREAM(1),
    TRANSCODE(2),
    LOCAL(3);

    companion object {
        fun fromValue(value: Int): PlayMethod = entries.find { it.value == value } ?: DIRECT_PLAY
    }
}
