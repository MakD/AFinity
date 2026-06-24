package com.makd.afinity.data.models.extensions

import androidx.core.net.toUri
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

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
        playCount = userData?.playCount,
        normalizationGain = normalizationGain,
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
        favorite = userData?.isFavorite == true,
        played = userData?.played == true,
        playCount = userData?.playCount,
        images = toAfinityImages(baseUrl),
    )
}

fun BaseItemDto.toAfinityArtist(baseUrl: String): AfinityArtist {
    return AfinityArtist(
        id = id,
        name = name.orEmpty(),
        overview = overview,
        albumCount = childCount,
        genres = genres ?: emptyList(),
        favorite = userData?.isFavorite == true,
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
        images = toAfinityImages(baseUrl),
    )
}
