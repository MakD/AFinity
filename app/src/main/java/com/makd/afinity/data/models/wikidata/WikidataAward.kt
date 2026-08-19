package com.makd.afinity.data.models.wikidata

import kotlinx.serialization.Serializable

enum class WikidataSubjectType(val value: String) {
    MOVIE("movie"),
    TV("tv"),
    PERSON("person");

    companion object {
        fun fromValue(value: String): WikidataSubjectType? =
            entries.firstOrNull { it.value == value }
    }
}

@Serializable
enum class AwardResult {
    WON,
    NOMINATED,
}

@Serializable
data class WikidataAward(
    val name: String,
    val result: AwardResult,
    val year: Int? = null,
    val recipients: List<String> = emptyList(),
    val works: List<String> = emptyList(),
)

@Serializable
data class WikidataAwards(
    val awards: List<WikidataAward> = emptyList(),
    val confirmed: Boolean = false,
) {
    val wins: Int
        get() = awards.count { it.result == AwardResult.WON }

    val nominations: Int
        get() = awards.count { it.result == AwardResult.NOMINATED }

    val found: Boolean
        get() = awards.isNotEmpty()

    companion object {
        val UNCONFIRMED = WikidataAwards(confirmed = false)
    }
}