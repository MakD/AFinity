package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.Serializable

@Serializable
data class FilterDataResponse(
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val narrators: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)
