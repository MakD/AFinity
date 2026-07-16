package com.makd.afinity.data.models.tmdb

import kotlinx.serialization.Serializable

@Serializable
data class TmdbImagesResponse(
    val id: Int? = null,
    val logos: List<TmdbImage> = emptyList(),
)

@Serializable
data class TmdbImage(
    val file_path: String? = null,
    val iso_639_1: String? = null,
    val vote_average: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
)