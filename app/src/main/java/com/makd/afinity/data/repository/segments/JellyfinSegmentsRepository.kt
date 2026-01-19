package com.makd.afinity.data.repository.segments

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.toAfinitySegment
import com.makd.afinity.data.repository.DatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.operations.MediaSegmentsApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinSegmentsRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository
) : SegmentsRepository {

    override suspend fun getSegments(itemId: UUID): List<AfinitySegment> = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Checking database for cached segments for item $itemId")
            val cachedSegments = databaseRepository.getSegmentsForItem(itemId)
            if (cachedSegments.isNotEmpty()) {
                Timber.i("Loaded ${cachedSegments.size} segments from database for item $itemId")
                return@withContext cachedSegments
            }

            Timber.d("No cached segments found in database, fetching from API")

            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val mediaSegmentsApi = MediaSegmentsApi(apiClient)
                val response = mediaSegmentsApi.getItemSegments(itemId)

                val segments = response.content?.items?.map { mediaSegmentDto ->
                    mediaSegmentDto.toAfinitySegment()
                } ?: emptyList()

                Timber.d("Fetched ${segments.size} segments from API for item $itemId")

                if (segments.isNotEmpty()) {
                    segments.forEach { segment ->
                        try {
                            Timber.d("Caching segment ${segment.type} (${segment.startTicks}-${segment.endTicks}) for item $itemId")
                            databaseRepository.insertSegment(segment, itemId)
                            Timber.d("Successfully cached segment ${segment.type}")
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to cache segment ${segment.type} in database")
                        }
                    }
                }

                segments
            } catch (e: Exception) {
                Timber.w(e, "API fetch failed, checking database again in case segments were cached")
                val fallbackSegments = databaseRepository.getSegmentsForItem(itemId)
                if (fallbackSegments.isNotEmpty()) {
                    Timber.i("Loaded ${fallbackSegments.size} segments from database after API failure")
                } else {
                    Timber.d("No segments available offline for item $itemId")
                }
                fallbackSegments
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get segments for item $itemId")
            emptyList()
        }
    }
}