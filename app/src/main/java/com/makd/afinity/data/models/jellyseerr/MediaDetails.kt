package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaDetails(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("numberOfSeason") val numberOfSeason: Int? = null,
    @SerialName("numberOfEpisodes") val numberOfEpisodes: Int? = null,
    @SerialName("seasons") val seasons: List<Season>? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("lastAirDate") val lastAirDate: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("voteAverage") val voteAverage: Double? = null,
    @SerialName("voteCount") val voteCount: Int? = null,
    @SerialName("popularity") val popularity: Double? = null,
    @SerialName("inProduction") val inProduction: Boolean? = null,
    @SerialName("mediaInfo") val mediaInfo: MediaInfo? = null,
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    @SerialName("genres") val genres: List<Genre>? = null,
    @SerialName("credits") val credits: Credits? = null,
    @SerialName("releases") val releases: Releases? = null,
    @SerialName("ratingsCombined") val ratingsCombined: RatingsCombined? = null,
    @SerialName("externalIds") val externalIds: ExternalIds? = null,
    @SerialName("collection") val collection: MediaCollectionInfo? = null,
    @SerialName("originalTitle") val originalTitle: String? = null,
    @SerialName("originalName") val originalName: String? = null,
    @SerialName("budget") val budget: Long? = null,
    @SerialName("revenue") val revenue: Long? = null,
    @SerialName("type") val seriesType: String? = null,
    @SerialName("episodeRunTime") val episodeRunTime: List<Int>? = null,
    @SerialName("nextEpisodeToAir") val nextEpisodeToAir: EpisodeAirInfo? = null,
    @SerialName("networks") val networks: List<TvNetwork>? = null,
    @SerialName("productionCountries") val productionCountries: List<ProductionCountry>? = null,
    @SerialName("spokenLanguages") val spokenLanguages: List<SpokenLanguage>? = null,
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
        return voteAverage?.let { if (it > 0) String.format("%.1f", it) else null }
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

    fun getDirector(): String? {
        return credits?.crew?.firstOrNull { it.job == "Director" }?.name
    }

    fun getGenreNames(): List<String> {
        return genres?.map { it.name } ?: emptyList()
    }

    fun getCertification(countryCode: String = "US"): String? {
        return releases
            ?.results
            ?.firstOrNull { it.iso_3166_1 == countryCode }
            ?.release_dates
            ?.firstOrNull()
            ?.certification
    }
}

@Serializable
data class Credits(
    @SerialName("cast") val cast: List<CastMember>? = null,
    @SerialName("crew") val crew: List<CrewMember>? = null,
)

@Serializable
data class CastMember(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("character") val character: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
)

@Serializable
data class CrewMember(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("job") val job: String,
    @SerialName("department") val department: String? = null,
)

@Serializable data class Releases(@SerialName("results") val results: List<ReleaseResult>? = null)

@Serializable
data class ReleaseResult(
    @SerialName("iso_3166_1") val iso_3166_1: String,
    @SerialName("release_dates") val release_dates: List<ReleaseDate>? = null,
)

@Serializable
data class ReleaseDate(
    @SerialName("certification") val certification: String? = null,
    @SerialName("release_date") val release_date: String? = null,
    @SerialName("type") val type: Int? = null,
)

@Serializable
data class ExternalIds(
    @SerialName("tvdbId") val tvdbId: Int? = null,
    @SerialName("imdbId") val imdbId: String? = null,
)

@Serializable
data class MediaCollectionInfo(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
)

@Serializable
data class EpisodeAirInfo(
    @SerialName("airDate") val airDate: String? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    @SerialName("episodeNumber") val episodeNumber: Int? = null,
)

@Serializable
data class TvNetwork(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("logoPath") val logoPath: String? = null,
)

@Serializable
data class ProductionCountry(
    @SerialName("iso_3166_1") val iso_3166_1: String? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class SpokenLanguage(
    @SerialName("iso_639_1") val iso_639_1: String? = null,
    @SerialName("englishName") val englishName: String? = null,
    @SerialName("name") val name: String? = null,
)
