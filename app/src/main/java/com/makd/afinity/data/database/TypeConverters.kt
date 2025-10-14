package com.makd.afinity.data.database

import androidx.room.TypeConverter
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinitySourceType
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
}