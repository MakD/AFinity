package com.makd.afinity.data.models

import androidx.core.net.toUri
import com.makd.afinity.data.models.media.AfinityItem
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
    val appearanceCount: Int,
    val imageTag: String? = null,
)

data class PersonWithCount(val person: AfinityPerson, val appearanceCount: Int) {
    fun toCached(): CachedPersonWithCount {
        return CachedPersonWithCount(
            personId = person.id.toString(),
            personName = person.name,
            personType = person.type.name,
            personRole = person.role,
            appearanceCount = appearanceCount,
            imageTag = person.image.uri?.getQueryParameter("tag"),
        )
    }

    companion object {
        fun fromCached(cached: CachedPersonWithCount, baseUrl: String): PersonWithCount {
            val imageUri = cached.imageTag?.let { tag ->
                baseUrl.toUri().buildUpon()
                    .appendEncodedPath("Items/${cached.personId}/Images/Primary")
                    .appendQueryParameter("tag", tag)
                    .build()
            }
            return PersonWithCount(
                person =
                    AfinityPerson(
                        id = UUID.fromString(cached.personId),
                        name = cached.personName,
                        type = PersonKind.valueOf(cached.personType),
                        role = cached.personRole,
                        image = AfinityPersonImage(uri = imageUri, blurHash = null),
                    ),
                appearanceCount = cached.appearanceCount,
            )
        }
    }
}

enum class PersonSectionType {
    STARRING,
    DIRECTED_BY,
    WRITTEN_BY;

    fun toPersonKind(): PersonKind =
        when (this) {
            STARRING -> PersonKind.ACTOR
            DIRECTED_BY -> PersonKind.DIRECTOR
            WRITTEN_BY -> PersonKind.WRITER
        }
}

data class PersonSection(
    val person: AfinityPerson,
    val appearanceCount: Int,
    val items: List<AfinityItem>,
    val sectionType: PersonSectionType,
)

enum class MovieSectionType {
    BECAUSE_YOU_WATCHED,
    STARRING_ACTOR_FROM,
}

data class MovieSection(
    val referenceMovie: AfinityMovie,
    val recommendedItems: List<AfinityMovie>,
    val sectionType: MovieSectionType,
)

data class PersonFromMovieSection(
    val person: AfinityPerson,
    val referenceMovie: AfinityMovie,
    val items: List<AfinityItem>,
)
