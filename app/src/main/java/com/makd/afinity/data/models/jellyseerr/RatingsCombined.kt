package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RatingsCombined(
    @SerialName("rt") val rt: RottenTomatoesRating? = null,
    @SerialName("imdb") val imdb: ImdbRating? = null,
)

@Serializable
data class RottenTomatoesRating(
    @SerialName("title") val title: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("criticsRating") val criticsRating: String? = null,
    @SerialName("criticsScore") val criticsScore: Int? = null,
    @SerialName("audienceRating") val audienceRating: String? = null,
    @SerialName("audienceScore") val audienceScore: Int? = null,
    @SerialName("year") val year: Int? = null,
)

@Serializable
data class ImdbRating(
    @SerialName("title") val title: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("criticsScore") val criticsScore: Double? = null,
)
