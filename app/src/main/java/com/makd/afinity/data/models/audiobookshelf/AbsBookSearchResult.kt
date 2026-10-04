package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.Serializable

@Serializable
data class AbsBookSearchResult(
    val asin: String? = null,
    val title: String? = null,
    val author: String? = null,
)
