package com.makd.afinity.data.models.media

import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.util.UUID

fun AfinityUserDataOwner.patchedWithUserData(data: UserItemDataDto): AfinityUserDataOwner =
    when (val owner = this) {
        is AfinityItem -> owner.withUserData(data)
        is AfinityTrack -> owner.withUserData(data)
        is AfinityAlbum -> owner.withUserData(data)
        is AfinityArtist -> owner.withUserData(data)
        else -> owner
    }

fun AfinityTrack.withUserData(data: UserItemDataDto): AfinityTrack {
    val isLiked = data.likes == true
    if (
        played == data.played &&
            favorite == data.isFavorite &&
            liked == isLiked &&
            playbackPositionTicks == data.playbackPositionTicks &&
            playCount == data.playCount
    ) {
        return this
    }
    return copy(
        played = data.played,
        favorite = data.isFavorite,
        liked = isLiked,
        playbackPositionTicks = data.playbackPositionTicks,
        playCount = data.playCount,
    )
}

fun AfinityAlbum.withUserData(data: UserItemDataDto): AfinityAlbum {
    val isLiked = data.likes == true
    if (
        played == data.played &&
            favorite == data.isFavorite &&
            liked == isLiked &&
            playbackPositionTicks == data.playbackPositionTicks &&
            playCount == data.playCount
    ) {
        return this
    }
    return copy(
        played = data.played,
        favorite = data.isFavorite,
        liked = isLiked,
        playbackPositionTicks = data.playbackPositionTicks,
        playCount = data.playCount,
    )
}

fun AfinityArtist.withUserData(data: UserItemDataDto): AfinityArtist {
    val isLiked = data.likes == true
    if (played == data.played && favorite == data.isFavorite && liked == isLiked) {
        return this
    }
    return copy(played = data.played, favorite = data.isFavorite, liked = isLiked)
}

fun AfinityItem.withUserData(data: UserItemDataDto): AfinityItem {
    val isLiked = data.likes == true
    if (
        played == data.played &&
            favorite == data.isFavorite &&
            liked == isLiked &&
            playbackPositionTicks == data.playbackPositionTicks &&
            unplayedItemCount == data.unplayedItemCount
    ) {
        return this
    }

    return when (this) {
        is AfinityMovie ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinityShow ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinitySeason ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinityEpisode ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinityBoxSet ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinityVideo ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinityVideoPlaylist ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinityFolder ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        is AfinityCollection ->
            copy(
                played = data.played,
                favorite = data.isFavorite,
                liked = isLiked,
                playbackPositionTicks = data.playbackPositionTicks,
                unplayedItemCount = data.unplayedItemCount,
            )
        else -> this
    }
}

fun <T : AfinityItem> List<T>.withUserData(itemId: UUID, data: UserItemDataDto): List<T> {
    if (none { it.id == itemId }) return this
    var changed = false
    val patched =
        map { item ->
            if (item.id != itemId) {
                item
            } else {
                @Suppress("UNCHECKED_CAST") val next = item.withUserData(data) as T
                if (next !== item) changed = true
                next
            }
        }
    return if (changed) patched else this
}