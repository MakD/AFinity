package com.makd.afinity.data.models

import com.makd.afinity.data.models.media.AfinityItem
import kotlinx.serialization.Serializable

@Serializable
enum class HomeSectionType {
    STARRING,
    DIRECTED_BY,
    WRITTEN_BY,
    BECAUSE_YOU_WATCHED,
    BECAUSE_YOU_LIKED,
    ACTOR_FROM_MOVIE,
    DIRECTOR_FROM_MOVIE,
    WRITER_FROM_MOVIE,
    WATCH_AGAIN,
    SPOTLIGHT_GENRE_MOVIE,
    SPOTLIGHT_GENRE_SHOW,
    SPOTLIGHT_STUDIO,
    SPOTLIGHT_BOXSET,
    GENRE_MOVIE,
    GENRE_SHOW,
    CRITICS_CHOICE,
    CUSTOM,
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
    val customSectionId: String? = null,
    val cardStyle: String? = null,
)

sealed interface HomeSectionContent {
    data class Person(val section: PersonSection) : HomeSectionContent

    data class Movie(val section: MovieSection) : HomeSectionContent

    data class PersonFromMovie(val section: PersonFromMovieSection) : HomeSectionContent

    data class Spotlight(val items: List<AfinityItem>) : HomeSectionContent

    data class Items(val items: List<AfinityItem>) : HomeSectionContent

    data class RankedItems(val items: List<AfinityItem>) : HomeSectionContent

    data object Empty : HomeSectionContent
}
