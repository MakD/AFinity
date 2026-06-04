package com.makd.afinity.data.models.audiobookshelf

import kotlinx.serialization.Serializable

@Serializable
data class AbsCoverSearchResult(
    val id: String? = null,
    val asin: String? = null,
    val title: String? = null,
    val author: String? = null,
    val cover: String? = null,
)