package com.makd.afinity.data.database.entities

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityAlbum
import java.util.UUID

@Entity(
    tableName = "music_albums",
    primaryKeys = ["id", "serverId", "userId"],
    indices = [Index("serverId", "userId")],
)
data class MusicAlbumEntity(
    val id: String,
    val serverId: String,
    val userId: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val productionYear: Int?,
    val songCount: Int?,
    val runtimeTicks: Long,
    val images: AfinityImages?,
    val localImagePath: String? = null,
)

fun AfinityAlbum.toMusicAlbumEntity(serverId: String, userId: String) = MusicAlbumEntity(
    id = id.toString(),
    serverId = serverId,
    userId = userId,
    name = name,
    artist = artist,
    artistId = artistId?.toString(),
    productionYear = productionYear,
    songCount = songCount,
    runtimeTicks = runtimeTicks,
    images = images,
)

fun MusicAlbumEntity.toAfinityAlbum(): AfinityAlbum {
    val resolvedImages = if (localImagePath != null) {
        (images ?: AfinityImages()).copy(primary = Uri.parse(localImagePath))
    } else {
        images ?: AfinityImages()
    }
    return AfinityAlbum(
        id = UUID.fromString(id),
        name = name,
        artistId = artistId?.let { UUID.fromString(it) },
        artist = artist,
        artists = if (artist != null) listOf(artist) else emptyList(),
        productionYear = productionYear,
        songCount = songCount,
        runtimeTicks = runtimeTicks,
        genres = emptyList(),
        overview = null,
        favorite = false,
        played = false,
        playCount = null,
        images = resolvedImages,
    )
}