package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyseerrRequest(
    @SerialName("id") val id: Int,
    @SerialName("status") val status: Int,
    @SerialName("media") val media: MediaInfo,
    @SerialName("requestedBy") val requestedBy: RequestUser,
    @SerialName("modifiedBy") val modifiedBy: RequestUser? = null,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("updatedAt") val updatedAt: String,
    @SerialName("seasons") val seasons: List<SeasonRequest>? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
) {
    fun getRequestStatus(): RequestStatus = RequestStatus.fromValue(status)

    fun getMediaType(): MediaType? =
        try {
            MediaType.fromApiString(media.mediaType)
        } catch (e: Exception) {
            null
        }
}

@Serializable
data class SeasonRequest(
    @SerialName("id") val id: Int,
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("status") val status: Int,
)
