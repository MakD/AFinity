package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaInfo(
    @SerialName("id")
    val id: Int,
    @SerialName("mediaType")
    val mediaType: String,
    @SerialName("tmdbId")
    val tmdbId: Int?,
    @SerialName("tvdbId")
    val tvdbId: Int?,
    @SerialName("status")
    val status: Int?,
    @SerialName("mediaAddedAt")
    val mediaAddedAt: String? = null,
    @SerialName("seasons")
    val seasons: List<MediaInfoSeason>? = null,
    @SerialName("requests")
    val requests: List<JellyseerrRequest>? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null
) {
    fun getAvailableSeasons(): List<Int> {
        return seasons?.filter { season ->
            season.status == 5
        }?.mapNotNull { it.seasonNumber } ?: emptyList()
    }

    fun isSeasonAvailable(seasonNumber: Int): Boolean {
        return seasons?.any { it.seasonNumber == seasonNumber && it.status == 5 } ?: false
    }
}

@Serializable
data class MediaInfoSeason(
    @SerialName("id")
    val id: Int? = null,
    @SerialName("seasonNumber")
    val seasonNumber: Int? = null,
    @SerialName("status")
    val status: Int? = null
)