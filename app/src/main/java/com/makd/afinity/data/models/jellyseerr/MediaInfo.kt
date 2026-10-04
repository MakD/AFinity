package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val SEASON_PRESENT_STATUSES =
    setOf(
        MediaStatus.AVAILABLE.value,
        MediaStatus.PARTIALLY_AVAILABLE.value,
        MediaStatus.PROCESSING.value,
    )

private fun JellyseerrRequest.isActive(): Boolean =
    status != RequestStatus.DECLINED.value && status != RequestStatus.COMPLETED.value

@Serializable
data class MediaInfo(
    @SerialName("id") val id: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("tmdbId") val tmdbId: Int?,
    @SerialName("tvdbId") val tvdbId: Int?,
    @SerialName("status") val status: Int?,
    @SerialName("status4k") val status4k: Int? = null,
    @SerialName("mediaAddedAt") val mediaAddedAt: String? = null,
    @SerialName("seasons") val seasons: List<MediaInfoSeason>? = null,
    @SerialName("requests") val requests: List<JellyseerrRequest>? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("jellyfinMediaId") val jellyfinMediaId: String? = null,
    @SerialName("jellyfinMediaId4k") val jellyfinMediaId4k: String? = null,
) {
    fun getAvailableSeasons(): List<Int> {
        return seasons?.filter { season -> season.status == 5 }?.mapNotNull { it.seasonNumber }
            ?: emptyList()
    }

    fun isSeasonAvailable(seasonNumber: Int): Boolean {
        return seasons?.any { it.seasonNumber == seasonNumber && it.status == 5 } ?: false
    }

    fun getDisplayTitle(): String = title ?: name ?: "Unknown"

    fun laneStatus(is4k: Boolean): MediaStatus =
        MediaStatus.fromValue((if (is4k) status4k else status) ?: MediaStatus.UNKNOWN.value)

    fun hasActiveRequest(is4k: Boolean): Boolean =
        requests.orEmpty().any { it.is4k == is4k && it.isActive() }

    fun canRequestLane(is4k: Boolean): Boolean =
        when (laneStatus(is4k)) {
            MediaStatus.UNKNOWN -> true
            MediaStatus.DELETED -> !hasActiveRequest(is4k)
            else -> false
        }

    fun blockedSeasons(is4k: Boolean): List<Int> {
        val requested =
            requests
                .orEmpty()
                .filter { it.is4k == is4k && it.isActive() }
                .flatMap { request -> request.seasons.orEmpty().map { it.seasonNumber } }
        val present =
            seasons
                .orEmpty()
                .filter { season ->
                    (if (is4k) season.status4k else season.status) in SEASON_PRESENT_STATUSES
                }
                .mapNotNull { it.seasonNumber }
        return (requested + present).distinct()
    }

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

    fun isFullyAvailable(): Boolean {
        return status == 5 && jellyfinMediaId != null
    }

    fun isPartiallyAvailable(): Boolean {
        return status == 4
    }

    fun getJellyfinItemId(): String? {
        return jellyfinMediaId?.let { id ->
            if (id.length == 32 && !id.contains("-")) {
                "${id.take(8)}-${id.substring(8, 12)}-${id.substring(12, 16)}-${id.substring(16, 20)}-${id.substring(20)}"
            } else {
                id
            }
        }
    }
}

@Serializable
data class MediaInfoSeason(
    @SerialName("id") val id: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("status4k") val status4k: Int? = null,
)
