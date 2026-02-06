package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.models.extensions.toAfinityChannel
import com.makd.afinity.data.repository.JellyfinRepository
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
    jellyfinRepository: JellyfinRepository,
    serverDatabase: ServerDatabaseDao? = null,
): AfinityItem? {
    return when (type) {
        BaseItemKind.MOVIE -> toAfinityMovie(jellyfinRepository, serverDatabase)
        BaseItemKind.EPISODE -> toAfinityEpisode(jellyfinRepository)
        BaseItemKind.SEASON -> toAfinitySeason(jellyfinRepository)
        BaseItemKind.SERIES -> toAfinityShow(jellyfinRepository)
        BaseItemKind.BOX_SET -> toAfinityBoxSet(jellyfinRepository)
        BaseItemKind.FOLDER -> toAfinityFolder(jellyfinRepository)
        BaseItemKind.TV_CHANNEL -> toAfinityChannel(jellyfinRepository.getBaseUrl())
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
