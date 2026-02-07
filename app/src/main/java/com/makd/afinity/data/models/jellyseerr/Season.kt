package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Season(
    @SerialName("id") val id: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    @SerialName("episodeCount") val episodeCount: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("airDate") val airDate: String? = null,
)
