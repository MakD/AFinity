package com.makd.afinity.data.models.extensions

import androidx.core.net.toUri
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType

fun List<AfinityTrack>.toRecentlyPlayedAlbums(limit: Int): List<AfinityAlbum> {
    val seen = mutableSetOf<UUID>()
    return mapNotNull { track ->
            val albumId = track.albumId ?: return@mapNotNull null
            val albumName = track.album ?: return@mapNotNull null
            if (!seen.add(albumId)) return@mapNotNull null
            track.toAlbumStub(albumId, albumName, playCount = null)
        }
        .take(limit)
}

fun List<AfinityTrack>.toMostPlayedAlbums(limit: Int): List<AfinityAlbum> {
    val plays = LinkedHashMap<UUID, Int>()
    val sources = HashMap<UUID, AfinityTrack>()
    for (track in this) {
        val albumId = track.albumId ?: continue
        if (track.album == null) continue
        plays[albumId] = (plays[albumId] ?: 0) + (track.playCount ?: 0)
        sources.putIfAbsent(albumId, track)
    }
    return plays.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }
        .take(limit)
        .mapNotNull { (albumId, count) ->
            val track = sources[albumId] ?: return@mapNotNull null
            val albumName = track.album ?: return@mapNotNull null
            track.toAlbumStub(albumId, albumName, playCount = count)
        }
}

fun List<AfinityTrack>.topArtistIds(limit: Int): List<UUID> {
    val plays = LinkedHashMap<UUID, Int>()
    for (track in this) {
        val artistId = track.artistId ?: continue
        plays[artistId] = (plays[artistId] ?: 0) + (track.playCount ?: 0)
    }
    return plays.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
}

private fun AfinityTrack.toAlbumStub(
    albumId: UUID,
    albumName: String,
    playCount: Int?,
): AfinityAlbum =
    AfinityAlbum(
        id = albumId,
        name = albumName,
        artistId = artistId,
        artist = artist,
        artists = artists,
        productionYear = productionYear,
        songCount = null,
        runtimeTicks = 0L,
        genres = emptyList(),
        overview = null,
        favorite = false,
        played = false,
        playCount = playCount,
        images = images,
    )

fun BaseItemDto.toAfinityTrack(baseUrl: String): AfinityTrack {
    val baseUri = baseUrl.trimEnd('/').toUri()
    val primary =
        imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            baseUri
                .buildUpon()
                .appendEncodedPath("Items/$id/Images/Primary")
                .appendQueryParameter("tag", tag)
                .build()
        }
            ?: albumPrimaryImageTag?.let { tag ->
                albumId?.let { aId ->
                    baseUri
                        .buildUpon()
                        .appendEncodedPath("Items/$aId/Images/Primary")
                        .appendQueryParameter("tag", tag)
                        .build()
                }
            }
    val blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.values?.firstOrNull()
    return AfinityTrack(
        id = id,
        name = name.orEmpty(),
        albumId = albumId,
        album = album,
        artistId = artistItems?.firstOrNull()?.id,
        artist = albumArtist ?: artistItems?.firstOrNull()?.name,
        artists = artists ?: emptyList(),
        indexNumber = indexNumber,
        discNumber = parentIndexNumber,
        productionYear = productionYear,
        runtimeTicks = runTimeTicks ?: 0L,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        liked = userData?.likes == true,
        playCount = userData?.playCount,
        normalizationGain = normalizationGain,
        albumNormalizationGain = albumNormalizationGain,
        images =
            AfinityImages(
                primary = primary,
                primaryImageBlurHash = blurHash,
            ),
        playlistItemId = playlistItemId,
    )
}

fun BaseItemDto.toAfinityAlbum(baseUrl: String): AfinityAlbum {
    return AfinityAlbum(
        id = id,
        name = name.orEmpty(),
        artistId = albumArtists?.firstOrNull()?.id,
        artist = albumArtist ?: albumArtists?.firstOrNull()?.name,
        artists = albumArtists?.mapNotNull { it.name } ?: emptyList(),
        productionYear = productionYear,
        songCount = childCount,
        runtimeTicks = runTimeTicks ?: 0L,
        genres = genres ?: emptyList(),
        overview = overview,
        externalUrls = toAfinityExternalUrls(),
        favorite = userData?.isFavorite == true,
        played = userData?.played == true,
        liked = userData?.likes == true,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
        playCount = userData?.playCount,
        images = toAfinityImages(baseUrl),
    )
}

fun BaseItemDto.toAfinityArtist(baseUrl: String): AfinityArtist {
    return AfinityArtist(
        id = id,
        name = name.orEmpty(),
        overview = overview,
        externalUrls = toAfinityExternalUrls(),
        albumCount = childCount,
        genres = genres ?: emptyList(),
        favorite = userData?.isFavorite == true,
        liked = userData?.likes == true,
        played = userData?.played == true,
        images = toAfinityImages(baseUrl),
    )
}

fun BaseItemDto.toAfinityPlaylist(baseUrl: String): AfinityPlaylist {
    return AfinityPlaylist(
        id = id,
        name = name.orEmpty(),
        overview = overview,
        songCount = childCount,
        runtimeTicks = runTimeTicks ?: 0L,
        favorite = userData?.isFavorite == true,
        images = toAfinityImages(baseUrl, ImageFormat.JPG),
    )
}
