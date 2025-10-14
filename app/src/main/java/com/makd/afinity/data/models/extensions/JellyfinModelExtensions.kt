package com.makd.afinity.data.models.extensions

import android.net.Uri
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityFolder
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityExternalUrl
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.PlayAccess


fun BaseItemDto.toAfinityExternalUrls(): List<AfinityExternalUrl>? {
    return externalUrls?.map { it.toAfinityExternalUrl() }
}

fun BaseItemDto.toAfinityItem(
    baseUrl: String,
): AfinityItem? {
    return when (type) {
        BaseItemKind.MOVIE -> toAfinityMovie(baseUrl)
        BaseItemKind.EPISODE -> toAfinityEpisode(baseUrl)
        BaseItemKind.SEASON -> toAfinitySeason(baseUrl)
        BaseItemKind.SERIES -> toAfinityShow(baseUrl)
        BaseItemKind.BOX_SET -> toAfinityBoxSet(baseUrl)
        BaseItemKind.FOLDER -> toAfinityFolder(baseUrl)
        BaseItemKind.VIDEO -> toAfinityVideo(baseUrl)
        else -> null
    }
}

fun BaseItemDto.toAfinityMovie(
    baseUrl: String,
): AfinityMovie {
    return AfinityMovie(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        sources = mediaSources?.map { mediaSource ->
            AfinitySource(
                id = mediaSource.id.orEmpty(),
                name = mediaSource.name.orEmpty(),
                type = AfinitySourceType.REMOTE,
                path = mediaSource.path.orEmpty(),
                size = mediaSource.size ?: 0L,
                mediaStreams = mediaSource.mediaStreams?.map { mediaStream ->
                    AfinityMediaStream(
                        title = mediaStream.title.orEmpty(),
                        displayTitle = mediaStream.displayTitle,
                        language = mediaStream.language.orEmpty(),
                        type = mediaStream.type,
                        codec = mediaStream.codec.orEmpty(),
                        isExternal = mediaStream.isExternal,
                        path = if (mediaStream.isExternal && !mediaStream.deliveryUrl.isNullOrBlank()) {
                            baseUrl + mediaStream.deliveryUrl
                        } else {
                            null
                        },
                        channelLayout = mediaStream.channelLayout,
                        videoRangeType = mediaStream.videoRangeType,
                        height = mediaStream.height,
                        width = mediaStream.width,
                        videoDoViTitle = mediaStream.videoDoViTitle,
                        index = mediaStream.index,
                        channels = mediaStream.channels,
                        isDefault = mediaStream.isDefault,
                    )
                } ?: emptyList()
            )
        } ?: emptyList(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        runtimeTicks = runTimeTicks ?: 0,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0,
        premiereDate = premiereDate,
        dateCreated = dateCreated,
        people = people?.map { it.toAfinityPerson(baseUrl) } ?: emptyList(),
        genres = genres ?: emptyList(),
        communityRating = communityRating,
        officialRating = officialRating,
        criticRating = criticRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        endDate = endDate,
        trailer = remoteTrailers?.firstOrNull()?.url,
        tagline = taglines?.firstOrNull(),
        images = toAfinityImages(baseUrl),
        chapters = toAfinityChapters(),
        trickplayInfo = null,
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = toAfinityExternalUrls(),
    )
}

fun BaseItemDto.toAfinityShow(
    baseUrl: String,
): AfinityShow {
    return AfinityShow(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        sources = mediaSources?.map { mediaSource ->
            AfinitySource(
                id = mediaSource.id.orEmpty(),
                name = mediaSource.name.orEmpty(),
                type = AfinitySourceType.REMOTE,
                path = mediaSource.path.orEmpty(),
                size = mediaSource.size ?: 0L,
                mediaStreams = mediaSource.mediaStreams?.map { mediaStream ->
                    AfinityMediaStream(
                        title = mediaStream.title.orEmpty(),
                        displayTitle = mediaStream.displayTitle,
                        language = mediaStream.language.orEmpty(),
                        type = mediaStream.type,
                        codec = mediaStream.codec.orEmpty(),
                        isExternal = mediaStream.isExternal,
                        path = if (mediaStream.isExternal && !mediaStream.deliveryUrl.isNullOrBlank()) {
                            baseUrl + mediaStream.deliveryUrl
                        } else {
                            null
                        },
                        channelLayout = mediaStream.channelLayout,
                        videoRangeType = mediaStream.videoRangeType,
                        height = mediaStream.height,
                        width = mediaStream.width,
                        videoDoViTitle = mediaStream.videoDoViTitle,
                        index = mediaStream.index,
                        channels = mediaStream.channels,
                        isDefault = mediaStream.isDefault,
                    )
                } ?: emptyList()
            )
        } ?: emptyList(),
        seasons = emptyList(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        unplayedItemCount = userData?.unplayedItemCount,
        genres = genres ?: emptyList(),
        people = people?.map { it.toAfinityPerson(baseUrl) } ?: emptyList(),
        runtimeTicks = runTimeTicks ?: 0,
        communityRating = communityRating,
        officialRating = officialRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        premiereDate = premiereDate,
        dateCreated = dateCreated,
        dateLastContentAdded = dateLastMediaAdded,
        endDate = endDate,
        trailer = remoteTrailers?.firstOrNull()?.url,
        tagline = taglines?.firstOrNull(),
        seasonCount = childCount,
        episodeCount = recursiveItemCount,
        images = toAfinityImages(baseUrl),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = toAfinityExternalUrls(),
    )
}

fun BaseItemDto.toAfinitySeason(
    baseUrl: String,
): AfinitySeason {
    return AfinitySeason(
        id = id,
        name = name.orEmpty(),
        seriesId = seriesId!!,
        seriesName = seriesName.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        sources = emptyList(),
        indexNumber = indexNumber ?: 0,
        episodes = emptyList(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        unplayedItemCount = userData?.unplayedItemCount,
        images = toAfinityImages(baseUrl),
        episodeCount = childCount,
        productionYear = productionYear,
        premiereDate = premiereDate,
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = toAfinityExternalUrls(),
    )
}

fun BaseItemDto.toAfinityEpisode(
    baseUrl: String,
): AfinityEpisode? {
    return try {
        AfinityEpisode(
            id = id,
            name = name.orEmpty(),
            originalTitle = originalTitle,
            overview = overview.orEmpty(),
            indexNumber = indexNumber ?: 0,
            indexNumberEnd = indexNumberEnd,
            parentIndexNumber = parentIndexNumber ?: 0,
            sources = mediaSources?.map { mediaSource ->
                AfinitySource(
                    id = mediaSource.id.orEmpty(),
                    name = mediaSource.name.orEmpty(),
                    type = AfinitySourceType.REMOTE,
                    path = mediaSource.path.orEmpty(),
                    size = mediaSource.size ?: 0L,
                    mediaStreams = mediaSource.mediaStreams?.map { mediaStream ->
                        AfinityMediaStream(
                            title = mediaStream.title.orEmpty(),
                            displayTitle = mediaStream.displayTitle,
                            language = mediaStream.language.orEmpty(),
                            type = mediaStream.type,
                            codec = mediaStream.codec.orEmpty(),
                            isExternal = mediaStream.isExternal,
                            path = if (mediaStream.isExternal && !mediaStream.deliveryUrl.isNullOrBlank()) {
                                baseUrl + mediaStream.deliveryUrl
                            } else {
                                null
                            },
                            channelLayout = mediaStream.channelLayout,
                            videoRangeType = mediaStream.videoRangeType,
                            height = mediaStream.height,
                            width = mediaStream.width,
                            videoDoViTitle = mediaStream.videoDoViTitle,
                            index = mediaStream.index,
                            channels = mediaStream.channels,
                            isDefault = mediaStream.isDefault,
                        )
                    } ?: emptyList()
                )
            } ?: emptyList(),
            played = userData?.played == true,
            favorite = userData?.isFavorite == true,
            canPlay = playAccess != PlayAccess.NONE,
            canDownload = canDownload == true,
            runtimeTicks = runTimeTicks ?: 0,
            playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
            premiereDate = premiereDate,
            seriesName = seriesName.orEmpty(),
            seriesId = seriesId!!,
            seriesLogo = null,
            seriesLogoBlurHash = null,
            seasonId = seasonId!!,
            communityRating = communityRating,
            people = people?.map { it.toAfinityPerson(baseUrl) } ?: emptyList(),
            missing = locationType == LocationType.VIRTUAL,
            images = toAfinityImages(baseUrl),
            chapters = toAfinityChapters(),
            trickplayInfo = null,
            providerIds = providerIds?.mapNotNull { (key, value) ->
                value?.let { key to it }
            }?.toMap(),
            externalUrls = toAfinityExternalUrls(),
        )
    } catch (_: NullPointerException) {
        null
    }
}

fun BaseItemDto.toAfinityBoxSet(
    baseUrl: String,
): AfinityBoxSet {
    return AfinityBoxSet(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        runtimeTicks = runTimeTicks ?: 0,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0,
        unplayedItemCount = userData?.unplayedItemCount,
        images = toAfinityImages(baseUrl),
        chapters = toAfinityChapters(),
        items = emptyList(),
        itemCount = childCount,
        productionYear = productionYear,
        genres = genres ?: emptyList(),
        communityRating = communityRating,
        officialRating = officialRating,
        people = people?.map { it.toAfinityPerson(baseUrl) } ?: emptyList(),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = toAfinityExternalUrls(),
    )
}

fun BaseItemDto.toAfinityFolder(
    baseUrl: String,
): AfinityFolder {
    return AfinityFolder(
        id = id,
        name = name.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        unplayedItemCount = userData?.unplayedItemCount,
        images = toAfinityImages(baseUrl),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = toAfinityExternalUrls(),
    )
}

fun BaseItemDto.toAfinityCollection(
    baseUrl: String,
): AfinityCollection? {
    val type = CollectionType.fromString(collectionType?.serialName)

    if (type !in CollectionType.supported) {
        return null
    }

    return AfinityCollection(
        id = id,
        name = name.orEmpty(),
        type = type,
        images = toAfinityImages(baseUrl),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = toAfinityExternalUrls(),
    )
}

fun BaseItemDto.toAfinityPersonDetail(
    baseUrl: String,
): AfinityPersonDetail {
    return AfinityPersonDetail(
        id = id,
        name = name.orEmpty(),
        overview = overview.orEmpty(),
        images = toAfinityImages(baseUrl),
    )
}

fun BaseItemDto.toAfinityImages(
    baseUrl: String,
): AfinityImages {
    val baseUri = Uri.parse(baseUrl)

    return AfinityImages(
        primary = imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$id/Images/Primary")
                .appendQueryParameter("tag", tag)
                .build()
        },
        thumb = imageTags?.get(ImageType.THUMB)?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$id/Images/Thumb")
                .appendQueryParameter("tag", tag)
                .build()
        },
        backdrop = backdropImageTags?.firstOrNull()?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$id/Images/Backdrop/0")
                .appendQueryParameter("tag", tag)
                .build()
        },
        logo = imageTags?.get(ImageType.LOGO)?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$id/Images/Logo")
                .appendQueryParameter("tag", tag)
                .build()
        },
        showPrimary = seriesPrimaryImageTag?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$seriesId/Images/Primary")
                .appendQueryParameter("tag", tag)
                .build()
        },
        showBackdrop = seriesPrimaryImageTag?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$seriesId/Images/Backdrop/0")
                .appendQueryParameter("tag", tag)
                .build()
        },
        showLogo = seriesPrimaryImageTag?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$seriesId/Images/Logo")
                .appendQueryParameter("tag", tag)
                .build()
        },
    )
}

fun BaseItemDto.toAfinityChapters(): List<AfinityChapter> {
    return chapters?.mapIndexed { index, chapter ->
        AfinityChapter(
            startPosition = chapter.startPositionTicks / 10000,
            name = chapter.name,
            imageIndex = index
        )
    } ?: emptyList()
}

fun BaseItemDto.toAfinityVideo(
    baseUrl: String,
): AfinityVideo {
    return AfinityVideo(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        sources = emptyList(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        runtimeTicks = runTimeTicks ?: 0,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0,
        unplayedItemCount = userData?.unplayedItemCount,
        premiereDate = premiereDate,
        people = people?.map { it.toAfinityPerson(baseUrl) } ?: emptyList(),
        genres = genres ?: emptyList(),
        communityRating = communityRating,
        officialRating = officialRating,
        criticRating = criticRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        endDate = endDate,
        trailer = remoteTrailers?.firstOrNull()?.url,
        tagline = taglines?.firstOrNull(),
        images = toAfinityImages(baseUrl),
        chapters = toAfinityChapters(),
        trickplayInfo = null,
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = toAfinityExternalUrls(),
    )
}

fun BaseItemPerson.toAfinityPerson(
    baseUrl: String,
): AfinityPerson {
    val baseUri = Uri.parse(baseUrl)

    val personImage = AfinityPersonImage(
        uri = primaryImageTag?.let { tag ->
            baseUri.buildUpon()
                .appendEncodedPath("Items/$id/Images/Primary")
                .appendQueryParameter("tag", tag)
                .build()
        },
        blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(primaryImageTag),
    )

    return AfinityPerson(
        id = id,
        name = name.orEmpty(),
        type = type,
        role = role.orEmpty(),
        image = personImage,
    )
}