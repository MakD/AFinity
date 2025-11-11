package com.makd.afinity.data.database

import android.net.Uri
import androidx.room.TypeConverter
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinitySourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.MediaStreamType
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
}

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