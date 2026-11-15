package com.makd.afinity.data.audiobooks

import com.makd.afinity.data.models.extensions.toAfinityAudiobook
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.asBook
import com.makd.afinity.data.models.media.inPartOrder
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@Singleton
class JellyfinAudiobookLoader
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val downloadStore: AudiobookDownloadStore,
) {

    suspend fun loadParts(itemId: UUID): Result<List<AfinityAudiobook>?> {
        val baseUrl = mediaRepository.getBaseUrl()
        val online =
            mediaRepository.getItemResult(itemId, fields = FieldSets.AUDIOBOOK_DETAIL).map { dto ->
                val lead =
                    dto?.takeIf { it.type == BaseItemKind.AUDIO_BOOK }?.toAfinityAudiobook(baseUrl)
                lead?.let { partsOf(it, baseUrl) }
            }
        if (online.isSuccess) return online
        val offline = downloadStore.downloadedBookParts(itemId) ?: return online
        return Result.success(offline)
    }

    suspend fun loadBook(itemId: UUID): Result<AfinityAudiobook?> =
        loadParts(itemId).map { it?.asBook() }

    private suspend fun partsOf(lead: AfinityAudiobook, baseUrl: String): List<AfinityAudiobook> {
        val album = lead.album?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val parentId = lead.parentId
        if (album == null || parentId == null) return listOf(lead)
        val siblings =
            mediaRepository
                .getItemsResult(
                    parentId = parentId,
                    includeItemTypes = listOf("AUDIO_BOOK"),
                    fields = FieldSets.AUDIOBOOK_DETAIL,
                    recursive = false,
                )
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    Timber.w(e, "Could not load the other parts of ${lead.id}")
                    return listOf(lead)
                }
                .items
                .filter { it.type == BaseItemKind.AUDIO_BOOK }
                .map { it.toAfinityAudiobook(baseUrl) }
                .filter { it.album?.trim()?.lowercase() == album }
        val withLead = if (siblings.any { it.id == lead.id }) siblings else siblings + lead
        return withLead.inPartOrder()
    }
}
