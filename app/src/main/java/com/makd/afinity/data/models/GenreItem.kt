package com.makd.afinity.data.models

enum class GenreType {
    MOVIE,
    SHOW,
}

data class GenreItem(val name: String, val type: GenreType)
