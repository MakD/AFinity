package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResultItem(
    @SerialName("id")
    val id: Int,
    @SerialName("mediaType")
    val mediaType: String,
    @SerialName("title")
    val title: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("originalTitle")
    val originalTitle: String? = null,
    @SerialName("originalName")
    val originalName: String? = null,
    @SerialName("overview")
    val overview: String? = null,
    @SerialName("posterPath")
    val posterPath: String? = null,
    @SerialName("backdropPath")
    val backdropPath: String? = null,
    @SerialName("releaseDate")
    val releaseDate: String? = null,
    @SerialName("firstAirDate")
    val firstAirDate: String? = null,
    @SerialName("voteAverage")
    val voteAverage: Double? = null,
    @SerialName("voteCount")
    val voteCount: Int? = null,
    @SerialName("popularity")
    val popularity: Double? = null,
    @SerialName("genreIds")
    val genreIds: List<Int>? = null,
    @SerialName("originalLanguage")
    val originalLanguage: String? = null,
    @SerialName("adult")
    val adult: Boolean? = null,
    @SerialName("mediaInfo")
    val mediaInfo: MediaInfo? = null
) {
    fun getDisplayTitle(): String = title ?: name ?: originalTitle ?: originalName ?: "Unknown"

    fun getMediaType(): MediaType? = try {
        MediaType.fromApiString(mediaType)
    } catch (e: Exception) {
        null
    }

    fun getPosterUrl(baseUrl: String = "https://image.tmdb.org/t/p/w500"): String? {
        return posterPath?.let { "$baseUrl$it" }
    }

    fun hasExistingRequest(): Boolean = mediaInfo?.status != null

    fun getMediaStatus(): MediaStatus? = mediaInfo?.status?.let { MediaStatus.fromValue(it) }

    fun getDisplayStatus(): MediaStatus? {
        val mediaStatus = getMediaStatus() ?: return null

        if (mediaStatus == MediaStatus.PENDING) {
            val hasApprovedRequest = mediaInfo?.requests?.any { it.status == 2 } == true
            if (hasApprovedRequest) {
                return MediaStatus.PROCESSING
            }
        }

        return mediaStatus
    }

    fun getRating(): String? {
        return voteAverage?.let {
            if (it > 0) String.format("%.1f", it) else null
        }
    }

    fun getReleaseYear(): String? {
        val date = releaseDate ?: firstAirDate
        return date?.take(4)
    }

    fun getBackdropUrl(baseUrl: String = "https://image.tmdb.org/t/p/w1280"): String? {
        return backdropPath?.let { "$baseUrl$it" }
    }
}