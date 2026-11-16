package com.makd.afinity.data.models.media

import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.ImageOrientation

data class AfinityPhoto(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String,
    override val played: Boolean,
    override val favorite: Boolean,
    override val liked: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val sources: List<AfinitySource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int? = null,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
    val parentId: UUID?,
    val albumId: UUID?,
    val album: String?,
    val width: Int?,
    val height: Int?,
    val orientation: ImageOrientation?,
    val aspectRatio: Float?,
    val dateTaken: LocalDateTime?,
    val dateCreated: LocalDateTime?,
    val productionYear: Int?,
    val genres: List<String>,
    val tags: List<String>,
    val communityRating: Float?,
    val exif: AfinityPhotoExif,
    val fileName: String? = null,
) : AfinityItem

data class AfinityPhotoExif(
    val cameraMake: String?,
    val cameraModel: String?,
    val software: String?,
    val exposureTime: Double?,
    val focalLength: Double?,
    val aperture: Double?,
    val shutterSpeed: Double?,
    val isoSpeedRating: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null

    val isEmpty: Boolean
        get() =
            cameraMake == null &&
                cameraModel == null &&
                exposureTime == null &&
                focalLength == null &&
                aperture == null &&
                isoSpeedRating == null &&
                !hasLocation
}

private val QUARTER_TURN_ORIENTATIONS =
    setOf(
        ImageOrientation.LEFT_TOP,
        ImageOrientation.RIGHT_TOP,
        ImageOrientation.RIGHT_BOTTOM,
        ImageOrientation.LEFT_BOTTOM,
    )

fun uprightAspectRatio(
    reportedRatio: Double?,
    width: Int?,
    height: Int?,
    orientation: ImageOrientation?,
): Float? {
    val fromSize =
        if (width != null && height != null && width > 0 && height > 0) {
            width.toDouble() / height
        } else {
            null
        }
    val stored = reportedRatio?.takeIf { it > 0.0 } ?: fromSize ?: return null
    val upright = if (orientation in QUARTER_TURN_ORIENTATIONS) 1.0 / stored else stored
    return upright.toFloat()
}
