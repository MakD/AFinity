package com.makd.afinity.data.database

import android.net.Uri
import androidx.room.TypeConverter
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class TypeConverters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(uuidString: String?): UUID? = uuidString?.let { UUID.fromString(it) }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? =
        dateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    @TypeConverter
    fun toLocalDateTime(dateTimeString: String?): LocalDateTime? =
        dateTimeString?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

    @TypeConverter
    fun fromChapterList(chapters: List<AfinityChapter>?): String? =
        chapters?.let { json.encodeToString(it) }

    @TypeConverter
    fun toChapterList(chaptersString: String?): List<AfinityChapter>? =
        chaptersString?.let {
            try {
                json.decodeFromString<List<AfinityChapter>>(it)
            } catch (e: Exception) {
                null
            }
        }

    @TypeConverter
    fun fromStringList(strings: List<String>?): String? =
        strings?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringList(stringsString: String?): List<String>? =
        stringsString?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                null
            }
        }

    @TypeConverter
    fun fromSourceType(sourceType: AfinitySourceType?): String? = sourceType?.name

    @TypeConverter
    fun toSourceType(sourceTypeName: String?): AfinitySourceType? =
        sourceTypeName?.let {
            try {
                AfinitySourceType.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }

    @TypeConverter
    fun fromSegmentType(segmentType: AfinitySegmentType?): String? = segmentType?.name

    @TypeConverter
    fun toSegmentType(segmentTypeName: String?): AfinitySegmentType? =
        segmentTypeName?.let {
            try {
                AfinitySegmentType.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }

    @TypeConverter
    fun fromMediaStreamType(mediaStreamType: MediaStreamType?): String? = mediaStreamType?.name

    @TypeConverter
    fun toMediaStreamType(mediaStreamTypeName: String?): MediaStreamType? =
        mediaStreamTypeName?.let {
            try {
                MediaStreamType.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }

    @TypeConverter
    fun fromUri(uri: Uri?): String? = uri?.toString()

    @TypeConverter
    fun toUri(uriString: String?): Uri? = uriString?.let { Uri.parse(it) }

    @TypeConverter
    fun fromAfinityImages(images: AfinityImages?): String? {
        if (images == null) return null
        val serializable = SerializableAfinityImages(
            primary = images.primary?.toString(),
            backdrop = images.backdrop?.toString(),
            thumb = images.thumb?.toString(),
            logo = images.logo?.toString(),
            showPrimary = images.showPrimary?.toString(),
            showBackdrop = images.showBackdrop?.toString(),
            showLogo = images.showLogo?.toString(),
            primaryImageBlurHash = images.primaryImageBlurHash,
            backdropImageBlurHash = images.backdropImageBlurHash,
            thumbImageBlurHash = images.thumbImageBlurHash,
            logoImageBlurHash = images.logoImageBlurHash,
            showPrimaryImageBlurHash = images.showPrimaryImageBlurHash,
            showBackdropImageBlurHash = images.showBackdropImageBlurHash,
            showLogoImageBlurHash = images.showLogoImageBlurHash
        )
        return json.encodeToString(serializable)
    }

    @TypeConverter
    fun toAfinityImages(imagesString: String?): AfinityImages? {
        if (imagesString == null) return null
        return try {
            val serializable = json.decodeFromString<SerializableAfinityImages>(imagesString)
            AfinityImages(
                primary = serializable.primary?.let { Uri.parse(it) },
                backdrop = serializable.backdrop?.let { Uri.parse(it) },
                thumb = serializable.thumb?.let { Uri.parse(it) },
                logo = serializable.logo?.let { Uri.parse(it) },
                showPrimary = serializable.showPrimary?.let { Uri.parse(it) },
                showBackdrop = serializable.showBackdrop?.let { Uri.parse(it) },
                showLogo = serializable.showLogo?.let { Uri.parse(it) },
                primaryImageBlurHash = serializable.primaryImageBlurHash,
                backdropImageBlurHash = serializable.backdropImageBlurHash,
                thumbImageBlurHash = serializable.thumbImageBlurHash,
                logoImageBlurHash = serializable.logoImageBlurHash,
                showPrimaryImageBlurHash = serializable.showPrimaryImageBlurHash,
                showBackdropImageBlurHash = serializable.showBackdropImageBlurHash,
                showLogoImageBlurHash = serializable.showLogoImageBlurHash
            )
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromAfinityPersonList(people: List<AfinityPerson>?): String? {
        if (people == null) return null
        val serializable = people.map { person ->
            SerializableAfinityPerson(
                id = person.id.toString(),
                name = person.name,
                type = person.type.name,
                role = person.role,
                imageUri = person.image.uri?.toString(),
                imageBlurHash = person.image.blurHash
            )
        }
        return json.encodeToString(serializable)
    }

    @TypeConverter
    fun toAfinityPersonList(peopleString: String?): List<AfinityPerson>? {
        if (peopleString == null) return null
        return try {
            val serializable = json.decodeFromString<List<SerializableAfinityPerson>>(peopleString)
            serializable.map { person ->
                AfinityPerson(
                    id = UUID.fromString(person.id),
                    name = person.name,
                    type = PersonKind.valueOf(person.type),
                    role = person.role,
                    image = AfinityPersonImage(
                        uri = person.imageUri?.let { Uri.parse(it) },
                        blurHash = person.imageBlurHash
                    )
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromAfinityMovie(movie: AfinityMovie?): String? {
        if (movie == null) return null
        val serializable = SerializableAfinityMovie(
            id = movie.id.toString(),
            name = movie.name,
            originalTitle = movie.originalTitle,
            overview = movie.overview,
            played = movie.played,
            favorite = movie.favorite,
            liked = movie.liked,
            canPlay = movie.canPlay,
            canDownload = movie.canDownload,
            runtimeTicks = movie.runtimeTicks,
            playbackPositionTicks = movie.playbackPositionTicks,
            premiereDate = movie.premiereDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            dateCreated = movie.dateCreated?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            genres = movie.genres,
            communityRating = movie.communityRating,
            officialRating = movie.officialRating,
            criticRating = movie.criticRating,
            taglines = movie.taglines,
            status = movie.status,
            productionYear = movie.productionYear,
            endDate = movie.endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            trailer = movie.trailer,
            tagline = movie.tagline,
            unplayedItemCount = movie.unplayedItemCount,
            imagesJson = fromAfinityImages(movie.images),
            peopleJson = fromAfinityPersonList(movie.people)
        )
        return json.encodeToString(serializable)
    }

    @TypeConverter
    fun toAfinityMovie(movieString: String?): AfinityMovie? {
        if (movieString == null) return null
        return try {
            val serializable = json.decodeFromString<SerializableAfinityMovie>(movieString)
            AfinityMovie(
                id = UUID.fromString(serializable.id),
                name = serializable.name,
                originalTitle = serializable.originalTitle,
                overview = serializable.overview,
                sources = emptyList(),
                played = serializable.played,
                favorite = serializable.favorite,
                liked = serializable.liked,
                canPlay = serializable.canPlay,
                canDownload = serializable.canDownload,
                runtimeTicks = serializable.runtimeTicks,
                playbackPositionTicks = serializable.playbackPositionTicks,
                premiereDate = serializable.premiereDate?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                dateCreated = serializable.dateCreated?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                people = toAfinityPersonList(serializable.peopleJson) ?: emptyList(),
                genres = serializable.genres,
                communityRating = serializable.communityRating,
                officialRating = serializable.officialRating,
                criticRating = serializable.criticRating,
                taglines = serializable.taglines,
                status = serializable.status,
                productionYear = serializable.productionYear,
                endDate = serializable.endDate?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                trailer = serializable.trailer,
                tagline = serializable.tagline,
                unplayedItemCount = serializable.unplayedItemCount,
                images = toAfinityImages(serializable.imagesJson) ?: AfinityImages(),
                chapters = emptyList(),
                trickplayInfo = null,
                providerIds = null,
                externalUrls = null
            )
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromAfinityShow(show: AfinityShow?): String? {
        if (show == null) return null
        val serializable = SerializableAfinityShow(
            id = show.id.toString(),
            name = show.name,
            originalTitle = show.originalTitle,
            overview = show.overview,
            played = show.played,
            favorite = show.favorite,
            liked = show.liked,
            canPlay = show.canPlay,
            canDownload = show.canDownload,
            runtimeTicks = show.runtimeTicks,
            playbackPositionTicks = show.playbackPositionTicks,
            premiereDate = show.premiereDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            dateCreated = show.dateCreated?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            dateLastContentAdded = show.dateLastContentAdded?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            genres = show.genres,
            communityRating = show.communityRating,
            officialRating = show.officialRating,
            taglines = show.taglines,
            status = show.status,
            productionYear = show.productionYear,
            endDate = show.endDate?.toString(),
            trailer = show.trailer,
            tagline = show.tagline,
            unplayedItemCount = show.unplayedItemCount,
            seasonCount = show.seasonCount,
            episodeCount = show.episodeCount,
            imagesJson = fromAfinityImages(show.images),
            peopleJson = fromAfinityPersonList(show.people)
        )
        return json.encodeToString(serializable)
    }

    @TypeConverter
    fun toAfinityShow(showString: String?): AfinityShow? {
        if (showString == null) return null
        return try {
            val serializable = json.decodeFromString<SerializableAfinityShow>(showString)
            AfinityShow(
                id = UUID.fromString(serializable.id),
                name = serializable.name,
                originalTitle = serializable.originalTitle,
                overview = serializable.overview,
                sources = emptyList(),
                seasons = emptyList(),
                played = serializable.played,
                favorite = serializable.favorite,
                liked = serializable.liked,
                canPlay = serializable.canPlay,
                canDownload = serializable.canDownload,
                playbackPositionTicks = serializable.playbackPositionTicks,
                runtimeTicks = serializable.runtimeTicks,
                premiereDate = serializable.premiereDate?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                dateCreated = serializable.dateCreated?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                dateLastContentAdded = serializable.dateLastContentAdded?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                people = toAfinityPersonList(serializable.peopleJson) ?: emptyList(),
                genres = serializable.genres,
                communityRating = serializable.communityRating,
                officialRating = serializable.officialRating,
                taglines = serializable.taglines,
                status = serializable.status,
                productionYear = serializable.productionYear,
                endDate = null,
                trailer = serializable.trailer,
                tagline = serializable.tagline,
                unplayedItemCount = serializable.unplayedItemCount,
                seasonCount = serializable.seasonCount,
                episodeCount = serializable.episodeCount,
                images = toAfinityImages(serializable.imagesJson) ?: AfinityImages(),
                chapters = emptyList(),
                providerIds = null,
                externalUrls = null
            )
        } catch (e: Exception) {
            null
        }
    }

    @TypeConverter
    fun fromAfinityStudio(studio: AfinityStudio?): String? {
        if (studio == null) return null
        val serializable = SerializableAfinityStudio(
            id = studio.id.toString(),
            name = studio.name,
            primaryImageUrl = studio.primaryImageUrl,
            itemCount = studio.itemCount
        )
        return json.encodeToString(serializable)
    }

    @TypeConverter
    fun toAfinityStudio(studioString: String?): AfinityStudio? {
        if (studioString == null) return null
        return try {
            val serializable = json.decodeFromString<SerializableAfinityStudio>(studioString)
            AfinityStudio(
                id = UUID.fromString(serializable.id),
                name = serializable.name,
                primaryImageUrl = serializable.primaryImageUrl,
                itemCount = serializable.itemCount
            )
        } catch (e: Exception) {
            null
        }
    }
}

@Serializable
private data class SerializableAfinityStudio(
    val id: String,
    val name: String,
    val primaryImageUrl: String?,
    val itemCount: Int
)

@Serializable
private data class SerializableAfinityPerson(
    val id: String,
    val name: String,
    val type: String,
    val role: String,
    val imageUri: String? = null,
    val imageBlurHash: String? = null
)

@Serializable
private data class SerializableAfinityImages(
    val primary: String? = null,
    val backdrop: String? = null,
    val thumb: String? = null,
    val logo: String? = null,
    val showPrimary: String? = null,
    val showBackdrop: String? = null,
    val showLogo: String? = null,
    val primaryImageBlurHash: String? = null,
    val backdropImageBlurHash: String? = null,
    val thumbImageBlurHash: String? = null,
    val logoImageBlurHash: String? = null,
    val showPrimaryImageBlurHash: String? = null,
    val showBackdropImageBlurHash: String? = null,
    val showLogoImageBlurHash: String? = null
)

@Serializable
private data class SerializableAfinityMovie(
    val id: String,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val played: Boolean,
    val favorite: Boolean,
    val liked: Boolean,
    val canPlay: Boolean,
    val canDownload: Boolean,
    val runtimeTicks: Long,
    val playbackPositionTicks: Long,
    val premiereDate: String?,
    val dateCreated: String?,
    val genres: List<String>,
    val communityRating: Float?,
    val officialRating: String?,
    val criticRating: Float?,
    val taglines: List<String>,
    val status: String,
    val productionYear: Int?,
    val endDate: String?,
    val trailer: String?,
    val tagline: String?,
    val unplayedItemCount: Int?,
    val imagesJson: String?,
    val peopleJson: String?
)

@Serializable
private data class SerializableAfinityShow(
    val id: String,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val played: Boolean,
    val favorite: Boolean,
    val liked: Boolean,
    val canPlay: Boolean,
    val canDownload: Boolean,
    val runtimeTicks: Long,
    val playbackPositionTicks: Long,
    val premiereDate: String?,
    val dateCreated: String?,
    val dateLastContentAdded: String?,
    val genres: List<String>,
    val communityRating: Float?,
    val officialRating: String?,
    val taglines: List<String>,
    val status: String,
    val productionYear: Int?,
    val endDate: String?,
    val trailer: String?,
    val tagline: String?,
    val unplayedItemCount: Int?,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val imagesJson: String?,
    val peopleJson: String?
)