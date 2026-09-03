package com.makd.afinity.ui.downloads

import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.download.DownloadInfo
import java.util.UUID

enum class DownloadCategory {
    VIDEO,
    MUSIC,
    AUDIOBOOK,
    PODCAST,
}

enum class DownloadSort {
    RECENT,
    LARGEST,
    TITLE,
}

sealed interface DownloadCatalogRef {
    data class JellyfinItem(val downloadId: UUID) : DownloadCatalogRef

    data class JellyfinSeries(val seriesId: String) : DownloadCatalogRef

    data class MusicAlbum(val albumId: String) : DownloadCatalogRef

    data class AbsBook(val libraryItemId: String) : DownloadCatalogRef

    data class AbsPodcast(val libraryItemId: String) : DownloadCatalogRef
}

data class DownloadCatalogEntry(
    val key: String,
    val ref: DownloadCatalogRef,
    val category: DownloadCategory,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val sizeBytes: Long,
    val childCount: Int,
    val isAvailable: Boolean,
    val createdAt: Long,
    val childIds: List<UUID>,
) {
    val isGroup: Boolean
        get() = childCount > 0
}

fun buildDownloadCatalog(
    jellyfinDownloads: List<DownloadInfo>,
    absDownloads: List<AbsDownloadInfo>,
    unavailableVolumeIds: Set<String>,
): List<DownloadCatalogEntry> {
    val entries = mutableListOf<DownloadCatalogEntry>()

    val (audio, video) = jellyfinDownloads.partition { it.itemType == "Audio" }

    val (episodes, standaloneVideo) = video.partition { !it.seriesId.isNullOrBlank() }

    standaloneVideo.forEach { download ->
        entries +=
            DownloadCatalogEntry(
                key = "jf_${download.id}",
                ref = DownloadCatalogRef.JellyfinItem(download.id),
                category = DownloadCategory.VIDEO,
                title = download.itemName,
                subtitle = download.releaseYear,
                imageUrl = download.imageUrl,
                sizeBytes = download.totalBytes,
                childCount = 0,
                isAvailable = download.storageVolumeId !in unavailableVolumeIds,
                createdAt = download.createdAt,
                childIds = listOf(download.id),
            )
    }

    episodes
        .groupBy { it.seriesId.orEmpty() }
        .forEach { (seriesId, group) ->
            val first = group.minByOrNull { it.createdAt } ?: return@forEach
            entries +=
                DownloadCatalogEntry(
                    key = "jf_series_$seriesId",
                    ref = DownloadCatalogRef.JellyfinSeries(seriesId),
                    category = DownloadCategory.VIDEO,
                    title = first.seriesName?.takeIf { it.isNotBlank() } ?: first.itemName,
                    subtitle = null,
                    imageUrl = first.seriesImageUrl ?: first.imageUrl,
                    sizeBytes = group.sumOf { it.totalBytes },
                    childCount = group.size,
                    isAvailable = group.none { it.storageVolumeId in unavailableVolumeIds },
                    createdAt = group.maxOf { it.createdAt },
                    childIds = group.map { it.id },
                )
        }

    val (albumTracks, looseTracks) = audio.partition { !it.seriesId.isNullOrBlank() }

    looseTracks.forEach { download ->
        entries +=
            DownloadCatalogEntry(
                key = "jf_${download.id}",
                ref = DownloadCatalogRef.JellyfinItem(download.id),
                category = DownloadCategory.MUSIC,
                title = download.itemName,
                subtitle = download.sourceName.takeIf { it.isNotBlank() },
                imageUrl = download.imageUrl,
                sizeBytes = download.totalBytes,
                childCount = 0,
                isAvailable = download.storageVolumeId !in unavailableVolumeIds,
                createdAt = download.createdAt,
                childIds = listOf(download.id),
            )
    }

    albumTracks
        .groupBy { it.seriesId.orEmpty() }
        .forEach { (albumId, group) ->
            val first = group.minByOrNull { it.createdAt } ?: return@forEach
            entries +=
                DownloadCatalogEntry(
                    key = "jf_album_$albumId",
                    ref = DownloadCatalogRef.MusicAlbum(albumId),
                    category = DownloadCategory.MUSIC,
                    title = first.seriesName?.takeIf { it.isNotBlank() } ?: first.itemName,
                    subtitle = null,
                    imageUrl = first.imageUrl,
                    sizeBytes = group.sumOf { it.totalBytes },
                    childCount = group.size,
                    isAvailable = group.none { it.storageVolumeId in unavailableVolumeIds },
                    createdAt = group.maxOf { it.createdAt },
                    childIds = group.map { it.id },
                )
        }

    val (podcastEpisodes, books) = absDownloads.partition { it.episodeId != null }

    books.forEach { download ->
        entries +=
            DownloadCatalogEntry(
                key = "abs_${download.libraryItemId}",
                ref = DownloadCatalogRef.AbsBook(download.libraryItemId),
                category = DownloadCategory.AUDIOBOOK,
                title = download.title,
                subtitle = download.authorName,
                imageUrl = download.coverUrl,
                sizeBytes = download.totalBytes,
                childCount = 0,
                isAvailable = true,
                createdAt = download.createdAt,
                childIds = listOf(download.id),
            )
    }

    podcastEpisodes
        .groupBy { it.libraryItemId }
        .forEach { (libraryItemId, group) ->
            val first = group.minByOrNull { it.createdAt } ?: return@forEach
            val podcastName =
                group.firstNotNullOfOrNull { it.authorName?.takeIf { name -> name.isNotBlank() } }
                    ?: first.title
            entries +=
                DownloadCatalogEntry(
                    key = "abs_podcast_$libraryItemId",
                    ref = DownloadCatalogRef.AbsPodcast(libraryItemId),
                    category = DownloadCategory.PODCAST,
                    title = podcastName,
                    subtitle = null,
                    imageUrl = group.firstNotNullOfOrNull { it.coverUrl } ?: first.coverUrl,
                    sizeBytes = group.sumOf { it.totalBytes },
                    childCount = group.size,
                    isAvailable = true,
                    createdAt = group.maxOf { it.createdAt },
                    childIds = group.map { it.id },
                )
        }

    return entries
}

fun jellyfinChildrenOf(
    entry: DownloadCatalogEntry,
    downloads: List<DownloadInfo>,
): List<DownloadInfo> {
    if (entry.ref is DownloadCatalogRef.AbsBook || entry.ref is DownloadCatalogRef.AbsPodcast) {
        return emptyList()
    }
    val ids = entry.childIds.toSet()
    return downloads
        .filter { it.id in ids }
        .sortedWith(
            compareBy({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE })
        )
}

fun absChildrenOf(
    entry: DownloadCatalogEntry,
    downloads: List<AbsDownloadInfo>,
): List<AbsDownloadInfo> {
    if (entry.ref !is DownloadCatalogRef.AbsPodcast) return emptyList()
    val ids = entry.childIds.toSet()
    return downloads.filter { it.id in ids }.sortedByDescending { it.publishedAt ?: it.createdAt }
}

fun List<DownloadCatalogEntry>.sortedForCatalog(sort: DownloadSort): List<DownloadCatalogEntry> =
    when (sort) {
        DownloadSort.RECENT -> sortedByDescending { it.createdAt }
        DownloadSort.LARGEST -> sortedByDescending { it.sizeBytes }
        DownloadSort.TITLE -> sortedBy { it.title.lowercase() }
    }
