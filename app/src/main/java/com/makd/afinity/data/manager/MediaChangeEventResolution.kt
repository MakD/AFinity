package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.repository.media.MediaRepository
import timber.log.Timber
import java.util.UUID

suspend fun MediaChangeEvent.resolveChangedItems(
    mediaRepository: MediaRepository,
    displayedIds: Set<UUID>? = null,
): List<AfinityItem> {
    val provided = listOfNotNull(updatedItem, parentItem, seasonItem).associateBy { it.id }
    val resolvedSeriesId =
        seriesId
            ?: (updatedItem as? AfinityEpisode)?.seriesId
            ?: (updatedItem as? AfinitySeason)?.seriesId

    val candidateIds = linkedSetOf(itemId)
    resolvedSeriesId?.let { candidateIds.add(it) }
    seasonId?.let { candidateIds.add(it) }

    return candidateIds
        .filter { displayedIds == null || it in displayedIds }
        .mapNotNull { id ->
            provided[id]
                ?: run {
                    val fetchAllowed =
                        displayedIds != null || id == itemId || id == resolvedSeriesId
                    if (!fetchAllowed) return@mapNotNull null
                    try {
                        mediaRepository.getItemById(id)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to resolve item $id for media change patch")
                        null
                    }
                }
        }
}