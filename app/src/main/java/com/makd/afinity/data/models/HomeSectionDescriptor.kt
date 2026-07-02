package com.makd.afinity.data.models

import com.makd.afinity.data.models.media.AfinityItem
import kotlinx.serialization.Serializable

@Serializable
enum class HomeSectionType {
    STARRING,
    DIRECTED_BY,
    WRITTEN_BY,
    BECAUSE_YOU_WATCHED,
    ACTOR_FROM_MOVIE,
    SPOTLIGHT_GENRE_MOVIE,
    SPOTLIGHT_GENRE_SHOW,
    SPOTLIGHT_STUDIO,
    SPOTLIGHT_BOXSET,
    GENRE_MOVIE,
    GENRE_SHOW,
}

@Serializable
data class HomeSectionDescriptor(
    val key: String,
    val type: HomeSectionType,
    val title: String,
    val person: CachedPersonWithCount? = null,
    val referenceMovieJson: String? = null,
    val genreName: String? = null,
    val studioName: String? = null,
    val boxSetId: String? = null,
)

sealed interface HomeSectionContent {
    data class Person(val section: PersonSection) : HomeSectionContent

    data class Movie(val section: MovieSection) : HomeSectionContent

    data class PersonFromMovie(val section: PersonFromMovieSection) : HomeSectionContent

    data class Spotlight(val items: List<AfinityItem>) : HomeSectionContent

    data object Empty : HomeSectionContent
}
