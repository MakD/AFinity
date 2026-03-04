package com.makd.afinity.data.models.tmdb

import kotlinx.serialization.Serializable

@Serializable
data class TmdbReviewResponse(
    val id: Int? = null,
    val page: Int? = null,
    val results: List<TmdbReview> = emptyList(),
    val total_pages: Int? = null,
    val total_results: Int? = null,
)

@Serializable
data class TmdbReview(
    val id: String,
    val author: String,
    val content: String,
    val created_at: String? = null,
    val url: String? = null,
    val author_details: TmdbAuthorDetails? = null,
)

@Serializable
data class TmdbAuthorDetails(
    val name: String? = null,
    val username: String? = null,
    val avatar_path: String? = null,
    val rating: Double? = null,
)
