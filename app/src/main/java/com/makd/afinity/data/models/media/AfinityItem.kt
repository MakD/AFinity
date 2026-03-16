package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.models.extensions.toAfinityBoxSet
import com.makd.afinity.data.models.extensions.toAfinityChannel
import com.makd.afinity.data.models.extensions.toAfinityFolder
import com.makd.afinity.data.models.extensions.toAfinityVideo
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

interface AfinityItem {
    val id: UUID
    val name: String
    val originalTitle: String?
    val overview: String
    val played: Boolean
    val favorite: Boolean
    val liked: Boolean
    val canPlay: Boolean
    val canDownload: Boolean
    val sources: List<AfinitySource>
    val runtimeTicks: Long
    val playbackPositionTicks: Long
    val unplayedItemCount: Int?
    val images: AfinityImages
    val chapters: List<AfinityChapter>
    val providerIds: Map<String, String>?
    val externalUrls: List<AfinityExternalUrl>?
}

suspend fun BaseItemDto.toAfinityItem(
    baseUrl: String,
    serverDatabase: ServerDatabaseDao? = null,
): AfinityItem? {
    return when (type) {
        BaseItemKind.MOVIE -> toAfinityMovie(baseUrl, serverDatabase)
        BaseItemKind.EPISODE -> toAfinityEpisode(baseUrl)
        BaseItemKind.SEASON -> toAfinitySeason(baseUrl)
        BaseItemKind.SERIES -> toAfinityShow(baseUrl)
        BaseItemKind.BOX_SET -> toAfinityBoxSet(baseUrl)
        BaseItemKind.FOLDER -> toAfinityFolder(baseUrl)
        BaseItemKind.VIDEO -> toAfinityVideo(baseUrl)
        BaseItemKind.TV_CHANNEL -> toAfinityChannel(baseUrl)
        else -> null
    }
}

fun AfinityItem.isDownloading(): Boolean {
    return sources
        .filter { it.type == AfinitySourceType.LOCAL }
        .any { it.path.endsWith(".download") }
}

fun AfinityItem.isDownloaded(): Boolean {
    return sources
        .filter { it.type == AfinitySourceType.LOCAL }
        .any { !it.path.endsWith(".download") }
}
