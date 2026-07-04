package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PersonCombinedCreditsResponse(
    @SerialName("id") val id: Int? = null,
    @SerialName("cast") val cast: List<SearchResultItem> = emptyList(),
    @SerialName("crew") val crew: List<SearchResultItem> = emptyList(),
)

@Serializable
data class CollectionDetails(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("parts") val parts: List<SearchResultItem> = emptyList(),
)