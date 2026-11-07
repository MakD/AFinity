package com.makd.afinity.data.models.media

import java.util.UUID

data class LibraryIndexEntry(
    val id: UUID,
    val name: String,
    val year: Int?,
    val isSeries: Boolean,
    val imdbId: String?,
    val tmdbCollectionId: String?,
)
