package com.makd.afinity.data.models.tmdb

import com.makd.afinity.data.models.jellyseerr.PersonCombinedCreditsResponse
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import kotlinx.serialization.Serializable

@Serializable
data class TmdbCollectionResponse(
    val id: Int,
    val name: String? = null,
    val parts: List<TmdbMediaItem> = emptyList(),
)

@Serializable
data class TmdbCombinedCreditsResponse(
    val id: Int? = null,
    val cast: List<TmdbMediaItem> = emptyList(),
    val crew: List<TmdbMediaItem> = emptyList(),
) {
    fun toPersonCombinedCredits(): PersonCombinedCreditsResponse =
        PersonCombinedCreditsResponse(
            id = id,
            cast = cast.map { it.toSearchResultItem() },
            crew = crew.map { it.toSearchResultItem() },
        )
}

@Serializable
data class TmdbMediaItem(
    val id: Int,
    val media_type: String? = null,
    val title: String? = null,
    val name: String? = null,
    val original_title: String? = null,
    val original_name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val popularity: Double? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
    val genre_ids: List<Int>? = null,
    val character: String? = null,
    val job: String? = null,
    val episode_count: Int? = null,
) {
    fun toSearchResultItem(): SearchResultItem =
        SearchResultItem(
            id = id,
            mediaType = media_type ?: "movie",
            title = title,
            name = name,
            originalTitle = original_title,
            originalName = original_name,
            overview = overview,
            posterPath = poster_path,
            backdropPath = backdrop_path,
            releaseDate = release_date?.ifBlank { null },
            firstAirDate = first_air_date?.ifBlank { null },
            voteAverage = vote_average,
            voteCount = vote_count,
            popularity = popularity,
            genreIds = genre_ids,
            character = character,
            job = job,
            episodeCount = episode_count,
        )
}