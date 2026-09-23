package com.makd.afinity.data.models.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbDetailsResponse(
    val id: Int? = null,
    val reviews: TmdbReviewResponse? = null,
    @SerialName("watch/providers") val watchProviders: TmdbWatchProvidersResponse? = null,
)

@Serializable
data class TmdbWatchProvidersResponse(val results: Map<String, TmdbRegionProviders> = emptyMap()) {
    fun forRegion(region: String): TmdbRegionProviders? =
        results[region]?.copy(region = region)?.takeIf { it.hasAny }
}

@Serializable
data class TmdbRegionProviders(
    val region: String = "",
    val link: String? = null,
    val flatrate: List<TmdbWatchProvider> = emptyList(),
    val free: List<TmdbWatchProvider> = emptyList(),
    val ads: List<TmdbWatchProvider> = emptyList(),
    val rent: List<TmdbWatchProvider> = emptyList(),
    val buy: List<TmdbWatchProvider> = emptyList(),
) {
    val hasAny: Boolean
        get() =
            flatrate.isNotEmpty() ||
                free.isNotEmpty() ||
                ads.isNotEmpty() ||
                rent.isNotEmpty() ||
                buy.isNotEmpty()
}

@Serializable
data class TmdbWatchProvider(
    val provider_id: Int,
    val provider_name: String,
    val logo_path: String? = null,
    val display_priority: Int? = null,
) {
    fun logoUrl(): String? = logo_path?.let { "https://image.tmdb.org/t/p/w154$it" }
}
