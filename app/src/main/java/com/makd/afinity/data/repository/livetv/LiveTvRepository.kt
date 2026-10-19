package com.makd.afinity.data.repository.livetv

import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.AfinityRecording
import com.makd.afinity.data.models.livetv.ChannelFavoriteChange
import com.makd.afinity.data.models.livetv.ChannelSortOrder
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.models.livetv.LiveTvSimilarItem
import com.makd.afinity.data.models.livetv.ProgramCategory
import com.makd.afinity.data.models.livetv.RecordingPadding
import com.makd.afinity.data.models.livetv.SeriesRecordingOptions
import com.makd.afinity.data.models.livetv.SeriesRecordingRule
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

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

    suspend fun getChannelsResult(
        type: ChannelType? = null,
        isFavorite: Boolean? = null,
        isMovie: Boolean? = null,
        isSeries: Boolean? = null,
        isNews: Boolean? = null,
        isKids: Boolean? = null,
        isSports: Boolean? = null,
        limit: Int? = null,
        sortOrder: ChannelSortOrder = ChannelSortOrder.NUMBER,
        favoritesFirst: Boolean = false,
        withCurrentProgram: Boolean = true,
    ): Result<List<AfinityChannel>>

    fun getCachedChannels(
        type: ChannelType? = null,
        isFavorite: Boolean? = null,
        isMovie: Boolean? = null,
        isSeries: Boolean? = null,
        isNews: Boolean? = null,
        isKids: Boolean? = null,
        isSports: Boolean? = null,
        limit: Int? = null,
        sortOrder: ChannelSortOrder = ChannelSortOrder.NUMBER,
        favoritesFirst: Boolean = false,
    ): List<AfinityChannel>?

    suspend fun getCurrentProgramsResult(channelIds: List<UUID>): Result<Map<UUID, AfinityProgram>>

    suspend fun getChannel(channelId: UUID): AfinityChannel?

    suspend fun getChannelResult(channelId: UUID): Result<AfinityChannel>

    suspend fun getChannelScheduleResult(channelId: UUID): Result<List<AfinityProgram>>

    suspend fun getProgramResult(programId: UUID): Result<AfinityProgram>

    suspend fun getSimilarToProgram(programId: UUID, limit: Int): List<LiveTvSimilarItem>

    suspend fun scheduleRecordingResult(programId: UUID): Result<Unit>

    suspend fun scheduleSeriesRecordingResult(programId: UUID): Result<Unit>

    suspend fun cancelSeriesRecordingResult(seriesTimerId: String): Result<Unit>

    suspend fun cancelRecordingResult(timerId: String): Result<Unit>

    suspend fun getRecordingPaddingResult(timerId: String): Result<RecordingPadding>

    suspend fun updateRecordingPaddingResult(
        timerId: String,
        padding: RecordingPadding,
    ): Result<Unit>

    suspend fun getScheduledRecordingsResult(): Result<List<AfinityProgram>>

    suspend fun getSeriesRecordingRulesResult(): Result<List<SeriesRecordingRule>>

    suspend fun getRecordingsResult(): Result<List<AfinityRecording>>

    suspend fun getSeriesRecordingOptionsResult(
        seriesTimerId: String
    ): Result<SeriesRecordingOptions>

    suspend fun updateSeriesRecordingOptionsResult(
        seriesTimerId: String,
        options: SeriesRecordingOptions,
    ): Result<Unit>

    suspend fun getOnNowResult(limit: Int): Result<List<AfinityProgram>>

    suspend fun getUpcomingResult(
        category: ProgramCategory,
        limit: Int,
    ): Result<List<AfinityProgram>>

    suspend fun getGuideDataEnd(): LocalDateTime?

    suspend fun getGuidePrograms(
        channelIds: List<UUID>,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): List<AfinityProgram>

    suspend fun getGuideProgramsResult(
        channelIds: List<UUID>,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): Result<List<AfinityProgram>>

    suspend fun getChannelPlaybackInfo(
        channelId: UUID,
        allowDirectPlay: Boolean = true,
    ): LiveTvPlaybackInfo?

    suspend fun closeLiveStream(liveStreamId: String)

    suspend fun toggleChannelFavorite(channelId: UUID): Boolean

    val channelFavoriteChanges: SharedFlow<ChannelFavoriteChange>

    val timerChanges: Flow<Unit>

    val hasLiveTvAccessFlow: Flow<Boolean>

    suspend fun hasLiveTvAccessResult(): Result<Boolean>
}
