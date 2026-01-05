package com.makd.afinity.data.models.jellyseerr

enum class MediaType {
    MOVIE,
    TV;

    fun toApiString(): String = when (this) {
        MOVIE -> "movie"
        TV -> "tv"
    }

    companion object {
        fun fromApiString(value: String): MediaType = when (value.lowercase()) {
            "movie" -> MOVIE
            "tv" -> TV
            else -> throw IllegalArgumentException("Unknown media type: $value")
        }
    }
}