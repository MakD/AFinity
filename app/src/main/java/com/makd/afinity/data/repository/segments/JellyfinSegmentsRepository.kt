package com.makd.afinity.data.repository.segments

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.mergeOverlapping
import com.makd.afinity.data.models.media.toAfinitySegment
import com.makd.afinity.data.repository.DatabaseRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.operations.MediaSegmentApi
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber

@Singleton
class JellyfinSegmentsRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
) : SegmentsRepository {

    override suspend fun getSegments(
        itemId: UUID,
        mediaSourceId: String?,
        preferCache: Boolean,
    ): List<AfinitySegment> =
        withContext(Dispatchers.IO) {
            val ownerId = mediaSourceId?.removeSuffix(LOCAL_SOURCE_SUFFIX)?.toUUIDOrNull() ?: itemId
            return@withContext try {
                if (preferCache) {
                    val cached = databaseRepository.getSegmentsForItem(ownerId)
                    if (cached.isNotEmpty()) return@withContext cached.mergeOverlapping()
                }

                val fetched = fetchSegments(ownerId)
                if (fetched == null) {
                    databaseRepository.getSegmentsForItem(ownerId).mergeOverlapping()
                } else {
                    if (fetched.isNotEmpty()) {
                        databaseRepository.replaceSegmentsForItem(ownerId, fetched)
                    }
                    fetched.mergeOverlapping()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get segments for item $ownerId")
                emptyList()
            }
        }

    private suspend fun fetchSegments(ownerId: UUID): List<AfinitySegment>? =
        try {
            val apiClient = sessionManager.getCurrentApiClient() ?: return null
            MediaSegmentApi(apiClient)
                .getItemSegments(itemId = ownerId, includeSegmentTypes = FETCHED_TYPES)
                .content
                .items
                .map { it.toAfinitySegment() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Segment fetch failed for item $ownerId, falling back to cache")
            null
        }

    private companion object {
        const val LOCAL_SOURCE_SUFFIX = "_local"
        val FETCHED_TYPES =
            listOf(
                MediaSegmentType.INTRO,
                MediaSegmentType.RECAP,
                MediaSegmentType.PREVIEW,
                MediaSegmentType.COMMERCIAL,
                MediaSegmentType.OUTRO,
            )
    }
}
