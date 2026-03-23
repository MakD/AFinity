package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ListeningStats(
    @SerialName("totalTime") val totalTime: Double = 0.0,
    @SerialName("today") val today: Double? = null,
    @SerialName("days") val days: Map<String, JsonElement>? = null,
    @SerialName("dayListeningMap") val dayListeningMap: Map<String, JsonElement>? = null,
    @SerialName("recentSessions") val recentSessions: List<ListeningSession>? = null,
)

@Serializable
data class ListeningSession(
    @SerialName("id") val id: String? = null,
    @SerialName("libraryItemId") val libraryItemId: String? = null,
    @SerialName("displayTitle") val displayTitle: String? = null,
    @SerialName("displayAuthor") val displayAuthor: String? = null,
    @SerialName("timeListening") val timeListening: Double = 0.0,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("mediaMetadata") val mediaMetadata: MediaMetadataCompact? = null,
    @SerialName("deviceInfo") val deviceInfo: DeviceInfoCompact? = null,
)

@Serializable
data class MediaMetadataCompact(
    @SerialName("title") val title: String? = null,
    @SerialName("authorName") val authorName: String? = null,
)

@Serializable
data class DeviceInfoCompact(
    @SerialName("clientName") val clientName: String? = null,
    @SerialName("deviceName") val deviceName: String? = null,
)

@Serializable
data class ListeningSessionsResponse(
    @SerialName("sessions") val sessions: List<ListeningSession> = emptyList(),
    @SerialName("total") val total: Int = 0,
    @SerialName("numPages") val numPages: Int = 0,
    @SerialName("page") val page: Int = 0,
)