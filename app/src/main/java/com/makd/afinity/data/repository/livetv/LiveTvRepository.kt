package com.makd.afinity.data.repository.livetv

import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.ChannelType
import java.time.LocalDateTime
import java.util.UUID

interface LiveTvRepository {

    suspend fun getChannels(
        type: ChannelType? = null,
        isFavorite: Boolean? = null,
        isMovie: Boolean? = null,
        isSeries: Boolean? = null,
        isNews: Boolean? = null,
        isKids: Boolean? = null,
        isSports: Boolean? = null,
        limit: Int? = null,
    ): List<AfinityChannel>

    suspend fun getChannel(channelId: UUID): AfinityChannel?

    suspend fun getPrograms(
        channelIds: List<UUID>? = null,
        minStartDate: LocalDateTime? = null,
        maxEndDate: LocalDateTime? = null,
        hasAired: Boolean? = null,
        isMovie: Boolean? = null,
        isSeries: Boolean? = null,
        isNews: Boolean? = null,
        isKids: Boolean? = null,
        isSports: Boolean? = null,
        limit: Int? = null,
    ): List<AfinityProgram>

    suspend fun getCurrentProgram(channelId: UUID): AfinityProgram?

    suspend fun getRecommendedPrograms(
        isAiring: Boolean = true,
        limit: Int = 20,
    ): List<AfinityProgram>

    suspend fun getChannelStreamUrl(channelId: UUID): String?

    suspend fun toggleChannelFavorite(channelId: UUID): Boolean

    suspend fun hasLiveTvAccess(): Boolean
}
