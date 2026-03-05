package com.makd.afinity.data.models.mdblist

import kotlinx.serialization.Serializable

@Serializable
data class MdbListApiResult(
    val title: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val ratings: List<MdbListRating> = emptyList(),
)

@Serializable
data class MdbListRating(
    val source: String,
    val value: Double? = null,
    val score: Int? = null,
    val votes: Long? = null,
    val popular: Boolean? = null,
    val url: String? = null,
)
