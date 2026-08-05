package com.makd.afinity.data.models.audiobookshelf

data class PersonalizedSection(
    val id: String,
    val label: String,
    val items: List<LibraryItem>,
    val series: List<AudiobookshelfSeries> = emptyList(),
)
