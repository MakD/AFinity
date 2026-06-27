package com.makd.afinity.data.database.entities

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityTrack
import java.util.UUID

@Entity(
    tableName = "music_tracks",
    primaryKeys = ["id", "serverId", "userId"],
    indices =
        [
            Index("serverId", "userId"),
            Index("albumId", "serverId", "userId"),
        ],
)
data class MusicTrackEntity(
    val id: String,
    val serverId: String,
    val userId: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val albumId: String?,
    val albumName: String?,
    val indexNumber: Int?,
    val discNumber: Int?,
    val productionYear: Int?,
    val runtimeTicks: Long,
    val images: AfinityImages?,
    val localFilePath: String? = null,
    val localImagePath: String? = null,
)

fun AfinityTrack.toMusicTrackEntity(serverId: String, userId: String) =
    MusicTrackEntity(
        id = id.toString(),
        serverId = serverId,
        userId = userId,
        name = name,
        artist = artist,
        artistId = artistId?.toString(),
        albumId = albumId?.toString(),
        albumName = album,
        indexNumber = indexNumber,
        discNumber = discNumber,
        productionYear = productionYear,
        runtimeTicks = runtimeTicks,
        images = images,
    )

fun MusicTrackEntity.toAfinityTrack(): AfinityTrack {
    val resolvedImages =
        if (localImagePath != null) {
            (images ?: AfinityImages()).copy(primary = Uri.parse(localImagePath))
        } else {
            images ?: AfinityImages()
        }
    return AfinityTrack(
        id = UUID.fromString(id),
        name = name,
        albumId = albumId?.let { UUID.fromString(it) },
        album = albumName,
        artistId = artistId?.let { UUID.fromString(it) },
        artist = artist,
        artists = if (artist != null) listOf(artist) else emptyList(),
        indexNumber = indexNumber,
        discNumber = discNumber,
        productionYear = productionYear,
        runtimeTicks = runtimeTicks,
        playbackPositionTicks = 0L,
        played = false,
        favorite = false,
        playCount = null,
        normalizationGain = null,
        images = resolvedImages,
        localFilePath = localFilePath,
        localImagePath = localImagePath,
    )
}
