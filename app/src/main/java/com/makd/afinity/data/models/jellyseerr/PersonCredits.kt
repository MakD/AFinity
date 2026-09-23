package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val SELF_CHARACTER =
    Regex("""\b(self|himself|herself|themselves|host)\b""", RegexOption.IGNORE_CASE)

private val NON_FICTION_GENRE_IDS = setOf(10763, 10764, 10767)

private val CREATIVE_JOBS = setOf("Director", "Writer", "Screenplay", "Novel", "Creator")

private const val MIN_TV_EPISODES = 3

@Serializable
data class PersonCombinedCreditsResponse(
    @SerialName("id") val id: Int? = null,
    @SerialName("cast") val cast: List<SearchResultItem> = emptyList(),
    @SerialName("crew") val crew: List<SearchResultItem> = emptyList(),
) {
    fun meaningfulCredits(): List<SearchResultItem> {
        val roles = cast.filter { credit ->
            credit.isFiction() &&
                credit.isSubstantial() &&
                !SELF_CHARACTER.containsMatchIn(credit.character.orEmpty())
        }
        val creative = crew.filter { credit ->
            credit.isFiction() && credit.isSubstantial() && credit.job in CREATIVE_JOBS
        }
        return (roles + creative)
            .distinctBy { it.mediaType to it.id }
            .sortedByDescending { it.popularity ?: 0.0 }
    }

    private fun SearchResultItem.isFiction(): Boolean {
        if (getMediaType() == null) return false
        val genres = genreIds.orEmpty()
        if (genres.any { it in NON_FICTION_GENRE_IDS }) return false
        return !(mediaType == "tv" && genres.isEmpty())
    }

    private fun SearchResultItem.isSubstantial(): Boolean =
        mediaType != "tv" || job == "Creator" || (episodeCount ?: 0) >= MIN_TV_EPISODES
}

@Serializable
data class CollectionDetails(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("parts") val parts: List<SearchResultItem> = emptyList(),
)
