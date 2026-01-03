package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyseerrSearchResult(
    @SerialName("page")
    val page: Int = 1,
    @SerialName("total_pages")
    val totalPages: Int = 1,
    @SerialName("total_results")
    val totalResults: Int = 0,
    @SerialName("results")
    val results: List<SearchResultItem> = emptyList()
)