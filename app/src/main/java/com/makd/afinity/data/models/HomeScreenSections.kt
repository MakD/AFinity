package com.makd.afinity.data.models

import android.net.Uri
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

@Serializable
data class CachedPersonWithCount(
    val personId: String,
    val personName: String,
    val personType: String,
    val personRole: String,
    val appearanceCount: Int
)

data class PersonWithCount(
    val person: AfinityPerson,
    val appearanceCount: Int
) {
    fun toCached(): CachedPersonWithCount {
        return CachedPersonWithCount(
            personId = person.id.toString(),
            personName = person.name,
            personType = person.type.name,
            personRole = person.role,
            appearanceCount = appearanceCount
        )
    }

    companion object {
        fun fromCached(cached: CachedPersonWithCount): PersonWithCount {
            return PersonWithCount(
                person = AfinityPerson(
                    id = UUID.fromString(cached.personId),
                    name = cached.personName,
                    type = PersonKind.valueOf(cached.personType),
                    role = cached.personRole,
                    image = AfinityPersonImage(uri = null, blurHash = null)
                ),
                appearanceCount = cached.appearanceCount
            )
        }
    }
}

enum class PersonSectionType {
    STARRING,
    DIRECTED_BY,
    WRITTEN_BY;

    fun toPersonKind(): PersonKind = when (this) {
        STARRING -> PersonKind.ACTOR
        DIRECTED_BY -> PersonKind.DIRECTOR
        WRITTEN_BY -> PersonKind.WRITER
    }

    fun toDisplayTitle(personName: String): String = when (this) {
        STARRING -> "Starring $personName"
        DIRECTED_BY -> "Directed by $personName"
        WRITTEN_BY -> "Written by $personName"
    }
}

data class PersonSection(
    val person: AfinityPerson,
    val appearanceCount: Int,
    val items: List<AfinityMovie>,
    val sectionType: PersonSectionType
)

enum class MovieSectionType {
    BECAUSE_YOU_WATCHED,
    STARRING_ACTOR_FROM;

    fun toDisplayTitle(referenceName: String, personName: String? = null): String = when (this) {
        BECAUSE_YOU_WATCHED -> "Because you watched $referenceName"
        STARRING_ACTOR_FROM -> "Starring $personName because you watched $referenceName"
    }
}

data class MovieSection(
    val referenceMovie: AfinityMovie,
    val recommendedItems: List<AfinityMovie>,
    val sectionType: MovieSectionType
)

data class PersonFromMovieSection(
    val person: AfinityPerson,
    val referenceMovie: AfinityMovie,
    val items: List<AfinityMovie>
) {
    fun toDisplayTitle(): String {
        return "Starring ${person.name} because you watched ${referenceMovie.name}"
    }
}