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
    val updatedAt: String? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("posterPath")
    val posterPath: String? = null,
    @SerialName("backdropPath")
    val backdropPath: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("firstAirDate")
    val firstAirDate: String? = null
) {
    fun getAvailableSeasons(): List<Int> {
        return seasons?.filter { season ->
            season.status == 5
        }?.mapNotNull { it.seasonNumber } ?: emptyList()
    }

    fun isSeasonAvailable(seasonNumber: Int): Boolean {
        return seasons?.any { it.seasonNumber == seasonNumber && it.status == 5 } ?: false
    }

    fun getDisplayTitle(): String = title ?: name ?: "Unknown"

    fun getReleaseYear(): String? {
        val date = releaseDate ?: firstAirDate
        return date?.take(4)
    }

    fun getPosterUrl(baseUrl: String = "https://image.tmdb.org/t/p/w500"): String? {
        return posterPath?.let { "$baseUrl$it" }
    }

    fun getBackdropUrl(baseUrl: String = "https://image.tmdb.org/t/p/w1280"): String? {
        return backdropPath?.let { "$baseUrl$it" }
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