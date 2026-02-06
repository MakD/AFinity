package com.makd.afinity.data.repository.segments

import com.makd.afinity.data.models.media.AfinitySegment
import java.util.UUID

interface SegmentsRepository {
    suspend fun getSegments(itemId: UUID): List<AfinitySegment>
}
