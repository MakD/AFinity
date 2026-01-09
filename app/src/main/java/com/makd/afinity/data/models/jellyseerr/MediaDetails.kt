package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaDetails(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("overview")
    val overview: String? = null,
    @SerialName("posterPath")
    val posterPath: String? = null,
    @SerialName("backdropPath")
    val backdropPath: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("numberOfSeason")
    val numberOfSeason: Int? = null,
    @SerialName("numberOfEpisodes")
    val numberOfEpisodes: Int? = null,
    @SerialName("seasons")
    val seasons: List<Season>? = null,
    @SerialName("firstAirDate")
    val firstAirDate: String? = null,
    @SerialName("lastAirDate")
    val lastAirDate: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("voteAverage")
    val voteAverage: Double? = null,
    @SerialName("voteCount")
    val voteCount: Int? = null,
    @SerialName("popularity")
    val popularity: Double? = null,
    @SerialName("inProduction")
    val inProduction: Boolean? = null,
    @SerialName("mediaInfo")
    val mediaInfo: MediaInfo? = null
) {
    fun getSeasonCount(): Int {
        return seasons?.filter { (it.seasonNumber ?: 0) > 0 }?.size ?: numberOfSeason ?: 0
    }

    fun getPosterUrl(baseUrl: String = "https://image.tmdb.org/t/p/w500"): String? {
        return posterPath?.let { "$baseUrl$it" }
    }

    fun getBackdropUrl(baseUrl: String = "https://image.tmdb.org/t/p/w1280"): String? {
        return backdropPath?.let { "$baseUrl$it" }
    }

    fun getRating(): String? {
        return voteAverage?.let {
            if (it > 0) String.format("%.1f", it) else null
        }
    }

    fun getYear(): String? {
        return firstAirDate?.take(4)
    }

    fun getStatusDisplay(): String {
        return when (status) {
            "Returning Series" -> "Ongoing"
            "Ended" -> "Ended"
            "Canceled" -> "Canceled"
            else -> status ?: "Unknown"
        }
    }
}