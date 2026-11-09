package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchlistItem(
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("title") val title: String? = null,
    @SerialName("media") val media: MediaInfo? = null,
)

fun seerrArtworkKey(mediaType: String, tmdbId: Int): String = "${mediaType.lowercase()}_$tmdbId"

fun SearchResultItem.withArtwork(artwork: Map<String, SearchResultItem>): SearchResultItem {
    if (posterPath != null) return this
    val art = artwork[seerrArtworkKey(mediaType, id)] ?: return this
    return art.copy(mediaInfo = mediaInfo)
}

@Serializable
data class WatchlistResponse(
    @SerialName("page") val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 1,
    @SerialName("totalResults") val totalResults: Int = 0,
    @SerialName("results") val results: List<WatchlistItem> = emptyList(),
)

@Serializable
data class WatchlistAddBody(
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("title") val title: String,
)

data class WatchlistEvent(val tmdbId: Int, val mediaType: MediaType, val onWatchlist: Boolean)
