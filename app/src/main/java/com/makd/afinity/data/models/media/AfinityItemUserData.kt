package com.makd.afinity.data.models.media

import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.user.AfinityUserDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.util.UUID

data class UserDataPatch(
    val played: Boolean? = null,
    val favorite: Boolean? = null,
    val liked: Boolean? = null,
    val playbackPositionTicks: Long? = null,
)

data class UserDataValues(
    val played: Boolean,
    val favorite: Boolean,
    val liked: Boolean,
    val playbackPositionTicks: Long,
    val unplayedItemCount: Int? = null,
    val playCount: Int? = null,
)

fun UserItemDataDto.toUserDataValues(): UserDataValues =
    UserDataValues(
        played = played,
        favorite = isFavorite,
        liked = likes == true,
        playbackPositionTicks = playbackPositionTicks,
        unplayedItemCount = unplayedItemCount,
        playCount = playCount,
    )

fun AfinityUserDataDto.toUserDataValues(): UserDataValues =
    UserDataValues(
        played = played,
        favorite = favorite,
        liked = likes,
        playbackPositionTicks = playbackPositionTicks,
        unplayedItemCount = unplayedItemCount,
        playCount = playCount,
    )

fun AfinityUserDataOwner.toUserDataValues(): UserDataValues =
    UserDataValues(
        played = played,
        favorite = favorite,
        liked = liked,
        playbackPositionTicks = playbackPositionTicks,
        unplayedItemCount = (this as? AfinityItem)?.unplayedItemCount,
        playCount = (this as? AfinityTrack)?.playCount ?: (this as? AfinityAlbum)?.playCount,
    )

fun UserDataPatch.resolvedAgainst(owner: AfinityUserDataOwner): UserDataValues =
    UserDataValues(
        played = played ?: owner.played,
        favorite = favorite ?: owner.favorite,
        liked = liked ?: owner.liked,
        playbackPositionTicks = playbackPositionTicks ?: owner.playbackPositionTicks,
    )

fun AfinityItem.applying(values: UserDataValues): AfinityItem {
    val unplayed = values.unplayedItemCount ?: unplayedItemCount
    if (
        played == values.played &&
            favorite == values.favorite &&
            liked == values.liked &&
            playbackPositionTicks == values.playbackPositionTicks &&
            unplayedItemCount == unplayed
    ) {
        return this
    }
    val p = values.played
    val f = values.favorite
    val l = values.liked
    val t = values.playbackPositionTicks
    return when (this) {
        is AfinityMovie ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinityShow ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinitySeason ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinityEpisode ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinityBoxSet ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinityVideo ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinityVideoPlaylist ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinityFolder ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        is AfinityCollection ->
            copy(
                played = p,
                favorite = f,
                liked = l,
                playbackPositionTicks = t,
                unplayedItemCount = unplayed,
            )
        else -> this
    }
}

fun AfinityTrack.applying(values: UserDataValues): AfinityTrack {
    val count = values.playCount ?: playCount
    if (
        played == values.played &&
            favorite == values.favorite &&
            liked == values.liked &&
            playbackPositionTicks == values.playbackPositionTicks &&
            playCount == count
    ) {
        return this
    }
    return copy(
        played = values.played,
        favorite = values.favorite,
        liked = values.liked,
        playbackPositionTicks = values.playbackPositionTicks,
        playCount = count,
    )
}

fun AfinityAlbum.applying(values: UserDataValues): AfinityAlbum {
    val count = values.playCount ?: playCount
    if (
        played == values.played &&
            favorite == values.favorite &&
            liked == values.liked &&
            playbackPositionTicks == values.playbackPositionTicks &&
            playCount == count
    ) {
        return this
    }
    return copy(
        played = values.played,
        favorite = values.favorite,
        liked = values.liked,
        playbackPositionTicks = values.playbackPositionTicks,
        playCount = count,
    )
}

fun AfinityArtist.applying(values: UserDataValues): AfinityArtist {
    if (played == values.played && favorite == values.favorite && liked == values.liked) {
        return this
    }
    return copy(played = values.played, favorite = values.favorite, liked = values.liked)
}

fun AfinityUserDataOwner.applying(values: UserDataValues): AfinityUserDataOwner =
    when (val owner = this) {
        is AfinityItem -> owner.applying(values)
        is AfinityTrack -> owner.applying(values)
        is AfinityAlbum -> owner.applying(values)
        is AfinityArtist -> owner.applying(values)
        else -> owner
    }

fun AfinityItem.withUserData(data: UserItemDataDto): AfinityItem =
    applying(data.toUserDataValues())

fun AfinityItem.withUserData(data: AfinityUserDataDto): AfinityItem =
    applying(data.toUserDataValues())

fun AfinityItem.withUserDataFrom(source: AfinityUserDataOwner): AfinityItem =
    applying(source.toUserDataValues())

fun AfinityItem.withUserDataPatch(patch: UserDataPatch): AfinityItem =
    applying(patch.resolvedAgainst(this))

fun AfinityTrack.withUserData(data: UserItemDataDto): AfinityTrack =
    applying(data.toUserDataValues())

fun AfinityAlbum.withUserData(data: UserItemDataDto): AfinityAlbum =
    applying(data.toUserDataValues())

fun AfinityArtist.withUserData(data: UserItemDataDto): AfinityArtist =
    applying(data.toUserDataValues())

fun AfinityUserDataOwner.patchedWithUserData(data: UserItemDataDto): AfinityUserDataOwner =
    applying(data.toUserDataValues())

fun AfinityUserDataOwner.patchedWith(patch: UserDataPatch): AfinityUserDataOwner =
    applying(patch.resolvedAgainst(this))

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