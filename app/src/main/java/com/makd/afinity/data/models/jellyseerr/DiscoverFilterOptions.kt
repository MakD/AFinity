package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.Serializable

@Serializable
data class DiscoverFilterOptions(
    val genreIds: List<Int> = emptyList(),
    val releaseDateGte: String? = null,
    val releaseDateLte: String? = null,
    val runtimeGte: Int? = null,
    val runtimeLte: Int? = null,
    val voteAverageGte: Double? = null,
    val voteAverageLte: Double? = null,
    val voteCountGte: Int? = null,
    val voteCountLte: Int? = null,
    val tvStatus: List<Int> = emptyList(),
    val certification: List<String> = emptyList(),
    val watchProviderIds: List<Int> = emptyList(),
    val watchRegion: String? = null,
    val keywordIds: List<Int> = emptyList(),
    val excludeKeywordIds: List<Int> = emptyList(),
) {
    fun isEmpty(): Boolean =
        genreIds.isEmpty() &&
            releaseDateGte == null &&
            releaseDateLte == null &&
            runtimeGte == null &&
            runtimeLte == null &&
            voteAverageGte == null &&
            voteAverageLte == null &&
            voteCountGte == null &&
            voteCountLte == null &&
            tvStatus.isEmpty() &&
            certification.isEmpty() &&
            watchProviderIds.isEmpty() &&
            watchRegion == null &&
            keywordIds.isEmpty() &&
            excludeKeywordIds.isEmpty()
}