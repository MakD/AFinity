package com.makd.afinity.data.repository.segments

import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.toAfinitySegment
import com.makd.afinity.data.repository.DatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.MediaSegmentsApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinSegmentsRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val databaseRepository: DatabaseRepository
) : SegmentsRepository {

    override suspend fun getSegments(itemId: UUID): List<AfinitySegment> = withContext(Dispatchers.IO) {
        return@withContext try {
            val cachedSegments = databaseRepository.getSegmentsForItem(itemId)
            if (cachedSegments.isNotEmpty()) {
                Timber.d("Found ${cachedSegments.size} cached segments for item $itemId")
                return@withContext cachedSegments
            }

            val mediaSegmentsApi = MediaSegmentsApi(apiClient)
            val response = mediaSegmentsApi.getItemSegments(itemId)

            val segments = response.content?.items?.map { mediaSegmentDto ->
                mediaSegmentDto.toAfinitySegment()
            } ?: emptyList()

            Timber.d("Fetched ${segments.size} segments from API for item $itemId")

            segments.forEach { segment ->
                try {
                    databaseRepository.insertSegment(segment, itemId)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to cache segment in database")
                }
            }

            segments
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch segments for item $itemId")
            emptyList()
        }
    }
}