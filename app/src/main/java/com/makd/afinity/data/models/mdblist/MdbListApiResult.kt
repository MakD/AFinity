package com.makd.afinity.data.models.mdblist

import kotlinx.serialization.Serializable

@Serializable
data class MdbListApiResult(
    val title: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val ratings: List<MdbListRating> = emptyList(),
    val keywords: List<MdbListKeyword> = emptyList(),
    val keyword: List<MdbListKeyword> = emptyList(),
)

@Serializable
data class MdbListRating(
    val source: String,
    val value: Double? = null,
    val score: Double? = null,
    val votes: Long? = null,
    val popular: Int? = null,
    val url: String? = null,
)

@Serializable
data class MdbListKeyword(
    val id: Int? = null,
    val name: String,
)

@Serializable
data class MdbListRatingBadges(
    val certifiedFresh: Boolean = false,
    val verifiedHot: Boolean = false,
) {
    val hasAny: Boolean
        get() = certifiedFresh || verifiedHot
}

data class MdbListRatingsResult(
    val ratings: List<MdbListRating> = emptyList(),
    val badges: MdbListRatingBadges = MdbListRatingBadges(),
)
